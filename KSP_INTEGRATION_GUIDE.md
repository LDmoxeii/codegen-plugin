# KSP 集成方案：简化注解处理

## 1. 方案概述

本文档描述如何集成 KSP (Kotlin Symbol Processing) 来简化基于注解的代码生成，替代原方案中的正则表达式解析。

### 1.1 KSP 简介

**KSP (Kotlin Symbol Processing)** 是 Google 开发的 Kotlin 编译器插件 API，专门用于注解处理：

✅ **类型安全**：完整的类型信息，无需字符串解析
✅ **性能优越**：比 KAPT 快 2 倍以上
✅ **Kotlin 原生**：完全支持 Kotlin 特性（扩展函数、属性委托等）
✅ **增量编译**：支持增量处理，只处理变更的文件
✅ **IDE 集成**：与 IntelliJ IDEA 深度集成，提供实时错误检查

### 1.2 KSP vs 正则表达式对比

| 维度 | 正则表达式 | KSP |
|------|-----------|-----|
| **准确性** | ⚠️ 有限，复杂注解易出错 | ✅ 完全准确，编译器级别 |
| **类型信息** | ❌ 无，只能解析字符串 | ✅ 完整类型信息 |
| **性能** | ✅ 快速 | ✅ 快速（增量编译更快） |
| **维护成本** | ⚠️ 正则复杂，难维护 | ✅ 代码清晰，易维护 |
| **依赖** | ✅ 零依赖 | ⚠️ 需要 KSP 依赖 |
| **复杂注解** | ❌ 不支持嵌套、数组等 | ✅ 完全支持 |
| **IDE 支持** | ❌ 无 | ✅ 完整 IDE 支持 |

### 1.3 推荐方案：混合模式

**保留正则表达式作为后备方案**，同时支持 KSP 作为高级选项：

```kotlin
codegen {
    annotation {
        // 模式选择
        parsingMode.set("ksp")  // "ksp" 或 "regex"
    }
}
```

## 2. KSP 架构设计

### 2.1 整体架构

```
┌────────────────────────────────────────────────────────┐
│                    Gradle Plugin                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │              GenAnnotationTask                    │  │
│  │  - 触发 KSP 编译                                  │  │
│  │  - 读取 KSP 输出的元数据                          │  │
│  │  - 构建 AnnotationContext                        │  │
│  │  - 调用 Generator 生成代码                       │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────┐
        │      KSP Processor             │
        │  (编译时运行)                  │
        │  ┌──────────────────────────┐  │
        │  │ AnnotationProcessor      │  │
        │  │ - 扫描 @Aggregate        │  │
        │  │ - 扫描 @Entity           │  │
        │  │ - 扫描 @Id               │  │
        │  │ - 生成元数据 JSON        │  │
        │  └──────────────────────────┘  │
        └────────────────────────────────┘
                         ↓
        ┌────────────────────────────────┐
        │  元数据 JSON 文件               │
        │  build/generated/ksp/metadata/  │
        │  - aggregates.json             │
        │  - entities.json               │
        │  - annotations.json            │
        └────────────────────────────────┘
                         ↓
        ┌────────────────────────────────┐
        │  AnnotationContext             │
        │  (从 JSON 构建)                │
        └────────────────────────────────┘
                         ↓
        ┌────────────────────────────────┐
        │  Generators                    │
        │  - RepositoryGenerator         │
        │  - ServiceGenerator            │
        │  - ControllerGenerator         │
        └────────────────────────────────┘
```

### 2.2 关键决策

#### 为什么不直接在 KSP Processor 中生成代码？

**原因：**
1. **架构一致性**：保持 Context + Builder + Generator 模式
2. **灵活性**：Processor 只负责元数据提取，生成逻辑在 Gradle Plugin 中
3. **可测试性**：元数据和生成逻辑分离，易于测试
4. **后备方案**：可以在 KSP 失败时降级到正则表达式

#### KSP Processor 的职责

- ✅ 扫描注解
- ✅ 提取类型信息
- ✅ 生成元数据 JSON
- ❌ **不生成**最终代码（由 Gradle Plugin 负责）

## 3. 依赖配置

### 3.1 项目结构

```
codegen-plugin/
├── plugin/                      # Gradle Plugin（现有）
│   └── build.gradle.kts
├── ksp-processor/               # KSP Processor（新增）
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       └── com/only/codegen/ksp/
│           ├── AnnotationProcessor.kt
│           ├── visitors/
│           └── models/
└── settings.gradle.kts
```

