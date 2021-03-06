package com.nimbusframework.nimbusaws.annotation.services.useresources

import com.nimbusframework.nimbusaws.annotation.services.ResourceFinder
import com.nimbusframework.nimbusaws.cloudformation.CloudFormationFiles
import com.nimbusframework.nimbusaws.cloudformation.resource.function.FunctionResource
import com.nimbusframework.nimbuscore.annotations.notification.UsesNotificationTopic
import com.nimbusframework.nimbuscore.persisted.ClientType
import com.nimbusframework.nimbuscore.persisted.NimbusState
import com.nimbusframework.nimbuscore.wrappers.annotations.datamodel.UsesNotificationTopicAnnotation
import javax.annotation.processing.Messager
import javax.lang.model.element.Element
import javax.tools.Diagnostic

class UsesNotificationTopicProcessor(
        private val cfDocuments: Map<String, CloudFormationFiles>,
        private val messager: Messager,
        private val resourceFinder: ResourceFinder,
        nimbusState: NimbusState
): UsesResourcesProcessor(nimbusState)  {


    override fun handleUseResources(serverlessMethod: Element, functionResource: FunctionResource) {
        val iamRoleResource = functionResource.getIamRoleResource()

        for (notificationTopic in serverlessMethod.getAnnotationsByType(UsesNotificationTopic::class.java)) {
            functionResource.addClient(ClientType.Notification)

            for (stage in stageService.determineStages(notificationTopic.stages)) {
                if (stage == functionResource.stage) {

                    val snsTopicResource = resourceFinder.getNotificationTopicResource(UsesNotificationTopicAnnotation(notificationTopic), serverlessMethod, stage)

                    if (snsTopicResource == null) {
                        messager.printMessage(Diagnostic.Kind.ERROR, "Unable to find notification topic class", serverlessMethod)
                        return
                    }

                    val cloudFormationDocuments = cfDocuments[stage]
                    if (cloudFormationDocuments == null) {
                        messager.printMessage(Diagnostic.Kind.ERROR, "No serverless function annotation found for UsesNotificationTopic", serverlessMethod)
                        return
                    }

                    cloudFormationDocuments.updateTemplate.resources.addResource(snsTopicResource)

                    functionResource.addEnvVariable("SNS_TOPIC_ARN_" + snsTopicResource.topic.toUpperCase(), snsTopicResource.getRef())
                    iamRoleResource.addAllowStatement("sns:Subscribe", snsTopicResource, "")
                    iamRoleResource.addAllowStatement("sns:Unsubscribe", snsTopicResource, "")
                    iamRoleResource.addAllowStatement("sns:Publish", snsTopicResource, "")
                }
            }
        }
    }
}