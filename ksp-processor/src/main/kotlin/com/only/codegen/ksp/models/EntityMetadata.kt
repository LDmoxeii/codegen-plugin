package com.only.codegen.ksp.models

/**
 * 实体元数据
 * 从 @Entity 注解提取的实体信息
 */
data class EntityMetadata(
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
     * 字段列表
     */
    val fields: List<FieldMetadata>
)
