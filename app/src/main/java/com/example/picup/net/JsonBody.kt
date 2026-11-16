package com.example.picup.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Reading the three host APIs without generating a data class per response. */
object JsonBody {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun parse(text: String): JsonElement = json.parseToJsonElement(text)

    fun objectOf(text: String): JsonObject? =
        runCatching { parse(text).jsonObject }.getOrNull()

    fun arrayOf(text: String): JsonArray? =
        runCatching { parse(text).jsonArray }.getOrNull()

    fun build(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
        buildJsonObject(block).toString()
}

fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull

fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull

fun JsonObject.obj(key: String): JsonObject? =
    (this[key] as? JsonObject)

fun JsonObject.arr(key: String): JsonArray? =
    (this[key] as? JsonArray)

fun JsonElement.strOrNull(): String? =
    runCatching { jsonPrimitive.content }.getOrNull()