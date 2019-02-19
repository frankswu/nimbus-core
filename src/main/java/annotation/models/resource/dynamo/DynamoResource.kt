package annotation.models.resource.dynamo

import annotation.annotations.keyvalue.KeyType
import annotation.models.persisted.NimbusState
import annotation.models.resource.Resource
import com.google.gson.JsonArray
import com.google.gson.JsonObject

class DynamoResource(
        private val tableName: String,
        nimbusState: NimbusState
) : Resource(nimbusState) {

    private val keys: MutableList<Attribute> = mutableListOf()
    private val attributes: MutableList<Attribute> = mutableListOf()


    override fun toCloudFormation(): JsonObject {
        val dynamoTable = JsonObject()

        dynamoTable.addProperty("Type", "AWS::DynamoDB::Table")

        val properties = getProperties()
        properties.addProperty("TableName", tableName)
        properties.add("AttributeDefinitions", attributeDefsCloudFormation())
        properties.add("KeySchema", keyDefsCloudFormation())

        val provisionedThroughput = JsonObject()
        provisionedThroughput.addProperty("ReadCapacityUnits", 1)
        provisionedThroughput.addProperty("WriteCapacityUnits", 1)
        properties.add("ProvisionedThroughput", provisionedThroughput)

        dynamoTable.add("Properties", properties)
        return dynamoTable
    }

    override fun getName(): String {
        return tableName
    }

    private fun attributeDefsCloudFormation(): JsonArray {
        val attributeDefs = JsonArray()

        for (attribute in attributes) {
            val attributeJson = JsonObject()
            attributeJson.addProperty("AttributeName", attribute.name)
            attributeJson.addProperty("AttributeType", attribute.type)
            attributeDefs.add(attributeJson)
        }

        return attributeDefs
    }

    private fun keyDefsCloudFormation(): JsonArray {
        val keyDefs = JsonArray()

        for (key in keys) {
            val attributeJson = JsonObject()
            attributeJson.addProperty("AttributeName", key.name)
            attributeJson.addProperty("KeyType", key.type)
            keyDefs.add(attributeJson)
        }

        return keyDefs
    }

    fun addHashKey(name: String, type: Any) {
        keys.add(Attribute(name, "HASH"))
        attributes.add(Attribute(name, getDynamoType(type)))
    }

    private fun getDynamoType(obj: Any): String {
        if (obj is KeyType) {
            return when (obj) {
                KeyType.STRING -> "S"
                KeyType.NUMBER -> "N"
                KeyType.BOOLEAN -> "B"
                KeyType.OBJECT -> "S"
            }
        }
        return when (obj) {
            is String -> "S"
            is Number -> "N"
            is Boolean -> "BOOL"
            else -> "S"
        }
    }

    private data class Attribute(val name: String, val type: String)
}