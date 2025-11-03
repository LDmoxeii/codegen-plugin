package com.only4.codegen.misc

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.only4.codegen.CodegenExtension

object AliasResolver {
    private val gson = Gson()

    private fun loadDefault(resourcePath: String): Map<String, String> {
        val cl = Thread.currentThread().contextClassLoader
        val stream = cl.getResourceAsStream(resourcePath)
            ?: AliasResolver::class.java.classLoader.getResourceAsStream(resourcePath)
            ?: return emptyMap()
        stream.use { s ->
            val json = s.bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, String>>() {}.type
            return gson.fromJson<Map<String, String>>(json, type) ?: emptyMap()
        }
    }

    fun aggregateAliases(ext: CodegenExtension): Map<String, String> {
        val defaults = loadDefault("aliases/aggregate.json")
        return defaults + (ext.aggregateTagAliases.getOrElse(emptyMap()))
    }

    fun designAliases(ext: CodegenExtension): Map<String, String> {
        val defaults = loadDefault("aliases/design.json")
        return defaults + (ext.designTagAliases.getOrElse(emptyMap()))
    }
}

