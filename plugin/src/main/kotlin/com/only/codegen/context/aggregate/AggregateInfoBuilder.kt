package com.only.codegen.context.aggregate

class AggregateInfoBuilder : AggregateContextBuilder {

    override val order: Int = 20

    override fun build(context: MutableAnnotationContext) {
        val aggregateRoots = findAggregateRoots(context)

        aggregateRoots.forEach { (aggregateName, rootClassInfo) ->
            val aggregateInfo = buildAggregateInfo(
                aggregateName = aggregateName,
                rootClassInfo = rootClassInfo,
                context = context
            )

            context.aggregateMap[aggregateName] = aggregateInfo
        }
    }

    private fun findAggregateRoots(context: MutableAnnotationContext): Map<String, ClassInfo> {
        val roots = mutableMapOf<String, ClassInfo>()

        context.classMap.values.forEach { classInfo ->
            if (classInfo.isAggregateRoot) {
                val aggregateAnnotation = classInfo.annotations.find { it.name == "Aggregate" }
                val aggregateName = aggregateAnnotation
                    ?.attributes?.get("root") as? String
                    ?: classInfo.simpleName

                roots[aggregateName] = classInfo
            }
        }

        return roots
    }

    private fun buildAggregateInfo(
        aggregateName: String,
        rootClassInfo: ClassInfo,
        context: MutableAnnotationContext,
    ): AggregateInfo {
        val aggregateClasses = context.classMap.values.filter { classInfo ->
            val aggregateAnnotation = classInfo.annotations.find { it.name == "Aggregate" }
            val belongsToAggregate = aggregateAnnotation
                ?.attributes?.get("aggregate") as? String
                ?: classInfo.simpleName

            belongsToAggregate == aggregateName
        }

        val entities = mutableListOf<ClassInfo>()
        val valueObjects = mutableListOf<ClassInfo>()

        aggregateClasses.forEach { classInfo ->
            when {
                classInfo.isAggregateRoot -> {}

                classInfo.isEntity -> entities.add(classInfo)
                classInfo.isValueObject -> valueObjects.add(classInfo)
            }
        }

        val identityType = resolveIdentityType(rootClassInfo)

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

    private fun resolveModulePath(packageName: String, context: MutableAnnotationContext): String {
        return context.domainPath
    }
}
