# 3. 核心接口和类设计

[← 上一章：核心架构设计](02-architecture.md) | [返回目录](README.md) | [下一章：KSP Processor 实现 →](04-ksp-processor.md)

---

由于本章内容较长，已拆分为以下子章节：

- [3.1 BaseContext 层调整](#31-basecontext-层调整)
- [3.2 AnnotationContext 层](#32-annotationcontext-层)
- [3.3 Builder 层](#33-builder-层)
- [3.4 Generator 层](#34-generator-层)
- [3.5 Task 层](#35-task-层)

---

## 3.1 BaseContext 层调整

### 3.1.1 BaseContext 接口扩展

```kotlin
package com.only.codegen.context

interface BaseContext {
    // === 现有属性 ===
    val baseMap: Map<String, Any?>
    val templateNodeMap: Map<String, List<TemplateNode>>
    val typeMapping: MutableMap<String, String>

    // === 模块路径信息（AbstractCodegenTask 中已有实现） ===
    val adapterPath: String       // adapter 模块的绝对路径
    val applicationPath: String   // application 模块的绝对路径
    val domainPath: String        // domain 模块的绝对路径

    // === 包路径信息（从 EntityContext 下放，GenEntityTask 已有实现） ===
    val aggregatesPath: String    // 聚合根包的绝对路径
    val schemaPath: String        // Schema 包的绝对路径
    val subscriberPath: String    // 订阅者包的绝对路径

    // === 包名信息（从 EntityContext 下放） ===
    val aggregatesPackage: String
    val schemaPackage: String
    val subscriberPackage: String

    // === 辅助方法 ===
    fun getString(key: String, default: String? = null): String?
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun getInt(key: String, default: Int = 0): Int
}
```

**说明**：
- `adapterPath`, `applicationPath`, `domainPath` 已在 `AbstractCodegenTask` 中实现，无需修改
- `aggregatesPath`, `schemaPath`, `subscriberPath` 等需要从 `EntityContext` 接口移动到 `BaseContext` 接口
- 实现逻辑保持在 `GenEntityTask` 中不变

### 3.1.2 AbstractCodegenTask 中的现有实现

```kotlin
// AbstractCodegenTask.kt（现有代码，无需修改）
abstract class AbstractCodegenTask : DefaultTask(), BaseContext {

    // === 现有实现（通过 extension 获取） ===
    @get:Internal
    override val adapterPath: String by lazy { extension.get().adapterPath }

    @get:Internal
    override val applicationPath: String by lazy { extension.get().applicationPath }

    @get:Internal
    override val domainPath: String by lazy { extension.get().domainPath }

    // === 模块路径计算逻辑（现有代码） ===
    private val CodegenExtension.adapterPath: String
        get() = modulePath(moduleNameSuffix4Adapter.get())

    private val CodegenExtension.applicationPath: String
        get() = modulePath(moduleNameSuffix4Application.get())

    private val CodegenExtension.domainPath: String
        get() = modulePath(moduleNameSuffix4Domain.get())

    private fun CodegenExtension.modulePath(suffix: String): String =
        if (multiModule.get()) {
            "${project.projectDir.absolutePath}${File.separator}${project.name}$suffix"
        } else {
            project.projectDir.absolutePath
        }
}
```

### 3.1.3 GenEntityTask 中的路径实现

```kotlin
// GenEntityTask.kt（现有实现，接口声明需要调整到 BaseContext）
open class GenEntityTask : GenArchTask(), MutableEntityContext {

    @get:Internal
    override var aggregatesPath: String = ""
        get() = field.takeIf { it.isNotBlank() } ?: resolvePackageDirectory(
            domainPath,
            "${getString("basePackage")}.$AGGREGATE_PACKAGE"
        ).also { field = it }

    @get:Internal
    override var schemaPath: String = ""
        get() = field.takeIf { it.isNotBlank() } ?: resolvePackageDirectory(
            domainPath,
            "${getString("basePackage")}.${getString("entitySchemaOutputPackage").takeIf { it.isNotBlank() } ?: "domain._share.meta"}"
        ).also { field = it }

    @get:Internal
    override var subscriberPath: String = ""
        get() = field.takeIf { it.isNotBlank() } ?: resolvePackageDirectory(
            domainPath,
            "${getString("basePackage")}.$DOMAIN_EVENT_SUBSCRIBER_PACKAGE"
        ).also { field = it }

    @get:Internal
    override val aggregatesPackage: String by lazy {
        resolvePackage("${aggregatesPath}${File.separator}X.kt")
            .substring(getString("basePackage").length + 1)
    }

    @get:Internal
    override val schemaPackage: String by lazy {
        resolvePackage("${schemaPath}${File.separator}X.kt")
            .substring(getString("basePackage").length + 1)
    }

    @get:Internal
    override val subscriberPackage: String by lazy {
        resolvePackage("${subscriberPath}${File.separator}X.kt")
            .substring(getString("basePackage").length + 1)
    }
}
```

---

## 3.2 AnnotationContext 层

### 3.2.1 AnnotationContext（只读接口）

```kotlin
package com.only.codegen.context

/**
 * 基于注解的代码生成上下文（只读）
 */
interface AnnotationContext : BaseContext {
    /**
     * 类信息映射
     * key: 类的全限定名（FQN）
     * value: ClassInfo（包含包名、类名、注解、字段等信息）
     */
    val classMap: Map<String, ClassInfo>

    /**
     * 注解信息映射
     * key: 注解的简单名称（如 "Aggregate", "Entity"）
     * value: 包含该注解的所有类的信息列表
     */
    val annotationMap: Map<String, List<AnnotationInfo>>

    /**
     * 聚合信息映射
     * key: 聚合名称（如 "User", "Order"）
     * value: AggregateInfo（包含聚合根、实体、值对象等）
     */
    val aggregateMap: Map<String, AggregateInfo>

    /**
     * 类型映射（继承自 BaseContext.typeMapping）
     * key: 类型简单名（如 "User", "UserRepository"）
     * value: 类型全限定名
     */
    // 继承自 BaseContext.typeMapping

    /**
     * 源代码根目录（用于 KSP 扫描）
     */
    val sourceRoots: List<String>

    /**
     * 扫描的包路径（可选过滤）
     */
    val scanPackages: List<String>
}

/**
 * 可变的注解上下文（用于构建阶段）
 */
interface MutableAnnotationContext : AnnotationContext {
    override val classMap: MutableMap<String, ClassInfo>
    override val annotationMap: MutableMap<String, MutableList<AnnotationInfo>>
    override val aggregateMap: MutableMap<String, AggregateInfo>
}
```

### 3.2.2 数据模型

```kotlin
package com.only.codegen.context.model

/**
 * 类信息（从 KSP 元数据构建）
 */
data class ClassInfo(
    val packageName: String,
    val simpleName: String,
    val fullName: String,
    val filePath: String,
    val annotations: List<AnnotationInfo>,
    val fields: List<FieldInfo>,
    val superClass: String?,
    val interfaces: List<String>,
    val isAggregateRoot: Boolean = false,
    val isEntity: Boolean = false,
    val isValueObject: Boolean = false
)

/**
 * 注解信息
 */
data class AnnotationInfo(
    val name: String,                        // 注解名称（如 "Aggregate"）
    val fullName: String,                    // 完整注解名
    val attributes: Map<String, Any?>,       // 注解属性
    val targetClass: String                  // 注解所在的类
)

/**
 * 字段信息
 */
data class FieldInfo(
    val name: String,
    val type: String,
    val annotations: List<AnnotationInfo>,
    val isId: Boolean = false,
    val isNullable: Boolean = false,
    val defaultValue: String? = null
)

/**
 * 聚合信息
 */
data class AggregateInfo(
    val name: String,                        // 聚合名称
    val aggregateRoot: ClassInfo,            // 聚合根实体
    val entities: List<ClassInfo>,           // 聚合内的实体
    val valueObjects: List<ClassInfo>,       // 聚合内的值对象
    val identityType: String,                // 聚合根的 ID 类型
    val modulePath: String                   // 所属模块路径
)
```

---

## 3.3 Builder 层

### 3.3.1 AnnotationContextBuilder 接口

```kotlin
package com.only.codegen.context.builders.annotation

import com.only.codegen.context.MutableAnnotationContext

/**
 * 注解上下文构建器（独立接口，不依赖 EntityContext）
 */
interface AnnotationContextBuilder {
    /**
     * 构建顺序（数字越小越先执行）
     */
    val order: Int

    /**
     * 构建上下文信息
     */
    fun build(context: MutableAnnotationContext)
}
```

**关键点**：
- ✅ 独立接口，不依赖 `MutableEntityContext`
- ✅ 参数类型为 `MutableAnnotationContext`
- ✅ 与现有 `ContextBuilder` 完全解耦

### 3.3.2 KspMetadataContextBuilder

```kotlin
package com.only.codegen.context.builders.annotation

import com.google.gson.Gson
import com.only.codegen.ksp.models.*
import java.io.File

/**
 * KSP 元数据上下文构建器
 * 负责读取 KSP 生成的 JSON 元数据并填充 AnnotationContext
 */
class KspMetadataContextBuilder : AnnotationContextBuilder {
    override val order = 10

    override fun build(context: MutableAnnotationContext) {
        val metadataDir = resolveMetadataDir(context)
        if (!metadataDir.exists()) {
            logger.error("KSP metadata directory not found: $metadataDir")
            return
        }

        loadAggregatesMetadata(metadataDir, context)
        loadEntitiesMetadata(metadataDir, context)

        logger.lifecycle("Loaded ${context.classMap.size} classes from KSP metadata")
    }

    private fun resolveMetadataDir(context: MutableAnnotationContext): File {
        val buildDir = context.baseMap["buildDir"] as? File
            ?: error("buildDir not found in context")
        return File(buildDir, "generated/ksp/main/metadata")
    }

    private fun loadAggregatesMetadata(
        metadataDir: File,
        context: MutableAnnotationContext
    ) {
        val aggregatesFile = File(metadataDir, "aggregates.json")
        if (!aggregatesFile.exists()) return

        val gson = Gson()
        val aggregates = gson.fromJson(
            aggregatesFile.readText(),
            Array<AggregateMetadata>::class.java
        )

        aggregates.forEach { metadata ->
            val classInfo = ClassInfo(
                packageName = metadata.packageName,
                simpleName = metadata.className,
                fullName = metadata.qualifiedName,
                filePath = "",
                annotations = listOf(),
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

            context.classMap[metadata.qualifiedName] = classInfo

            // 填充 annotationMap
            if (metadata.isAggregateRoot) {
                val annotationInfo = AnnotationInfo(
                    name = "Aggregate",
                    fullName = "com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate",
                    attributes = mapOf(
                        "aggregate" to metadata.aggregateName,
                        "root" to true
                    ),
                    targetClass = metadata.qualifiedName
                )
                context.annotationMap.computeIfAbsent("Aggregate") { mutableListOf() }
                    .add(annotationInfo)
            }
        }
    }

    private fun loadEntitiesMetadata(
        metadataDir: File,
        context: MutableAnnotationContext
    ) {
        val entitiesFile = File(metadataDir, "entities.json")
        if (!entitiesFile.exists()) return

        val gson = Gson()
        val entities = gson.fromJson(
            entitiesFile.readText(),
            Array<EntityMetadata>::class.java
        )

        entities.forEach { metadata ->
            if (!context.classMap.containsKey(metadata.qualifiedName)) {
                val classInfo = ClassInfo(
                    packageName = metadata.packageName,
                    simpleName = metadata.className,
                    fullName = metadata.qualifiedName,
                    filePath = "",
                    annotations = listOf(),
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
                    isAggregateRoot = false,
                    isEntity = true,
                    isValueObject = false
                )
                context.classMap[metadata.qualifiedName] = classInfo
            }
        }
    }
}
```

### 3.3.3 AggregateInfoBuilder

```kotlin
package com.only.codegen.context.builders.annotation

/**
 * 聚合信息构建器
 * 负责识别和组织聚合结构
 */
class AggregateInfoBuilder : AnnotationContextBuilder {
    override val order = 20

    override fun build(context: MutableAnnotationContext) {
        val aggregateRoots = context.classMap.values.filter { it.isAggregateRoot }

        aggregateRoots.forEach { root ->
            val aggregateName = extractAggregateName(root)
            val entities = findAggregateEntities(aggregateName, context)
            val valueObjects = findAggregateValueObjects(aggregateName, context)
            val identityType = resolveIdentityType(root)

            val aggregateInfo = AggregateInfo(
                name = aggregateName,
                aggregateRoot = root,
                entities = entities,
                valueObjects = valueObjects,
                identityType = identityType,
                modulePath = context.domainPath
            )

            context.aggregateMap[aggregateName] = aggregateInfo
        }

        logger.lifecycle("Built ${context.aggregateMap.size} aggregate infos")
    }

    private fun extractAggregateName(root: ClassInfo): String {
        return root.annotations
            .find { it.name == "Aggregate" }
            ?.attributes?.get("aggregate") as? String
            ?: root.simpleName
    }

    private fun findAggregateEntities(
        aggregateName: String,
        context: MutableAnnotationContext
    ): List<ClassInfo> {
        return context.classMap.values.filter { classInfo ->
            classInfo.isEntity &&
            classInfo.annotations.any {
                it.name == "Aggregate" &&
                it.attributes["aggregate"] == aggregateName &&
                it.attributes["root"] != true
            }
        }
    }

    private fun findAggregateValueObjects(
        aggregateName: String,
        context: MutableAnnotationContext
    ): List<ClassInfo> {
        return context.classMap.values.filter { classInfo ->
            classInfo.isValueObject &&
            classInfo.annotations.any {
                it.name == "Aggregate" &&
                it.attributes["aggregate"] == aggregateName
            }
        }
    }

    private fun resolveIdentityType(root: ClassInfo): String {
        val idFields = root.fields.filter { it.isId }
        return when {
            idFields.isEmpty() -> "Long"
            idFields.size == 1 -> idFields.first().type
            else -> "${root.simpleName}.PK"
        }
    }
}
```

### 3.3.4 IdentityTypeBuilder

```kotlin
package com.only.codegen.context.builders.annotation

/**
 * ID 类型构建器
 * 负责解析和推断 ID 类型，填充 typeMapping
 */
class IdentityTypeBuilder : AnnotationContextBuilder {
    override val order = 30

    override fun build(context: MutableAnnotationContext) {
        context.classMap.values.forEach { classInfo ->
            val identityType = resolveIdentityType(classInfo)
            context.typeMapping["${classInfo.simpleName}Id"] = identityType
        }

        logger.lifecycle("Built identity type mappings for ${context.classMap.size} classes")
    }

    private fun resolveIdentityType(classInfo: ClassInfo): String {
        val idFields = classInfo.fields.filter { it.isId }
        return when {
            idFields.isEmpty() -> "Long"
            idFields.size == 1 -> idFields.first().type
            else -> "${classInfo.simpleName}.PK"
        }
    }
}
```

---

## 3.4 Generator 层

### 3.4.1 AnnotationTemplateGenerator 接口

```kotlin
package com.only.codegen.generators.annotation

import com.only.codegen.context.AnnotationContext
import com.only.codegen.context.model.ClassInfo
import com.only.codegen.template.TemplateNode

/**
 * 基于注解的模板生成器接口（独立接口）
 */
interface AnnotationTemplateGenerator {
    val tag: String
    val order: Int

    fun shouldGenerate(classInfo: ClassInfo, context: AnnotationContext): Boolean
    fun buildContext(classInfo: ClassInfo, context: AnnotationContext): Map<String, Any?>
    fun getDefaultTemplateNode(): TemplateNode
    fun onGenerated(classInfo: ClassInfo, context: AnnotationContext) {}
}
```

### 3.4.2 RepositoryGenerator

```kotlin
package com.only.codegen.generators.annotation

/**
 * Repository 生成器
 * 为聚合根生成 JPA Repository 接口
 */
class RepositoryGenerator : AnnotationTemplateGenerator {
    override val tag = "repository"
    override val order = 10

    private val generated = mutableSetOf<String>()

    override fun shouldGenerate(
        classInfo: ClassInfo,
        context: AnnotationContext
    ): Boolean {
        if (!classInfo.isAggregateRoot) return false
        if (generated.contains(classInfo.fullName)) return false
        if (!context.getBoolean("generateRepository", true)) return false
        return true
    }

    override fun buildContext(
        classInfo: ClassInfo,
        context: AnnotationContext
    ): Map<String, Any?> {
        val aggregateName = extractAggregateName(classInfo)
        val aggregateInfo = context.aggregateMap[aggregateName]
            ?: error("Aggregate not found: $aggregateName")

        val repositoryName = "${classInfo.simpleName}Repository"
        val identityType = aggregateInfo.identityType

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            // 使用 BaseContext 提供的 adapterPath
            resultContext.putContext(tag, "modulePath", adapterPath)
            resultContext.putContext(tag, "package", resolveRepositoryPackage(classInfo))
            resultContext.putContext(tag, "Entity", classInfo.simpleName)
            resultContext.putContext(tag, "EntityPackage", classInfo.packageName)
            resultContext.putContext(tag, "Repository", repositoryName)
            resultContext.putContext(tag, "IdentityType", identityType)
            resultContext.putContext(tag, "Aggregate", aggregateName)
        }

        return resultContext
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@RepositoryGenerator.tag
            name = "{{ Repository }}.kt"
            format = "resource"
            data = "repository"
            conflict = "skip"
        }
    }

    override fun onGenerated(classInfo: ClassInfo, context: AnnotationContext) {
        val repositoryName = "${classInfo.simpleName}Repository"
        val packageName = resolveRepositoryPackage(classInfo)
        val fullName = "$packageName.$repositoryName"

        context.typeMapping[repositoryName] = fullName
        generated.add(classInfo.fullName)
    }

    private fun extractAggregateName(classInfo: ClassInfo): String {
        return classInfo.annotations
            .find { it.name == "Aggregate" }
            ?.attributes?.get("aggregate") as? String
            ?: classInfo.simpleName
    }

    private fun resolveRepositoryPackage(classInfo: ClassInfo): String {
        return classInfo.packageName.replace("domain.aggregates", "adapter.persistence")
    }
}
```

### 3.4.3 ServiceGenerator

```kotlin
package com.only.codegen.generators.annotation

/**
 * Application Service 生成器
 */
class ServiceGenerator : AnnotationTemplateGenerator {
    override val tag = "service"
    override val order = 20

    private val generated = mutableSetOf<String>()

    override fun shouldGenerate(
        classInfo: ClassInfo,
        context: AnnotationContext
    ): Boolean {
        if (!classInfo.isAggregateRoot) return false
        if (generated.contains(classInfo.fullName)) return false
        if (!context.getBoolean("generateService", false)) return false
        return true
    }

    override fun buildContext(
        classInfo: ClassInfo,
        context: AnnotationContext
    ): Map<String, Any?> {
        val aggregateName = extractAggregateName(classInfo)
        val serviceName = "${classInfo.simpleName}Service"
        val repositoryName = "${classInfo.simpleName}Repository"

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            // 使用 BaseContext 提供的 applicationPath
            resultContext.putContext(tag, "modulePath", applicationPath)
            resultContext.putContext(tag, "package", resolveServicePackage(classInfo))
            resultContext.putContext(tag, "Entity", classInfo.simpleName)
            resultContext.putContext(tag, "Service", serviceName)
            resultContext.putContext(tag, "Repository", repositoryName)
            resultContext.putContext(tag, "Aggregate", aggregateName)

            // 添加 Repository 的导入
            val repositoryFQN = typeMapping[repositoryName]
            if (repositoryFQN != null) {
                resultContext.putContext(tag, "RepositoryImport", repositoryFQN)
            }
        }

        return resultContext
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@ServiceGenerator.tag
            name = "{{ Service }}.kt"
            format = "resource"
            data = "service"
            conflict = "skip"
        }
    }

    override fun onGenerated(classInfo: ClassInfo, context: AnnotationContext) {
        val serviceName = "${classInfo.simpleName}Service"
        val packageName = resolveServicePackage(classInfo)
        val fullName = "$packageName.$serviceName"

        context.typeMapping[serviceName] = fullName
        generated.add(classInfo.fullName)
    }

    private fun extractAggregateName(classInfo: ClassInfo): String {
        return classInfo.annotations
            .find { it.name == "Aggregate" }
            ?.attributes?.get("aggregate") as? String
            ?: classInfo.simpleName
    }

    private fun resolveServicePackage(classInfo: ClassInfo): String {
        return classInfo.packageName.replace("domain.aggregates", "application")
    }
}
```

---

## 3.5 Task 层

### GenAnnotationTask 完整实现

```kotlin
package com.only.codegen

import com.only.codegen.context.MutableAnnotationContext
import com.only.codegen.context.builders.annotation.*
import com.only.codegen.generators.annotation.*
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.resolvePackageDirectory
import org.gradle.api.tasks.TaskAction

/**
 * 基于注解的代码生成任务（使用 KSP）
 */
open class GenAnnotationTask : GenArchTask(), MutableAnnotationContext {

    @Internal
    override val classMap: MutableMap<String, ClassInfo> = mutableMapOf()

    @Internal
    override val annotationMap: MutableMap<String, MutableList<AnnotationInfo>> = mutableMapOf()

    @Internal
    override val aggregateMap: MutableMap<String, AggregateInfo> = mutableMapOf()

    @Internal
    override val sourceRoots: List<String> by lazy {
        listOf(domainPath)
    }

    @Internal
    override val scanPackages: List<String> by lazy {
        listOfNotNull(getString("basePackage"))
    }

    // === 实现 BaseContext 的包路径属性 ===
    @get:Internal
    override var aggregatesPath: String = ""
        get() = field.takeIf { it.isNotBlank() } ?: resolvePackageDirectory(
            domainPath,
            "${getString("basePackage")}.$AGGREGATE_PACKAGE"
        ).also { field = it }

    @get:Internal
    override var schemaPath: String = ""
        get() = field.takeIf { it.isNotBlank() } ?: resolvePackageDirectory(
            domainPath,
            "${getString("basePackage")}.${getString("entitySchemaOutputPackage").takeIf { it.isNotBlank() } ?: "domain._share.meta"}"
        ).also { field = it }

    @get:Internal
    override var subscriberPath: String = ""
        get() = field.takeIf { it.isNotBlank() } ?: resolvePackageDirectory(
            domainPath,
            "${getString("basePackage")}.$DOMAIN_EVENT_SUBSCRIBER_PACKAGE"
        ).also { field = it }

    @get:Internal
    override val aggregatesPackage: String by lazy {
        resolvePackage("${aggregatesPath}${File.separator}X.kt")
            .substring(getString("basePackage").length + 1)
    }

    @get:Internal
    override val schemaPackage: String by lazy {
        resolvePackage("${schemaPath}${File.separator}X.kt")
            .substring(getString("basePackage").length + 1)
    }

    @get:Internal
    override val subscriberPackage: String by lazy {
        resolvePackage("${subscriberPath}${File.separator}X.kt")
            .substring(getString("basePackage").length + 1)
    }

    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()

        genFromKspMetadata()
    }

    private fun genFromKspMetadata() {
        logger.lifecycle("Using KSP metadata for annotation processing")

        val context = buildAnnotationContext()

        if (context.classMap.isEmpty()) {
            logger.warn("No classes found in KSP metadata")
            return
        }

        logger.lifecycle("Found ${context.classMap.size} classes from KSP")
        logger.lifecycle("Found ${context.aggregateMap.size} aggregates from KSP")

        generateFiles(context)
    }

    private fun buildAnnotationContext(): AnnotationContext {
        val builders = listOf(
            KspMetadataContextBuilder(),
            AggregateInfoBuilder(),
            IdentityTypeBuilder(),
        )

        builders
            .sortedBy { it.order }
            .forEach { builder ->
                logger.lifecycle("Building annotation context: ${builder.javaClass.simpleName}")
                builder.build(this)
            }

        return this
    }

    private fun generateFiles(context: AnnotationContext) {
        val generators = listOf(
            RepositoryGenerator(),
            ServiceGenerator(),
        )

        generators
            .sortedBy { it.order }
            .forEach { generator ->
                logger.lifecycle("Generating files: ${generator.tag}")
                generateForClasses(generator, context)
            }
    }

    /**
     * 为类生成代码（参考 GenEntityTask.generateForTables 的实现）
     */
    private fun generateForClasses(
        generator: AnnotationTemplateGenerator,
        context: AnnotationContext,
    ) {
        val classes = context.classMap.values.toMutableList()

        while (classes.isNotEmpty()) {
            val classInfo = classes.first()

            if (!generator.shouldGenerate(classInfo, context)) {
                classes.removeFirst()
                continue
            }

            val classContext = generator.buildContext(classInfo, context)
            val templateNodes = context.templateNodeMap
                .getOrDefault(generator.tag, listOf(generator.getDefaultTemplateNode()))

            templateNodes.forEach { templateNode ->
                val pathNode = templateNode.deepCopy().resolve(classContext)

                forceRender(
                    pathNode,
                    resolvePackageDirectory(
                        classContext["modulePath"].toString(),
                        concatPackage(
                            getString("basePackage"),
                            classContext["package"].toString()
                        )
                    )
                )
            }

            generator.onGenerated(classInfo, context)
        }
    }
}
```

**关键改进点**：

1. **while 循环模式**
   - 使用 `while (classes.isNotEmpty())` + `classes.removeFirst()`
   - 与 `GenEntityTask.generateForTables` 保持一致

2. **路径解析复用**
   - 使用 `resolvePackageDirectory()` 和 `concatPackage()`
   - 从 `classContext["modulePath"]` 获取模块路径

3. **BaseContext 属性实现**
   - `aggregatesPath`, `schemaPath` 等实现与 `GenEntityTask` 完全相同
   - 复用 `resolvePackageDirectory()` 工具方法

---

[← 上一章：核心架构设计](02-architecture.md) | [返回目录](README.md) | [下一章：KSP Processor 实现 →](04-ksp-processor.md)
