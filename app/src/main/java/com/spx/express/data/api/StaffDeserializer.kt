package com.spx.express.data.api

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.spx.express.data.model.Staff
import java.lang.reflect.Type

class StaffDeserializer : JsonDeserializer<Staff> {
    @Throws(JsonParseException::class)
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Staff {
        val obj = json.asJsonObject
        val stfId = if (obj.has("Stf_Id") && !obj.get("Stf_Id").isJsonNull) obj.get("Stf_Id").asInt else 0
        val stfEmail = if (obj.has("Stf_Email") && !obj.get("Stf_Email").isJsonNull) obj.get("Stf_Email").asString else ""
        val stfPasswordHash = if (obj.has("Stf_Password_Hash") && !obj.get("Stf_Password_Hash").isJsonNull) obj.get("Stf_Password_Hash").asString else ""
        val stfFname = if (obj.has("Stf_Fname") && !obj.get("Stf_Fname").isJsonNull) obj.get("Stf_Fname").asString else ""
        val stfLname = if (obj.has("Stf_Lname") && !obj.get("Stf_Lname").isJsonNull) obj.get("Stf_Lname").asString else ""
        val stfRole = if (obj.has("Stf_Role") && !obj.get("Stf_Role").isJsonNull) obj.get("Stf_Role").asString else "staff"
        
        val stfContactNumber = if (obj.has("Stf_Contact_Number") && !obj.get("Stf_Contact_Number").isJsonNull) {
            obj.get("Stf_Contact_Number").asString
        } else null
        
        val stfBrchId = if (obj.has("Stf_Brch_Id") && !obj.get("Stf_Brch_Id").isJsonNull) {
            obj.get("Stf_Brch_Id").asInt
        } else null
        
        val stfIsActive = if (obj.has("Stf_Is_Active") && !obj.get("Stf_Is_Active").isJsonNull) obj.get("Stf_Is_Active").asInt else 1
        val stfCreatedAt = if (obj.has("Stf_Created_At") && !obj.get("Stf_Created_At").isJsonNull) obj.get("Stf_Created_At").asString else ""

        return Staff(
            stfId = stfId,
            stfEmail = stfEmail,
            stfPasswordHash = stfPasswordHash,
            stfFname = stfFname,
            stfLname = stfLname,
            stfRole = stfRole,
            stfContactNumber = stfContactNumber,
            stfBrchId = stfBrchId,
            stfIsActive = stfIsActive,
            stfCreatedAt = stfCreatedAt
        )
    }
}
