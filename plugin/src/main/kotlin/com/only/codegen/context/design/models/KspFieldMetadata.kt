package com.only.codegen.context.design.models

/**
 * KSP 字段元数据（设计上下文独立副本）
 *
 * 注意：此类是 ksp-processor 模块的 FieldMetadata 的独立副本
 * 目的是保持 plugin 模块的独立性，避免跨模块引用
 */
data class KspFieldMetadata(
    /**
     * 字段名称
     */
    val name: String,

    /**
     * 字段类型（全限定名）
     */
    val type: String,

    /**
     * 是否为 ID 字段
     */
    val isId: Boolean,

    /**
     * 是否可空
     */
    val isNullable: Boolean,

    /**
     * 字段上的注解列表
     */
    val annotations: List<String>
)
