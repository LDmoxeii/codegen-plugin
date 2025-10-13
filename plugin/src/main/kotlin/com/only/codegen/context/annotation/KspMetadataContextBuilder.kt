package com.only.codegen.context.annotation

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.only.codegen.ksp.models.AggregateMetadata
import java.io.File

class KspMetadataContextBuilder(
    private val metadataPath: String,
) : AnnotationContextBuilder {

    override val order: Int = 10

    private val gson = Gson()

    override fun build(context: MutableAnnotationContext) {
        val aggregatesFile = File(metadataPath, "aggregates.json")
        if (aggregatesFile.exists()) {
            val aggregates = parseAggregatesMetadata(aggregatesFile)
            processAggregates(aggregates, context)
        }
    }

    private fun parseAggregatesMetadata(file: File): List<AggregateMetadata> {
        val type = object : TypeToken<List<AggregateMetadata>>() {}.type
        return gson.fromJson(file.readText(), type)
    }

    private fun processAggregates(
        aggregates: List<AggregateMetadata>,
        context: MutableAnnotationContext,
    ) {
        aggregates.forEach { metadata ->
            val classInfo = ClassInfo(
                packageName = metadata.packageName,
                simpleName = metadata.className,
                fullName = metadata.qualifiedName,
                filePath = "", // KSP 元数据不包含文件路径
                annotations = listOf(
                    AnnotationInfo(
                        name = "Aggregate",
                        fullName = "com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate",
                        attributes = mapOf(
                            "aggregate" to metadata.aggregateName,
                            "root" to metadata.isAggregateRoot,
                            "type" to when {
                                metadata.isEntity -> "Aggregate.TYPE_ENTITY"
                                metadata.isValueObject -> "Aggregate.TYPE_VALUE_OBJECT"
                                else -> "Aggregate.TYPE_ENTITY"
                            }
                        ),
                        targetClass = metadata.qualifiedName
                    )
                ),
                fields = metadata.fields.map { field ->
                    FieldInfo(
                        name = field.name,
                        type = field.type,
                        annotations = field.annotations.map { annotationName ->
                            AnnotationInfo(
                                name = annotationName,
                                fullName = annotationName, // 简化处理
                                attributes = emptyMap(),
                                targetClass = metadata.qualifiedName
                            )
                        },
                        isId = field.isId,
                        isNullable = field.isNullable
                    )
                },
                superClass = null,
                interfaces = emptyList(),
                isAggregateRoot = metadata.isAggregateRoot,
                isEntity = metadata.isEntity,
                isValueObject = metadata.isValueObject
            )

            context.classMap[metadata.qualifiedName] = classInfo

            val aggregateAnnotation = classInfo.annotations.first { it.name == "Aggregate" }
            context.annotationMap
                .getOrPut("Aggregate") { mutableListOf() }
                .add(aggregateAnnotation)
        }
    }
}
