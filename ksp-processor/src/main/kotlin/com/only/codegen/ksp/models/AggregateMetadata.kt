package com.only.codegen.ksp.models

/**
 * 聚合元数据
 * 从 @Aggregate 注解提取的聚合信息
 */
data class AggregateMetadata(
    /**
     * 聚合名称（来自 @Aggregate(aggregate = "...") 属性）
     */
    val aggregateName: String,

    /**
     * 类名（简单名称）
     */
    val className: String,

    /**
     * 类的全限定名
     */
    val qualifiedName: String,

    /**
     * 包名
     */
    val packageName: String,

    /**
     * 是否为聚合根
     */
    val isAggregateRoot: Boolean,

    /**
     * 是否为实体
     */
    val isEntity: Boolean,

    /**
     * 是否为值对象
     */
    val isValueObject: Boolean,

    /**
     * 标识类型（ID 字段的类型）
     */
    val identityType: String,

    /**
     * 字段列表
     */
    val fields: List<FieldMetadata>
)
