package com.nimbusframework.nimbusaws.annotation.services

import com.nimbusframework.nimbuscore.annotations.function.HttpServerlessFunction
import com.nimbusframework.nimbusaws.cloudformation.outputs.RestApiOutput
import com.nimbusframework.nimbusaws.cloudformation.outputs.WebSocketApiOutput
import com.nimbusframework.nimbusaws.cloudformation.processing.MethodInformation
import com.nimbusframework.nimbusaws.cloudformation.resource.IamRoleResource
import com.nimbusframework.nimbusaws.cloudformation.resource.LogGroupResource
import com.nimbusframework.nimbusaws.cloudformation.resource.NimbusBucketResource
import com.nimbusframework.nimbusaws.cloudformation.resource.Resource
import com.nimbusframework.nimbusaws.cloudformation.resource.basic.CronRule
import com.nimbusframework.nimbusaws.cloudformation.resource.dynamo.DynamoStreamResource
import com.nimbusframework.nimbusaws.cloudformation.resource.function.FunctionConfig
import com.nimbusframework.nimbusaws.cloudformation.resource.function.FunctionEventMappingResource
import com.nimbusframework.nimbusaws.cloudformation.resource.function.FunctionPermissionResource
import com.nimbusframework.nimbusaws.cloudformation.resource.function.FunctionResource
import com.nimbusframework.nimbusaws.cloudformation.resource.http.*
import com.nimbusframework.nimbusaws.cloudformation.resource.websocket.*
import com.google.gson.JsonObject
import com.nimbusframework.nimbuscore.annotations.function.HttpMethod
import com.nimbusframework.nimbusaws.cloudformation.CloudFormationFiles
import com.nimbusframework.nimbuscore.persisted.ExportInformation
import com.nimbusframework.nimbuscore.persisted.HandlerInformation
import com.nimbusframework.nimbuscore.persisted.NimbusState

