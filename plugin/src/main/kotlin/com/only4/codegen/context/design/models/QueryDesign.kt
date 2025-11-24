package com.only4.codegen.context.design.models

import com.only4.codegen.misc.toUpperCamelCase

data class QueryDesign(
    override val type: String,
    override val `package`: String,
    override val name: String,
    override val desc: String,
    override val aggregate: String?,
    override val aggregates: List<String>,
    override val primaryAggregateMetadata: AggregateInfo?,
    override val aggregateMetadataList: List<AggregateInfo>,
) : BaseDesign {
    override fun className(): String {
        val candidate = if (name.endsWith("Qry")) name else "${name}Qry"
        return toUpperCamelCase(candidate)!!
    }
}
