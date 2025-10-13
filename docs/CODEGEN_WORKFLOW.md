# Codegen Plugin 完整工作流程

## 概述

本文档详细描述 `codegen-plugin` 的完整代码生成流程，包括架构生成、实体生成和注解生成三个阶段。

## 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                    Codegen Plugin 架构                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────┐   ┌──────────────┐   ┌─────────────────┐ │
│  │  GenArch     │   │  GenEntity   │   │ GenAnnotation   │ │
│  │  Task        │ → │  Task        │ → │ Task            │ │
│  │  脚手架生成   │   │  实体生成     │   │ 注解代码生成     │ │
│  └──────────────┘   └──────────────┘   └─────────────────┘ │
│         │                   │                    │          │
│         ↓                   ↓                    ↓          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           AbstractCodegenTask (抽象基类)              │  │
│  │  - 基础上下文构建 (baseMap)                           │  │
│  │  - 模板引擎初始化 (Pebble)                            │  │
│  │  - 文件渲染 (render/renderFile/renderDir)            │  │
│  │  - 模板别名系统 (templateAliasMap)                    │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## 阶段 0: 基础上下文构建

### 执行时机
所有任务执行前，通过 `AbstractCodegenTask.baseMap` 延迟初始化。

### 数据来源
用户在 `build.gradle.kts` 中的 `codegen` 配置块：

```kotlin
codegen {
    // 基础配置
    basePackage.set("com.example")
    multiModule.set(true)
    archTemplate.set("templates/arch.json")

    // 数据库配置
    database {
        url.set("jdbc:mysql://localhost:3306/mydb")
        username.set("root")
        password.set("password")
        schema.set("mydb")
    }

    // 生成配置
    generation {
        versionField.set("version")
        deletedField.set("deleted")
        entityBaseClass.set("BaseEntity")
        // ... 更多配置
    }
}
```

### 基础上下文内容

AbstractCodegenTask.kt:80-147 构建的 `baseMap` 包含：

```kotlin
baseMap = mapOf(
    // 项目信息
    "artifactId" to projectName,
    "groupId" to projectGroup,
    "version" to projectVersion,

    // 基础配置
    "archTemplate" to ext.archTemplate,
    "basePackage" to ext.basePackage,
    "basePackage__as_path" to "com/example",  // 用于文件路径
    "multiModule" to "true",

    // 模块路径
    "adapterModulePath" to "/path/to/adapter",
    "applicationModulePath" to "/path/to/application",
    "domainModulePath" to "/path/to/domain",

    // 数据库配置
    "dbUrl" to "jdbc:mysql://...",
    "dbUsername" to "root",
    "dbPassword" to "password",
    "dbSchema" to "mydb",
    "dbTables" to "user,order",

    // 生成配置（40+ 个配置项）
    "versionField" to "version",
    "deletedField" to "deleted",
    "entityBaseClass" to "BaseEntity",
    // ... 更多配置

    // 系统信息
    "date" to "2025/01/15",
    "SEPARATOR" to File.separator
)
```

### 模板别名系统

AbstractCodegenTask.kt:207-356 定义了 300+ 个模板变量别名映射，允许模板使用多种命名风格：

```kotlin
templateAliasMap = mapOf(
    // Comment 变量的多种写法
    "schema.Comment" to listOf("Comment", "comment", "COMMENT"),

    // Entity 变量的多种写法
    "schema.Entity" to listOf(
        "Entity", "entity", "ENTITY",
        "entityType", "EntityType", "ENTITY_TYPE"
    ),

    // ... 更多别名映射
)
```

**作用**：模板中可以使用任意命名风格，都会被正确解析：
- `{{ Entity }}` → "User"
- `{{ entity }}` → "User"
- `{{ entityType }}` → "User"

## 阶段 1: GenArch - 脚手架生成

### 1.1 执行入口

```kotlin
// GenArchTask.kt:35
private fun genArch() {
    // 1. 初始化 Pebble 模板引擎
    val config = PebbleConfig(encoding = ext.archTemplateEncoding.get())
    PebbleInitializer.initPebble(config)

    // 2. 验证配置
    val archTemplate = validateAndGetArchTemplate(ext) ?: return

    // 3. 加载模板
    template = loadTemplate(archTemplate, ext)

    // 4. 渲染脚手架
    render(template!!, projectDir.get())
}
```

### 1.2 模板加载

GenArchTask.kt:49-57

```kotlin
private fun loadTemplate(templatePath: String, ext: CodegenExtension): Template {
    // 1. 读取模板文件内容
    val templateContent = loadFileContent(templatePath, ext.archTemplateEncoding.get())

    // 2. 设置模板目录（用于解析相对路径）
    PathNode.setDirectory(resolveDirectory(templatePath, projectDir.get()))

    // 3. JSON 反序列化为 Template 对象
    return JSON.parseObject(templateContent, Template::class.java).apply {
        // 4. 使用基础上下文解析模板（初步解析）
        resolve(baseMap)
    }
}
```

