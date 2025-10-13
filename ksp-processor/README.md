# KSP Processor - 注解元数据提取器

## 概述

这是一个基于 **KSP (Kotlin Symbol Processing)** 实现的注解处理器，用于在编译时扫描 Domain 层的注解并生成元数据 JSON 文件。

## KSP 简介

### 什么是 KSP？

**KSP (Kotlin Symbol Processing)** 是 Google 为 Kotlin 开发的轻量级编译器插件 API，用于**编译时代码分析和生成**。

**核心特性：**
- 比 KAPT 快 2 倍
- 原生支持 Kotlin 特性（可空性、扩展函数、数据类等）
- 更轻量的 API 设计
- 增量编译支持

### KSP vs KAPT

| 特性 | KSP | KAPT |
|------|-----|------|
| 性能 | 快 2 倍 | 慢（需要生成 Java stubs） |
| Kotlin 支持 | 原生支持所有 Kotlin 特性 | 通过 Java 兼容层 |
| API | 专为 Kotlin 设计 | Java 的 JSR 269 |
| 类型系统 | 理解 Kotlin 类型系统 | 只理解 Java 类型 |
| 学习曲线 | 简单直观 | 需要理解 Java APT |

## 架构设计

### 核心组件

```
┌─────────────────────────────────────────────────────────────┐
│                    KSP 框架层                                │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Java SPI 机制: 发现并加载 Provider                    │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│         AnnotationProcessorProvider                         │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  SymbolProcessorProvider 实现                          │  │
│  │  - create(): 创建 AnnotationProcessor 实例             │  │
│  │  - 注入 CodeGenerator, Logger, Options                 │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│            AnnotationProcessor                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  SymbolProcessor 实现                                   │  │
│  │                                                         │  │
│  │  ① process(resolver): 多轮处理                         │  │
│  │     - processAggregateAnnotations()                    │  │
│  │     - processEntityAnnotations()                       │  │
│  │                                                         │  │
│  │  ② finish(): 最终生成                                  │  │
│  │     - generateMetadata()                               │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    元数据模型                                │
│  ┌─────────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │ AggregateMetadata│  │EntityMetadata│  │FieldMetadata │   │
│  └─────────────────┘  └──────────────┘  └──────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   生成产物                                   │
│  build/generated/ksp/main/resources/metadata/               │
│    ├── aggregates.json                                      │
│    └── entities.json                                        │
└─────────────────────────────────────────────────────────────┘
```

## KSP 处理流程

### 生命周期

```
编译开始
  ↓
① Provider 创建 Processor
   [AnnotationProcessorProvider.create()]
  ↓
② 第 1 轮：process(resolver)
   - 扫描 @Aggregate 注解
   - 扫描 @Entity 注解
   - 收集元数据到内存
  ↓
③ 第 2 轮：process(resolver)
   - 如果生成了新文件，继续处理
   - 累积更多元数据
  ↓
④ 第 N 轮：process(resolver)
   - 直到没有新符号需要处理
  ↓
⑤ finish()
   - 所有处理完成后调用（只调用一次）
   - 生成 JSON 文件
  ↓
编译结束
```

### 多轮处理机制

**为什么需要多轮处理？**

```kotlin
// 第 1 轮：扫描到这个类
@Aggregate(aggregate = "User", root = true)
class User { ... }

// 第 1 轮：生成这个类
class UserFactory { ... }

// 第 2 轮：如果 UserFactory 也有注解，需要再次处理
@Generated
class UserFactory { ... }
```

**实现方式：**

```kotlin
override fun process(resolver: Resolver): List<KSAnnotated> {
    processAggregateAnnotations(resolver)
    processEntityAnnotations(resolver)
    return emptyList()  // 返回空表示没有延迟处理的符号
}
```

- 返回 `emptyList()`: 表示所有符号都已处理
- 返回 `List<KSAnnotated>`: 表示这些符号推迟到下一轮处理

## 核心 API 详解

### 1. SymbolProcessorProvider

**作用：** 处理器的工厂接口

```kotlin
class AnnotationProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return AnnotationProcessor(
            environment.codeGenerator,  // 文件生成器
            environment.logger,         // 日志工具
            environment.options         // 编译参数
        )
    }
}
```

**注册机制：** Java SPI

文件路径：`src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`

内容：
```
com.only.codegen.ksp.AnnotationProcessorProvider
```

### 2. SymbolProcessor

**作用：** 实际的处理器接口

```kotlin
interface SymbolProcessor {
    // 每轮处理调用（可能多次）
    fun process(resolver: Resolver): List<KSAnnotated>

    // 所有处理完成后调用（只调用一次）
    fun finish() {}

    // 处理出错时调用
    fun onError() {}
}
```

