package com.only.codegen.context.aggregate

class IdentityTypeBuilder : AggregateContextBuilder {

    override val order: Int = 30

    override fun build(context: MutableAnnotationContext) {
        context.aggregateMap.values.forEach { aggregateInfo ->

            val rootClassInfo = aggregateInfo.aggregateRoot
            context.typeMapping[rootClassInfo.simpleName] = rootClassInfo.fullName

            val identityType = aggregateInfo.identityType
            recordIdentityType(identityType, rootClassInfo, context)

            aggregateInfo.entities.forEach { entity ->
                context.typeMapping[entity.simpleName] = entity.fullName
            }

            aggregateInfo.valueObjects.forEach { valueObject ->
                context.typeMapping[valueObject.simpleName] = valueObject.fullName
            }
        }
    }

    private fun recordIdentityType(
        identityType: String,
        rootClassInfo: ClassInfo,
        context: MutableAnnotationContext,
    ) {
        when {
            identityType.contains(".") -> {
                val idTypeName = identityType.substringAfter(".")
                val fullIdType = "${rootClassInfo.fullName}.$idTypeName"
                context.typeMapping[identityType] = fullIdType
                context.typeMapping[idTypeName] = fullIdType
            }

            identityType in listOf("Long", "String", "Int", "UUID") -> {
                // 原始类型不需要记录到 typeMapping
            }

            else -> {
                val idClassInfo = context.classMap.values.find { it.simpleName == identityType }
                if (idClassInfo != null) {
                    context.typeMapping[identityType] = idClassInfo.fullName
                } else {
                    val assumedFullName = "${rootClassInfo.packageName}.$identityType"
                    context.typeMapping[identityType] = assumedFullName
                }
            }
        }
    }
}
