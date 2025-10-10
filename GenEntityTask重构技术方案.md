# GenEntityTask 重构技术方案

## 一、背景与目标

### 1.1 现状问题

当前 `GenEntityTask` 类存在以下问题：

1. **职责不清**：所有生成逻辑（Entity、Enum、Schema 等）都集中在一个类中，代码量超过 1400 行
2. **扩展困难**：新增模板类型（如 Factory、Specification）需要修改主类，违反开闭原则
3. **依赖混乱**：Entity 依赖 Enum 的上下文缓存（`enumPackageMap`），但依赖关系不明确
4. **难以测试**：各模块耦合紧密，无法进行独立单元测试
5. **复用性差**：上下文构建逻辑分散，导致重复计算

### 1.2 重构目标

1. **解耦**：通过接口和策略模式，将不同模板的生成逻辑分离
2. **可扩展**：新增模板类型无需修改主类，只需实现接口并注册
3. **性能优化**：通过上下文分层和缓存，避免重复计算
4. **清晰的依赖关系**：通过执行顺序（`order`）明确模块间依赖
5. **可测试**：每个生成器可独立测试

---

## 二、架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────┐
│                    GenEntityTask                         │
│  - 注册生成器                                             │
│  - 注册上下文构建器                                        │
│  - 协调执行流程                                           │
└───────────────────┬─────────────────────────────────────┘
                    │
        ┌───────────┴───────────┐
        │                       │
        ▼                       ▼
┌──────────────┐      ┌──────────────────┐
│ContextBuilder│      │TemplateGenerator │
│  (构建上下文) │      │  (生成文件)       │
└──────────────┘      └──────────────────┘
        │                       │
        ▼                       ▼
┌──────────────┐      ┌──────────────────┐
│- TableContext│      │- EnumGenerator   │
│- RelationCtx │      │- EntityGenerator │
│- EnumContext │      │- FactoryGenerator│
└──────────────┘      └──────────────────┘
```

### 2.2 核心接口设计

#### 2.2.1 GenerationContext（生成上下文）

```kotlin
/**
 * 代码生成上下文（只读，共享数据）
 */
interface GenerationContext {
    // === 基础信息 ===
    val extension: CodeGenExtension
    val logger: Logger
    val dbType: String
    val baseDir: String

    // === 表信息 ===
    val tableMap: Map<String, Map<String, Any?>>
    val columnsMap: Map<String, List<Map<String, Any?>>>
    val relations: Map<String, Map<String, String>>
    val tablePackageMap: Map<String, String>

    // === 枚举信息 ===
    val enumConfigMap: Map<String, Map<Int, Array<String>>>
    val enumPackageMap: Map<String, String>  // ← Entity 依赖此字段
    val enumTableNameMap: Map<String, String>

    // === 模板信息 ===
    val templateNodeMap: Map<String, List<TemplateNode>>

    // === 工具方法 ===
    fun resolveEntityType(tableName: String): String
    fun resolveEntityPackage(tableName: String): String
    fun resolveModule(tableName: String): String
    fun resolveAggregate(tableName: String): String
    fun resolveAggregateWithModule(tableName: String): String
    fun resolveAggregatesPackage(): String
    fun resolveIdColumns(columns: List<Map<String, Any?>>): List<Map<String, Any?>>
}
```

#### 2.2.2 MutableGenerationContext（可变上下文）

```kotlin
/**
 * 可变的生成上下文（用于构建阶段）
 */
interface MutableGenerationContext : GenerationContext {
    override val tableMap: MutableMap<String, Map<String, Any?>>
    override val columnsMap: MutableMap<String, List<Map<String, Any?>>>
    override val relations: MutableMap<String, Map<String, String>>
    override val tablePackageMap: MutableMap<String, String>
    override val enumConfigMap: MutableMap<String, Map<Int, Array<String>>>
    override val enumPackageMap: MutableMap<String, String>
    override val enumTableNameMap: MutableMap<String, String>
}
```

#### 2.2.3 ContextBuilder（上下文构建器）

```kotlin
/**
 * 上下文构建器（负责收集和处理数据）
 */
interface ContextBuilder {
    /**
     * 构建顺序（数字越小越先执行）
     */
    val order: Int

    /**
     * 构建上下文信息
     */
    fun build(context: MutableGenerationContext)
}
```

#### 2.2.4 TemplateGenerator（模板生成器）

```kotlin
/**
 * 模板文件生成器
 */
interface TemplateGenerator {
    /**
     * 模板标签（entity, enum, factory 等）
     */
    val tag: String

    /**
     * 执行顺序（数字越小越先执行，用于处理依赖关系）
     */
    val order: Int

    /**
     * 判断是否需要为该表生成此模板
     */
    fun shouldGenerate(table: Map<String, Any?>, context: GenerationContext): Boolean

    /**
     * 构建模板上下文
     * @return 单个上下文 Map 或包含 "items" 键的 Map（用于批量生成）
     */
    fun buildContext(table: Map<String, Any?>, context: GenerationContext): Map<String, Any?>

    /**
     * 获取默认模板节点
     */
    fun getDefaultTemplateNode(): TemplateNode