### 3.2 依赖添加

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

    // JSON 序列化
    implementation("com.google.code.gson:gson:2.10.1")

    // 测试
    testImplementation("com.google.devtools.ksp:symbol-processing:1.9.20-1.0.14")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing-ksp:1.5.0")
}
```

#### settings.gradle.kts

```kotlin
rootProject.name = "codegen-plugin"

include(":plugin")
include(":ksp-processor")  // 新增
```

## 4. KSP Processor 实现

### 4.1 AnnotationProcessor

```kotlin
package com.only.codegen.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.gson.GsonBuilder
import java.io.File

/**
 * KSP 注解处理器
 * 扫描 Domain 层的注解并生成元数据
 */
class AnnotationProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private val aggregates = mutableListOf<AggregateMetadata>()
    private val entities = mutableListOf<EntityMetadata>()
    private val annotations = mutableMapOf<String, MutableList<AnnotationUsage>>()

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

### 4.2 元数据模型

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

/**
 * 注解使用记录
 */
data class AnnotationUsage(
    val annotationName: String,
    val targetClass: String,
    val attributes: Map<String, Any?>
)
```

### 4.3 KSP 服务注册

在 `ksp-processor/src/main/resources/META-INF/services/` 目录下创建：

**com.google.devtools.ksp.processing.SymbolProcessorProvider**

```
com.only.codegen.ksp.AnnotationProcessorProvider
```

## 5. Gradle Plugin 集成

### 5.1 更新 GenAnnotationTask

```kotlin
package com.only.codegen

import com.google.gson.Gson
import com.only.codegen.ksp.models.AggregateMetadata
import java.io.File

open class GenAnnotationTask : GenArchTask(), MutableAnnotationContext {

    /**
     * 解析模式：ksp 或 regex
     */
    private val parsingMode by lazy {
        getString("annotationParsingMode", "regex")
    }

    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()

        when (parsingMode) {
            "ksp" -> genFromKspMetadata()
            "regex" -> genFromRegexParsing()
            else -> {
                logger.error("Unknown parsing mode: $parsingMode")
                genFromRegexParsing() // 降级到正则
            }
        }
    }

    /**
     * 从 KSP 生成的元数据构建上下文
     */
    private fun genFromKspMetadata() {
        logger.lifecycle("Using KSP metadata for annotation processing")

        val metadataDir = File(project.buildDir, "generated/ksp/main/metadata")
        if (!metadataDir.exists()) {
            logger.error("KSP metadata directory not found: $metadataDir")
            logger.warn("Falling back to regex parsing")
            genFromRegexParsing()
            return
        }

        val context = buildContextFromKspMetadata(metadataDir)

        if (context.classMap.isEmpty()) {
            logger.warn("No classes found in KSP metadata")
            return
        }

        logger.lifecycle("Found ${context.classMap.size} classes from KSP")
        logger.lifecycle("Found ${context.aggregateMap.size} aggregates from KSP")

        generateFiles(context)
    }

    /**
     * 从 KSP 元数据构建 AnnotationContext
     */
    private fun buildContextFromKspMetadata(metadataDir: File): AnnotationContext {
        val gson = Gson()

        // 读取 aggregates.json
        val aggregatesFile = File(metadataDir, "aggregates.json")
        if (aggregatesFile.exists()) {
            val aggregatesJson = aggregatesFile.readText()
            val aggregates = gson.fromJson(
                aggregatesJson,
                Array<AggregateMetadata>::class.java
            )

            aggregates.forEach { metadata ->
                val classInfo = ClassInfo(
                    packageName = metadata.packageName,
                    simpleName = metadata.className,
                    fullName = metadata.qualifiedName,
                    filePath = "", // KSP 不需要文件路径
                    annotations = listOf(), // 后续填充
                    fields = metadata.fields.map { field ->
                        FieldInfo(
                            name = field.name,
                            type = field.type,
                            annotations = emptyList(),
                            isId = field.isId,
                            isNullable = field.isNullable,
                            defaultValue = null
                        )
                    },
                    superClass = null,
                    interfaces = emptyList(),
                    isAggregateRoot = metadata.isAggregateRoot,
                    isEntity = metadata.isEntity,
                    isValueObject = metadata.isValueObject
                )

                classMap[metadata.qualifiedName] = classInfo

                // 如果是聚合根，添加到 aggregateMap
                if (metadata.isAggregateRoot) {
                    val aggregateInfo = AggregateInfo(
                        name = metadata.aggregateName,
                        aggregateRoot = classInfo,
                        entities = emptyList(), // 后续填充
                        valueObjects = emptyList(),
                        identityType = metadata.identityType,
                        modulePath = "" // 从配置获取
                    )
                    aggregateMap[metadata.aggregateName] = aggregateInfo
                }
            }
        }

        return this
    }

    /**
     * 使用正则表达式解析（后备方案）
     */
    private fun genFromRegexParsing() {
        logger.lifecycle("Using regex parsing for annotation processing")

        val context = buildAnnotationContext()

        if (context.classMap.isEmpty()) {
            logger.warn("No classes found in source roots")
            return
        }

        logger.lifecycle("Found ${context.classMap.size} classes")
        logger.lifecycle("Found ${context.aggregateMap.size} aggregates")

        generateFiles(context)
    }

    // ... 其他方法保持不变
}
```

### 5.2 配置 KSP 编译

在用户项目的 `build.gradle.kts` 中：

```kotlin
plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "1.9.20-1.0.14"
    id("com.only.codegen") version "1.0.0"
}

