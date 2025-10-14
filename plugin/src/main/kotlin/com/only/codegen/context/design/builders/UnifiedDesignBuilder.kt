package com.only.codegen.context.design.builders

import com.only.codegen.context.design.MutableDesignContext
import com.only.codegen.context.design.models.*
import com.only.codegen.misc.toUpperCamelCase

class UnifiedDesignBuilder : DesignContextBuilder {

    override val order: Int = 20

    override fun build(context: MutableDesignContext) {
        val designMap = mutableMapOf<String, MutableList<BaseDesign>>()

        context.designElementMap.forEach { (type, elements) ->
            elements.forEach {
                val design = buildDesign(type, it, context)
                designMap.computeIfAbsent(type) { mutableListOf() }.add(design)
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

    private fun buildIntegrationEventDesign(
        element: DesignElement,
        primaryAggregate: String?,
        aggregates: List<String>,
        primaryAggregateMetadata: AggregateInfo?,
        aggregateMetadataList: List<AggregateInfo>,
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

    private fun buildDomainEventDesign(
        element: DesignElement,
        primaryAggregate: String?,
        aggregates: List<String>,
        primaryAggregateMetadata: AggregateInfo?,
        aggregateMetadataList: List<AggregateInfo>,
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

    private fun getTypeSuffix(type: String): String = when (type) {
        "cmd" -> "Cmd"
        "qry" -> "Qry"
        "saga" -> "Saga"
        "cli" -> "Client"
        "svc" -> "Service"
        else -> ""
    }

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

    private fun normalizeName(name: String, vararg suffixes: String): String {
        var normalized = toUpperCamelCase(name).orEmpty()

        val hasSuffix = suffixes.any { normalized.endsWith(it) }

        if (!hasSuffix && suffixes.isNotEmpty()) {
            normalized += suffixes.first()
        }

        return normalized
    }
}