    /**
     * 生成文件后的回调（可选，用于收集信息或日志）
     */
    fun onGenerated(table: Map<String, Any?>, context: GenerationContext) {}
}
```

---

## 三、执行流程

### 3.1 两阶段生成

```
┌─────────────────────────────────────────────────┐
│ 阶段1：构建全局上下文（Context Building Phase）   │
└─────────────────────────────────────────────────┘
                     │
      ┌──────────────┼──────────────┐
      ▼              ▼              ▼
┌──────────┐  ┌──────────┐  ┌──────────┐
│ Table    │  │ Relation │  │ Enum     │
│ Context  │  │ Context  │  │ Context  │
│ (order10)│  │ (order20)│  │ (order30)│
└──────────┘  └──────────┘  └──────────┘
      │              │              │
      └──────────────┼──────────────┘
                     ▼
           GlobalContext (就绪)
                     │
┌─────────────────────────────────────────────────┐
│ 阶段2：生成文件（File Generation Phase）          │
└─────────────────────────────────────────────────┘
                     │
      ┌──────────────┼──────────────┐
      ▼              ▼              ▼
┌──────────┐  ┌──────────┐  ┌──────────┐
│ Enum     │  │ Entity   │  │ Factory  │
│ Generator│  │ Generator│  │ Generator│
│ (order10)│  │ (order20)│  │ (order30)│
└──────────┘  └──────────┘  └──────────┘
```

### 3.2 流程伪代码

```kotlin
fun genEntity() {
    // === 阶段1：构建全局上下文 ===
    val context = buildGenerationContext()

    // === 阶段2：生成文件 ===
    generateFiles(context)
}

fun buildGenerationContext(): GenerationContext {
    // 1. 初始化数据库连接
    initDatabase()

    // 2. 创建可变上下文
    val mutableContext = MutableGenerationContextImpl(...)

    // 3. 按顺序执行所有上下文构建器
    contextBuilders
        .sortedBy { it.order }
        .forEach { it.build(mutableContext) }

    // 4. 返回只读上下文
    return mutableContext
}

fun generateFiles(context: GenerationContext) {
    // 按顺序执行所有生成器（Enum → Entity → Factory）
    generators
        .sortedBy { it.order }
        .forEach { generator ->
            context.tableMap.values.forEach { table ->
                if (generator.shouldGenerate(table, context)) {
                    val ctx = generator.buildContext(table, context)
                    renderTemplate(generator, ctx, context)
                    generator.onGenerated(table, context)
                }
            }
        }
}
```

---

## 四、具体实现

### 4.1 上下文构建器实现

#### 4.1.1 TableContextBuilder（表信息收集）

```kotlin
/**
 * 表和列信息构建器
 */
class TableContextBuilder : ContextBuilder {
    override val order = 10

    override fun build(context: MutableGenerationContext) {
        // 1. 查询数据库获取表和列
        val tables = SqlSchemaUtils.resolveTables(
            context.extension.database.url.get(),
            context.extension.database.username.get(),
            context.extension.database.password.get()
        )

        val allColumns = SqlSchemaUtils.resolveColumns(...)

        // 2. 分组和排序
        tables.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)
            val columns = allColumns
                .filter { SqlSchemaUtils.isColumnInTable(it, table) }
                .sortedBy { SqlSchemaUtils.getOrdinalPosition(it) }

            context.tableMap[tableName] = table
            context.columnsMap[tableName] = columns
        }

        // 3. 日志输出
        logTableInfo(tables, context)
    }
}
```

#### 4.1.2 RelationContextBuilder（关系处理）

```kotlin
/**
 * 表关系构建器
 */
class RelationContextBuilder : ContextBuilder {
    override val order = 20  // 依赖 TableContextBuilder

    override fun build(context: MutableGenerationContext) {
        context.tableMap.values.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)
            val columns = context.columnsMap[tableName]!!

            // 1. 解析表关系（OneToMany, ManyToOne, ManyToMany）
            val relationTable = resolveRelationTable(table, columns, context)

            // 2. 合并到全局关系 Map
            relationTable.forEach { (key, value) ->
                context.relations.merge(key, value) { existing, new ->
                    existing.toMutableMap().apply { putAll(new) }
                }
            }

            // 3. 计算包路径
            context.tablePackageMap[tableName] = resolveEntityFullPackage(
                table,
                context.extension.basePackage.get(),
                context.baseDir
            )
        }
    }
}
```

#### 4.1.3 EnumContextBuilder（枚举信息收集）

```kotlin
/**
 * 枚举配置构建器
 */
class EnumContextBuilder : ContextBuilder {
    override val order = 30  // 依赖 TableContextBuilder