### 1.3 模板结构

#### Template 对象

Template.kt:9-22

```kotlin
class Template : PathNode() {
    // 模板节点列表（用于实体生成阶段）
    var templates: MutableList<TemplateNode>? = null

    // 根据 tag 查找模板节点
    fun select(tag: String): List<TemplateNode> =
        templates?.filter { it.tag == tag } ?: emptyList()
}
```

#### PathNode 对象

PathNode.kt:14-60

```kotlin
open class PathNode {
    var type: String?      // 节点类型：root|dir|file|segment
    var tag: String?       // 节点标签：关联模板节点
    var name: String?      // 节点名称
    var format: String     // 模板源类型：raw|url|resource
    var encoding: String?  // 输出编码
    var data: String?      // 模板数据/文件路径
    var conflict: String   // 冲突处理：skip|warn|overwrite
    var children: MutableList<PathNode>?  // 下级节点
}
```

#### TemplateNode 对象

TemplateNode.kt:11-27

```kotlin
class TemplateNode : PathNode() {
    var pattern: String = ""  // 元素匹配正则

    fun deepCopy(): TemplateNode {
        return JSON.parseObject(JSON.toJSONString(this), TemplateNode::class.java)
    }

    override fun resolve(context: Map<String, Any?>): PathNode {
        super.resolve(context)
        this.tag = ""  // 清空 tag，避免重复处理
        return this
    }
}
```

### 1.4 模板示例

```json
{
  "type": "root",
  "children": [
    {
      "type": "dir",
      "name": "{{ basePackage__as_path }}",
      "conflict": "skip",
      "tag": "aggregate",
      "children": [
        {
          "type": "file",
          "name": "User.kt",
          "conflict": "skip",
          "format": "url",
          "data": "templates/entity.peb"
        }
      ]
    }
  ],
  "templates": [
    {
      "tag": "aggregate",
      "type": "file",
      "name": "{{ Entity }}.kt",
      "conflict": "overwrite",
      "format": "url",
      "data": "templates/entity.peb",
      "pattern": ".*"
    }
  ]
}
```

### 1.5 模板解析过程

PathNode.kt:62-105

```kotlin
fun resolve(context: Map<String, Any?>): PathNode {
    // 1. 渲染节点名称（支持变量替换）
    name = name
        ?.replace("{{ basePackage }}", "{{ basePackage__as_path }}")
        ?.let { renderString(it, context) }

    // 2. 根据 format 加载模板内容
    val rawData = when (format.lowercase()) {
        "url" -> {
            // 从文件系统或 HTTP 加载
            val absolutePath = if (isAbsolutePathOrHttpUri(data)) {
                data
            } else {
                concatPathOrHttpUri(directory.get(), data)
            }
            loadFileContent(absolutePath, encoding)
        }
        "resource" -> {
            // 从 classpath 加载
            PathNode::class.java.classLoader
                .getResourceAsStream(data)
                ?.bufferedReader()?.use { it.readText() } ?: ""
        }
        else -> {
            // raw: 直接使用 data 字段内容
            data ?: ""
        }
    }

    // 3. 渲染模板内容（Pebble 引擎）
    data = renderString(rawData, context)
    format = "raw"

    // 4. 递归处理子节点
    children?.forEach { it.resolve(context) }
    return this
}
```

**关键点**：
- ✅ 此阶段只有 `baseMap` 上下文
- ⚠️ 如果模板中使用了实体特定变量（如 `{{ Entity }}`），此时无法完全解析
- 🔄 实体特定变量会在 GenEntity 阶段再次解析

### 1.6 文件渲染

AbstractCodegenTask.kt:368-468

```kotlin
protected fun render(pathNode: PathNode, parentPath: String): String =
    when (pathNode.type?.lowercase()) {
        "root" -> {
            // 根节点：递归处理子节点
            pathNode.children?.forEach { render(it, parentPath) }
            parentPath
        }

        "dir" -> {
            // 目录节点：创建目录 + 处理 tag 关联的模板
            val dirPath = renderDir(pathNode, parentPath)
            pathNode.children?.forEach { render(it, dirPath) }
            dirPath
        }

        "file" -> renderFile(pathNode, parentPath)  // 文件节点：写入文件
        else -> parentPath
    }
```

#### 目录渲染

AbstractCodegenTask.kt:400-431

```kotlin
private fun renderDir(pathNode: PathNode, parentPath: String): String {
    val path = "$parentPath${File.separator}$name"
    val dirFile = File(path)

    // 1. 根据 conflict 策略处理目录
    when {
        !dirFile.exists() -> dirFile.mkdirs()
        pathNode.conflict == "overwrite" -> {
            dirFile.deleteRecursively()
            dirFile.mkdirs()
        }
        pathNode.conflict == "skip" -> logger.info("目录已存在，跳过")
        pathNode.conflict == "warn" -> logger.warn("目录已存在，继续")
    }

    // 2. 处理 tag 关联的模板节点（实体生成阶段使用）
    pathNode.tag?.let { tag ->
        tag.split(",", ";")
            .filter { it.isNotBlank() }
            .forEach { renderTemplate(template!!.select(it), path) }
    }

    return path
}
```

