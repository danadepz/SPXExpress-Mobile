package com.spx.express.data.api

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class FirebaseMapDeserializer : JsonDeserializer<Map<String, *>> {
    @Throws(JsonParseException::class)
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Map<String, *> {
        val result = LinkedHashMap<String, Any>()
        
        // Extract the value type of the Map (e.g. Staff from Map<String, Staff>)
        val valueType = if (typeOfT is ParameterizedType) {
            typeOfT.actualTypeArguments[1]
        } else {
            Any::class.java
        }

        if (json.isJsonArray) {
            val jsonArray = json.asJsonArray
            for (i in 0 until jsonArray.size()) {
                val element = jsonArray.get(i)
                if (element != null && !element.isJsonNull) {
                    val value = context.deserialize<Any>(element, valueType)
                    if (value != null) {
                        result[i.toString()] = value
                    }
                }
            }
        } else if (json.isJsonObject) {
            val jsonObject = json.asJsonObject
            for ((key, element) in jsonObject.entrySet()) {
                if (element != null && !element.isJsonNull) {
                    val value = context.deserialize<Any>(element, valueType)
                    if (value != null) {
                        result[key] = value
                    }
                }
            }
        }
        return result
    }
}