    override fun build(context: MutableGenerationContext) {
        // 1. 收集枚举配置
        context.tableMap.values.forEach { table ->
            if (SqlSchemaUtils.isIgnore(table)) return@forEach

            val tableName = SqlSchemaUtils.getTableName(table)
            val columns = context.columnsMap[tableName]!!

            columns.forEach { column ->
                if (SqlSchemaUtils.hasEnum(column) && !SqlSchemaUtils.isIgnore(column)) {
                    val enumType = SqlSchemaUtils.getType(column)
                    val enumConfig = SqlSchemaUtils.getEnum(column)

                    context.enumConfigMap[enumType] = enumConfig
                    context.enumTableNameMap[enumType] = tableName
                }
            }
        }

        // 2. 预填充 enumPackageMap（关键步骤！Entity 依赖此数据）
        context.enumConfigMap.keys.forEach { enumType ->
            val tableName = context.enumTableNameMap[enumType] ?: return@forEach
            val enumPackage = resolveEnumPackage(enumType, tableName, context)
            context.enumPackageMap[enumType] = enumPackage
        }
    }

    private fun resolveEnumPackage(
        enumType: String,
        tableName: String,
        context: GenerationContext
    ): String {
        val basePackage = context.extension.basePackage.get()
        val entityPackage = context.resolveEntityPackage(tableName)
        val enumSubPackage = context.templateNodeMap["enum"]
            ?.firstOrNull()?.name
            ?.takeIf { it.isNotBlank() }
            ?: "enums"

        return "$basePackage.$entityPackage.$enumSubPackage"
    }
}
```

### 4.2 模板生成器实现

#### 4.2.1 EnumGenerator（枚举生成）

```kotlin
/**
 * 枚举文件生成器
 */
class EnumGenerator(
    private val task: GenArchTask
) : TemplateGenerator {

    override val tag = "enum"
    override val order = 10  // 优先级最高（Entity 依赖 Enum）

    // 已生成的枚举集合（避免重复生成）
    private val generatedEnums = mutableSetOf<String>()

    override fun shouldGenerate(table: Map<String, Any?>, context: GenerationContext): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false

        val tableName = SqlSchemaUtils.getTableName(table)
        val columns = context.columnsMap[tableName] ?: return false

        // 检查该表是否有需要生成的枚举
        return columns.any { column ->
            SqlSchemaUtils.hasEnum(column) &&
            !SqlSchemaUtils.isIgnore(column)
        }
    }

    override fun buildContext(table: Map<String, Any?>, context: GenerationContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val columns = context.columnsMap[tableName]!!

        // 收集该表的所有枚举上下文
        val enumContexts = mutableListOf<Map<String, Any?>>()

        columns.forEach { column ->
            if (SqlSchemaUtils.hasEnum(column) && !SqlSchemaUtils.isIgnore(column)) {
                val enumType = SqlSchemaUtils.getType(column)

                // 避免重复生成
                if (generatedEnums.add(enumType)) {
                    val enumConfig = context.enumConfigMap[enumType]!!
                    enumContexts.add(buildSingleEnumContext(enumType, enumConfig, tableName, context))
                }
            }
        }

        // 返回批量上下文
        return mapOf("items" to enumContexts)
    }

    private fun buildSingleEnumContext(
        enumType: String,
        enumConfig: Map<Int, Array<String>>,
        tableName: String,
        context: GenerationContext
    ): Map<String, Any?> {
        val aggregate = context.resolveAggregateWithModule(tableName)
        val entityType = context.resolveEntityType(tableName)
        val entityPackage = context.tablePackageMap[tableName]!!

        // 枚举项列表
        val enumItems = enumConfig.toSortedMap().map { (value, arr) ->
            mapOf(
                "value" to value,
                "name" to arr[0],
                "desc" to arr[1]
            )
        }

        return buildMap {
            put("DEFAULT_ENUM_PACKAGE", GenEntityTask.DEFAULT_ENUM_PACKAGE)
            put("enum.templatePackage", refPackage(context.resolveAggregatesPackage()))
            put("enum.package", refPackage(aggregate))
            put("enum.path", aggregate.replace(".", File.separator))
            put("enum.Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
            put("enum.entityPackage", refPackage(entityPackage, context.extension.basePackage.get()))
            put("enum.Entity", entityType)
            put("enum.Enum", enumType)
            put("enum.EnumItems", enumItems)
            put("enum.EnumValueField", context.extension.generation.enumValueField.get())
            put("enum.EnumNameField", context.extension.generation.enumNameField.get())
        }
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = "enum"
            name = "{{ enum.path }}{{ SEPARATOR }}{{ DEFAULT_ENUM_PACKAGE }}{{ SEPARATOR }}{{ enum.Enum }}.kt"
            format = "resouce"
            data = "enum"
            conflict = "overwrite"
        }
    }

    override fun onGenerated(table: Map<String, Any?>, context: GenerationContext) {
        context.logger.info("已生成枚举文件：${SqlSchemaUtils.getTableName(table)}")
    }
}
```

#### 4.2.2 EntityGenerator（实体生成）

```kotlin
/**
 * 实体文件生成器
 */