**关键点**：
- `pathNode.tag` 指定了哪些模板节点应该在这个目录生成
- 此时 `renderTemplate()` 是抽象方法，由子类实现
- GenArchTask 中 `renderTemplate()` 为空实现
- GenEntityTask 中实现为缓存模板路径

#### 文件渲染

AbstractCodegenTask.kt:433-468

```kotlin
protected fun renderFile(pathNode: PathNode, parentPath: String): String {
    val path = "$parentPath${File.separator}$name"

    // renderFileSwitch 控制是否实际写入文件
    if (!renderFileSwitch) return path

    val file = File(path)
    val content = pathNode.data.orEmpty()

    // 根据 conflict 策略处理文件
    when {
        !file.exists() -> {
            file.parentFile?.mkdirs()
            file.writeText(content, charset)
            logger.info("创建文件: $path")
        }

        pathNode.conflict == "skip" ->
            logger.info("文件已存在，跳过: $path")

        pathNode.conflict == "warn" ->
            logger.warn("文件已存在，继续: $path")

        pathNode.conflict == "overwrite" -> {
            // 检查保护标记
            if (file.readText(charset).contains(FLAG_DO_NOT_OVERWRITE)) {
                logger.warn("文件已存在且包含保护标记，跳过: $path")
            } else {
                file.writeText(content, charset)
                logger.info("文件覆盖: $path")
            }
        }
    }

    return path
}
```

**关键标识**：
```kotlin
const val FLAG_DO_NOT_OVERWRITE = "[cap4k-ddd-codegen-gradle-plugin:do-not-overwrite]"
```

### 1.7 GenArch 阶段总结

**输入**：
- 用户配置（`CodegenExtension`）
- 架构模板 JSON 文件

**处理**：
1. 初始化 Pebble 引擎
2. 加载并解析架构模板
3. 渲染脚手架目录结构
4. 缓存模板节点路径（为 GenEntity 准备）

**输出**：
- 项目基础目录结构
- `aggregatesPath`、`schemaPath`、`subscriberPath` 等路径信息
- `templateNodeMap` 缓存（tag → TemplateNode 映射）

**限制**：
- ⚠️ 只有基础上下文，无法渲染实体特定内容
- ⚠️ 模板中的 `{{ Entity }}`、`{{ Aggregate }}` 等变量无法解析

## 阶段 2: GenEntity - 实体生成

### 2.1 执行入口

GenEntityTask.kt:124-131

```kotlin
@TaskAction
override fun generate() {
    // 1. 设置不写入文件标志（只缓存路径）
    renderFileSwitch = false

    // 2. 调用父类 generate()，重新执行 genArch()
    //    目的：缓存模板节点路径到 templateNodeMap
    super.generate()

    // 3. 设置上下文引用
    SqlSchemaUtils.context = this

    // 4. 执行实体生成
    genEntity()
}
```

**关键点**：
- ✅ `renderFileSwitch = false` 防止重复生成脚手架文件
- ✅ `super.generate()` 重新执行 `render()` 流程，但只缓存路径
- ✅ 此时会执行 `renderTemplate()` 缓存模板节点

### 2.2 模板节点缓存

GenEntityTask.kt:92-105

```kotlin
override fun renderTemplate(
    templateNodes: List<TemplateNode>,
    parentPath: String,
) {
    templateNodes.forEach { templateNode ->
        // 1. 标准化 tag 名称
        val alias = alias4Design(templateNode.tag!!)

        // 2. 缓存目录路径（重要！）
        when (alias) {
            "aggregate" -> aggregatesPath = parentPath
            "schema_base" -> schemaPath = parentPath
            "domain_event_handler" -> subscriberPath = parentPath
        }

        // 3. 缓存模板节点到 map
        templateNodeMap
            .computeIfAbsent(alias) { mutableListOf() }
            .add(templateNode)
    }
}
```

**tag 别名映射**：

GenEntityTask.kt:108-122

```kotlin
private fun alias4Design(name: String): String = when (name.lowercase()) {
    "entity", "aggregate", "entities", "aggregates" -> "aggregate"
    "schema", "schemas" -> "schema"
    "enum", "enums" -> "enum"
    "factory", "factories", "fac" -> "factory"
    "specification", "specifications", "spec" -> "specification"
    "domain_event", "domain_events", "de" -> "domain_event"
    "domain_event_handler", "domain_event_subscriber" -> "domain_event_handler"
    else -> name
}
```

**结果**：
```kotlin
templateNodeMap = mapOf(
    "aggregate" to [TemplateNode(...), TemplateNode(...)],
    "schema" to [TemplateNode(...)],
    "enum" to [TemplateNode(...)],
    // ...
)

aggregatesPath = "/path/to/domain/aggregates"
schemaPath = "/path/to/domain/schema"
subscriberPath = "/path/to/application/subscribers"
```

