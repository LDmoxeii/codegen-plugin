package com.only4.codegen.ksp.models

/**
 * 聚合元数据（聚合级别）
 *
 * 以聚合根为核心，包含聚合内的所有元素：
 * - 1个聚合根（必须）
 * - 0-N个实体
 * - 0-N个值对象
 * - 0-N个枚举
 * - 0-1个仓储
 * - 0-1个工厂
 * - 0-1个工厂负载
 * - 0-1个规约
 * - 0-N个领域事件
 */
data class AggregateMetadata(
    /**
     * 聚合名称（来自 @Aggregate(aggregate = "...") 属性）
     */
    val aggregateName: String,

    /**
     * 聚合根（必须存在，type=entity 且 root=true）
     */
    val aggregateRoot: ElementMetadata,

    /**
     * 聚合内的实体列表（type=entity 且 root=false）
     */
    val entities: List<ElementMetadata> = emptyList(),

    /**
     * 聚合内的值对象列表（type=value-object）
     */
    val valueObjects: List<ElementMetadata> = emptyList(),

    /**
     * 聚合内的枚举列表（type=enum）
     */
    val enums: List<ElementMetadata> = emptyList(),

    /**
     * 聚合的仓储（type=repository，最多1个）
     */
    val repository: ElementMetadata? = null,

    /**
     * 聚合的工厂（type=factory，最多1个）
     */
    val factory: ElementMetadata? = null,

    /**
     * 工厂负载（type=factory-payload，最多1个）
     */
    val factoryPayload: ElementMetadata? = null,

    /**
     * 聚合的规约（type=specification，最多1个）
     */
    val specification: ElementMetadata? = null,

    /**
     * 聚合的领域事件列表（type=domain-event）
     */
    val domainEvents: List<ElementMetadata> = emptyList()
) {
    /**
     * 获取聚合根的 ID 类型
     */
    val identityType: String
        get() = aggregateRoot.identityType

    /**
     * 获取聚合根的包名
     */
    val packageName: String
        get() = aggregateRoot.packageName

    /**
     * 获取聚合根的全限定名
     */
    val qualifiedName: String
        get() = aggregateRoot.qualifiedName
}