class EntityGenerator(
    private val task: GenArchTask
) : TemplateGenerator {

    override val tag = "entity"
    override val order = 20  // 在 Enum 之后执行

    override fun shouldGenerate(table: Map<String, Any?>, context: GenerationContext): Boolean {
        // 1. 忽略标记的表
        if (SqlSchemaUtils.isIgnore(table)) return false

        // 2. 关系表不生成实体
        if (SqlSchemaUtils.hasRelation(table)) return false

        // 3. 必须有主键
        val tableName = SqlSchemaUtils.getTableName(table)
        val columns = context.columnsMap[tableName]!!
        val ids = context.resolveIdColumns(columns)

        return ids.isNotEmpty()
    }

    override fun buildContext(table: Map<String, Any?>, context: GenerationContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val columns = context.columnsMap[tableName]!!

        return EntityContextBuilder(task, context).build(
            table,
            columns,
            context.tablePackageMap,
            context.relations,
            context.baseDir
        )
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = "entity"
            name = "{{ entity.Entity }}.kt"
            format = "resouce"
            data = "entity"
            conflict = "overwrite"
        }
    }

    override fun onGenerated(table: Map<String, Any?>, context: GenerationContext) {
        context.logger.info("已生成实体文件：${SqlSchemaUtils.getTableName(table)}")
    }
}

/**
 * Entity 上下文构建器（复杂逻辑独立封装）
 */
class EntityContextBuilder(
    private val task: GenArchTask,
    private val globalContext: GenerationContext
) {
    fun build(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        tablePackageMap: Map<String, String>,
        relations: Map<String, Map<String, String>>,
        baseDir: String
    ): Map<String, Any?> {
        val tag = "entity"
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = globalContext.resolveEntityType(tableName)
        val entityFullPackage = tablePackageMap[tableName] ?: ""
        val ids = globalContext.resolveIdColumns(columns)

        // 1. 计算基类和接口
        val identityType = if (ids.size != 1) "Long" else SqlSchemaUtils.getColumnType(ids[0])
        val baseClass = resolveBaseClass(table, entityType, identityType)
        val extendsClause = if (baseClass?.isNotBlank() == true) " : $baseClass()" else ""
        val implementsClause = if (SqlSchemaUtils.isValueObject(table)) ", ValueObject<$identityType>" else ""

        // 2. 处理现有文件的自定义内容
        val importLines = mutableListOf<String>()
        val annotationLines = mutableListOf<String>()
        val customerLines = mutableListOf<String>()
        val enums = mutableListOf<String>()

        val filePath = task.resolveSourceFile(baseDir, entityFullPackage, entityType)
        task.processEntityCustomerSourceFile(filePath, importLines, annotationLines, customerLines)
        task.processAnnotationLines(table, columns, annotationLines)

        // 3. 准备列数据
        val columnDataList = columns.map { column ->
            prepareColumnData(table, column, ids, relations, enums)
        }.filter { it["needGenerate"] == true }

        // 4. 准备关系数据
        val relationDataList = prepareRelationData(table, relations, tablePackageMap)

        // 5. 准备 imports
        val entityClassExtraImports = getEntityClassExtraImports(table)

        // 6. 准备注释行
        val commentLines = prepareCommentLines(table)

        // 7. 构建上下文
        val fullPackage = globalContext.resolveEntityPackage(tableName)
        val aggregatesPackage = globalContext.resolveAggregatesPackage()
        val relativePackage = calculateRelativePackage(fullPackage, aggregatesPackage)

        return getEscapeContext().toMutableMap().apply {
            putContext(tag, "templatePackage", ".$aggregatesPackage")
            putContext(tag, "package", relativePackage)
            putContext(tag, "path", fullPackage.replace(".", File.separator))
            putContext(tag, "Entity", entityType)
            putContext(tag, "entityType", entityType)
            putContext(tag, "extendsClause", extendsClause)
            putContext(tag, "implementsClause", implementsClause)
            putContext(tag, "columns", columnDataList)
            putContext(tag, "relations", relationDataList)
            putContext(tag, "annotationLines", annotationLines)
            putContext(tag, "customerLines", customerLines)
            putContext(tag, "imports", entityClassExtraImports)
            putContext(tag, "commentLines", commentLines)
        }
    }

    // ... 辅助方法
}
```

#### 4.2.3 FactoryGenerator（工厂生成，示例）

```kotlin
/**
 * 工厂类生成器（示例）
 */
class FactoryGenerator(
    private val task: GenArchTask
) : TemplateGenerator {

    override val tag = "factory"
    override val order = 30  // 在 Entity 之后执行

    override fun shouldGenerate(table: Map<String, Any?>, context: GenerationContext): Boolean {
        // 只为聚合根生成工厂
        return SqlSchemaUtils.isAggregateRoot(table) &&
               !SqlSchemaUtils.isIgnore(table)
    }

    override fun buildContext(table: Map<String, Any?>, context: GenerationContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = context.resolveEntityType(tableName)
        val entityPackage = context.tablePackageMap[tableName]!!
        val columns = context.columnsMap[tableName]!!

        // 构造参数列表（可以安全使用 enumPackageMap）
        val constructorParams = columns
            .filter { !SqlSchemaUtils.isIgnore(it) }
            .map { column ->
                mapOf(
                    "name" to toLowerCamelCase(SqlSchemaUtils.getColumnName(column)),
                    "type" to SqlSchemaUtils.getColumnType(column, context) // ← 使用 enumPackageMap
                )
            }

        return buildMap {
            put("factory.templatePackage", context.resolveAggregatesPackage())
            put("factory.package", context.resolveEntityPackage(tableName))
            put("factory.entityPackage", entityPackage)
            put("factory.Entity", entityType)
            put("factory.constructorParams", constructorParams)
        }
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = "factory"
            name = "{{ factory.DEFAULT_FAC_PACKAGE }}{{ SEPARATOR }}{{ factory.Entity }}Factory.kt"
            format = "resouce"
            data = "factory"
            conflict = "overwrite"
        }
    }
}
```

### 4.3 主类重构

```kotlin
open class GenEntityTask : GenArchTask() {

