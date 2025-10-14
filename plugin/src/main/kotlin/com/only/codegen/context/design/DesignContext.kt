package com.only.codegen.context.design

import com.only.codegen.context.BaseContext

/**
 * 设计元素生成上下文 (只读接口)
 *
 * 数据来源:
 * - JSON 设计文件 (_gen.json)
 * - KSP 聚合元信息 (aggregates.json)
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

    // === 统一设计映射 ===
    /**
     * 统一的设计映射 (替代 7 个独立 map)
     * key: designType (cmd/qry/saga/cli/ie/de/svc)
     * value: 该类型的所有设计对象列表
     */
    val designMap: Map<String, List<BaseDesign>>
}
