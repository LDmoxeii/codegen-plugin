package com.only.codegen.context.design.builders

import com.only.codegen.context.design.DesignContextBuilder
import com.only.codegen.context.design.MutableDesignContext
import org.gradle.api.logging.Logging

/**
 * 类型映射构建器
 *
 * Order: 18
 * 职责: 基于 KSP 加载的聚合元数据,构建全局类型映射 typeMapping
 *
 * 依赖: 依赖 KspMetadataLoader (order=15) 已填充 aggregateMetadataMap
 *
 * 映射内容:
 * - 聚合根 (AggregateRoot)
 * - 实体 (Entity)
 * - 值对象 (ValueObject)
 * - 枚举 (Enum)
 * - 仓储 (Repository)
 * - 工厂 (Factory, FactoryPayload)
 * - 规约 (Specification)
 * - 领域事件 (DomainEvent)
 */
class TypeMappingBuilder : DesignContextBuilder {

    private val logger = Logging.getLogger(TypeMappingBuilder::class.java)

    override val order: Int = 18

    override fun build(context: MutableDesignContext) {
        if (context.aggregateMetadataMap.isEmpty()) {
            logger.warn("No aggregate metadata found, skipping type mapping")
            return
        }

        var totalMappings = 0

        context.aggregateMetadataMap.values.forEach { aggregateMetadata ->
            // 聚合根类型映射
            context.typeMapping[aggregateMetadata.aggregateRoot.name] = aggregateMetadata.aggregateRoot.fullName
            totalMappings++

            // 实体类型映射
            aggregateMetadata.entities.forEach { entity ->
                context.typeMapping[entity.name] = entity.fullName
                totalMappings++
            }

            logger.debug("Built type mappings for aggregate: ${aggregateMetadata.name}")
        }

        logger.lifecycle("Built $totalMappings type mappings from KSP metadata")
    }
}
