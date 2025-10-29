package com.only4.codegen.context.design.builders

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.only4.codegen.context.ContextBuilder
import com.only4.codegen.context.design.MutableDesignContext
import com.only4.codegen.context.design.models.DesignElement
import java.io.File
import java.nio.charset.Charset

class DesignContextBuilder : ContextBuilder<MutableDesignContext> {

    override val order: Int = 10

    override fun build(context: MutableDesignContext) {
        val designFiles = context.baseMap["designFiles"] as? Set<File> ?: emptySet()


        if (designFiles.isEmpty()) {
            return
        }

        designFiles.forEach { file ->
            loadDesignFile(file, context)
        }
    }

    private fun loadDesignFile(file: File, context: MutableDesignContext) {
        val encoding = context.getString("designEncoding", "UTF-8")
        val content = file.readText(Charset.forName(encoding))
        val jsonArray = JSON.parseArray(content)

        parseJsonDesignArray(jsonArray, context)
    }

    private fun parseJsonDesignArray(jsonArray: JSONArray, context: MutableDesignContext) {
        // 定义支持的设计类型别名映射（基于条目内的 tag 字段）
        val typeAliasMap = mapOf(
            // command
            "cmd" to "cmd",
            "command" to "cmd",
            "commands" to "cmd",

            // query
            "qry" to "qry",
            "query" to "qry",
            "queries" to "qry",

            // saga
            "saga" to "saga",
            "sagas" to "saga",

            // client
            "cli" to "cli",
            "client" to "cli",
            "clients" to "cli",

            // integration event
            "ie" to "ie",
            "integration_event" to "ie",
            "integration_events" to "ie",

            // domain event
            "de" to "de",
            "domain_event" to "de",
            "domain_events" to "de",

            // domain service
            "svc" to "svc",
            "service" to "svc",
            "services" to "svc",
            "domain_service" to "svc",
            "domain_services" to "svc"
        )

        jsonArray.forEach { item ->
            val obj = item as? JSONObject ?: return@forEach
            val rawTag = obj.getString("tag")?.lowercase() ?: return@forEach
            val normalizedType = typeAliasMap[rawTag] ?: rawTag

            val element = parseDesignElement(normalizedType, obj)
            context.designElementMap
                .computeIfAbsent(normalizedType) { mutableListOf() }
                .add(element)
        }
    }

    private fun parseDesignElement(type: String, jsonObj: JSONObject): DesignElement {
        val `package` = jsonObj.getString("package") ?: ""
        val name = jsonObj.getString("name") ?: ""
        val desc = jsonObj.getString("desc") ?: ""
        val aggregates = jsonObj.getJSONArray("aggregates")?.map { it.toString() }

        val metadata = mutableMapOf<String, Any?>()
        jsonObj.keys.forEach { key ->
            when (key) {
                "package", "name", "aggregate", "aggregates", "desc", "tag" -> {} // 跳过基础字段

                "metadata" -> {
                    // 如果有 metadata 对象,合并到 map
                    val metadataObj = jsonObj.getJSONObject(key)
                    metadataObj?.keys?.forEach { metaKey ->
                        metadata[metaKey] = metadataObj[metaKey]
                    }
                }

                else -> {
                    // 其他字段直接作为 metadata
                    metadata[key] = jsonObj[key]
                }
            }
        }

        return DesignElement(
            type = type,
            `package` = `package`,
            name = name,
            aggregates = aggregates,
            desc = desc,
            metadata = metadata
        )
    }
}