    companion object {
        const val DEFAULT_SCHEMA_PACKAGE = "meta"
        const val DEFAULT_SPEC_PACKAGE = "specs"
        const val DEFAULT_FAC_PACKAGE = "factory"
        const val DEFAULT_ENUM_PACKAGE = "enums"
        const val DEFAULT_DOMAIN_EVENT_PACKAGE = "events"
        const val DEFAULT_SCHEMA_BASE_CLASS_NAME = "Schema"
    }

    // === 缓存数据（仅供工具方法使用）===
    @Internal
    val tableMap = mutableMapOf<String, Map<String, Any?>>()

    @Internal
    val columnsMap = mutableMapOf<String, List<Map<String, Any?>>>()

    @Internal
    val relations = mutableMapOf<String, Map<String, String>>()

    @Internal
    val tablePackageMap = mutableMapOf<String, String>()

    @Internal
    val enumConfigMap = mutableMapOf<String, Map<Int, Array<String>>>()

    @Internal
    val enumPackageMap = mutableMapOf<String, String>()

    @Internal
    val enumTableNameMap = mutableMapOf<String, String>()

    @Internal
    val templateNodeMap = mutableMapOf<String, MutableList<TemplateNode>>()

    @Internal
    var dbType = "mysql"

    // === 注册上下文构建器 ===
    private val contextBuilders: List<ContextBuilder> by lazy {
        listOf(
            TableContextBuilder(),
            RelationContextBuilder(),
            EnumContextBuilder(),
        )
    }

    // === 注册模板生成器 ===
    private val generators: List<TemplateGenerator> by lazy {
        listOf(
            EnumGenerator(this),
            EntityGenerator(this),
            FactoryGenerator(this),
            // 未来扩展：
            // SpecificationGenerator(this),
            // DomainEventGenerator(this),
        )
    }

    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()
        genEntity()
    }

    /**
     * 主入口
     */
    fun genEntity() {
        SqlSchemaUtils.task = this

        // === 阶段1：构建全局上下文 ===
        val context = buildGenerationContext()

        if (context.tableMap.isEmpty()) {
            logger.warn("No tables found in database")
            return
        }

        // === 阶段2：生成文件 ===
        generateFiles(context)
    }

    /**
     * 阶段1：构建全局上下文
     */
    private fun buildGenerationContext(): GenerationContext {
        // 1. 初始化数据库
        val (url, _, _) = resolveDatabaseConfig()
        dbType = SqlSchemaUtils.recognizeDbType(url)
        SqlSchemaUtils.processSqlDialect(dbType)
        SqlSchemaUtils.loadLogger(logger)

        // 2. 创建可变上下文
        val mutableContext = MutableGenerationContextImpl(
            extension = extension.get(),
            logger = logger,
            dbType = dbType,
            baseDir = getDomainModulePath(),
            task = this
        )

        // 3. 按顺序执行所有上下文构建器
        contextBuilders
            .sortedBy { it.order }
            .forEach { builder ->
                logger.lifecycle("Building context: ${builder.javaClass.simpleName}")
                builder.build(mutableContext)
            }

        return mutableContext
    }

    /**
     * 阶段2：生成文件
     */
    private fun generateFiles(context: GenerationContext) {
        // 按顺序执行所有生成器（Enum → Entity → Factory）
        generators
            .sortedBy { it.order }
            .forEach { generator ->
                logger.lifecycle("Generating files: ${generator.tag}")
                generateForTables(generator, context)
            }
    }

    /**
     * 为表生成文件（统一逻辑）
     */
    private fun generateForTables(generator: TemplateGenerator, context: GenerationContext) {
        context.tableMap.values.forEach { table ->
            if (generator.shouldGenerate(table, context)) {
                try {
                    val ctx = generator.buildContext(table, context)

                    // 检查是否为批量上下文
                    val items = ctx["items"] as? List<Map<String, Any?>>
                    if (items != null) {
                        // 批量渲染（用于 Enum）
                        items.forEach { itemCtx ->
                            renderTemplate(generator, itemCtx, context)
                        }
                    } else {
                        // 单个渲染
                        renderTemplate(generator, ctx, context)
                    }

                    generator.onGenerated(table, context)
                } catch (e: Exception) {
                    logger.error("Failed to generate ${generator.tag} for table: ${SqlSchemaUtils.getTableName(table)}", e)
                }
            }
        }
    }

    /**
     * 渲染模板（统一入口）
     */
    private fun renderTemplate(
        generator: TemplateGenerator,
        context: Map<String, Any?>,
        globalContext: GenerationContext
    ) {
        val templateNodes = globalContext.templateNodeMap[generator.tag]
            ?: listOf(generator.getDefaultTemplateNode())

        templateNodes.forEach { templateNode ->
            val pathNode = templateNode.deepCopy().resolve(context)
            val packagePath = context["${generator.tag}.templatePackage"]?.toString()
                ?: context["templatePackage"]?.toString()
                ?: ""

            forceRender(
                pathNode,
                resolvePackageDirectory(
                    globalContext.baseDir,
                    concatPackage(
                        globalContext.extension.basePackage.get(),
                        packagePath
                    )
                )
            )
        }
    }

    // === 工具方法（保留，供生成器使用）===
    fun resolveEntityType(tableName: String): String { ... }
    fun resolveModule(tableName: String): String { ... }
    fun resolveAggregate(tableName: String): String { ... }
    fun resolveAggregateWithModule(tableName: String): String { ... }
    fun resolveAggregatesPackage(): String { ... }
    fun resolveEntityPackage(tableName: String): String { ... }
    fun resolveIdColumns(columns: List<Map<String, Any?>>): List<Map<String, Any?>> { ... }
    // ...
}
```

---

## 五、关键技术点

### 5.1 依赖关系处理

**问题**：Entity 生成依赖 Enum 的 `enumPackageMap`

**解决方案**：
1. **构建阶段**：`EnumContextBuilder` (order=30) 预填充 `enumPackageMap`
2. **生成阶段**：`EnumGenerator` (order=10) 先执行，`EntityGenerator` (order=20) 后执行

```kotlin
// EnumContextBuilder（构建阶段）
override fun build(context: MutableGenerationContext) {
    // 1. 收集枚举配置
    context.enumConfigMap[enumType] = enumConfig

    // 2. 预填充 enumPackageMap（关键！）
    context.enumPackageMap[enumType] = resolveEnumPackage(...)
}