dependencies {
    // 添加 KSP Processor
    ksp(project(":codegen-plugin:ksp-processor"))
    // 或从 Maven 发布的版本
    // ksp("com.only:codegen-ksp-processor:1.0.0")
}

codegen {
    annotation {
        // 启用 KSP 模式
        parsingMode.set("ksp")
    }
}
```

### 5.3 自动配置 KSP

在 `CodegenPlugin.kt` 中自动应用 KSP 插件：

```kotlin
class CodegenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 现有配置...

        // 检查是否启用 KSP
        val extension = project.extensions.create("codegen", CodegenExtension::class.java)

        project.afterEvaluate {
            if (extension.annotation.parsingMode.get() == "ksp") {
                // 自动应用 KSP 插件
                project.plugins.apply("com.google.devtools.ksp")

                // 添加 KSP Processor 依赖
                project.dependencies.add(
                    "ksp",
                    "com.only:codegen-ksp-processor:${VERSION}"
                )

                logger.lifecycle("KSP annotation processing enabled")
            }
        }
    }
}
```

## 6. KSP 元数据示例

### 6.1 aggregates.json

```json
[
  {
    "aggregateName": "User",
    "className": "User",
    "qualifiedName": "com.example.domain.aggregates.user.User",
    "packageName": "com.example.domain.aggregates.user",
    "isAggregateRoot": true,
    "isEntity": true,
    "isValueObject": false,
    "identityType": "Long",
    "fields": [
      {
        "name": "id",
        "type": "kotlin.Long",
        "isId": true,
        "isNullable": false,
        "annotations": ["Id", "GeneratedValue", "Column"]
      },
      {
        "name": "name",
        "type": "kotlin.String",
        "isId": false,
        "isNullable": true,
        "annotations": ["Column"]
      },
      {
        "name": "email",
        "type": "kotlin.String",
        "isId": false,
        "isNullable": true,
        "annotations": ["Column"]
      }
    ]
  }
]
```

## 7. 测试 KSP Processor

### 7.1 测试依赖

```kotlin
// ksp-processor/build.gradle.kts
dependencies {
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing-ksp:1.5.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("com.google.truth:truth:1.1.5")
}
```

### 7.2 测试示例

```kotlin
package com.only.codegen.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.junit.jupiter.api.Test
import java.io.File
import com.google.common.truth.Truth.assertThat

class AnnotationProcessorTest {

    @Test
    fun `should process Aggregate annotation`() {
        val kotlinSource = SourceFile.kotlin(
            "User.kt", """
            package com.example.domain

            import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
            import jakarta.persistence.Entity
            import jakarta.persistence.Id

            @Aggregate(aggregate = "User", root = true)
            @Entity
            class User(
                @Id
                var id: Long = 0L,
                var name: String? = null
            )
        """
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(kotlinSource)
            symbolProcessorProviders = listOf(AnnotationProcessorProvider())
            inheritClassPath = true
        }

        val result = compilation.compile()

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

        // 验证生成的 JSON 文件
        val metadataDir = File(compilation.kspSourcesDir, "metadata")
        val aggregatesFile = File(metadataDir, "aggregates.json")

        assertThat(aggregatesFile.exists()).isTrue()

        val content = aggregatesFile.readText()
        assertThat(content).contains("\"aggregateName\": \"User\"")
        assertThat(content).contains("\"isAggregateRoot\": true")
    }