### 2.3 上下文构建阶段

GenEntityTask.kt:133-165

```kotlin
private fun genEntity() {
    // 构建生成上下文
    val context = buildGenerationContext()

    if (context.tableMap.isEmpty()) {
        logger.warn("No tables found in database")
        return
    }

    // 生成文件
    generateFiles(context)
}

private fun buildGenerationContext(): EntityContext {
    val contextBuilders = listOf(
        TableContextBuilder(),          // order=10  - 表和列信息
        EntityTypeContextBuilder(),     // order=20  - 实体类型
        AnnotationContextBuilder(),     // order=20  - 注解信息
        ModuleContextBuilder(),         // order=20  - 模块信息
        RelationContextBuilder(),       // order=20  - 表关系
        EnumContextBuilder(),           // order=20  - 枚举信息
        AggregateContextBuilder(),      // order=30  - 聚合信息
        TablePackageContextBuilder(),   // order=40  - 表包信息
    )

    // 按 order 排序执行
    contextBuilders
        .sortedBy { it.order }
        .forEach { builder ->
            logger.lifecycle("Building context: ${builder.javaClass.simpleName}")
            builder.build(this)  // 填充 MutableEntityContext
        }

    return this
}
```

#### Context Builder 执行流程

```
1. TableContextBuilder (order=10)
   ↓ 填充 tableMap, columnsMap

2. EntityTypeContextBuilder (order=20)
   ↓ 填充 entityTypeMap

3. AnnotationContextBuilder (order=20)
   ↓ 填充 annotationsMap

4. ModuleContextBuilder (order=20)
   ↓ 填充 tableModuleMap

5. RelationContextBuilder (order=20)
   ↓ 填充 relationsMap

6. EnumContextBuilder (order=20)
   ↓ 填充 enumConfigMap, enumPackageMap

7. AggregateContextBuilder (order=30)
   ↓ 填充 tableAggregateMap

8. TablePackageContextBuilder (order=40)
   ↓ 填充 tablePackageMap
```

**Context Builder 基础接口**:

```kotlin
interface ContextBuilder<T : BaseContext> {
    val order: Int  // 执行顺序

    // 填充上下文数据
    fun build(context: T)
}

interface EntityContextBuilder : ContextBuilder<EntityContext>
```

#### EntityContext 数据结构

```kotlin
interface EntityContext : BaseContext {
    // 表信息
    val tableMap: Map<String, Map<String, Any?>>
    val columnsMap: Map<String, List<Map<String, Any?>>>
    val relationsMap: Map<String, Map<String, String>>

    // 类型映射
    val entityTypeMap: Map<String, String>
    val tablePackageMap: Map<String, String>
    val tableModuleMap: Map<String, String>
    val tableAggregateMap: Map<String, String>

    // 注解和枚举
    val annotationsMap: Map<String, Map<String, String>>
    val enumConfigMap: Map<String, Map<Int, Array<String>>>
    val enumPackageMap: Map<String, String>

    // 额外配置
    val dbType: String
    val entityClassExtraImports: List<String>
}
```

### 2.4 文件生成阶段

GenEntityTask.kt:167-221

```kotlin
private fun generateFiles(context: EntityContext) {
    val generators = listOf(
        SchemaBaseGenerator(),           // order=10 - Schema 基类
        EnumGenerator(),                 // order=10 - 枚举类
        EntityGenerator(),               // order=20 - 实体类
        SpecificationGenerator(),        // order=30 - 规约类
        FactoryGenerator(),              // order=30 - 工厂类
        DomainEventGenerator(),          // order=30 - 领域事件类
        DomainEventHandlerGenerator(),   // order=30 - 领域事件处理器
        AggregateGenerator(),            // order=40 - 聚合封装类
        SchemaGenerator(),               // order=50 - Schema 类
    )

    // 按 order 排序执行
    generators.sortedBy { it.order }
        .forEach { generator ->
            logger.lifecycle("Generating files: ${generator.tag}")
            generateForTables(generator, context)
        }
}
```

#### Generator 执行流程

GenEntityTask.kt:187-221

```kotlin
private fun generateForTables(
    generator: TemplateGenerator,
    context: EntityContext,
) {
    val tables = context.tableMap.values.toMutableList()

    while (tables.isNotEmpty()) {
        val table = tables.first()

        // 1. 判断是否需要生成
        if (!generator.shouldGenerate(table, context)) {
            tables.removeFirst()
            continue
        }

        // 2. 构建表级上下文
        val tableContext = generator.buildContext(table, context)

        // 3. 获取模板节点（优先用户自定义，否则用默认）
        val templateNodes = context.templateNodeMap
            .getOrDefault(generator.tag, listOf(generator.getDefaultTemplateNode()))

        // 4. 为每个模板节点生成文件
        templateNodes.forEach { templateNode ->
            // 4.1 深拷贝并解析模板
            val pathNode = templateNode.deepCopy().resolve(tableContext)

            // 4.2 渲染文件
            forceRender(
                pathNode,
                resolvePackageDirectory(
                    tableContext["modulePath"].toString(),
                    concatPackage(
                        getString("basePackage"),
                        tableContext["templatePackage"].toString(),
                        tableContext["package"].toString()
                    )
                )
            )
        }

        // 5. 生成后回调（缓存类型映射）
        generator.onGenerated(table, context)
    }
}
```

