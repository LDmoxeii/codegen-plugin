package com.only.codegen.context.aggregate

import com.only.codegen.context.aggregate.builders.AggregateContextBuilder
import com.only.codegen.ksp.models.ElementMetadata

/**
 * 类型映射构建器
 *
 * 遍历所有聚合，收集所有类型到 typeMapping
 * 用于后续生成器的类型引用（如 import 语句）
 *
 * Order: 20（在 KspMetadataContextBuilder 之后执行）
 */
class TypeMappingBuilder : AggregateContextBuilder {

    override val order: Int = 20

    override fun build(context: MutableAggregateContext) {
        context.aggregateMap.values.forEach { aggregateInfo ->
            // 1. 注册聚合根类型
            registerElement(aggregateInfo.aggregateRoot, context)

            // 2. 注册 ID 类型
            recordIdentityType(aggregateInfo.identityType, aggregateInfo.aggregateRoot, context)

            // 3. 注册实体类型
            aggregateInfo.entities.forEach { entity ->
                registerElement(entity, context)
            }

            // 4. 注册值对象类型
            aggregateInfo.valueObjects.forEach { valueObject ->
                registerElement(valueObject, context)
            }

            // 5. 注册枚举类型
            aggregateInfo.enums.forEach { enum ->
                registerElement(enum, context)
            }

            // 6. 注册仓储类型
            aggregateInfo.repository?.let { repo ->
                registerElement(repo, context)
            }

            // 7. 注册工厂类型
            aggregateInfo.factory?.let { factory ->
                registerElement(factory, context)
            }

            // 8. 注册工厂负载类型
            aggregateInfo.factoryPayload?.let { payload ->
                registerElement(payload, context)
            }

            // 9. 注册规约类型
            aggregateInfo.specification?.let { spec ->
                registerElement(spec, context)
            }

            // 10. 注册领域事件类型
            aggregateInfo.domainEvents.forEach { event ->
                registerElement(event, context)
            }
        }
    }

    /**
     * 注册元素类型到 typeMapping
     */
    private fun registerElement(element: ElementMetadata, context: MutableAggregateContext) {
        context.typeMapping[element.className] = element.qualifiedName
    }

    /**
     * 注册 ID 类型到 typeMapping
     */
    private fun recordIdentityType(
        identityType: String,
        rootElement: ElementMetadata,
        context: MutableAggregateContext,
    ) {
        when {
            // 复合主键 (User.PK)
            identityType.contains(".") -> {
                val idTypeName = identityType.substringAfter(".")  // "PK"
                val fullIdType = "${rootElement.qualifiedName}.$idTypeName"
                context.typeMapping[identityType] = fullIdType  // "User.PK" -> "com.example.User.PK"
                context.typeMapping[idTypeName] = fullIdType     // "PK" -> "com.example.User.PK"
            }

            // 原始类型 (Long, String, Int, UUID)
            identityType in listOf("Long", "String", "Int", "UUID") -> {
                // 不需要记录到 typeMapping
            }

            // 自定义 ID 类型 (UserId)
            else -> {
                // 查找是否在 aggregateMap 中
                val idElement = context.aggregateMap.values
                    .flatMap { listOfNotNull(
                        it.aggregateRoot,
                        *it.entities.toTypedArray(),
                        *it.valueObjects.toTypedArray()
                    ) }
                    .find { it.className == identityType }

                if (idElement != null) {
                    context.typeMapping[identityType] = idElement.qualifiedName
                } else {
                    // 假设在聚合根同一包下
                    val assumedFullName = "${rootElement.packageName}.$identityType"
                    context.typeMapping[identityType] = assumedFullName
                }
            }
        }
    }
}
