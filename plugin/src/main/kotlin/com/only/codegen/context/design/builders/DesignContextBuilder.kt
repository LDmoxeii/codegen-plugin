package com.only.codegen.context.design.builders

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.only.codegen.context.ContextBuilder
import com.only.codegen.context.design.MutableDesignContext
import com.only.codegen.context.design.models.DesignElement
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
        val jsonObj = JSON.parseObject(content)

        parseJsonDesign(jsonObj, context)
    }

    private fun parseJsonDesign(jsonObj: JSONObject, context: MutableDesignContext) {
        // 定义支持的设计类型别名映射
        val typeAliasMap = mapOf(
            "cmd" to "cmd",
            "command" to "cmd",
            "commands" to "cmd",

            "qry" to "qry",
            "query" to "qry",
            "queries" to "qry",

            "saga" to "saga",
            "sagas" to "saga",

            "cli" to "cli",
            "client" to "cli",
            "clients" to "cli",

            "ie" to "ie",
            "integration_event" to "ie",
            "integration_events" to "ie",

            "de" to "de",
            "domain_event" to "de",
            "domain_events" to "de",

            "svc" to "svc",
            "service" to "svc",
            "services" to "svc",
            "domain_service" to "svc",
            "domain_services" to "svc"
        )

        jsonObj.keys.forEach { key ->
            val normalizedType = typeAliasMap[key.lowercase()] ?: key.lowercase()
            val elements = jsonObj.getJSONArray(key)

            elements?.forEach { item ->
                if (item is JSONObject) {
                    val element = parseDesignElement(normalizedType, item)
                    context.designElementMap
                        .computeIfAbsent(normalizedType) { mutableListOf() }
                        .add(element)
                }
            }
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
                "package", "name", "aggregate", "aggregates", "desc" -> {} // 跳过基础字段

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