#### TemplateGenerator 接口

```kotlin
interface TemplateGenerator {
    val tag: String              // 生成器标签
    val order: Int               // 执行顺序

    // 判断是否需要为该表生成
    fun shouldGenerate(table: Map<String, Any?>, context: EntityContext): Boolean

    // 构建表级上下文（扩展基础上下文）
    fun buildContext(table: Map<String, Any?>, context: EntityContext): MutableMap<String, Any?>

    // 获取默认模板节点
    fun getDefaultTemplateNode(): TemplateNode

    // 生成后回调（缓存类型映射）
    fun onGenerated(table: Map<String, Any?>, context: EntityContext)
}
```

**AnnotationTemplateGenerator 接口**:

```kotlin
interface AnnotationTemplateGenerator {
    val tag: String              // 生成器标签
    val order: Int               // 执行顺序

    // 判断是否需要为该聚合生成
    fun shouldGenerate(aggregate: AggregateInfo, context: AnnotationContext): Boolean

    // 构建聚合级上下文
    fun buildContext(aggregate: AggregateInfo, context: AnnotationContext): MutableMap<String, Any?>

    // 获取默认模板节点
    fun getDefaultTemplateNode(): TemplateNode

    // 生成后回调
    fun onGenerated(aggregate: AggregateInfo, context: AnnotationContext)
}
```

#### 表级上下文示例

```kotlin
tableContext = baseMap + mapOf(
    // 表信息
    "tableName" to "sys_user",
    "tableComment" to "用户表",

    // 实体信息
    "Entity" to "User",
    "entity" to "User",
    "ENTITY" to "USER",
    "Aggregate" to "user",
    "aggregate" to "user",

    // 包信息
    "entityPackage" to "com.example.domain.aggregates.user",
    "templatePackage" to "domain.aggregates",
    "package" to "user",
    "modulePath" to "/path/to/domain",

    // 字段信息
    "FIELD_ITEMS" to listOf(...),
    "JOIN_ITEMS" to listOf(...),
    "IdField" to "id",

    // 类型映射
    "User" to "com.example.domain.aggregates.user.User",
    // ...
)
```

### 2.5 GenEntity 阶段总结

**输入**：
- 基础上下文（baseMap）
- 架构模板缓存（templateNodeMap）
- 数据库元数据

**处理**：
1. 缓存模板节点和路径（renderFileSwitch = false）
2. 通过 8 个 ContextBuilder 构建完整上下文
3. 通过 9 个 Generator 生成代码文件

**输出**：
- Domain 层实体类
- Schema 元数据类
- 枚举类
- 规约类
- 工厂类
- 领域事件类
- 聚合封装类
- typeMapping 缓存（全限定类名映射）

**与 GenArch 的区别**：
- ✅ 拥有完整的数据库上下文
- ✅ 可以渲染实体特定变量（`{{ Entity }}`、`{{ Aggregate }}`）
- ✅ 支持按表循环生成
- ✅ 填充 typeMapping 供后续使用

## 阶段 3: GenAnnotation - 注解代码生成

### 3.1 执行入口

GenAnnotationTask.kt:51-82

```kotlin
@TaskAction
override fun generate() {
    // 1. 设置不写入文件标志（只初始化 Pebble）
    renderFileSwitch = false

    // 2. 调用父类 generate()，初始化 Pebble 引擎
    super.generate()

    // 3. 执行注解代码生成
    genAnnotation()
}

private fun genAnnotation() {
    logger.lifecycle("Starting annotation-based code generation...")

    // 1. 解析 KSP 元数据路径
    val metadataPath = resolveMetadataPath()
    if (!metadataPath.exists()) {
        logger.warn("KSP metadata not found at: ${metadataPath.absolutePath}")
        logger.warn("Please run KSP processor first to generate metadata")
        return
    }

    // 2. 构建生成上下文
    val context = buildGenerationContext(metadataPath.absolutePath)

    if (context.aggregateMap.isEmpty()) {
        logger.warn("No aggregates found in metadata")
        return
    }

    // 3. 生成文件
    logger.lifecycle("Found ${context.aggregateMap.size} aggregates")
    generateFiles(context)

    logger.lifecycle("Annotation-based code generation completed")
}
```

### 3.2 KSP 元数据路径解析

GenAnnotationTask.kt:84-130

