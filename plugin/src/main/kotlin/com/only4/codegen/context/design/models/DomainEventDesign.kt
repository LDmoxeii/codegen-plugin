package com.only4.codegen.context.design.models

data class DomainEventDesign(
    override val type: String = "de",
    override val `package`: String,
    override val name: String,
    override val desc: String,
    override val aggregate: String,
    override val aggregates: List<String>,
    override val primaryAggregateMetadata: AggregateInfo?,
    override val aggregateMetadataList: List<AggregateInfo>,
    val entity: String,
    val persist: Boolean = false,
) : BaseDesign
