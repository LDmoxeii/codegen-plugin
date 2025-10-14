package com.only.codegen.context.design.models

/**
 * 领域事件设计 (有专属字段)
 */
data class DomainEventDesign(
    override val type: String = "de",
    override val name: String,
    override val fullName: String,
    override val packagePath: String,
    override val aggregate: String,        // 必须关联聚合
    override val aggregates: List<String>,
    override val desc: String,
    override val primaryAggregateMetadata: AggregateInfo?,
    override val aggregateMetadataList: List<AggregateInfo>,
    val entity: String,            // 关联实体 (用户配置)
    val persist: Boolean = false,   // 是否持久化 (业务逻辑)
) : BaseDesign
