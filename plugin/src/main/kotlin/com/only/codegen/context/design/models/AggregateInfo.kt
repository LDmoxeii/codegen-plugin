package com.only.codegen.context.design.models

import com.only.codegen.ksp.models.AggregateMetadata
import com.only.codegen.ksp.models.ElementMetadata

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
