package com.only.codegen.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.gson.GsonBuilder
import com.only.codegen.ksp.models.AggregateMetadata
import com.only.codegen.ksp.models.FieldMetadata

/**
 * KSP 注解处理器
 * 扫描 Domain 层的注解并生成元数据 JSON
 */
class AnnotationProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private val aggregates = mutableListOf<AggregateMetadata>()

    @Volatile
    private var metadataGenerated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        processAggregateAnnotations(resolver)

        return emptyList()
    }

    override fun finish() {
        if (!metadataGenerated) {
            generateMetadata()
            metadataGenerated = true
        }
    }

    private fun processAggregateAnnotations(resolver: Resolver) {
        val aggregateSymbols = resolver.getSymbolsWithAnnotation(
            "com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate"
        )

        aggregateSymbols.filterIsInstance<KSClassDeclaration>().forEach { classDecl ->
            val annotation = classDecl.annotations.first {
                it.shortName.asString() == "Aggregate"
            }

            val aggregateName = annotation.arguments
                .find { it.name?.asString() == "aggregate" }
                ?.value as? String
                ?: classDecl.simpleName.asString()

            val isRoot = annotation.arguments
                .find { it.name?.asString() == "root" }
                ?.value as? Boolean
                ?: false

            val type = annotation.arguments
                .find { it.name?.asString() == "type" }
                ?.value as? String
                ?: "Aggregate.TYPE_ENTITY"

            val identityType = resolveIdentityType(classDecl)

            val metadata = AggregateMetadata(
                aggregateName = aggregateName,
                className = classDecl.simpleName.asString(),
                qualifiedName = classDecl.qualifiedName?.asString() ?: "",
                packageName = classDecl.packageName.asString(),
                isAggregateRoot = isRoot,
                isEntity = type == "Aggregate.TYPE_ENTITY",
                isValueObject = type == "Aggregate.TYPE_VALUE_OBJECT",
                identityType = identityType,
                fields = extractFields(classDecl)
            )

            aggregates.add(metadata)
        }
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
        }
    }
}
