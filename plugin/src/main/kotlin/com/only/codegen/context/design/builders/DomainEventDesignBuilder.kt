package com.only.codegen.context.design.builders

import com.only.codegen.context.design.DesignContextBuilder
import com.only.codegen.context.design.DomainEventDesign
import com.only.codegen.context.design.MutableDesignContext
import com.only.codegen.misc.toUpperCamelCase
import org.gradle.api.logging.Logging

/**
 * 领域事件设计构建器
 *
 * Order: 25
 * 职责: 解析领域事件设计元素,生成 DomainEventDesign 对象
 * 特点: 必须关联聚合根
 */
class DomainEventDesignBuilder : DesignContextBuilder {

    private val logger = Logging.getLogger(DomainEventDesignBuilder::class.java)

    override val order: Int = 25

    override fun build(context: MutableDesignContext) {
        val domainEventElements = context.designElementMap["de"] ?: emptyList()

        domainEventElements.forEach { element ->
            try {
                // 领域事件必须有聚合
                if (element.aggregate.isNullOrBlank()) {
                    logger.warn("Domain event must have aggregate: ${element.name}")
                    return@forEach
                }

                val domainEventDesign = buildDomainEventDesign(element, context)
                context.domainEventDesignMap[domainEventDesign.fullName] = domainEventDesign
            } catch (e: Exception) {
                logger.error("Failed to build domain event design for: ${element.name}", e)
            }
        }

        logger.lifecycle("Built ${context.domainEventDesignMap.size} domain event designs")
    }

    private fun buildDomainEventDesign(
        element: com.only.codegen.context.design.DesignElement,
        context: MutableDesignContext
    ): DomainEventDesign {
        val aggregate = element.aggregate!!

        // 解析 name: "category.CategoryCreated" 或 "CategoryCreated"
        val parts = element.name.split(".")
        val packagePath = if (parts.size > 1) {
            parts.dropLast(1).joinToString(".")
        } else {
            aggregate
        }

        val rawName = parts.lastOrNull() ?: element.name
        var eventName = toUpperCamelCase(rawName).orEmpty()

        // 自动添加 DomainEvent 后缀
        if (!eventName.endsWith("Event") && !eventName.endsWith("DomainEvent")) {
            eventName += "DomainEvent"
        }

        val fullName = if (packagePath.isNotBlank()) {
            "$packagePath.$eventName"
        } else {
            eventName
        }

        // 从聚合元数据获取实体名
        val aggregateMetadata = context.aggregateMetadataMap[aggregate]
        val entity = aggregateMetadata?.aggregateRoot?.name ?: aggregate

        // 从 metadata 解析是否持久化
        val persist = element.metadata["persist"]?.toString()?.toBoolean() ?: false

        return DomainEventDesign(
            name = eventName,
            fullName = fullName,
            packagePath = packagePath,
            aggregate = aggregate,
            entity = entity,
            desc = element.desc,
            persist = persist,
            aggregateMetadata = aggregateMetadata
        )
    }
}
