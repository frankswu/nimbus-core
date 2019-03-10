package annotation.services.functions

import annotation.annotations.function.BasicServerlessFunction
import annotation.processor.FunctionInformation
import annotation.services.FunctionEnvironmentService
import cloudformation.CloudFormationDocuments
import persisted.NimbusState
import cloudformation.resource.ResourceCollection
import cloudformation.resource.function.FunctionConfig
import wrappers.basic.BasicServerlessFunctionFileBuilder
import java.util.*
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment

class BasicFunctionResourceCreator(
        cfDocuments: MutableMap<String, CloudFormationDocuments>,
        nimbusState: NimbusState,
        processingEnv: ProcessingEnvironment
): FunctionResourceCreator(cfDocuments, nimbusState, processingEnv) {

    override fun handle(roundEnv: RoundEnvironment, functionEnvironmentService: FunctionEnvironmentService): List<FunctionInformation> {
        val annotatedElements = roundEnv.getElementsAnnotatedWith(BasicServerlessFunction::class.java)
        val results = LinkedList<FunctionInformation>()
        for (type in annotatedElements) {
            val basicFunction = type.getAnnotation(BasicServerlessFunction::class.java)
            val methodInformation = extractMethodInformation(type)

            val cloudFormationDocuments = cfDocuments.getOrPut(basicFunction.stage) {CloudFormationDocuments()}
            val updateResources = cloudFormationDocuments.updateResources

            val fileBuilder = BasicServerlessFunctionFileBuilder(
                    processingEnv,
                    methodInformation,
                    type
            )

            val handler = fileBuilder.getHandler()

            val config = FunctionConfig(basicFunction.timeout, basicFunction.memory, basicFunction.stage)
            val functionResource = functionEnvironmentService.newFunction(handler, methodInformation, config)

            //Configure cron if necessary
            if (basicFunction.cron != "") {
                functionEnvironmentService.newCronTrigger(basicFunction.cron, functionResource)
            }
            updateResources.addInvokableFunction(functionResource)

            fileBuilder.createClass()

            results.add(FunctionInformation(type, functionResource))
        }
        return results
    }

}