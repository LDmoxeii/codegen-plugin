package com.only4.codegen.context.design.builders

import com.only4.codegen.context.ContextBuilder
import com.only4.codegen.context.design.MutableDesignContext
import com.only4.codegen.context.design.models.*
import com.only4.codegen.misc.toUpperCamelCase

class UnifiedDesignBuilder : ContextBuilder<MutableDesignContext> {

    override val order: Int = 20

    private val designTypeToGeneratorTags = mapOf(
        "domain_event" to listOf("domain_event", "domain_event_handler"),
        "query" to listOf("query", "query_handler"),
    )

    override fun build(context: MutableDesignContext) {
        val designMap = mutableMapOf<String, MutableList<BaseDesign>>()

        context.designElementMap.forEach { (type, elements) ->
            val normalizedType = context.designTagAliasMap[type.lowercase()] ?: type.lowercase()

            elements.forEach {
                val design = buildDesign(normalizedType, it, context)

                val targetTags = designTypeToGeneratorTags[normalizedType] ?: listOf(normalizedType)

                targetTags.forEach { tag ->
                    designMap.computeIfAbsent(tag) { mutableListOf() }.add(design)
                }
            }
        }

        context.designMap.putAll(designMap)
    }

    private fun buildDesign(
        type: String,
        element: DesignElement,
        context: MutableDesignContext,
    ): BaseDesign {
        val aggregates = extractAggregates(element)

        val aggregateMetadataList = aggregates.mapNotNull { aggName ->
            context.aggregateMap[aggName]
        }

        val primaryAggregate = aggregates.firstOrNull()
        val primaryAggregateMetadata = aggregateMetadataList.firstOrNull()

        return when (type) {
            "command" -> buildCommonDesign(
                "command", element, primaryAggregate, aggregates, primaryAggregateMetadata, aggregateMetadataList
            )

            "query" -> buildCommonDesign(
                "query", element, primaryAggregate, aggregates, primaryAggregateMetadata, aggregateMetadataList
            )

            "saga" -> buildCommonDesign(
                "saga", element, primaryAggregate, aggregates, primaryAggregateMetadata, aggregateMetadataList
            )

            "client" -> buildCommonDesign(
                "client", element, primaryAggregate, aggregates, primaryAggregateMetadata, aggregateMetadataList
            )

            "domain_service" -> buildCommonDesign(
                "domain_service", element, primaryAggregate, aggregates, primaryAggregateMetadata, aggregateMetadataList
            )

            "integration_event" -> buildIntegrationEventDesign(
                "integration_event", element, primaryAggregate, aggregates, primaryAggregateMetadata, aggregateMetadataList
            )

            "domain_event" -> buildDomainEventDesign(
                "domain_event", element, primaryAggregate, aggregates, primaryAggregateMetadata, aggregateMetadataList
            )

            else -> throw IllegalArgumentException("Unknown design type: $type")
        }
    }

    private fun extractAggregates(element: DesignElement): List<String> {
        val aggregatesFromArray = element.aggregates ?: emptyList()
        if (aggregatesFromArray.isNotEmpty()) {
            return aggregatesFromArray
        }

        return emptyList()
    }

    private fun buildCommonDesign(
        type: String,
        element: DesignElement,
        primaryAggregate: String?,
        aggregates: List<String>,
        primaryAggregateMetadata: AggregateInfo?,
        aggregateMetadataList: List<AggregateInfo>,
    ): CommonDesign {
        return CommonDesign(
            type = type,
            `package` = element.`package`,
            name = element.name,
            desc = element.desc,
            aggregate = primaryAggregate,
            aggregates = aggregates,
            primaryAggregateMetadata = primaryAggregateMetadata,
            aggregateMetadataList = aggregateMetadataList
        )
    }

    private fun buildIntegrationEventDesign(
        type: String,
        element: DesignElement,
        primaryAggregate: String?,
        aggregates: List<String>,
        primaryAggregateMetadata: AggregateInfo?,
        aggregateMetadataList: List<AggregateInfo>,
    ): IntegrationEventDesign {
        val eventName = normalizeName(element.name, "Event")

        return IntegrationEventDesign(
            type = type,
            `package` = element.`package`,
            name = eventName,
            desc = element.desc,
            aggregate = primaryAggregate,
            aggregates = aggregates,
            primaryAggregateMetadata = primaryAggregateMetadata,
            aggregateMetadataList = aggregateMetadataList,
            mqTopic = element.metadata["mqTopic"] as? String,
            mqConsumer = element.metadata["mqConsumer"] as? String,
        )
    }

    private fun buildDomainEventDesign(
        type: String,
        element: DesignElement,
        primaryAggregate: String?,
        aggregates: List<String>,
        primaryAggregateMetadata: AggregateInfo?,
        aggregateMetadataList: List<AggregateInfo>,
    ): DomainEventDesign {
        require(primaryAggregate != null) { "Domain event must have an aggregate: ${element.name}" }

        return DomainEventDesign(
            type = type,
            `package` = element.`package`,
            name = element.name,
            desc = element.desc,
            aggregate = primaryAggregate,
            aggregates = aggregates,
            primaryAggregateMetadata = primaryAggregateMetadata,
            aggregateMetadataList = aggregateMetadataList,
            entity = element.metadata["entity"] as? String ?: "",
            persist = element.metadata["persist"] as? Boolean ?: false,
        )
    }

    private fun normalizeName(name: String, vararg suffixes: String): String {
        var normalized = toUpperCamelCase(name).orEmpty()

        val hasSuffix = suffixes.any { normalized.endsWith(it) }

        if (!hasSuffix && suffixes.isNotEmpty()) {
            normalized += suffixes.first()
        }

        return normalized
    }
}
