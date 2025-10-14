package com.only.codegen.context.design.builders

import com.only.codegen.context.design.*
import com.only.codegen.misc.toUpperCamelCase
import org.gradle.api.logging.Logging

/**
 * 统一设计构建器
 *
 * Order: 20
 * 职责: 统一解析所有设计类型 (cmd/qry/saga/cli/ie/de/svc)
 * 自动关联聚合元信息,支持无聚合/单聚合/多聚合三种场景
 */
class UnifiedDesignBuilder : DesignContextBuilder {

    private val logger = Logging.getLogger(UnifiedDesignBuilder::class.java)

    override val order: Int = 20

    override fun build(context: MutableDesignContext) {
        val designMap = mutableMapOf<String, MutableList<BaseDesign>>()

        context.designElementMap.forEach { (type, elements) ->
            elements.forEach { element ->
                try {
                    val design = buildDesign(type, element, context)
                    designMap.computeIfAbsent(type) { mutableListOf() }.add(design)
                } catch (e: Exception) {
                    logger.error("Failed to build design for type=$type, name=${element.name}", e)
                }
            }
        }

        context.designMap.putAll(designMap)

        val totalDesigns = designMap.values.sumOf { it.size }
        logger.lifecycle("Built $totalDesigns designs across ${designMap.size} types")
    }

    /**
     * 根据类型构建对应的设计对象
     */
    private fun buildDesign(
        type: String,
        element: DesignElement,
        context: MutableDesignContext
    ): BaseDesign {
        // 1. 提取聚合列表 (支持单个/多个/无聚合)
        val aggregates = extractAggregates(element)

        // 2. 自动关联聚合元信息
        val aggregateMetadataList = aggregates.mapNotNull { aggName ->
            context.aggregateMetadataMap[aggName]
        }

        // 3. 主聚合 (第一个聚合)
        val primaryAggregate = aggregates.firstOrNull()
        val primaryAggregateMetadata = aggregateMetadataList.firstOrNull()

        // 4. 根据 type 构建对应设计对象
        return when (type) {
            "cmd", "qry", "saga", "cli", "svc" -> buildCommonDesign(
                type, element, primaryAggregate, aggregates, primaryAggregateMetadata, aggregateMetadataList
            )
            "ie" -> buildIntegrationEventDesign(
                element, primaryAggregate, aggregates, primaryAggregateMetadata, aggregateMetadataList
            )
            "de" -> buildDomainEventDesign(
                element, primaryAggregate, aggregates, primaryAggregateMetadata, aggregateMetadataList
            )
            else -> throw IllegalArgumentException("Unknown design type: $type")
        }
    }

    /**
     * 提取聚合列表 (兼容单个aggregate字段和aggregates数组)
     */
    private fun extractAggregates(element: DesignElement): List<String> {
        // 优先使用 aggregates 数组
        val aggregatesFromArray = element.aggregates ?: emptyList()
        if (aggregatesFromArray.isNotEmpty()) {
            return aggregatesFromArray
        }

        // 兼容旧的 aggregate 字段 (从 metadata 中提取)
        val aggregatesFromMetadata = element.metadata["aggregates"]
        if (aggregatesFromMetadata is List<*>) {
            return aggregatesFromMetadata.filterIsInstance<String>()
        }

        // 单个 aggregate 字段
        val singleAggregate = element.aggregate
        if (!singleAggregate.isNullOrBlank()) {
            return listOf(singleAggregate)
        }

        // 无聚合
        return emptyList()
    }

    // === 各设计类型的构建方法 ===

    /**
     * 构建通用设计 (适用于 cmd/qry/saga/cli/svc)
     */
    private fun buildCommonDesign(
        type: String,
        element: DesignElement,
        primaryAggregate: String?,
        aggregates: List<String>,
        primaryAggregateMetadata: AggregateMetadata?,
        aggregateMetadataList: List<AggregateMetadata>
    ): CommonDesign {
        val (packagePath, name) = parseNameAndPackage(element.name, primaryAggregate)
        val suffix = getTypeSuffix(type)
        val designName = normalizeName(name, suffix)
        val fullName = if (packagePath.isNotBlank()) "$packagePath.$designName" else designName

        return CommonDesign(
            type = type,
            name = designName,
            fullName = fullName,
            packagePath = packagePath,
            aggregate = primaryAggregate,
            aggregates = aggregates,
            desc = element.desc,
            primaryAggregateMetadata = primaryAggregateMetadata,
            aggregateMetadataList = aggregateMetadataList
        )
    }

