package com.only.codegen.context.annotation

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.only.codegen.ksp.models.AggregateMetadata
import com.only.codegen.ksp.models.EntityMetadata
import java.io.File

/**
 * KSP 元数据上下文构建器
 *
 * 负责读取 KSP Processor 生成的 JSON 元数据文件，并填充到 AnnotationContext 中
 *
 * **执行顺序**: 10（最先执行，提供基础数据）
 *
 * **填充的 Map**:
 * - classMap: 类信息映射（FQN → ClassInfo）
 * - annotationMap: 注解信息映射（注解名 → List<AnnotationInfo>）
 */
class KspMetadataContextBuilder(
    private val metadataPath: String,
) : AnnotationContextBuilder {

    override val order: Int = 10

    private val gson = Gson()

    override fun build(context: MutableAnnotationContext) {
        // 1. 读取 aggregates.json
        val aggregatesFile = File(metadataPath, "aggregates.json")
        if (aggregatesFile.exists()) {
            val aggregates = parseAggregatesMetadata(aggregatesFile)
            processAggregates(aggregates, context)
        }

        // 2. 读取 entities.json
        val entitiesFile = File(metadataPath, "entities.json")
        if (entitiesFile.exists()) {
            val entities = parseEntitiesMetadata(entitiesFile)
            processEntities(entities, context)
        }
    }

    /**
     * 解析 aggregates.json
     */
    private fun parseAggregatesMetadata(file: File): List<AggregateMetadata> {
        val type = object : TypeToken<List<AggregateMetadata>>() {}.type
        return gson.fromJson(file.readText(), type)
    }

    /**
     * 解析 entities.json
     */
    private fun parseEntitiesMetadata(file: File): List<EntityMetadata> {
        val type = object : TypeToken<List<EntityMetadata>>() {}.type
        return gson.fromJson(file.readText(), type)
    }

    /**
     * 处理 @Aggregate 注解的类
     */
    private fun processAggregates(
        aggregates: List<AggregateMetadata>,
        context: MutableAnnotationContext,
    ) {
        aggregates.forEach { metadata ->
            // 1. 构建 ClassInfo
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

            // 2. 放入 classMap
            context.classMap[metadata.qualifiedName] = classInfo

            // 3. 放入 annotationMap
            val aggregateAnnotation = classInfo.annotations.first { it.name == "Aggregate" }
            context.annotationMap
                .getOrPut("Aggregate") { mutableListOf() }
                .add(aggregateAnnotation)
        }
    }

    /**
     * 处理 @Entity 注解的类
     */
    private fun processEntities(
        entities: List<EntityMetadata>,
        context: MutableAnnotationContext,
    ) {
        entities.forEach { metadata ->
            // 如果已经处理过（在 aggregates 中），跳过或合并注解
            if (context.classMap.containsKey(metadata.qualifiedName)) {
                // 跳过，保留 aggregate 信息
                return@forEach
            }

            // 1. 构建 ClassInfo
            val classInfo = ClassInfo(
                packageName = metadata.packageName,
                simpleName = metadata.className,
                fullName = metadata.qualifiedName,
                filePath = "",
                annotations = listOf(
                    AnnotationInfo(
                        name = "Entity",
                        fullName = "jakarta.persistence.Entity",
                        attributes = emptyMap(),
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
                                fullName = annotationName,
                                attributes = emptyMap(),
                                targetClass = metadata.qualifiedName
                            )
                        },
                        isId = field.isId,
                        isNullable = field.isNullable
                    )
                },
                superClass = null,
                interfaces = emptyList()
            )

            // 2. 放入 classMap
            context.classMap[metadata.qualifiedName] = classInfo

            // 3. 放入 annotationMap
            val entityAnnotation = classInfo.annotations.first { it.name == "Entity" }
            context.annotationMap
                .getOrPut("Entity") { mutableListOf() }
                .add(entityAnnotation)
        }
    }
}
