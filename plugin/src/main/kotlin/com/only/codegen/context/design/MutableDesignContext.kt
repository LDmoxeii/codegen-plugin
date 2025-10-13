package com.only.codegen.context.design

/**
 * 可变设计上下文
 *
 * 用于 Context Building 阶段，允许 ContextBuilder 修改上下文数据
 */
interface MutableDesignContext : DesignContext {

    // === 覆盖为可变版本 ===
    override val designElementMap: MutableMap<String, MutableList<DesignElement>>
    override val aggregateMetadataMap: MutableMap<String, AggregateMetadata>
    override val entityMetadataMap: MutableMap<String, EntityMetadata>
    override val commandDesignMap: MutableMap<String, CommandDesign>
    override val queryDesignMap: MutableMap<String, QueryDesign>
    override val sagaDesignMap: MutableMap<String, SagaDesign>
    override val clientDesignMap: MutableMap<String, ClientDesign>
    override val integrationEventDesignMap: MutableMap<String, IntegrationEventDesign>
    override val domainEventDesignMap: MutableMap<String, DomainEventDesign>
    override val domainServiceDesignMap: MutableMap<String, DomainServiceDesign>
}