// EntityGenerator 使用时（生成阶段）
fun getColumnType(column: Map<String, Any?>, context: GenerationContext): String {
    val enumType = SqlSchemaUtils.getType(column)
    if (context.enumPackageMap.containsKey(enumType)) {
        return "${context.enumPackageMap[enumType]}.$enumType"
    }
    // ...
}
```

### 5.2 避免重复生成

**问题**：同一个枚举可能在多个表中使用

**解决方案**：使用 `Set` 记录已生成的枚举

```kotlin
class EnumGenerator : TemplateGenerator {
    private val generatedEnums = mutableSetOf<String>()

    override fun buildContext(table: Map<String, Any?>, context: GenerationContext): Map<String, Any?> {
        columns.forEach { column ->
            val enumType = SqlSchemaUtils.getType(column)

            // 只生成未生成过的枚举
            if (generatedEnums.add(enumType)) {
                enumContexts.add(buildSingleEnumContext(...))
            }
        }
    }
}
```

### 5.3 批量上下文处理

**问题**：一个表可能包含多个枚举

**解决方案**：返回 `items` 列表

```kotlin
override fun buildContext(table: Map<String, Any?>, context: GenerationContext): Map<String, Any?> {
    val enumContexts = mutableListOf<Map<String, Any?>>()
    // 收集多个枚举上下文...
    return mapOf("items" to enumContexts)
}

// 主类处理
val items = ctx["items"] as? List<Map<String, Any?>>
if (items != null) {
    items.forEach { itemCtx ->
        renderTemplate(generator, itemCtx, context)
    }
}
```

### 5.4 上下文命名规范

为避免冲突，建议使用 `tag.key` 格式：

```kotlin
context.apply {
    put("entity.Entity", entityType)
    put("entity.package", packageName)
    put("factory.Entity", entityType)
    put("factory.package", packageName)
}
```

### 5.5 性能优化

**优化点**：
1. **避免重复计算**：`enumPackageMap` 在构建阶段一次性填充
2. **懒加载**：使用 `by lazy` 初始化生成器列表
3. **并行潜力**：未来可以在生成阶段使用协程并行处理不同表

---

## 六、扩展示例

### 6.1 新增 Specification 生成器

```kotlin
/**
 * 规格模式生成器
 */
class SpecificationGenerator(
    private val task: GenArchTask
) : TemplateGenerator {

    override val tag = "specification"
    override val order = 40

    override fun shouldGenerate(table: Map<String, Any?>, context: GenerationContext): Boolean {
        // 只为聚合根生成规格
        return SqlSchemaUtils.isAggregateRoot(table) && !SqlSchemaUtils.isIgnore(table)
    }

    override fun buildContext(table: Map<String, Any?>, context: GenerationContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = context.resolveEntityType(tableName)

        return buildMap {
            put("specification.Entity", entityType)
            put("specification.package", context.resolveEntityPackage(tableName))
            // ...
        }
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = "specification"
            name = "specs{{ SEPARATOR }}{{ specification.Entity }}Specification.kt"
            format = "resouce"
            data = "specification"
            conflict = "overwrite"
        }
    }
}

