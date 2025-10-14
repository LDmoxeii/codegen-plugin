package com.only.codegen.context.aggregate

import com.only.codegen.context.BaseContext
import com.only.codegen.ksp.models.AggregateMetadata
import com.only.codegen.ksp.models.ElementMetadata

/**
 * 基于注解的代码生成上下文（只读接口）
 *
 * 与 EntityContext 完全独立，但共享 BaseContext 的基础属性
 * 直接使用 KSP 的 AggregateMetadata 结构，避免冗余转换
 */
interface AnnotationContext : BaseContext {

    /**
     * 聚合信息映射（直接映射 KSP 的 AggregateMetadata）
     * key: 聚合名称（如 "User", "Order"）
     * value: AggregateInfo（包含聚合根、实体、值对象等）
     */
    val aggregateMap: Map<String, AggregateInfo>

    /**
     * 源代码根目录（用于 KSP 扫描）
     */
    val sourceRoots: List<String>

    /**
     * 扫描的包路径（可选过滤）
     */
    val scanPackages: List<String>
}

/**
 * 可变的注解上下文（用于构建阶段）
 *
 * Builder 模式：各个 AggregateContextBuilder 会修改此上下文
 */
interface MutableAnnotationContext : AnnotationContext {
    override val aggregateMap: MutableMap<String, AggregateInfo>
}

/**
 * 聚合信息（从 KSP 的 AggregateMetadata 转换而来）
 */
data class AggregateInfo(
    val name: String,                        // 聚合名称
    val aggregateRoot: ElementMetadata,      // 聚合根（直接使用 KSP 的 ElementMetadata）
    val entities: List<ElementMetadata>,     // 聚合内的实体
    val valueObjects: List<ElementMetadata>, // 聚合内的值对象
    val enums: List<ElementMetadata>,        // 聚合内的枚举
    val repository: ElementMetadata?,        // 仓储（可选）
    val factory: ElementMetadata?,           // 工厂（可选）
    val factoryPayload: ElementMetadata?,    // 工厂负载（可选）
    val specification: ElementMetadata?,     // 规约（可选）
    val domainEvents: List<ElementMetadata>, // 领域事件
    val identityType: String,                // 聚合根的 ID 类型
    val modulePath: String,                   // 所属模块路径
) {
    companion object {
        /**
         * 从 KSP 的 AggregateMetadata 转换
         */
        fun fromKspMetadata(
            metadata: AggregateMetadata,
            modulePath: String
        ): AggregateInfo {
            return AggregateInfo(
                name = metadata.aggregateName,
                aggregateRoot = metadata.aggregateRoot,
                entities = metadata.entities,
                valueObjects = metadata.valueObjects,
                enums = metadata.enums,
                repository = metadata.repository,
                factory = metadata.factory,
                factoryPayload = metadata.factoryPayload,
                specification = metadata.specification,
                domainEvents = metadata.domainEvents,
                identityType = resolveIdentityType(metadata.aggregateRoot),
                modulePath = modulePath
            )
        }

        /**
         * 解析 ID 类型
         */
        private fun resolveIdentityType(root: ElementMetadata): String {
            val idFields = root.fields.filter { it.isId }

            return when {
                idFields.isEmpty() -> "Long"
                idFields.size == 1 -> {
                    val fieldType = idFields.first().type
                    // 简化类型名（去掉包名）
                    fieldType.substringAfterLast('.')
                }
                else -> "${root.className}.PK"
            }
        }
    }
}
