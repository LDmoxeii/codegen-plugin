package com.only.codegen.context.design.models

/**
 * 集成事件设计 (有专属字段)
 */
data class IntegrationEventDesign(
    override val type: String = "ie",
    override val name: String,
    override val fullName: String,
    override val packagePath: String,
    override val aggregate: String?,
    override val aggregates: List<String>,
    override val desc: String,
    override val primaryAggregateMetadata: AggregateInfo?,
    override val aggregateMetadataList: List<AggregateInfo>,
    val mqTopic: String?,          // MQ 主题 (用户配置)
    val mqConsumer: String?,       // MQ 消费者 (用户配置)
    val internal: Boolean = true,   // 是否内部事件 (业务逻辑)
) : BaseDesign