// 注册到主类
private val generators: List<TemplateGenerator> by lazy {
    listOf(
        EnumGenerator(this),
        EntityGenerator(this),
        FactoryGenerator(this),
        SpecificationGenerator(this), // ← 新增
    )
}
```

### 6.2 新增自定义 ContextBuilder

```kotlin
/**
 * 索引信息构建器（示例）
 */
class IndexContextBuilder : ContextBuilder {
    override val order = 40  // 在基础构建器之后

    override fun build(context: MutableGenerationContext) {
        val indexMap = mutableMapOf<String, List<IndexInfo>>()

        context.tableMap.values.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)
            val indexes = SqlSchemaUtils.resolveIndexes(tableName, ...)
            indexMap[tableName] = indexes
        }

        // 存储到上下文（需要扩展 GenerationContext 接口）
        (context as CustomMutableContext).indexMap.putAll(indexMap)
    }
}
```

---

## 七、迁移计划

### 7.1 迁移步骤

#### 阶段1：接口定义（1天）
- [ ] 定义 `GenerationContext` 接口
- [ ] 定义 `MutableGenerationContext` 接口
- [ ] 定义 `ContextBuilder` 接口
- [ ] 定义 `TemplateGenerator` 接口

#### 阶段2：上下文构建器实现（2天）
- [ ] 实现 `TableContextBuilder`
- [ ] 实现 `RelationContextBuilder`
- [ ] 实现 `EnumContextBuilder`
- [ ] 实现 `MutableGenerationContextImpl`

#### 阶段3：生成器实现（3天）
- [ ] 实现 `EnumGenerator`
- [ ] 实现 `EntityGenerator`
- [ ] 重构 `EntityContextBuilder`（提取复杂逻辑）

#### 阶段4：主类重构（2天）
- [ ] 重构 `genEntity()` 方法
- [ ] 实现 `buildGenerationContext()`
- [ ] 实现 `generateFiles()`
- [ ] 实现 `renderTemplate()`

#### 阶段5：测试与验证（2天）
- [ ] 单元测试各个生成器
- [ ] 集成测试完整流程
- [ ] 性能对比测试
- [ ] 回归测试（确保生成结果一致）

#### 阶段6：扩展示例（1天）
- [ ] 实现 `FactoryGenerator`
- [ ] 编写扩展文档

### 7.2 兼容性保证

**保持向后兼容**：
- 保留所有 `@Internal` 缓存字段（供工具方法使用）
- 保留所有公开方法（供外部调用）
- 生成的代码结构保持不变

**测试策略**：
```kotlin
@Test
fun `test generated entity is same as before`() {
    val oldTask = OldGenEntityTask()
    val newTask = NewGenEntityTask()

    val oldResult = oldTask.generate()
    val newResult = newTask.generate()

    assertEquals(oldResult, newResult)
}
```

---

## 八、测试策略

### 8.1 单元测试

```kotlin
class EnumGeneratorTest {

    @Test
    fun `should generate enum for table with enum column`() {
        // Arrange
        val mockContext = mockGenerationContext()
        val table = mapOf("TABLE_NAME" to "user")
        val generator = EnumGenerator(mockTask)

        // Act
        val shouldGenerate = generator.shouldGenerate(table, mockContext)

        // Assert
        assertTrue(shouldGenerate)
    }

    @Test
    fun `should not generate duplicate enums`() {
        // Arrange
        val generator = EnumGenerator(mockTask)
        val context = mockGenerationContext()
        val table1 = mapOf("TABLE_NAME" to "user")
        val table2 = mapOf("TABLE_NAME" to "order")

        // Act
        val ctx1 = generator.buildContext(table1, context)
        val ctx2 = generator.buildContext(table2, context)

        // Assert
        val items1 = ctx1["items"] as List<*>
        val items2 = ctx2["items"] as List<*>
        assertEquals(1, items1.size) // user 表有 1 个枚举
        assertEquals(0, items2.size) // order 表的枚举已被 user 表生成
    }
}
```

### 8.2 集成测试

```kotlin
class GenEntityTaskIntegrationTest {

    @Test
    fun `should generate all files in correct order`() {
        // Arrange
        val task = GenEntityTask()
        task.extension.set(mockExtension())

        // Act
        task.generate()

        // Assert
        assertTrue(File("path/to/enum/UserStatus.kt").exists())
        assertTrue(File("path/to/entity/User.kt").exists())
        assertTrue(File("path/to/factory/UserFactory.kt").exists())

        // 验证依赖关系
        val entityContent = File("path/to/entity/User.kt").readText()
        assertTrue(entityContent.contains("com.example.enums.UserStatus"))
    }
}
```

### 8.3 性能测试

```kotlin
@Test
fun `performance should not degrade`() {
    val task = GenEntityTask()

    val startTime = System.currentTimeMillis()
    task.generate()
    val duration = System.currentTimeMillis() - startTime

    // 性能不应下降超过 10%
    assertTrue(duration < BASELINE_DURATION * 1.1)
}
```

---

## 九、风险与应对

### 9.1 风险点

| 风险 | 影响 | 概率 | 应对措施 |
|------|------|------|----------|
| 生成结果不一致 | 高 | 中 | 完善回归测试，逐步迁移 |
| 性能下降 | 中 | 低 | 性能对比测试，优化上下文构建 |
| 扩展点设计不合理 | 中 | 中 | 先实现 2-3 个生成器验证设计 |
| 依赖关系复杂化 | 低 | 低 | 使用 order 字段明确顺序 |

### 9.2 回滚计划

- 保留原有代码分支
- 使用功能开关控制新旧实现
- 提供降级脚本

```kotlin
val useNewImplementation = System.getProperty("codegen.useNewImpl", "false").toBoolean()