```kotlin
private fun resolveMetadataPath(): File {
    // 1. 优先使用配置的路径
    val configuredPath = extension.get().annotation.metadataPath.orNull
    if (!configuredPath.isNullOrBlank()) {
        return File(configuredPath)
    }

    // 2. 多模块项目：查找 domain 模块
    val ext = extension.get()
    if (ext.multiModule.get()) {
        val domainModuleName = "${projectName.get()}${ext.moduleNameSuffix4Domain.get()}"
        val domainModulePath = File(projectDir.get(), domainModuleName)

        // KSP 默认输出路径
        val domainKspPath = File(domainModulePath, "build/generated/ksp/main/resources/metadata")

        if (domainKspPath.exists()) {
            logger.info("Found KSP metadata in domain module: ${domainKspPath.absolutePath}")
            return domainKspPath
        }

        // 查找其他子模块
        val projectRoot = File(projectDir.get())
        val subModules = projectRoot.listFiles { file ->
            file.isDirectory && file.name.startsWith(projectName.get())
        }?.toList() ?: emptyList()

        for (subModule in subModules) {
            val kspPath = File(subModule, "build/generated/ksp/main/resources/metadata")
            if (kspPath.exists()) {
                logger.info("Found KSP metadata in module ${subModule.name}: ${kspPath.absolutePath}")
                return kspPath
            }
        }

        return domainKspPath
    }

    // 3. 单模块项目：项目根目录
    return File(projectDir.get(), "build/generated/ksp/main/resources/metadata")
}
```

**KSP 元数据文件**：
```
build/generated/ksp/main/resources/metadata/
├── aggregates.json    # 聚合根和实体信息
└── entities.json      # JPA 实体信息
```

### 3.3 注解上下文构建

GenAnnotationTask.kt:132-158

```kotlin
private fun buildGenerationContext(metadataPath: String): AnnotationContext {
    val contextBuilders = listOf(
        KspMetadataContextBuilder(metadataPath),  // order=10 - 读取元数据
        AggregateInfoBuilder(),                   // order=20 - 聚合信息
        IdentityTypeBuilder(),                    // order=30 - ID 类型映射
    )

    contextBuilders
        .sortedBy { it.order }
        .forEach { builder ->
            logger.lifecycle("Building context: ${builder.javaClass.simpleName}")
            builder.build(this)

            // 输出调试信息
            logger.lifecycle("  - classMap size: ${classMap.size}")
            logger.lifecycle("  - aggregateMap size: ${aggregateMap.size}")
        }

    return this
}
```

**AnnotationContextBuilder 基础接口**:

```kotlin
interface ContextBuilder<T : BaseContext> {
    val order: Int  // 执行顺序

    // 填充上下文数据
    fun build(context: T)
}

interface AnnotationContextBuilder : ContextBuilder<AnnotationContext>
```

#### AnnotationContext 数据结构

```kotlin
interface AnnotationContext : BaseContext {
    // 类信息映射（完全限定名 -> ClassInfo）
    val classMap: Map<String, ClassInfo>

    // 注解信息映射（类名 -> 注解列表）
    val annotationMap: Map<String, List<AnnotationInfo>>

    // 聚合信息映射（聚合名 -> AggregateInfo）
    val aggregateMap: Map<String, AggregateInfo>

    // 扫描配置
    val sourceRoots: List<String>
    val scanPackages: List<String>
}
```

#### ClassInfo 结构

```kotlin
data class ClassInfo(
    val name: String,              // 类名
    val qualifiedName: String,     // 全限定名
    val packageName: String,       // 包名
    val isAggregateRoot: Boolean,  // 是否是聚合根
    val isEntity: Boolean,         // 是否是实体
    val isValueObject: Boolean,    // 是否是值对象
    val identityType: String,      // ID 类型
    val fields: List<FieldInfo>    // 字段列表
)
```

#### AggregateInfo 结构

```kotlin
data class AggregateInfo(
    val name: String,              // 聚合名
    val root: ClassInfo,           // 聚合根
    val entities: List<ClassInfo>, // 实体列表
    val valueObjects: List<ClassInfo>, // 值对象列表
    val packageName: String,       // 包名
    val modulePath: String         // 模块路径
)
```

### 3.4 文件生成阶段

GenAnnotationTask.kt:160-216

```kotlin
private fun generateFiles(context: AnnotationContext) {
    val generators = listOf(
        RepositoryGenerator(),  // order=10 - Repository 接口
        // ServiceGenerator(),   // order=20 - Service 类（已排除）
        // ControllerGenerator(), // order=30 - Controller 类（未实现）
    )

    generators.sortedBy { it.order }
        .forEach { generator ->
            logger.lifecycle("Generating files: ${generator.tag}")
            generateForAggregates(generator, context)
        }
}

private fun generateForAggregates(
    generator: AnnotationTemplateGenerator,
    context: AnnotationContext,
) {
    val aggregates = context.aggregateMap.values.toList()

    aggregates.forEach { aggregateInfo ->
        // 1. 判断是否需要生成
        if (!generator.shouldGenerate(aggregateInfo, context)) {
            logger.debug("Skipping ${generator.tag} for aggregate: ${aggregateInfo.name}")
            return@forEach
        }

        logger.lifecycle("Generating ${generator.tag} for aggregate: ${aggregateInfo.name}")

        // 2. 构建聚合级上下文
        val aggregateContext = generator.buildContext(aggregateInfo, context)

        // 3. 获取模板节点
        val templateNodes = context.templateNodeMap
            .getOrDefault(generator.tag, listOf(generator.getDefaultTemplateNode()))

        // 4. 生成文件
        templateNodes.forEach { templateNode ->
            val pathNode = templateNode.deepCopy().resolve(aggregateContext)
            forceRender(
                pathNode,
                resolvePackageDirectory(
                    aggregateContext["modulePath"].toString(),
                    concatPackage(
                        getString("basePackage"),
                        aggregateContext["templatePackage"].toString()
                    )
                )
            )
        }

        // 5. 生成后回调
        generator.onGenerated(aggregateInfo, context)
    }
}
```

