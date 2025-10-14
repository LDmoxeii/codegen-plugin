package com.only.codegen.context.aggregate

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.only.codegen.ksp.models.AggregateMetadata
import java.io.File

/**
 * KSP 元数据上下文构建器
 *
 * 直接解析 KSP 生成的 aggregates.json，转换为 AggregateInfo
 * 合并了原 AggregateInfoBuilder 的逻辑，简化数据流
 *
 * Order: 10（优先级最高，加载所有聚合元数据）
 */
class KspMetadataContextBuilder(
    private val metadataPath: String,
) : AggregateContextBuilder {

    override val order: Int = 10

    private val gson = Gson()

    override fun build(context: MutableAnnotationContext) {
        val aggregatesFile = File(metadataPath, "aggregates.json")
        if (!aggregatesFile.exists()) {
            println("KSP metadata file not found: ${aggregatesFile.absolutePath}")
            return
        }

        val aggregates = parseAggregatesMetadata(aggregatesFile)
        processAggregates(aggregates, context)

        println("Loaded ${aggregates.size} aggregates from KSP metadata")
    }

    /**
     * 解析 aggregates.json（直接使用 ksp-processor 的 AggregateMetadata）
     */
    private fun parseAggregatesMetadata(file: File): List<AggregateMetadata> {
        val type = object : TypeToken<List<AggregateMetadata>>() {}.type
        return gson.fromJson(file.readText(), type)
    }

    /**
     * 处理聚合元数据，直接转换为 AggregateInfo
     */
    private fun processAggregates(
        aggregates: List<AggregateMetadata>,
        context: MutableAnnotationContext,
    ) {
        aggregates.forEach { metadata ->
            val aggregateInfo = AggregateInfo.fromKspMetadata(
                metadata = metadata,
                modulePath = resolveModulePath(metadata.packageName, context)
            )

            context.aggregateMap[metadata.aggregateName] = aggregateInfo
        }
    }

    /**
     * 解析模块路径
     */
    private fun resolveModulePath(packageName: String, context: MutableAnnotationContext): String {
        return context.domainPath
    }
}
