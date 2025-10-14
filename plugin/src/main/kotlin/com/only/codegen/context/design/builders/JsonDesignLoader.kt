package com.only.codegen.context.design.builders

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.only.codegen.context.design.DesignContextBuilder
import com.only.codegen.context.design.DesignElement
import com.only.codegen.context.design.MutableDesignContext
import org.gradle.api.logging.Logging
import java.io.File
import java.nio.charset.Charset

/**
 * JSON 设计文件加载器
 *
 * Order: 10 (最先执行)
 * 职责: 解析 JSON 设计文件,填充 designElementMap
 */
class JsonDesignLoader : DesignContextBuilder {

    private val logger = Logging.getLogger(JsonDesignLoader::class.java)

    override val order: Int = 10

    override fun build(context: MutableDesignContext) {
        val designFiles = getDesignFiles(context)

        if (designFiles.isEmpty()) {
            logger.warn("No design files configured")
            return
        }

        designFiles.forEach { file ->
            if (!file.exists()) {
                logger.warn("Design file not found: ${file.absolutePath}")
                return@forEach
            }

            logger.lifecycle("Loading design file: ${file.absolutePath}")
            loadDesignFile(file, context)
        }

        logger.lifecycle("Loaded ${context.designElementMap.values.sumOf { it.size }} design elements")
    }

    private fun getDesignFiles(context: MutableDesignContext): List<File> {
        // 从配置中获取设计文件路径
        val designFilesConfig = context.getString("designFiles", "")

        if (designFilesConfig.isBlank()) {
            return emptyList()
        }

        return designFilesConfig.split(";", ",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { File(it) }
    }

    private fun loadDesignFile(file: File, context: MutableDesignContext) {
        try {
            val encoding = context.getString("designEncoding", "UTF-8")
            val content = file.readText(Charset.forName(encoding))
            val jsonObj = JSON.parseObject(content)

            parseJsonDesign(jsonObj, context)
        } catch (e: Exception) {
            logger.error("Failed to load design file: ${file.absolutePath}", e)
        }
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
        val name = jsonObj.getString("name") ?: ""
        val aggregate = jsonObj.getString("aggregate")
        val desc = jsonObj.getString("desc") ?: ""

        // 解析 aggregates 数组 (多聚合支持)
        val aggregates = jsonObj.getJSONArray("aggregates")?.map { it.toString() }

        // 解析 metadata 字段
        val metadata = mutableMapOf<String, Any?>()
        jsonObj.keys.forEach { key ->
            when (key) {
                "name", "aggregate", "aggregates", "desc" -> {} // 跳过基础字段
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
            name = name,
            aggregate = aggregate,
            aggregates = aggregates,
            desc = desc,
            metadata = metadata
        )
    }
}