### 3. Resolver

**作用：** 符号查找和解析

**常用方法：**

```kotlin
// 查找带有指定注解的所有符号
resolver.getSymbolsWithAnnotation("com.example.MyAnnotation")

// 获取所有源文件
resolver.getAllFiles()

// 获取本轮新生成的文件
resolver.getNewFiles()

// 通过全限定名查找类
resolver.getClassDeclarationByName("com.example.MyClass")
```

> 📚 **详细 API 文档**: 查看 [KSP_RESOLVER_API.md](./KSP_RESOLVER_API.md) 获取完整的 Resolver API 参考，包含所有方法的详细说明、使用示例和最佳实践。

### 4. KSP 符号类型

| 符号类型 | 表示 | 常用属性/方法 |
|---------|------|--------------|
| `KSClassDeclaration` | 类声明 | `simpleName`, `qualifiedName`, `getAllProperties()` |
| `KSPropertyDeclaration` | 属性/字段 | `simpleName`, `type`, `annotations` |
| `KSAnnotation` | 注解 | `shortName`, `arguments`, `annotationType` |
| `KSType` | 类型信息 | `declaration`, `isMarkedNullable`, `arguments` |
| `KSFunctionDeclaration` | 函数声明 | `simpleName`, `parameters`, `returnType` |

> 📚 **详细符号类型文档**: 查看 [KSP_SYMBOL_TYPES.md](./KSP_SYMBOL_TYPES.md) 获取完整的符号类型参考，包含类声明、属性、函数、注解、类型等9种符号的详细说明、继承关系、使用示例和实用工具函数。

### 5. CodeGenerator

**作用：** 生成文件

```kotlin
val file = codeGenerator.createNewFile(
    dependencies = Dependencies(aggregating = false),  // 依赖追踪
    packageName = "metadata",                          // 包名
    fileName = "aggregates",                           // 文件名
    extensionName = "json"                             // 扩展名
)
file.write(jsonContent.toByteArray())
file.close()
```

**生成路径：**
- Kotlin 代码: `build/generated/ksp/main/kotlin/`
- Java 代码: `build/generated/ksp/main/java/`
- 资源文件: `build/generated/ksp/main/resources/`

**Dependencies 参数：**
- `Dependencies(false)`: 独立文件，不依赖特定源文件
- `Dependencies(true, file1, file2)`: 聚合文件，依赖多个源文件（增量编译时只要其中一个改变就重新生成）

> 📚 **详细 API 文档**: 查看 [KSP_CODEGENERATOR_API.md](./KSP_CODEGENERATOR_API.md) 获取完整的 CodeGenerator API 参考，包含文件生成、依赖管理、增量编译等详细说明和最佳实践。

## 实现细节

### 注解扫描

#### 处理 @Aggregate 注解

```kotlin
private fun processAggregateAnnotations(resolver: Resolver) {
    // 1. 查找所有带 @Aggregate 注解的符号
    val aggregateSymbols = resolver.getSymbolsWithAnnotation(
        "com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate"
    )

    // 2. 过滤出类声明
    aggregateSymbols.filterIsInstance<KSClassDeclaration>().forEach { classDecl ->
        // 3. 获取注解实例
        val annotation = classDecl.annotations.first {
            it.shortName.asString() == "Aggregate"
        }

        // 4. 读取注解参数
        val aggregateName = annotation.arguments
            .find { it.name?.asString() == "aggregate" }
            ?.value as? String
            ?: classDecl.simpleName.asString()

        val isRoot = annotation.arguments
            .find { it.name?.asString() == "root" }
            ?.value as? Boolean
            ?: false

        // 5. 解析标识类型
        val identityType = resolveIdentityType(classDecl)

        // 6. 提取字段信息
        val fields = extractFields(classDecl)

        // 7. 构建元数据
        val metadata = AggregateMetadata(
            aggregateName = aggregateName,
            className = classDecl.simpleName.asString(),
            qualifiedName = classDecl.qualifiedName?.asString() ?: "",
            packageName = classDecl.packageName.asString(),
            isAggregateRoot = isRoot,
            identityType = identityType,
            fields = fields
        )

        // 8. 添加到集合
        aggregates.add(metadata)

        logger.info("Processed aggregate: $aggregateName (root=$isRoot)")
    }
}
```

#### 字段提取

