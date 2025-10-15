package com.only4.codegen.context.design.models

/**
 * 集成事件设计
 */
data class IntegrationEventDesign(
    override val type: String,
    override val `package`: String,
    override val name: String,
    override val desc: String,
    override val aggregate: String?,
    override val aggregates: List<String>,
    override val primaryAggregateMetadata: AggregateInfo?,
    override val aggregateMetadataList: List<AggregateInfo>,
    val mqTopic: String?,          // MQ 主题 (用户配置)
    val mqConsumer: String?,       // MQ 消费者 (用户配置)
) : BaseDesign
