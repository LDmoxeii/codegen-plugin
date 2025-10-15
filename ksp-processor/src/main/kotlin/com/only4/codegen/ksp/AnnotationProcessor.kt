package com.only4.codegen.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.gson.GsonBuilder
import com.only4.codegen.ksp.models.AggregateMetadata
import com.only4.codegen.ksp.models.ElementMetadata
import com.only4.codegen.ksp.models.ElementMetadata.Companion.ElementType
import com.only4.codegen.ksp.models.FieldMetadata

/**
 * KSP 注解处理器（两阶段处理）
 *
 * Phase 1: 扫描所有 @Aggregate 注解的类，构建 AnnotatedElement（包含 aggregateName）
 * Phase 2: 按 aggregateName 分组，构建以聚合根为中心的 AggregateMetadata
 */
class AnnotationProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    /**
     * 带注解信息的元素（临时结构）
     */
    private data class AnnotatedElement(
        val aggregateName: String,
        val element: ElementMetadata
    )

    // Phase 1: 扁平的元素列表（带 aggregateName）
    private val annotatedElements = mutableListOf<AnnotatedElement>()

    // Phase 2: 以聚合为单位的元数据
    private val aggregates = mutableListOf<AggregateMetadata>()

    @Volatile
    private var metadataGenerated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Phase 1: 扫描所有 @Aggregate 注解的类
        scanAggregateElements(resolver)

        return emptyList()
    }

    override fun finish() {
        if (!metadataGenerated) {
            // Phase 2: 构建聚合层次结构
            buildAggregateHierarchy()

            // 生成 JSON
            generateMetadata()
            metadataGenerated = true
        }
    }

    /**
     * Phase 1: 扫描所有 @Aggregate 注解的类，构建 ElementMetadata
     */
    private fun scanAggregateElements(resolver: Resolver) {
        val aggregateSymbols = resolver.getSymbolsWithAnnotation(
            "com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate"
        )

        aggregateSymbols.filterIsInstance<KSClassDeclaration>().forEach { classDecl ->
            val annotation = classDecl.annotations.first {
                it.shortName.asString() == "Aggregate"
            }

            // 提取注解属性
            val aggregateName = annotation.arguments
                .find { it.name?.asString() == "aggregate" }
                ?.value as? String
                ?: classDecl.simpleName.asString()

            val isRoot = annotation.arguments
                .find { it.name?.asString() == "root" }
                ?.value as? Boolean
                ?: false

            val typeRaw = annotation.arguments
                .find { it.name?.asString() == "type" }
                ?.value as? String
                ?: ""

            // 规范化类型（去掉 "Aggregate.TYPE_" 前缀）
            val type = normalizeType(typeRaw)

            val identityType = resolveIdentityType(classDecl)

            val element = ElementMetadata(
                className = resolveClassName(classDecl),
                qualifiedName = classDecl.qualifiedName?.asString() ?: "",
                packageName = classDecl.packageName.asString(),
                type = type,
                isAggregateRoot = isRoot,
                identityType = identityType,
                fields = extractFields(classDecl)
            )

            // 存储为 AnnotatedElement
            annotatedElements.add(AnnotatedElement(aggregateName, element))

            logger.info("Scanned element: aggregate=$aggregateName, class=${element.className}, type=$type, root=$isRoot")
        }
    }

    /**
     * 规范化类型字符串
     * 将 "Aggregate.TYPE_ENTITY" 转换为 "entity"
     */
    private fun normalizeType(typeRaw: String): String {
        return when {
            typeRaw.endsWith("TYPE_ENTITY") || typeRaw == ElementType.ENTITY -> ElementType.ENTITY
            typeRaw.endsWith("TYPE_VALUE_OBJECT") || typeRaw == ElementType.VALUE_OBJECT -> ElementType.VALUE_OBJECT
            typeRaw.endsWith("TYPE_ENUM") || typeRaw == ElementType.ENUM -> ElementType.ENUM
            typeRaw.endsWith("TYPE_REPOSITORY") || typeRaw == ElementType.REPOSITORY -> ElementType.REPOSITORY
            typeRaw.endsWith("TYPE_DOMAIN_EVENT") || typeRaw == ElementType.DOMAIN_EVENT -> ElementType.DOMAIN_EVENT
            typeRaw.endsWith("TYPE_FACTORY") || typeRaw == ElementType.FACTORY -> ElementType.FACTORY
            typeRaw.endsWith("TYPE_FACTORY_PAYLOAD") || typeRaw == ElementType.FACTORY_PAYLOAD -> ElementType.FACTORY_PAYLOAD
            typeRaw.endsWith("TYPE_SPECIFICATION") || typeRaw == ElementType.SPECIFICATION -> ElementType.SPECIFICATION
            else -> {
                logger.warn("Unknown type: $typeRaw, defaulting to entity")
                ElementType.ENTITY
            }
        }
    }

    /**
     * Phase 2: 按 aggregateName 分组，构建聚合层次结构
     */
    private fun buildAggregateHierarchy() {
        // 按 aggregateName 分组
        val elementsByAggregate = annotatedElements.groupBy { it.aggregateName }

        elementsByAggregate.forEach { (aggregateName, annotatedElementList) ->
            val elements = annotatedElementList.map { it.element }

            // 查找聚合根（root=true 且 type=entity）
            val aggregateRootCandidates = elements.filter {
                it.isAggregateRoot && it.type == ElementType.ENTITY
            }

            if (aggregateRootCandidates.isEmpty()) {
                logger.warn("Aggregate '$aggregateName' has no aggregate root (root=true, type=entity), skipping")
                return@forEach
            }

            if (aggregateRootCandidates.size > 1) {
                logger.error("Aggregate '$aggregateName' has multiple aggregate roots: ${aggregateRootCandidates.map { it.className }}, using first")
            }

            val aggregateRoot = aggregateRootCandidates.first()

            // 按类型分类其他元素
            val entities = elements.filter {
                it.type == ElementType.ENTITY && !it.isAggregateRoot
            }

            val valueObjects = elements.filter {
                it.type == ElementType.VALUE_OBJECT
            }

            val enums = elements.filter {
                it.type == ElementType.ENUM
            }

            val repositoryCandidates = elements.filter {
                it.type == ElementType.REPOSITORY
            }
            if (repositoryCandidates.size > 1) {
                logger.warn("Aggregate '$aggregateName' has multiple repositories: ${repositoryCandidates.map { it.className }}, using first")
            }
            val repository = repositoryCandidates.firstOrNull()

            val factoryCandidates = elements.filter {
                it.type == ElementType.FACTORY
            }
            if (factoryCandidates.size > 1) {
                logger.warn("Aggregate '$aggregateName' has multiple factories: ${factoryCandidates.map { it.className }}, using first")
            }
            val factory = factoryCandidates.firstOrNull()

            val factoryPayloadCandidates = elements.filter {
                it.type == ElementType.FACTORY_PAYLOAD
            }
            if (factoryPayloadCandidates.size > 1) {
                logger.warn("Aggregate '$aggregateName' has multiple factory payloads: ${factoryPayloadCandidates.map { it.className }}, using first")
            }
            val factoryPayload = factoryPayloadCandidates.firstOrNull()

            val specificationCandidates = elements.filter {
                it.type == ElementType.SPECIFICATION
            }
            if (specificationCandidates.size > 1) {
                logger.warn("Aggregate '$aggregateName' has multiple specifications: ${specificationCandidates.map { it.className }}, using first")
            }
            val specification = specificationCandidates.firstOrNull()

            val domainEvents = elements.filter {
                it.type == ElementType.DOMAIN_EVENT
            }

            // 构建 AggregateMetadata
            val aggregateMetadata = AggregateMetadata(
                aggregateName = aggregateName,
                aggregateRoot = aggregateRoot,
                entities = entities,
                valueObjects = valueObjects,
                enums = enums,
                repository = repository,
                factory = factory,
                factoryPayload = factoryPayload,
                specification = specification,
                domainEvents = domainEvents
            )

            aggregates.add(aggregateMetadata)

            logger.info(
                "Built aggregate: $aggregateName " +
                        "(root=${aggregateRoot.className}, " +
                        "entities=${entities.size}, " +
                        "valueObjects=${valueObjects.size}, " +
                        "enums=${enums.size}, " +
                        "repository=${repository?.className}, " +
                        "factory=${factory?.className}, " +
                        "domainEvents=${domainEvents.size})"
            )
        }
    }

    /**
     * 解析类名（处理嵌套类）
     *
     * 对于嵌套类，返回相对于包的完整路径，例如：
     * - 普通类: "Category"
     * - 嵌套类: "CategoryFactory.Payload"
     */
    private fun resolveClassName(classDecl: KSClassDeclaration): String {
        val qualifiedName = classDecl.qualifiedName?.asString() ?: return classDecl.simpleName.asString()
        val packageName = classDecl.packageName.asString()

        // 如果没有包名，直接返回简单名
        if (packageName.isEmpty()) {
            return classDecl.simpleName.asString()
        }

        // 去掉包名前缀，得到类的相对路径
        // 例如: "com.example.CategoryFactory.Payload" - "com.example" = "CategoryFactory.Payload"
        val relativeClassName = if (qualifiedName.startsWith("$packageName.")) {
            qualifiedName.substring(packageName.length + 1)
        } else {
            classDecl.simpleName.asString()
        }

        return relativeClassName
    }

    private fun resolveIdentityType(classDecl: KSClassDeclaration): String {
        val idFields = classDecl.getAllProperties()
            .filter { property ->
                property.annotations.any {
                    it.shortName.asString() == "Id"
                }
            }
            .toList()

        return when {
            idFields.isEmpty() -> "Long"
            idFields.size == 1 -> {
                val type = idFields.first().type.resolve()
                type.declaration.qualifiedName?.asString() ?: "Long"
            }

            else -> "${classDecl.simpleName.asString()}.PK"
        }
    }

    private fun extractFields(classDecl: KSClassDeclaration): List<FieldMetadata> {
        return classDecl.getAllProperties().map { property ->
            val isId = property.annotations.any {
                it.shortName.asString() == "Id"
            }

            val type = property.type.resolve()
            val typeName = type.declaration.qualifiedName?.asString()
                ?: type.toString()

            FieldMetadata(
                name = property.simpleName.asString(),
                type = typeName,
                isId = isId,
                isNullable = type.isMarkedNullable,
                annotations = property.annotations.map {
                    it.shortName.asString()
                }.toList()
            )
        }.toList()
    }

    private fun generateMetadata() {
        val gson = GsonBuilder().setPrettyPrinting().create()

        if (aggregates.isNotEmpty()) {
            val aggregatesFile = codeGenerator.createNewFile(
                Dependencies(false),
                "metadata",
                "aggregates",
                "json"
            )
            aggregatesFile.write(gson.toJson(aggregates).toByteArray())
            aggregatesFile.close()

            logger.info("Generated aggregates.json with ${aggregates.size} aggregates")
        } else {
            logger.warn("No aggregates found, skipping JSON generation")
        }
    }
}
