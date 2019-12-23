package com.nimbusframework.nimbusaws.annotation.services.useresources

import com.nimbusframework.nimbusaws.CompileStateService
import com.nimbusframework.nimbusaws.annotation.services.FunctionEnvironmentService
import com.nimbusframework.nimbusaws.annotation.services.functions.HttpFunctionResourceCreator
import com.nimbusframework.nimbusaws.annotation.services.functions.QueueFunctionResourceCreator
import com.nimbusframework.nimbusaws.annotation.services.resources.RelationalDatabaseResourceCreator
import com.nimbusframework.nimbusaws.cloudformation.CloudFormationFiles
import com.nimbusframework.nimbusaws.cloudformation.resource.IamRoleResource
import com.nimbusframework.nimbusaws.cloudformation.resource.function.FunctionResource
import com.nimbusframework.nimbuscore.persisted.ClientType
import com.nimbusframework.nimbuscore.persisted.NimbusState
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.AnnotationSpec
import io.mockk.mockk
import javax.annotation.processing.Messager
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.util.Elements

class UsesRelationalDatabaseProcessorTest: AnnotationSpec() {

    private lateinit var usesRelationalDatabaseProcessor: UsesRelationalDatabaseProcessor
    private lateinit var roundEnvironment: RoundEnvironment
    private lateinit var cfDocuments: MutableMap<String, CloudFormationFiles>
    private lateinit var nimbusState: NimbusState
    private lateinit var elements: Elements
    private lateinit var messager: Messager

    @BeforeEach
    fun setup() {
        nimbusState = NimbusState()
        cfDocuments = mutableMapOf()
        roundEnvironment = mockk()
        messager = mockk(relaxed = true)

        val compileState = CompileStateService("models/RelationalDatabaseModel.java", "handlers/UsesRDBHandler.java")
        elements = compileState.elements

        RelationalDatabaseResourceCreator(roundEnvironment, cfDocuments, nimbusState).handleAgnosticType(elements.getTypeElement("models.RelationalDatabaseModel"))

        HttpFunctionResourceCreator(cfDocuments, nimbusState, compileState.processingEnvironment).handleElement(elements.getTypeElement("handlers.UsesRDBHandler").enclosedElements[1], FunctionEnvironmentService(cfDocuments, nimbusState), mutableListOf())

        usesRelationalDatabaseProcessor = UsesRelationalDatabaseProcessor(cfDocuments, compileState.processingEnvironment, nimbusState)
    }

    @Test
    fun correctlySetsPermissions() {
        val functionResource = cfDocuments["dev"]!!.updateTemplate.resources.get("UsesRDBHandlerfuncFunction") as FunctionResource

        usesRelationalDatabaseProcessor.handleUseResources(elements.getTypeElement("handlers.UsesRDBHandler").enclosedElements[1], functionResource)

        functionResource.usesClient(ClientType.Database) shouldBe true
        functionResource.getJsonEnvValue("testRelationalDatabaseRdsInstance_CONNECTION_URL") shouldNotBe null
        functionResource.getStrEnvValue("testRelationalDatabaseRdsInstance_USERNAME") shouldBe "username"
        functionResource.getStrEnvValue("testRelationalDatabaseRdsInstance_PASSWORD") shouldBe "password"

        functionResource.containsDependency(com.mysql.cj.jdbc.Driver::class.java.canonicalName) shouldBe true
    }

}