    @Test
    fun `should resolve identity type correctly`() {
        val kotlinSource = SourceFile.kotlin(
            "Order.kt", """
            package com.example.domain

            import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
            import jakarta.persistence.Entity
            import jakarta.persistence.Id
            import java.util.UUID

            @Aggregate(aggregate = "Order", root = true)
            @Entity
            class Order(
                @Id
                var id: UUID = UUID.randomUUID()
            )
        """
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(kotlinSource)
            symbolProcessorProviders = listOf(AnnotationProcessorProvider())
            inheritClassPath = true
        }

        val result = compilation.compile()
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

        val aggregatesFile = File(
            File(compilation.kspSourcesDir, "metadata"),
            "aggregates.json"
        )
        val content = aggregatesFile.readText()

        assertThat(content).contains("\"identityType\": \"java.util.UUID\"")
    }
}
```

## 8. 优势总结

### 8.1 KSP 的优势

| 优势 | 说明 |
|------|------|
| **类型安全** | 完整的类型信息，避免字符串解析错误 |
| **准确性** | 编译器级别的准确性，支持复杂注解 |
| **性能** | 增量编译，只处理变更的文件 |
| **维护性** | 代码清晰，易于理解和维护 |
| **扩展性** | 轻松支持新注解和复杂场景 |
| **IDE 集成** | 实时错误检查，开发体验好 |

### 8.2 与正则表达式的互补

| 场景 | 推荐方案 |
|------|---------|
| **生产环境** | KSP（准确、可靠） |
| **简单项目** | 正则表达式（零依赖） |
| **复杂注解** | KSP（完全支持） |
| **快速原型** | 正则表达式（快速启动） |
| **KSP 不可用** | 正则表达式（后备方案） |

## 9. 迁移路径

### 9.1 Phase 1: KSP Processor 开发（2周）

- [ ] 创建 ksp-processor 模块
- [ ] 实现 AnnotationProcessor
- [ ] 实现元数据模型
- [ ] 编写单元测试
- [ ] 发布到 Maven

### 9.2 Phase 2: Plugin 集成（1周）

- [ ] 更新 GenAnnotationTask
- [ ] 实现 KSP 元数据读取
- [ ] 实现降级机制（KSP → Regex）
- [ ] 添加配置选项

### 9.3 Phase 3: 文档和测试（1周）

- [ ] 编写用户文档
- [ ] 编写集成测试
- [ ] 性能测试
- [ ] 示例项目

### 9.4 Phase 4: 优化和推广（持续）

- [ ] 性能优化
- [ ] 增量编译优化
- [ ] 社区反馈收集
- [ ] 持续改进

## 10. 常见问题

### Q1: KSP 会增加编译时间吗？

**A**: 初次编译会略微增加（~5-10%），但由于增量编译支持，后续编译会更快。

### Q2: KSP 与 KAPT 的区别？

**A**: KSP 专为 Kotlin 设计，比 KAPT 快 2 倍以上，且不需要生成 Java Stub。

### Q3: 如果 KSP 失败怎么办？

**A**: 自动降级到正则表达式模式，确保代码生成不中断。

### Q4: 需要修改现有代码吗？

**A**: 不需要，KSP 只是更好的解析方式，生成的代码和 API 保持不变。

### Q5: KSP 支持哪些 Kotlin 版本？

**A**: KSP 1.9.20-1.0.14 支持 Kotlin 1.9.20+，向后兼容。

## 11. 总结

**推荐策略：KSP 优先，正则表达式后备**

```kotlin
codegen {
    annotation {
        parsingMode.set("ksp")  // 默认使用 KSP

        // 如果 KSP 不可用，自动降级到 regex
        fallbackToRegex.set(true)
    }
}
```

**优势组合：**
- ✅ **生产环境**：KSP（准确、类型安全、性能优越）
- ✅ **开发环境**：KSP（实时错误检查、IDE 集成）
- ✅ **后备方案**：正则表达式（零依赖、快速启动）

这种混合模式既保证了生产环境的质量，又提供了灵活的降级选项。