if (useNewImplementation) {
    genEntityNew()
} else {
    genEntityOld()
}
```

---

## 十、总结

### 10.1 架构优势

1. **解耦清晰**：通过接口分离关注点，各模块独立开发和测试
2. **易扩展**：新增模板只需实现接口并注册，无需修改主类
3. **依赖明确**：通过 `order` 字段和两阶段设计，明确依赖关系
4. **性能优化**：上下文一次构建，多次使用，避免重复计算
5. **可测试**：每个生成器可独立测试，提高代码质量

### 10.2 关键设计

- **GenerationContext**：共享的只读上下文，避免数据污染
- **ContextBuilder**：职责单一，只负责收集数据
- **TemplateGenerator**：职责单一，只负责生成文件
- **两阶段执行**：构建阶段 + 生成阶段，清晰分离
- **order 字段**：明确执行顺序，处理依赖关系

### 10.3 后续改进

1. **并行化**：在生成阶段使用协程并行处理不同表
2. **插件化**：支持外部注册自定义生成器
3. **增量生成**：只生成变更的表
4. **可视化**：生成依赖关系图，便于理解

---

## 附录

### A. 完整类图

```
┌─────────────────────────────────────┐
│         GenerationContext           │
│  (interface)                        │
├─────────────────────────────────────┤
│ + tableMap: Map                     │
│ + columnsMap: Map                   │
│ + enumPackageMap: Map               │
│ + resolveEntityType(): String       │
└─────────────────────────────────────┘
              △
              │ implements
              │
┌─────────────────────────────────────┐
│    MutableGenerationContext         │
│  (interface)                        │
├─────────────────────────────────────┤
│ + tableMap: MutableMap              │
│ + columnsMap: MutableMap            │
└─────────────────────────────────────┘
              △
              │ implements
              │
┌─────────────────────────────────────┐
│  MutableGenerationContextImpl       │
│  (class)                            │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│       ContextBuilder                │
│  (interface)                        │
├─────────────────────────────────────┤
│ + order: Int                        │
│ + build(context)                    │
└─────────────────────────────────────┘
              △
              │ implements
      ┌───────┼───────┐
      │       │       │
┌─────────┐ ┌─────────┐ ┌─────────┐
│ Table   │ │Relation │ │  Enum   │
│ Context │ │ Context │ │ Context │
│ Builder │ │ Builder │ │ Builder │
└─────────┘ └─────────┘ └─────────┘

┌─────────────────────────────────────┐
│      TemplateGenerator              │
│  (interface)                        │
├─────────────────────────────────────┤
│ + tag: String                       │
│ + order: Int                        │
│ + shouldGenerate(): Boolean         │
│ + buildContext(): Map               │
│ + getDefaultTemplateNode(): Node   │
└─────────────────────────────────────┘
              △
              │ implements
      ┌───────┼────────┐
      │       │        │
┌─────────┐ ┌─────────┐ ┌─────────┐
│  Enum   │ │ Entity  │ │Factory  │
│Generator│ │Generator│ │Generator│
└─────────┘ └─────────┘ └─────────┘

┌─────────────────────────────────────┐
│         GenEntityTask               │
│  (class)                            │
├─────────────────────────────────────┤
│ - contextBuilders: List             │
│ - generators: List                  │
│ + genEntity()                       │
│ - buildGenerationContext()          │
│ - generateFiles()                   │
└─────────────────────────────────────┘
```

### B. 目录结构

```
com.only.codegen/
├── GenEntityTask.kt                    // 主类
├── context/
│   ├── GenerationContext.kt            // 上下文接口
│   ├── MutableGenerationContext.kt     // 可变上下文接口
│   ├── MutableGenerationContextImpl.kt // 上下文实现
│   └── builders/
│       ├── ContextBuilder.kt           // 构建器接口
│       ├── TableContextBuilder.kt      // 表信息构建器
│       ├── RelationContextBuilder.kt   // 关系构建器
│       └── EnumContextBuilder.kt       // 枚举构建器
└── generators/
    ├── TemplateGenerator.kt            // 生成器接口
    ├── EnumGenerator.kt                // 枚举生成器
    ├── EntityGenerator.kt              // 实体生成器
    ├── FactoryGenerator.kt             // 工厂生成器
    └── builders/
        └── EntityContextBuilder.kt     // Entity 上下文构建器
```

---

**文档版本**：v1.0
**编写日期**：2025-01-10
**状态**：待评审
