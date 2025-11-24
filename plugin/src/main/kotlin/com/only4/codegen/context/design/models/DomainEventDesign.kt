package com.only4.codegen.context.design.models

import com.only4.codegen.misc.toUpperCamelCase

data class DomainEventDesign(
    override val type: String = "domain_event",
    override val `package`: String,
    override val name: String,
    override val desc: String,
    override val aggregate: String,
    override val aggregates: List<String>,
    override val primaryAggregateMetadata: AggregateInfo?,
    override val aggregateMetadataList: List<AggregateInfo>,
    val entity: String,
    val persist: Boolean = false,
) : BaseDesign {
    override fun className(): String {
        var candidate = name
        if (!candidate.endsWith("Evt") && !candidate.endsWith("Event")) {
            candidate += "DomainEvent"
        }
        return toUpperCamelCase(candidate)!!
    }
}
