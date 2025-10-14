package com.only.codegen.context.design.models

/**
 * KSP 聚合元素元数据（设计上下文独立副本）
 *
 * 注意：此类是 ksp-processor 模块的 ElementMetadata 的独立副本
 * 目的是保持 plugin 模块的独立性，避免跨模块引用
 *
 * 用于表示聚合内的各种元素：
 * - 聚合根（Entity, root=true）
 * - 实体（Entity, root=false）
 * - 值对象（Value Object）
 * - 枚举（Enum）
 * - 仓储（Repository）
 * - 工厂（Factory）
 * - 工厂负载（Factory Payload）
 * - 规约（Specification）
 * - 领域事件（Domain Event）
 */
data class KspElementMetadata(
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
     * 元素类型
     * @see ElementType
     */
    val type: String,

    /**
     * 是否为聚合根
     * 只有 type=entity 且 root=true 时才是聚合根
     */
    val isAggregateRoot: Boolean,

    /**
     * 标识类型（ID 字段的类型）
     * 对于非实体/聚合根，可能为 null 或继承自聚合根的 ID 类型
     */
    val identityType: String,

    /**
     * 字段列表
     */
    val fields: List<KspFieldMetadata>
) {
    companion object {
        /**
         * 元素类型常量（与 @Aggregate 注解的 type 属性对应）
         */
        object ElementType {
            const val ENTITY = "entity"
            const val VALUE_OBJECT = "value-object"
            const val ENUM = "enum"
            const val REPOSITORY = "repository"
            const val DOMAIN_EVENT = "domain-event"
            const val FACTORY = "factory"
            const val FACTORY_PAYLOAD = "factory-payload"
            const val SPECIFICATION = "specification"
        }
    }
}
