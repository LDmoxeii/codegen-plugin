package com.only.codegen.context.design.builders

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.only.codegen.context.design.*
import org.gradle.api.logging.Logging
import java.io.File

/**
 * KSP 元数据加载器
 *
 * Order: 15
 * 职责: 加载 KSP 生成的聚合/实体元数据 JSON
 */
class KspMetadataLoader : DesignContextBuilder {

    private val logger = Logging.getLogger(KspMetadataLoader::class.java)

    override val order: Int = 15

    override fun build(context: MutableDesignContext) {
        val kspMetadataDir = getKspMetadataDir(context)

        if (kspMetadataDir.isBlank() || !File(kspMetadataDir).exists()) {
            logger.warn("KSP metadata directory not found: $kspMetadataDir")
            return
        }

        val aggregatesFile = File(kspMetadataDir, "aggregates.json")
        val entitiesFile = File(kspMetadataDir, "entities.json")

        if (aggregatesFile.exists()) {
            loadAggregatesMetadata(aggregatesFile, context)
        }

        if (entitiesFile.exists()) {
            loadEntitiesMetadata(entitiesFile, context)
        }

        logger.lifecycle("Loaded ${context.aggregateMetadataMap.size} aggregates, " +
                "${context.entityMetadataMap.size} entities from KSP metadata")
    }

    private fun getKspMetadataDir(context: MutableDesignContext): String {
        return context.getString("kspMetadataDir", "")
    }

    private fun loadAggregatesMetadata(file: File, context: MutableDesignContext) {
        try {
            val content = file.readText()
            val jsonArray = JSON.parseArray(content)

            jsonArray.forEach { item ->
                if (item is JSONObject) {
                    val metadata = parseAggregateMetadata(item)
                    context.aggregateMetadataMap[metadata.name] = metadata
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to load aggregates metadata: ${file.absolutePath}", e)
        }
    }

    private fun loadEntitiesMetadata(file: File, context: MutableDesignContext) {
        try {
            val content = file.readText()
            val jsonArray = JSON.parseArray(content)

            jsonArray.forEach { item ->
                if (item is JSONObject) {
                    val metadata = parseEntityMetadata(item)
                    context.entityMetadataMap[metadata.name] = metadata
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to load entities metadata: ${file.absolutePath}", e)
        }
    }

    private fun parseAggregateMetadata(jsonObj: JSONObject): AggregateMetadata {
        val name = jsonObj.getString("name") ?: ""
        val fullName = jsonObj.getString("fullName") ?: ""
        val packageName = jsonObj.getString("packageName") ?: ""
        val idType = jsonObj.getString("idType")

        // 解析聚合根实体
        val rootObj = jsonObj.getJSONObject("aggregateRoot")
        val aggregateRoot = if (rootObj != null) {
            parseEntityMetadata(rootObj)
        } else {
            EntityMetadata(
                name = name,
                fullName = fullName,
                packageName = packageName,
                isAggregateRoot = true,
                idType = idType
            )
        }

        // 解析包含的实体列表
        val entitiesArray = jsonObj.getJSONArray("entities") ?: JSONArray()
        val entities = entitiesArray.mapNotNull { item ->
            if (item is JSONObject) parseEntityMetadata(item) else null
        }

        return AggregateMetadata(
            name = name,
            fullName = fullName,
            packageName = packageName,
            aggregateRoot = aggregateRoot,
            entities = entities,
            idType = idType
        )
    }

    private fun parseEntityMetadata(jsonObj: JSONObject): EntityMetadata {
        val name = jsonObj.getString("name") ?: ""
        val fullName = jsonObj.getString("fullName") ?: ""
        val packageName = jsonObj.getString("packageName") ?: ""
        val isAggregateRoot = jsonObj.getBoolean("isAggregateRoot") ?: false
        val idType = jsonObj.getString("idType")

        // 解析字段列表
        val fieldsArray = jsonObj.getJSONArray("fields") ?: JSONArray()
        val fields = fieldsArray.mapNotNull { item ->
            if (item is JSONObject) {
                FieldMetadata(
                    name = item.getString("name") ?: "",
                    type = item.getString("type") ?: "",
                    nullable = item.getBoolean("nullable") ?: false
                )
            } else null
        }

        return EntityMetadata(
            name = name,
            fullName = fullName,
            packageName = packageName,
            isAggregateRoot = isAggregateRoot,
            idType = idType,
            fields = fields
        )
    }
}
