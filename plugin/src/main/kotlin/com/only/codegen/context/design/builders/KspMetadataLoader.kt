package com.only.codegen.context.design.builders

import com.alibaba.fastjson.JSON
import com.only.codegen.context.design.*
import com.only.codegen.context.design.models.KspAggregateMetadata
import org.gradle.api.logging.Logging
import java.io.File

/**
 * KSP 元数据加载器（重构版）
 *
 * Order: 15
 * 职责:
 * 1. 加载 KSP 生成的 aggregates.json（以聚合根为中心的层次结构）
 * 2. 转换为 DesignContext 所需的 AggregateMetadata 和 EntityMetadata
 * 3. 构建类型映射 typeMapping (聚合根、实体、Repository、Factory 等)
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

        if (aggregatesFile.exists()) {
            loadAggregatesMetadata(aggregatesFile, context)
        } else {
            logger.warn("KSP aggregates.json not found: ${aggregatesFile.absolutePath}")
        }

        logger.lifecycle("Loaded ${context.aggregateMetadataMap.size} aggregates, " +
                "${context.entityMetadataMap.size} entities from KSP metadata")
        logger.lifecycle("Built ${context.typeMapping.size} type mappings")
    }

    private fun getKspMetadataDir(context: MutableDesignContext): String {
        return context.getString("kspMetadataDir", "")
    }

    /**
     * 加载聚合元数据（以聚合为单位的层次结构）
     */
    private fun loadAggregatesMetadata(file: File, context: MutableDesignContext) {
        try {
            val content = file.readText()
            val kspAggregates = JSON.parseArray(content, KspAggregateMetadata::class.java)

            kspAggregates.forEach { kspAggregate ->
                // 转换聚合根为 EntityMetadata
                val aggregateRootEntity = EntityMetadata(
                    name = kspAggregate.aggregateRoot.className,
                    fullName = kspAggregate.aggregateRoot.qualifiedName,
                    packageName = kspAggregate.aggregateRoot.packageName,
                    isAggregateRoot = true,
                    idType = kspAggregate.aggregateRoot.identityType,
                    fields = kspAggregate.aggregateRoot.fields.map { field ->
                        FieldMetadata(
                            name = field.name,
                            type = field.type,
                            nullable = field.isNullable
                        )
                    }
                )

                // 转换聚合内的实体
                val entities = kspAggregate.entities.map { entity ->
                    EntityMetadata(
                        name = entity.className,
                        fullName = entity.qualifiedName,
                        packageName = entity.packageName,
                        isAggregateRoot = false,
                        idType = entity.identityType,
                        fields = entity.fields.map { field ->
                            FieldMetadata(
                                name = field.name,
                                type = field.type,
                                nullable = field.isNullable
                            )
                        }
                    )
                }

                // 构建 AggregateMetadata
                val aggregateMetadata = AggregateMetadata(
                    name = kspAggregate.aggregateName,
                    fullName = kspAggregate.aggregateRoot.qualifiedName,
                    packageName = kspAggregate.aggregateRoot.packageName,
                    aggregateRoot = aggregateRootEntity,
                    entities = entities,
                    idType = kspAggregate.aggregateRoot.identityType
                )

                context.aggregateMetadataMap[kspAggregate.aggregateName] = aggregateMetadata

                // 添加聚合根到 entityMetadataMap
                context.entityMetadataMap[aggregateRootEntity.name] = aggregateRootEntity

                // 添加聚合内的实体到 entityMetadataMap
                entities.forEach { entity ->
                    context.entityMetadataMap[entity.name] = entity
                }

                // === 构建类型映射 typeMapping ===
                buildTypeMapping(kspAggregate, context)

                logger.info("Loaded aggregate: ${kspAggregate.aggregateName} " +
                    "(root=${aggregateRootEntity.name}, entities=${entities.size})")
            }
        } catch (e: Exception) {
            logger.error("Failed to load aggregates metadata: ${file.absolutePath}", e)
        }
    }

    /**
     * 构建类型映射 (聚合根、实体、Repository、Factory、Specification、DomainEvent 等)
     */
    private fun buildTypeMapping(kspAggregate: KspAggregateMetadata, context: MutableDesignContext) {
        // 聚合根类型映射
        val aggregateRootName = kspAggregate.aggregateRoot.className
        val aggregateRootFullName = kspAggregate.aggregateRoot.qualifiedName
        context.typeMapping[aggregateRootName] = aggregateRootFullName

        // 实体类型映射
        kspAggregate.entities.forEach { entity ->
            context.typeMapping[entity.className] = entity.qualifiedName
        }

        // ValueObject 类型映射
        kspAggregate.valueObjects.forEach { vo ->
            context.typeMapping[vo.className] = vo.qualifiedName
        }

        // Enum 类型映射
        kspAggregate.enums.forEach { enum ->
            context.typeMapping[enum.className] = enum.qualifiedName
        }

        // Repository 类型映射
        kspAggregate.repository?.let { repo ->
            context.typeMapping[repo.className] = repo.qualifiedName
        }

        // Factory 类型映射
        kspAggregate.factory?.let { factory ->
            context.typeMapping[factory.className] = factory.qualifiedName
        }

        // FactoryPayload 类型映射
        kspAggregate.factoryPayload?.let { payload ->
            context.typeMapping[payload.className] = payload.qualifiedName
        }

        // Specification 类型映射
        kspAggregate.specification?.let { spec ->
            context.typeMapping[spec.className] = spec.qualifiedName
        }

        // DomainEvent 类型映射
        kspAggregate.domainEvents.forEach { event ->
            context.typeMapping[event.className] = event.qualifiedName
        }

        logger.debug("Built type mappings for aggregate: ${kspAggregate.aggregateName}")
    }
}