class FunctionEnvironmentService(
        private val cloudFormationFiles: MutableMap<String, CloudFormationFiles>,
        private val nimbusState: NimbusState
) {


    fun newFunction(handler: String, methodInformation: MethodInformation, handlerInformation: HandlerInformation, functionConfig: FunctionConfig): FunctionResource {
        val function = FunctionResource(handler, methodInformation, functionConfig, handlerInformation, nimbusState)

        //AWS Functions require org.joda.time.DateTime internally https://github.com/aws/aws-lambda-java-libs/issues/72
        function.addExtraDependency("org.joda.time.DateTime")

        val logGroup = LogGroupResource(methodInformation.className, methodInformation.methodName, function, nimbusState, functionConfig.stage)
        val bucket = NimbusBucketResource(nimbusState, functionConfig.stage)

        val iamRoleResource = IamRoleResource(function.getShortName(), nimbusState, functionConfig.stage)
        iamRoleResource.addAllowStatement("logs:CreateLogStream", logGroup, ":*")
        iamRoleResource.addAllowStatement("logs:PutLogEvents", logGroup, ":*:*")

        function.setIamRoleResource(iamRoleResource)

        val cloudFormationDocuments = cloudFormationFiles.getOrPut(functionConfig.stage) { CloudFormationFiles(
                nimbusState,
                functionConfig.stage
        ) }
        val updateResources = cloudFormationDocuments.updateTemplate.resources
        val createResources = cloudFormationDocuments.createTemplate.resources

        updateResources.addResource(iamRoleResource)
        updateResources.addResource(function)
        updateResources.addResource(logGroup)
        updateResources.addResource(bucket)

        createResources.addResource(bucket)

        return function
    }

    fun newHttpMethod(httpFunction: HttpServerlessFunction, function: FunctionResource) {
        val pathParts = httpFunction.path.split("/")

        val updateTemplate = cloudFormationFiles[function.stage]!!.updateTemplate
        val updateResources = updateTemplate.resources
        val updateOutputs = updateTemplate.outputs

        val restApi = if (updateTemplate.rootRestApi == null) {
            val restApi = RestApi(nimbusState, function.stage)
            updateTemplate.rootRestApi = restApi
            updateResources.addResource(restApi)
            val httpApiOutput = RestApiOutput(restApi, nimbusState)
            updateOutputs.addOutput(httpApiOutput)

            val exportInformation = ExportInformation(
                    httpApiOutput.getExportName(),
                    "Created REST API. Base URL is ",
                    "\${NIMBUS_REST_API_URL}")

            val exports = nimbusState.exports.getOrPut(function.stage) { mutableListOf()}
            exports.add(exportInformation)

            restApi
        } else {
            updateTemplate.rootRestApi!!
        }

        val apiGatewayDeployment = if (updateTemplate.apiGatewayDeployment == null) {
            val apiGatewayDeployment = ApiGatewayDeployment(restApi, nimbusState)
            updateTemplate.apiGatewayDeployment = apiGatewayDeployment
            updateResources.addResource(apiGatewayDeployment)
            apiGatewayDeployment
        } else {
            updateTemplate.apiGatewayDeployment!!
        }

        var resource: AbstractRestResource = restApi

        for (part in pathParts) {
            if (part.isNotEmpty()) {
                resource = RestApiResource(resource, part, nimbusState)
                updateResources.addResource(resource)
            }
        }

        val method = httpFunction.method.name
        val restMethod = RestMethod(resource, method, mapOf(), function, nimbusState)
        apiGatewayDeployment.addDependsOn(restMethod)
        updateResources.addResource(restMethod)

        if (httpFunction.method != HttpMethod.OPTIONS && httpFunction.method != HttpMethod.ANY) {
            if (httpFunction.allowedCorsOrigin.isNotEmpty()) {

                val newCorsMethod = CorsRestMethod(
                        resource,
                        updateTemplate,
                        nimbusState
                )
                val existingCorsMethod = updateResources.get(newCorsMethod.getName())
                val corsMethod = if (existingCorsMethod == null) {
                    apiGatewayDeployment.addDependsOn(newCorsMethod)
                    updateResources.addResource(newCorsMethod)
                    newCorsMethod
                } else {
                    existingCorsMethod as CorsRestMethod
                }
                corsMethod.addHeaders(httpFunction.allowedCorsHeaders)
                corsMethod.addOrigin(httpFunction.allowedCorsOrigin)
                corsMethod.addMethod(httpFunction.method)
            }
        }

        val permission = FunctionPermissionResource(function, restApi, nimbusState)
        updateResources.addResource(permission)

    }

    fun newStoreTrigger(store: Resource, function: FunctionResource) {
        val cfDocuments = cloudFormationFiles[function.stage]!!
        val updateResources = cfDocuments.updateTemplate.resources

        val eventMapping = FunctionEventMappingResource(
                store.getAttribute("StreamArn"),
                store.getName(),
                1,
                function,
                true,
                nimbusState
        )

        updateResources.addResource(eventMapping)

        val streamSpecification = JsonObject()
        streamSpecification.addProperty("StreamViewType", "NEW_AND_OLD_IMAGES")
        store.addExtraProperty("StreamSpecification", streamSpecification)

        val dynamoStreamResource = DynamoStreamResource(store, nimbusState)

        function.getIamRoleResource().addAllowStatement("dynamodb:*", dynamoStreamResource, "")
    }

    fun newCronTrigger(cron: String, function: FunctionResource) {
        val cfDocuments = cloudFormationFiles[function.stage]!!
        val updateResources = cfDocuments.updateTemplate.resources

        val cronRule = CronRule(cron, function, nimbusState)
        val lambdaPermissionResource = FunctionPermissionResource(function, cronRule, nimbusState)

        updateResources.addResource(cronRule)
        updateResources.addResource(lambdaPermissionResource)
    }

    fun newWebSocketRoute(routeKey: String, function: FunctionResource) {
        val updateTemplate = cloudFormationFiles[function.stage]!!.updateTemplate
        val updateResources = updateTemplate.resources
        val updateOutputs = updateTemplate.outputs

        val webSocketApi = if (updateTemplate.rootWebSocketApi == null) {
            val webSocketApi = WebSocketApi(nimbusState, function.stage)
            updateTemplate.rootWebSocketApi = webSocketApi
            updateResources.addResource(webSocketApi)
            val webSocketApiOutput = WebSocketApiOutput(webSocketApi, nimbusState)
            updateOutputs.addOutput(webSocketApiOutput)

            val exportInformation = ExportInformation(
                    webSocketApiOutput.getExportName(),
                    "Created WebSocket API. Base URL is ",
                    "\${NIMBUS_WEBSOCKET_API_URL}")

            val exports = nimbusState.exports.getOrPut(function.stage) { mutableListOf()}
            exports.add(exportInformation)

            webSocketApi
        } else {
            updateTemplate.rootWebSocketApi!!
        }

        val webSocketDeployment = if (updateTemplate.webSocketDeployment == null) {
            val webSocketDeployment = WebSocketDeployment(webSocketApi, nimbusState)
            updateTemplate.webSocketDeployment = webSocketDeployment
            val stage = WebSocketStage(webSocketApi, webSocketDeployment, nimbusState)
            updateResources.addResource(webSocketDeployment)
            updateResources.addResource(stage)
            webSocketDeployment
        } else {
            updateTemplate.webSocketDeployment!!
        }

        val integration = WebSocketIntegration(webSocketApi, function, routeKey, nimbusState)
        val route = WebSocketRoute(webSocketApi, integration, routeKey, nimbusState)

        webSocketDeployment.addDependsOn(route)

        updateResources.addResource(integration)
        updateResources.addResource(route)

        val functionPermissionResource = FunctionPermissionResource(function, webSocketApi, nimbusState)

        updateResources.addResource(functionPermissionResource)
    }
}