#### 聚合级上下文示例

```kotlin
aggregateContext = baseMap + mapOf(
    // 聚合信息
    "Aggregate" to "user",
    "aggregate" to "user",
    "AggregateRoot" to "User",
    "aggregateRoot" to "User",

    // 包信息
    "entityPackage" to "com.example.domain.aggregates.user",
    "templatePackage" to "adapter.domain.repositories",
    "modulePath" to "/path/to/adapter",

    // 实体信息
    "Entity" to "User",
    "entity" to "User",
    "identityType" to "Long",

    // 类型映射（继承自 EntityContext）
    "User" to "com.example.domain.aggregates.user.User",
    "UserRepository" to "com.example.adapter.domain.repositories.UserRepository",
    // ...
)
```

### 3.5 GenAnnotation 阶段总结

**前置条件**：
- ✅ GenEntity 已执行，生成了 Domain 层实体
- ✅ KSP Processor 已执行，生成了元数据 JSON

**输入**：
- KSP 生成的元数据（aggregates.json、entities.json）
- 基础上下文（baseMap）
- typeMapping（从 GenEntity 继承）

**处理**：
1. 读取 KSP 元数据 JSON
2. 通过 3 个 AnnotationContextBuilder 构建上下文
3. 通过 AnnotationTemplateGenerator 生成代码

**输出**：
- Repository 接口（adapter 层）
- Service 类（application 层，可选）
- Controller 类（adapter 层，未实现）

**与 GenEntity 的区别**：
- ✅ 不依赖数据库，只依赖源码注解
- ✅ 按聚合维度生成（而非按表）
- ✅ 生成基础设施层代码（而非领域层）

## 完整工作流程总结

### 流程图

```
用户配置 (build.gradle.kts)
    ↓
┌───────────────────────────────────────────────────────┐
│ 0. 基础上下文构建 (AbstractCodegenTask.baseMap)       │
│    - 项目信息 (artifactId, groupId, version)          │
│    - 模块路径 (domainPath, adapterPath, ...)          │
│    - 数据库配置 (dbUrl, dbSchema, ...)                │
│    - 生成配置 (40+ 配置项)                             │
│    - 模板别名系统 (300+ 别名映射)                      │
└───────────────────────────────────────────────────────┘
    ↓
┌───────────────────────────────────────────────────────┐
│ 1. GenArch - 脚手架生成                                │
│    输入: 架构模板 JSON                                 │
│    处理:                                               │
│      - 初始化 Pebble 引擎                             │
│      - 加载并解析模板 (loadTemplate)                  │
│      - 渲染目录结构 (render)                          │
│      - 写入脚手架文件 (renderFile)                    │
│    输出:                                               │
│      - 项目基础目录                                    │
│      - 配置文件、README 等                            │
│    限制:                                               │
│      ⚠️ 只有 baseMap，无法渲染实体变量                │
└───────────────────────────────────────────────────────┘
    ↓
┌───────────────────────────────────────────────────────┐
│ 2. GenEntity - 实体生成                                │
│    输入: 数据库元数据                                  │
│    处理:                                               │
│      2.1 缓存阶段 (renderFileSwitch = false)          │
│          - 重新执行 render()                          │
│          - 缓存模板节点到 templateNodeMap             │
│          - 记录路径 (aggregatesPath, schemaPath)      │
│      2.2 上下文构建阶段                                │
│          - TableContextBuilder (order=10)             │
│          - EntityTypeContextBuilder (order=20)        │
│          - AnnotationContextBuilder (order=20)        │
│          - ModuleContextBuilder (order=20)            │
│          - RelationContextBuilder (order=20)          │
│          - EnumContextBuilder (order=20)              │
│          - AggregateContextBuilder (order=30)         │
│          - TablePackageContextBuilder (order=40)      │
│      2.3 文件生成阶段                                  │
│          - SchemaBaseGenerator (order=10)             │
│          - EnumGenerator (order=10)                   │
│          - EntityGenerator (order=20)                 │
│          - SpecificationGenerator (order=30)          │
│          - FactoryGenerator (order=30)                │
│          - DomainEventGenerator (order=30)            │
│          - DomainEventHandlerGenerator (order=30)     │
│          - AggregateGenerator (order=40)              │
│          - SchemaGenerator (order=50)                 │
│    输出:                                               │
│      - Domain 层实体类                                │
│      - Schema 元数据类                                │
│      - 枚举类                                         │
│      - 规约类、工厂类                                 │
│      - 领域事件类                                     │
│      - typeMapping 缓存                               │
└───────────────────────────────────────────────────────┘
    ↓
┌───────────────────────────────────────────────────────┐
│ KSP Processor (编译时注解处理)                         │
│    - 扫描 @Aggregate、@Entity 注解                    │
│    - 生成 aggregates.json、entities.json              │
│    - 输出到 build/generated/ksp/main/resources/       │
└───────────────────────────────────────────────────────┘
    ↓
┌───────────────────────────────────────────────────────┐
│ 3. GenAnnotation - 注解代码生成                        │
│    输入: KSP 元数据 JSON                               │
│    处理:                                               │
│      3.1 元数据加载                                    │
│          - 解析 aggregates.json                       │
│          - 解析 entities.json                         │
│      3.2 上下文构建                                    │
│          - KspMetadataContextBuilder (order=10)       │
│          - AggregateInfoBuilder (order=20)            │
│          - IdentityTypeBuilder (order=30)             │
│      3.3 文件生成                                      │
│          - RepositoryGenerator (order=10)             │
│    输出:                                               │
│      - Repository 接口 (adapter 层)                   │
│      - Service 类 (application 层，可选)              │
│      - Controller 类 (adapter 层，未实现)             │
└───────────────────────────────────────────────────────┘
```

