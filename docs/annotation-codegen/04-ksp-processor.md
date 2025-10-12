# 4. KSP Processor 实现

[← 上一章：核心接口和类设计](03-core-interfaces.md) | [返回目录](README.md) | [下一章：配置和模板 →](05-configuration-templates.md)

---

## 4.1 项目结构

```
codegen-plugin/
├── plugin/                      # Gradle Plugin（现有）
│   └── build.gradle.kts
├── ksp-processor/               # KSP Processor（新增）
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       └── com/only/codegen/ksp/
│           ├── AnnotationProcessor.kt
│           ├── AnnotationProcessorProvider.kt
│           └── models/
│               ├── AggregateMetadata.kt
│               ├── EntityMetadata.kt
│               └── FieldMetadata.kt
└── settings.gradle.kts
```

## 4.2 依赖配置

#### plugin/build.gradle.kts

```kotlin
plugins {
    id("java-gradle-plugin")
    kotlin("jvm")
}

dependencies {
    // 现有依赖...

    // 添加 Gson 用于解析 KSP 输出的 JSON
    implementation("com.google.code.gson:gson:2.10.1")
}
```

#### ksp-processor/build.gradle.kts（新模块）

```kotlin
plugins {
    kotlin("jvm")
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:1.9.20-1.0.14")
    implementation("com.google.code.gson:gson:2.10.1")

    // 测试
    testImplementation("com.google.devtools.ksp:symbol-processing:1.9.20-1.0.14")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing-ksp:1.5.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("com.google.truth:truth:1.1.5")
}
```

#### settings.gradle.kts

```kotlin
rootProject.name = "codegen-plugin"

include(":plugin")
include(":ksp-processor")  // 新增
```

## 4.3 AnnotationProcessor 实现

```kotlin
package com.only.codegen.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.gson.GsonBuilder

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
    private val entities = mutableListOf<EntityMetadata>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // 1. 处理 @Aggregate 注解
        processAggregateAnnotations(resolver)

        // 2. 处理 @Entity 注解
        processEntityAnnotations(resolver)

        // 3. 生成元数据 JSON
        generateMetadata()

        return emptyList()
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

            logger.info("Processed aggregate: $aggregateName (root=$isRoot)")
        }
    }

    private fun processEntityAnnotations(resolver: Resolver) {
        val entitySymbols = resolver.getSymbolsWithAnnotation(
            "jakarta.persistence.Entity"
        )

        entitySymbols.filterIsInstance<KSClassDeclaration>().forEach { classDecl ->
            val metadata = EntityMetadata(
                className = classDecl.simpleName.asString(),
                qualifiedName = classDecl.qualifiedName?.asString() ?: "",
                packageName = classDecl.packageName.asString(),
                fields = extractFields(classDecl)
            )

            entities.add(metadata)
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

        // 生成 aggregates.json
        val aggregatesFile = codeGenerator.createNewFile(
            Dependencies(false),
            "metadata",
            "aggregates",
            "json"
        )
        aggregatesFile.write(gson.toJson(aggregates).toByteArray())

        // 生成 entities.json
        val entitiesFile = codeGenerator.createNewFile(
            Dependencies(false),
            "metadata",
            "entities",
            "json"
        )
        entitiesFile.write(gson.toJson(entities).toByteArray())

        logger.info("Generated metadata: ${aggregates.size} aggregates, ${entities.size} entities")
    }
}

/**
 * KSP Processor Provider
 */
class AnnotationProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return AnnotationProcessor(
            environment.codeGenerator,
            environment.logger,
            environment.options
        )
    }
}
```

## 4.4 元数据模型

```kotlin
package com.only.codegen.ksp.models

/**
 * 聚合元数据
 */
data class AggregateMetadata(
    val aggregateName: String,
    val className: String,
    val qualifiedName: String,
    val packageName: String,
    val isAggregateRoot: Boolean,
    val isEntity: Boolean,
    val isValueObject: Boolean,
    val identityType: String,
    val fields: List<FieldMetadata>
)

/**
 * 实体元数据
 */
data class EntityMetadata(
    val className: String,
    val qualifiedName: String,
    val packageName: String,
    val fields: List<FieldMetadata>
)

/**
 * 字段元数据
 */
data class FieldMetadata(
    val name: String,
    val type: String,
    val isId: Boolean,
    val isNullable: Boolean,
    val annotations: List<String>
)
```

## 4.5 KSP 服务注册

在 `ksp-processor/src/main/resources/META-INF/services/` 目录下创建：

**com.google.devtools.ksp.processing.SymbolProcessorProvider**

```
com.only.codegen.ksp.AnnotationProcessorProvider
```

---

[← 上一章：核心接口和类设计](03-core-interfaces.md) | [返回目录](README.md) | [下一章：配置和模板 →](05-configuration-templates.md)