```kotlin
private fun extractFields(classDecl: KSClassDeclaration): List<FieldMetadata> {
    return classDecl.getAllProperties().map { property ->
        // 检查是否是 ID 字段
        val isId = property.annotations.any {
            it.shortName.asString() == "Id"
        }

        // 解析字段类型
        val type = property.type.resolve()
        val typeName = type.declaration.qualifiedName?.asString()
            ?: type.toString()

        // 提取所有注解名称
        val annotations = property.annotations.map {
            it.shortName.asString()
        }.toList()

        FieldMetadata(
            name = property.simpleName.asString(),
            type = typeName,
            isId = isId,
            isNullable = type.isMarkedNullable,
            annotations = annotations
        )
    }.toList()
}
```

#### ID 类型解析

```kotlin
private fun resolveIdentityType(classDecl: KSClassDeclaration): String {
    // 查找所有带 @Id 注解的字段
    val idFields = classDecl.getAllProperties()
        .filter { property ->
            property.annotations.any {
                it.shortName.asString() == "Id"
            }
        }
        .toList()

    return when {
        // 没有 ID 字段 → 默认 Long
        idFields.isEmpty() -> "Long"

        // 单一 ID 字段 → 使用字段类型
        idFields.size == 1 -> {
            val type = idFields.first().type.resolve()
            type.declaration.qualifiedName?.asString() ?: "Long"
        }

        // 复合主键 → 使用内部类名称
        else -> "${classDecl.simpleName.asString()}.PK"
    }
}
```

### 元数据生成

```kotlin
override fun finish() {
    // 防止重复生成（虽然 finish() 只调用一次，但加个保险）
    if (!metadataGenerated) {
        generateMetadata()
        metadataGenerated = true
    }
}

private fun generateMetadata() {
    val gson = GsonBuilder().setPrettyPrinting().create()

    // 生成 aggregates.json
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

    // 生成 entities.json
    if (entities.isNotEmpty()) {
        val entitiesFile = codeGenerator.createNewFile(
            Dependencies(false),
            "metadata",
            "entities",
            "json"
        )
        entitiesFile.write(gson.toJson(entities).toByteArray())
        entitiesFile.close()
    }

    logger.info("Generated metadata: ${aggregates.size} aggregates, ${entities.size} entities")
}
```

## 关键设计决策

### 为什么在 finish() 中生成文件？

**问题：** 最初在 `process()` 中生成文件，导致 `FileAlreadyExistsException`

**原因：** KSP 的多轮处理机制
```
第 1 轮 process() → createNewFile("aggregates.json") ✓
第 2 轮 process() → createNewFile("aggregates.json") ✗ (文件已存在)
```

**解决方案：** 将文件生成移到 `finish()`
```
第 1 轮 process() → 收集元数据
第 2 轮 process() → 收集元数据
finish()          → createNewFile("aggregates.json") ✓ (只调用一次)
```

### 为什么使用 Dependencies(false)？

```kotlin
codeGenerator.createNewFile(
    Dependencies(false),  // ← 这里
    "metadata",
    "aggregates",
    "json"
)
```

**含义：**
- `false`: 生成的文件不依赖特定的源文件
- 这是一个**聚合文件**，汇总了所有带注解的类的信息

**如果用 Dependencies(true, file1, file2)**：
- 增量编译时，只要 file1 或 file2 改变，就会重新生成
- 适用于一对一的代码生成场景

### 为什么累积元数据而不是直接写文件？

```kotlin
private val aggregates = mutableListOf<AggregateMetadata>()  // 累积

override fun process(resolver: Resolver): List<KSAnnotated> {
    processAggregateAnnotations(resolver)  // 每轮累积
    // 不在这里写文件！
    return emptyList()
}

override fun finish() {
    generateMetadata()  // 一次性写入所有数据
}
```

**原因：**
1. **多轮处理**：每轮可能发现新的注解
2. **完整性**：确保收集到所有元数据后再生成文件
3. **性能**：避免多次 I/O 操作

## 元数据模型

### AggregateMetadata

```kotlin
data class AggregateMetadata(
    val aggregateName: String,        // 聚合名称
    val className: String,            // 类名
    val qualifiedName: String,        // 全限定名
    val packageName: String,          // 包名
    val isAggregateRoot: Boolean,     // 是否是聚合根
    val isEntity: Boolean,            // 是否是实体
    val isValueObject: Boolean,       // 是否是值对象
    val identityType: String,         // 标识类型（ID 类型）
    val fields: List<FieldMetadata>   // 字段列表
)
```

### EntityMetadata

```kotlin
data class EntityMetadata(
    val className: String,            // 类名
    val qualifiedName: String,        // 全限定名
    val packageName: String,          // 包名
    val fields: List<FieldMetadata>   // 字段列表
)
```

### FieldMetadata

```kotlin
data class FieldMetadata(
    val name: String,                 // 字段名
    val type: String,                 // 字段类型
    val isId: Boolean,                // 是否是 ID
    val isNullable: Boolean,          // 是否可空
    val annotations: List<String>     // 注解列表
)
```

