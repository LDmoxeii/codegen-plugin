package com.only.codegen.context.annotation

/**
 * 聚合信息构建器
 *
 * 负责从 classMap 中识别聚合根，并组织聚合结构（聚合根、实体、值对象）
 *
 * **执行顺序**: 20（在 KspMetadataContextBuilder 之后）
 *
 * **依赖的 Map**:
 * - classMap（已由 KspMetadataContextBuilder 填充）
 * - annotationMap（已由 KspMetadataContextBuilder 填充）
 *
 * **填充的 Map**:
 * - aggregateMap: 聚合信息映射（聚合名 → AggregateInfo）
 *
 * **聚合识别规则**:
 * 1. 查找标记了 `@Aggregate(root=true)` 的类作为聚合根
 * 2. 查找标记了 `@Aggregate(aggregate="同名")` 的类作为同聚合的实体/值对象
 * 3. 根据 `type` 属性区分实体和值对象
 */
class AggregateInfoBuilder : AnnotationContextBuilder {

    override val order: Int = 20

    override fun build(context: MutableAnnotationContext) {
        // 1. 找出所有聚合根
        val aggregateRoots = findAggregateRoots(context)

        // 2. 为每个聚合根构建 AggregateInfo
        aggregateRoots.forEach { (aggregateName, rootClassInfo) ->
            val aggregateInfo = buildAggregateInfo(
                aggregateName = aggregateName,
                rootClassInfo = rootClassInfo,
                context = context
            )

            context.aggregateMap[aggregateName] = aggregateInfo
        }
    }

    /**
     * 查找所有聚合根
     *
     * @return Map<聚合名, 聚合根ClassInfo>
     */
    private fun findAggregateRoots(context: MutableAnnotationContext): Map<String, ClassInfo> {
        val roots = mutableMapOf<String, ClassInfo>()

        context.classMap.values.forEach { classInfo ->
            println("Checking class: ${classInfo.simpleName}, isAggregateRoot: ${classInfo.isAggregateRoot}, annotations: ${classInfo.annotations.map { it.name }}")
            if (classInfo.isAggregateRoot) {
                // 从 @Aggregate 注解中获取聚合名称
                val aggregateAnnotation = classInfo.annotations.find { it.name == "Aggregate" }
                val aggregateName = aggregateAnnotation
                    ?.attributes?.get("aggregate") as? String
                    ?: classInfo.simpleName

                println("Found aggregate root: $aggregateName -> ${classInfo.simpleName}")
                roots[aggregateName] = classInfo
            }
        }

        println("Total aggregate roots found: ${roots.size}")
        return roots
    }

    /**
     * 为单个聚合构建 AggregateInfo
     */
    private fun buildAggregateInfo(
        aggregateName: String,
        rootClassInfo: ClassInfo,
        context: MutableAnnotationContext,
    ): AggregateInfo {
        // 1. 收集同聚合的所有类
        val aggregateClasses = context.classMap.values.filter { classInfo ->
            val aggregateAnnotation = classInfo.annotations.find { it.name == "Aggregate" }
            val belongsToAggregate = aggregateAnnotation
                ?.attributes?.get("aggregate") as? String
                ?: classInfo.simpleName

            belongsToAggregate == aggregateName
        }

        // 2. 分类：实体 vs 值对象
        val entities = mutableListOf<ClassInfo>()
        val valueObjects = mutableListOf<ClassInfo>()

        aggregateClasses.forEach { classInfo ->
            when {
                classInfo.isAggregateRoot -> {
                    // 聚合根不放入 entities 列表（已单独记录）
                }

                classInfo.isEntity -> entities.add(classInfo)
                classInfo.isValueObject -> valueObjects.add(classInfo)
            }
        }

        // 3. 解析聚合根的 ID 类型
        val identityType = resolveIdentityType(rootClassInfo)

        // 4. 确定模块路径（从包名推导）
        val modulePath = resolveModulePath(rootClassInfo.packageName, context)

        return AggregateInfo(
            name = aggregateName,
            aggregateRoot = rootClassInfo,
            entities = entities,
            valueObjects = valueObjects,
            identityType = identityType,
            modulePath = modulePath
        )
    }

    /**
     * 解析聚合根的 ID 类型
     *
     * 规则：
     * 1. 单个 @Id 字段 → 返回字段类型
     * 2. 多个 @Id 字段（复合主键）→ 返回 "${类名}.PK"
     * 3. 无 @Id 字段 → 默认 "Long"
     */
    private fun resolveIdentityType(rootClassInfo: ClassInfo): String {
        val idFields = rootClassInfo.fields.filter { it.isId }

        return when {
            idFields.isEmpty() -> "Long"
            idFields.size == 1 -> {
                val fieldType = idFields.first().type
                // 简化类型名（去掉包名）
                fieldType.substringAfterLast('.')
            }

            else -> "${rootClassInfo.simpleName}.PK"
        }
    }

    /**
     * 从包名推导模块路径
     *
     * 例如: "com.example.domain.aggregates.user" → "domain"
     *
     * 简化实现：从 BaseContext 获取 domainPath
     */
    private fun resolveModulePath(packageName: String, context: MutableAnnotationContext): String {
        // 简化：直接返回 domain 模块路径
        // 实际使用时会从 BaseContext 获取
        return context.domainPath
    }
}
