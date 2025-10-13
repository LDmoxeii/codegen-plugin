package com.only.codegen.context.design

import com.only.codegen.context.BaseContext

/**
 * 设计元素生成上下文 (只读接口)
 *
 * 数据来源:
 * - JSON 设计文件 (_gen.json)
 * - KSP 聚合元信息 (aggregates.json / entities.json)
 */
interface DesignContext : BaseContext {

    // === JSON 设计元数据 ===
    /**
     * 原始设计元素 Map
     * key: elementType (cmd/qry/saga/cli/ie/de/svc)
     * value: 该类型的设计元素列表
     */
    val designElementMap: Map<String, List<DesignElement>>

    // === KSP 聚合元信息 ===
    /**
     * 聚合元数据 Map
     * key: aggregateName (如 category)
     * value: 聚合元数据
     */
    val aggregateMetadataMap: Map<String, AggregateMetadata>

    /**
     * 实体元数据 Map
     * key: entityName (如 Category)
     * value: 实体元数据
     */
    val entityMetadataMap: Map<String, EntityMetadata>

    // === 设计元素解析结果 ===
    /**
     * 命令设计 Map
     * key: commandFullName (如 category.CreateCategoryCmd)
     * value: 命令设计对象
     */
    val commandDesignMap: Map<String, CommandDesign>

    /**
     * 查询设计 Map
     * key: queryFullName
     * value: 查询设计对象
     */
    val queryDesignMap: Map<String, QueryDesign>

    /**
     * Saga 设计 Map
     * key: sagaFullName
     * value: Saga 设计对象
     */
    val sagaDesignMap: Map<String, SagaDesign>

    /**
     * 客户端设计 Map
     * key: clientFullName
     * value: 客户端设计对象
     */
    val clientDesignMap: Map<String, ClientDesign>

    /**
     * 集成事件设计 Map
     * key: eventFullName
     * value: 集成事件设计对象
     */
    val integrationEventDesignMap: Map<String, IntegrationEventDesign>

    /**
     * 领域事件设计 Map
     * key: eventFullName
     * value: 领域事件设计对象
     */
    val domainEventDesignMap: Map<String, DomainEventDesign>

    /**
     * 领域服务设计 Map
     * key: serviceFullName
     * value: 领域服务设计对象
     */
    val domainServiceDesignMap: Map<String, DomainServiceDesign>
}