## 生成的 JSON 示例

### aggregates.json

```json
[
  {
    "aggregateName": "User",
    "className": "User",
    "qualifiedName": "com.example.domain.User",
    "packageName": "com.example.domain",
    "isAggregateRoot": true,
    "isEntity": true,
    "isValueObject": false,
    "identityType": "kotlin.Long",
    "fields": [
      {
        "name": "id",
        "type": "kotlin.Long",
        "isId": true,
        "isNullable": true,
        "annotations": ["Id", "GeneratedValue"]
      },
      {
        "name": "name",
        "type": "kotlin.String",
        "isId": false,
        "isNullable": true,
        "annotations": ["Column"]
      }
    ]
  }
]
```

### entities.json

```json
[
  {
    "className": "Address",
    "qualifiedName": "com.example.domain.Address",
    "packageName": "com.example.domain",
    "fields": [
      {
        "name": "street",
        "type": "kotlin.String",
        "isId": false,
        "isNullable": false,
        "annotations": []
      }
    ]
  }
]
```

## 使用方式

### 1. 在项目中应用 KSP

```kotlin
// build.gradle.kts
plugins {
    id("com.google.devtools.ksp") version "1.9.20-1.0.14"
}

dependencies {
    ksp(project(":codegen-plugin:ksp-processor"))
}
```

### 2. 编写带注解的代码

```kotlin
@Aggregate(aggregate = "User", root = true, type = Aggregate.TYPE_ENTITY)
class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false)
    var name: String? = null
}
```

### 3. 编译项目

```bash
./gradlew build
```

### 4. 查看生成的元数据

文件位置：
```
build/generated/ksp/main/resources/metadata/
├── aggregates.json
└── entities.json
```

## 调试技巧

### 1. 启用 KSP 日志

```kotlin
// build.gradle.kts
ksp {
    arg("option1", "value1")
    arg("verbose", "true")
}
```

### 2. 在处理器中输出日志

```kotlin
logger.info("Processed aggregate: $aggregateName")
logger.warn("Missing @Id annotation on $className")
logger.error("Invalid annotation parameter")
```

### 3. 查看生成的文件

```bash
# Windows
dir build\generated\ksp\main\resources\metadata

# Linux/Mac
ls -la build/generated/ksp/main/resources/metadata
```

### 4. 清理生成文件

```bash
./gradlew clean
```

## 常见问题

### Q1: FileAlreadyExistsException

**问题：** `kotlin.io.FileAlreadyExistsException: xxx.json`

**原因：** 在 `process()` 中生成文件，多轮处理导致重复创建

**解决：** 在 `finish()` 中生成文件

```kotlin
override fun finish() {
    if (!metadataGenerated) {
        generateMetadata()
        metadataGenerated = true
    }
}
```

### Q2: 找不到生成的文件

**问题：** 编译后找不到 JSON 文件

**检查：**
1. 确认 `aggregates.isNotEmpty()`（有数据才生成文件）
2. 检查生成路径：`build/generated/ksp/main/resources/metadata/`
3. 查看编译日志是否有错误

### Q3: 注解没有被扫描到

**问题：** `getSymbolsWithAnnotation()` 返回空

**检查：**
1. 确认注解的全限定名正确
2. 确认源代码中确实有该注解
3. 确认依赖配置正确（KSP 需要能访问注解类）

### Q4: 类型解析失败

**问题：** `type.declaration.qualifiedName` 返回 null

**解决：**
```kotlin
val typeName = type.declaration.qualifiedName?.asString()
    ?: type.toString()  // 降级方案
```

## 性能优化

### 1. 避免重复扫描

```kotlin
private val processedClasses = mutableSetOf<String>()

aggregateSymbols.forEach { classDecl ->
    val qualifiedName = classDecl.qualifiedName?.asString() ?: return@forEach
    if (qualifiedName in processedClasses) return@forEach
    processedClasses.add(qualifiedName)
    // 处理逻辑
}
```

### 2. 延迟解析

```kotlin
// 不要立即解析所有类型
val type = property.type  // 只获取引用，不解析

// 只在需要时解析
if (needTypeInfo) {
    val resolved = type.resolve()
}
```

### 3. 批量写入

```kotlin
// 不好：多次写入
aggregates.forEach {
    writeToFile(it)
}

// 好：一次性写入
val allData = gson.toJson(aggregates)
file.write(allData.toByteArray())
```

## 参考资源

- [KSP 官方文档](https://kotlinlang.org/docs/ksp-overview.html)
- [KSP GitHub](https://github.com/google/ksp)
- [KSP 示例项目](https://github.com/google/ksp/tree/main/examples)

## 许可

与主项目相同