### 核心概念总结

| 概念 | 说明 |
|------|------|
| **基础上下文（baseMap）** | 所有任务共享的配置上下文，包含项目、数据库、生成配置 |
| **脚手架节点（PathNode）** | 架构模板的目录和文件结构，GenArch 阶段渲染 |
| **模板节点（TemplateNode）** | 实体代码生成模板，GenEntity 阶段使用 |
| **templateNodeMap** | 模板节点缓存，key 为 tag，value 为模板节点列表 |
| **renderFileSwitch** | 文件写入开关，GenEntity 缓存阶段设为 false |
| **forceRender()** | 强制渲染，无视 renderFileSwitch |
| **ContextBuilder** | 上下文构建器基础接口 `ContextBuilder<T : BaseContext>`，按 order 顺序填充上下文 |
| **EntityContextBuilder** | 实体上下文构建器接口 `EntityContextBuilder : ContextBuilder<EntityContext>` |
| **AnnotationContextBuilder** | 注解上下文构建器接口 `AnnotationContextBuilder : ContextBuilder<AnnotationContext>` |
| **TemplateGenerator** | 实体代码生成器接口，按 order 顺序生成文件 |
| **AnnotationTemplateGenerator** | 注解代码生成器接口，用于基于注解的代码生成 |
| **typeMapping** | 类型映射缓存，存储全限定类名，供后续引用 |
| **AnnotationContext** | 基于注解的上下文，从 KSP 元数据构建 |

## 附录

### 关键文件清单

- `AbstractCodegenTask.kt` - 抽象基类，基础上下文和渲染逻辑
- `GenArchTask.kt` - 脚手架生成任务
- `GenEntityTask.kt` - 实体生成任务
- `GenAnnotationTask.kt` - 注解代码生成任务
- `Template.kt` - 模板对象
- `PathNode.kt` - 脚手架节点
- `TemplateNode.kt` - 模板节点
- `PebbleTemplateRenderer.kt` - Pebble 模板引擎
- `context/builders/` - 上下文构建器（ContextBuilder、EntityContextBuilder、AnnotationContextBuilder）
- `generators/` - 代码生成器（TemplateGenerator、AnnotationTemplateGenerator）

### 核心接口层次结构

```
codegen.core.context
├── BaseContext                    // 基础上下文接口
├── EntityContext : BaseContext    // 实体生成上下文
└── AnnotationContext : BaseContext // 注解生成上下文

codegen.core.context.builders
├── ContextBuilder<T : BaseContext>           // 上下文构建器基础接口
├── EntityContextBuilder : ContextBuilder<EntityContext>     // 实体上下文构建器
└── AnnotationContextBuilder : ContextBuilder<AnnotationContext> // 注解上下文构建器

codegen.core.generators
├── TemplateGenerator              // 实体代码生成器接口
└── AnnotationTemplateGenerator    // 注解代码生成器接口
```

### 相关文档

- `ksp-processor/README.md` - KSP Processor 详细文档
- `ksp-processor/KSP_RESOLVER_API.md` - Resolver API 参考
- `ksp-processor/KSP_CODEGENERATOR_API.md` - CodeGenerator API 参考
- `ksp-processor/KSP_SYMBOL_TYPES.md` - 符号类型参考

---

**最后更新**: 2025-10-13
