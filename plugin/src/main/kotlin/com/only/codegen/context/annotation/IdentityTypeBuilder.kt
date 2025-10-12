package com.only.codegen.context.annotation

/**
 * 标识类型构建器
 *
 * 负责解析聚合根的 ID 类型，并填充到 typeMapping 中
 *
 * **执行顺序**: 30（在 AggregateInfoBuilder 之后）
 *
 * **依赖的 Map**:
 * - aggregateMap（已由 AggregateInfoBuilder 填充）
 *
 * **填充的 Map**:
 * - typeMapping: 类型映射（类型简称 → 完整类名）
 *
 * **填充规则**:
 * 1. 对每个聚合根，提取 ID 类型
 * 2. 将 ID 类型放入 typeMapping（如 "UserId" → "com.example.domain.aggregates.user.UserId"）
 * 3. 同时记录聚合根本身（如 "User" → "com.example.domain.aggregates.user.User"）
 */
class IdentityTypeBuilder : AnnotationContextBuilder {

    override val order: Int = 30

    override fun build(context: MutableAnnotationContext) {
        context.aggregateMap.values.forEach { aggregateInfo ->
            // 1. 记录聚合根类型
            val rootClassInfo = aggregateInfo.aggregateRoot
            context.typeMapping[rootClassInfo.simpleName] = rootClassInfo.fullName

            // 2. 记录 ID 类型
            val identityType = aggregateInfo.identityType
            recordIdentityType(identityType, rootClassInfo, context)

            // 3. 记录聚合内的实体和值对象
            aggregateInfo.entities.forEach { entity ->
                context.typeMapping[entity.simpleName] = entity.fullName
            }

            aggregateInfo.valueObjects.forEach { valueObject ->
                context.typeMapping[valueObject.simpleName] = valueObject.fullName
            }
        }
    }

    /**
     * 记录 ID 类型到 typeMapping
     */
    private fun recordIdentityType(
        identityType: String,
        rootClassInfo: ClassInfo,
        context: MutableAnnotationContext,
    ) {
        when {
            // 复合主键：如 "User.PK"
            identityType.contains(".") -> {
                val idTypeName = identityType.substringAfter(".")
                val fullIdType = "${rootClassInfo.fullName}.$idTypeName"
                context.typeMapping[identityType] = fullIdType
                context.typeMapping[idTypeName] = fullIdType
            }

            // 原始类型：Long, String 等
            identityType in listOf("Long", "String", "Int", "UUID") -> {
                // 原始类型不需要记录到 typeMapping
            }

            // 自定义 ID 类型：如 "UserId"
            else -> {
                // 尝试从 classMap 中查找
                val idClassInfo = context.classMap.values.find { it.simpleName == identityType }
                if (idClassInfo != null) {
                    context.typeMapping[identityType] = idClassInfo.fullName
                } else {
                    // 假设在同一包下
                    val assumedFullName = "${rootClassInfo.packageName}.$identityType"
                    context.typeMapping[identityType] = assumedFullName
                }
            }
        }
    }
}