    /**
     * 构建集成事件设计
     */
    private fun buildIntegrationEventDesign(
        element: DesignElement,
        primaryAggregate: String?,
        aggregates: List<String>,
        primaryAggregateMetadata: AggregateMetadata?,
        aggregateMetadataList: List<AggregateMetadata>
    ): IntegrationEventDesign {
        val (packagePath, name) = parseNameAndPackage(element.name, primaryAggregate)
        val eventName = normalizeName(name, "Event")
        val fullName = if (packagePath.isNotBlank()) "$packagePath.$eventName" else eventName

        return IntegrationEventDesign(
            name = eventName,
            fullName = fullName,
            packagePath = packagePath,
            aggregate = primaryAggregate,
            aggregates = aggregates,
            desc = element.desc,
            primaryAggregateMetadata = primaryAggregateMetadata,
            aggregateMetadataList = aggregateMetadataList,
            mqTopic = element.metadata["mqTopic"] as? String,
            mqConsumer = element.metadata["mqConsumer"] as? String,
            internal = element.metadata["internal"] as? Boolean ?: true
        )
    }

    /**
     * 构建领域事件设计
     */
    private fun buildDomainEventDesign(
        element: DesignElement,
        primaryAggregate: String?,
        aggregates: List<String>,
        primaryAggregateMetadata: AggregateMetadata?,
        aggregateMetadataList: List<AggregateMetadata>
    ): DomainEventDesign {
        require(primaryAggregate != null) { "Domain event must have an aggregate: ${element.name}" }

        val (packagePath, name) = parseNameAndPackage(element.name, primaryAggregate)
        val eventName = normalizeName(name, "Event")
        val fullName = if (packagePath.isNotBlank()) "$packagePath.$eventName" else eventName

        return DomainEventDesign(
            name = eventName,
            fullName = fullName,
            packagePath = packagePath,
            aggregate = primaryAggregate,
            aggregates = aggregates,
            desc = element.desc,
            primaryAggregateMetadata = primaryAggregateMetadata,
            aggregateMetadataList = aggregateMetadataList,
            entity = element.metadata["entity"] as? String ?: "",
            persist = element.metadata["persist"] as? Boolean ?: false
        )
    }

    // === 辅助方法 ===

    /**
     * 获取类型后缀
     */
    private fun getTypeSuffix(type: String): String = when (type) {
        "cmd" -> "Cmd"
        "qry" -> "Qry"
        "saga" -> "Saga"
        "cli" -> "Client"
        "svc" -> "Service"
        else -> ""
    }

    /**
     * 解析名称和包路径
     * 例如: "category.CreateCategory" -> ("category", "CreateCategory")
     */
    private fun parseNameAndPackage(name: String, fallbackPackage: String?): Pair<String, String> {
        val parts = name.split(".")
        return if (parts.size > 1) {
            val packagePath = parts.dropLast(1).joinToString(".")
            val simpleName = parts.last()
            packagePath to simpleName
        } else {
            (fallbackPackage ?: "") to name
        }
    }

    /**
     * 规范化名称 (添加后缀)
     * 例如: normalizeName("CreateCategory", "Cmd", "Command") -> "CreateCategoryCmd"
     */
    private fun normalizeName(name: String, vararg suffixes: String): String {
        var normalized = toUpperCamelCase(name).orEmpty()

        // 检查是否已有任意一个后缀
        val hasSuffix = suffixes.any { normalized.endsWith(it) }

        // 如果没有后缀,添加第一个后缀
        if (!hasSuffix && suffixes.isNotEmpty()) {
            normalized += suffixes.first()
        }

        return normalized
    }
}
