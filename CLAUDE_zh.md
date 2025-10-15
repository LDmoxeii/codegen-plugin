# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指导。

## 项目概述

这是一个从数据库模式生成代码的 Gradle 插件。它使用 Pebble 模板生成 Kotlin 领域实体、枚举和其他 DDD（领域驱动设计）工件。该插件支持 MySQL 和 PostgreSQL 数据库。

**插件 ID**: `com.only4.codegen`
**代码包名**: `com.only4.codegen`
**Maven GroupId**: `com.only4`

## 构建和测试命令

### 构建插件
```bash
./gradlew build
```

### 运行测试
```bash
./gradlew test
```

### 发布到本地 Maven 仓库（用于在其他项目中测试）
```bash
./gradlew publishToMavenLocal
```

### 清理构建产物
```bash
./gradlew clean
```

## 核心架构

### 可扩展插件框架

该插件采用**高度可扩展的架构**，允许通过组合以下要素创建新的代码生成管道：
- **不同的数据源**（数据库元数据、KSP 注解、设计文件等）
- **自定义上下文类型**（EntityContext、AnnotationContext 或用户自定义）
- **专门的构建器和生成器**（扩展通用基础接口）

这种设计支持以下场景：
- 数据库驱动的领域生成（当前：`GenAggregateTask`）
- 设计文件驱动的应用层生成（当前：`GenDesignTask`）
- **KSP 元数据 + 模板 + 自定义设计上下文**（未来扩展性）

### 两阶段执行模型

所有生成任务遵循一致的两阶段模式：

1. **阶段 1：上下文构建** - 从数据源收集元数据到类型化上下文中
   - 上下文构建器实现带有泛型类型参数的 `ContextBuilder<T>` 接口
   - 构建器按顺序运行（由 `order` 属性控制）
   - 每个构建器填充上下文中的特定 Map（如 `tableMap`、`classMap`、`aggregateMap`）
   - 在生成开始前解决所有依赖关系

2. **阶段 2：文件生成** - 使用构建好的上下文生成文件
   - 生成器实现生成器接口（如 `AggregateTemplateGenerator`、`DesignTemplateGenerator`）
   - 生成器根据 `order` 属性按顺序运行
   - 每个生成器实现 `shouldGenerate()`、`buildContext()` 和 `getDefaultTemplateNodes()`
   - 使用 Pebble 模板引擎渲染模板
   - 生成的类型缓存在 `typeMapping` 中以便交叉引用

### 核心接口

插件使用**通用、可组合的接口**，支持不同的代码生成管道：

**上下文层** (`com.only4.codegen.context`):
- `BaseContext` - 基础配置和模板别名系统（所有上下文共享）
- `AggregateContext : BaseContext` - 数据库驱动生成上下文（表、列、关系、枚举）
- `DesignContext : BaseContext` - 设计文件驱动生成上下文（KSP 元数据、设计元素）
- `MutableAggregateContext` - AggregateContext 的可变版本，用于上下文构建阶段
- `MutableDesignContext` - DesignContext 的可变版本，用于上下文构建阶段

**构建器层** (`com.only4.codegen.context`)：
- `ContextBuilder<T>` - **泛型基础接口**，带有类型参数 `T`
  - 定义 `order: Int` 属性用于执行排序
  - 定义 `build(context: T)` 方法用于填充上下文数据
  - **支持为不同上下文类型进行类型安全的构建器组合**

**聚合上下文构建器** (`com.only4.codegen.context.aggregate.builders`) - 用于数据库驱动生成：
- 构建器按顺序执行：
  - `TableContextBuilder` (order=10) - 从数据库收集表和列元数据
  - `EntityTypeContextBuilder` (order=20) - 从表名确定实体类名
  - `AnnotationContextBuilder` (order=20) - 处理表/列注解和元数据
  - `ModuleContextBuilder` (order=20) - 如果启用，解析多模块结构
  - `RelationContextBuilder` (order=20) - 分析表之间的外键关系
  - `EnumContextBuilder` (order=20) - 从表注释中提取枚举定义
  - `AggregateContextBuilder` (order=30) - 识别聚合和聚合根
  - `TablePackageContextBuilder` (order=40) - 确定每个表的包结构
- 每个构建器填充 `AggregateContext` 中的特定 Map

**设计上下文构建器** (`com.only4.codegen.context.design.builders`) - 用于设计文件驱动生成：
- 构建器按顺序执行：
  - `DesignContextBuilder` (order=10) - 从 JSON 文件加载设计元素
  - `KspMetadataContextBuilder` (order=15) - 从 KSP 处理器输出加载聚合元数据
  - `TypeMappingBuilder` (order=18) - 从 KSP 元数据构建类型映射
  - `UnifiedDesignBuilder` (order=20) - 统一解析所有设计类型（命令、查询、事件）

**生成器层**：
- `AggregateTemplateGenerator` (`com.only4.codegen.generators.aggregate`) - 聚合/数据库驱动生成器接口
  - 属性：`tag: String`、`order: Int`
  - 方法：`shouldGenerate()`、`buildContext()`、`getDefaultTemplateNodes()`、`onGenerated()`、`generatorName()`
- `DesignTemplateGenerator` (`com.only4.codegen.generators.design`) - 设计驱动生成器接口
  - 类似结构，但操作 `BaseDesign` 和 `DesignContext`

**聚合生成器** (`com.only4.codegen.generators.aggregate`) - 用于数据库驱动生成：
- `SchemaBaseGenerator` (order=10) - 生成 SchemaBase 基类用于元数据跟踪
- `EnumGenerator` (order=10) - 生成枚举类，跟踪已生成的枚举以避免重复
- `EntityGenerator` (order=20) - 生成具有完整 DDD 支持的实体类，处理自定义代码保留
- `SpecificationGenerator` (order=30) - 生成规约基类用于领域规约
- `FactoryGenerator` (order=30) - 生成工厂类用于聚合根创建
- `DomainEventGenerator` (order=30) - 生成领域事件类用于事件驱动架构
- `DomainEventHandlerGenerator` (order=30) - 生成领域事件处理器/订阅者类
- `RepositoryGenerator` (order=30) - 生成 Repository 接口和适配器
- `AggregateGenerator` (order=40) - 生成聚合封装类用于聚合根管理
- `SchemaGenerator` (order=50) - 生成 Schema 类（类似于 JPA Metamodel）用于类型安全查询

**设计生成器** (`com.only4.codegen.generators.design`) - 用于设计文件驱动生成：
- `CommandGenerator` (order=10) - 生成命令类
- `QueryGenerator` (order=10) - 生成查询类
- `DomainEventGenerator` (order=10) - 生成领域事件类
- `DomainEventHandlerGenerator` (order=20) - 生成领域事件处理器类
- `QueryHandlerGenerator` (order=20) - 生成查询处理器类

### 任务

**任务层次结构** - 所有任务从共同的基类扩展，具有模板渲染能力：

```
AbstractCodegenTask                  # 基类：Pebble 渲染 + 模板别名
    └── GenArchTask                  # 架构脚手架
        ├── GenAggregateTask         # 数据库 → 领域层（实体驱动）
        └── GenDesignTask            # KSP + 设计文件 → 应用/领域层
```

**GenArchTask** - 架构脚手架基础任务
- 位置：`com.only4.codegen.GenArchTask`
- 读取架构模板并创建目录结构
- 提供通用脚手架功能的基类
- 可以扩展用于自定义生成场景
- 实现 `renderTemplate()` 用于模板节点管理

**GenAggregateTask** - 数据库驱动的领域生成
- 位置：`com.only4.codegen.GenAggregateTask`
- 数据源：通过 JDBC 获取数据库元数据
- 上下文：实现 `MutableAggregateContext`
- 工作流：`genEntity()` → `buildGenerationContext()` → `generateFiles()`
- 输出：领域实体、枚举、Schema、规约、工厂、领域事件、Repository、聚合

**GenDesignTask** - 设计文件驱动生成
- 位置：`com.only4.codegen.GenDesignTask`
- 数据源：
  - 来自 `build/generated/ksp/main/resources/metadata` 的 KSP 元数据
  - 来自项目配置的设计 JSON 文件
- 上下文：实现 `MutableDesignContext`
- 工作流：`genDesign()` → `buildDesignContext()` → `generateDesignFiles()`
- 输出：命令、查询、领域事件、事件处理器、查询处理器
- 支持的设计元素类型：
  - **应用层**：Command、Query、Integration Events
  - **领域层**：Domain Events、Domain Event Handlers
- 设计标签别名系统用于元素类型规范化（如 "cmd"、"command"、"commands" → "command"）

### 模板别名系统

插件使用复杂的模板别名系统（`BaseContext.putContext()` 扩展函数），自动将变量映射到多种命名约定：
- 示例：`putContext("entity", "Entity", "User")` 映射到 "Entity"、"entity"、"ENTITY"、"entityType" 等
- 在 `AbstractCodegenTask.templateAliasMap` 中定义（300+ 行映射）
- 允许模板使用任何命名约定

### 数据库支持

**SqlSchemaUtils** - 数据库元数据提取的核心工具
- 位置：`com.only4.codegen.misc.SqlSchemaUtils`
- `SqlSchemaUtils4Mysql` - MySQL 特定实现
- `SqlSchemaUtils4Postgresql` - PostgreSQL 特定实现
- 辅助方法：`isIgnore()`、`hasRelation()`、`hasEnum()`、`getTableName()`、`getType()` 等

## 配置

通过 `build.gradle.kts` 中的 `codegen` 扩展配置插件：

```kotlin
codegen {
    basePackage.set("com.example")
    multiModule.set(true)

    database {
        url.set("jdbc:mysql://localhost:3306/mydb")
        username.set("user")
        password.set("pass")
        schema.set("mydb")
        tables.set("table1,table2")  // 可选过滤器
    }

    generation {
        versionField.set("version")
        deletedField.set("deleted")
        entityBaseClass.set("BaseEntity")
        idGenerator.set("com.example.IdGenerator")
        // ... 更多选项
    }
}
```

## 重要模式

### 类型映射系统

所有生成器都实现 `onGenerated()` 方法，将生成的类全名缓存在 `context.typeMapping` 中：
- 目的：允许后续生成器引用之前生成的类
- 模式：`typeMapping[typeName] = fullQualifiedClassName`
- 示例：
  - `EntityGenerator`：`typeMapping["User"] = "com.example.domain.aggregates.user.User"`
  - `SchemaGenerator`：`typeMapping["SUser"] = "com.example.domain.aggregates.user.SUser"`
  - `FactoryGenerator`：`typeMapping["UserFactory"] = "com.example.domain.aggregates.user.factory.UserFactory"`
  - `SpecificationGenerator`：`typeMapping["UserSpecification"] = "com.example.domain.aggregates.user.specs.UserSpecification"`
  - `AggregateGenerator`：`typeMapping["UserAggregate"] = "com.example.domain.aggregates.user.UserAggregate"`

### EntityGenerator 中的自定义代码保留

`EntityGenerator.processEntityCustomerSourceFile()` 方法保留用户编写的代码：
- 收集注解之前的 import 语句
- 收集类级别注解（使用 `inAnnotationBlock` 标志在遇到 `class` 关键字时停止）
- 保留 "【字段映射开始】" 和 "【字段映射结束】" 标记之外的自定义代码
- **关键**：使用状态机和 `inAnnotationBlock` 避免收集字段级别的注解
- 重新生成的字段放置在标记之间，自定义方法保留在标记之外

## 框架扩展指南

### 创建新的生成管道

要创建新的代码生成管道（例如 KSP 元数据 + 模板 + 自定义设计）：

**步骤 1：定义自定义上下文**
```kotlin
// 1. 定义只读上下文接口
interface MyCustomContext : BaseContext {
    val myDataMap: Map<String, MyData>
    // ... 其他上下文数据
}

// 2. 定义用于构建的可变版本
interface MutableMyCustomContext : MyCustomContext {
    override val myDataMap: MutableMap<String, MyData>
}
```

**步骤 2：创建上下文构建器**
```kotlin
// 使用您的上下文类型实现 ContextBuilder<T>
class MyDataBuilder : ContextBuilder<MutableMyCustomContext> {
    override val order: Int = 10

    override fun build(context: MutableMyCustomContext) {
        // 解析您的数据源（KSP、文件等）
        // 填充 context.myDataMap
    }
}
```

**步骤 3：创建生成器**
```kotlin
// 实现生成器接口
class MyCustomGenerator(private val context: MyCustomContext) : TemplateGenerator {
    override val tag = "mycustom"
    override val order = 20

    override fun shouldGenerate(table: Map<String, Any?>): Boolean {
        // 决策逻辑
    }

    override fun buildContext(table: Map<String, Any?>): MutableMap<String, Any?> {
        // 使用 context.putContext() 构建模板上下文
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        // 定义模板路径和冲突处理
    }

    override fun onGenerated(table: Map<String, Any?>) {
        // 在 typeMapping 中缓存生成的类型
    }
}
```

**步骤 4：创建任务**
```kotlin
open class MyCustomTask : AbstractCodegenTask(), MutableMyCustomContext {
    // 实现上下文属性
    override val myDataMap: MutableMap<String, MyData> = mutableMapOf()

    @TaskAction
    fun generate() {
        // 阶段 1：构建上下文
        val builders = listOf(MyDataBuilder(), /* ... */)
        builders.sortedBy { it.order }.forEach { it.build(this) }

        // 阶段 2：生成文件
        val generators = listOf(MyCustomGenerator(this), /* ... */)
        generators.sortedBy { it.order }.forEach { /* generate */ }
    }
}
```

### 向现有管道添加生成器

**针对聚合生成（AggregateContext）**：

1. 在 `com.only4.codegen.generators.aggregate` 中创建实现 `AggregateTemplateGenerator` 的类
2. 设置 `tag`（例如 "repository"）和 `order`（数值越大，执行越晚）
3. 实现 `shouldGenerate(table, context)` - 如果此表需要此生成器则返回 true
4. 实现 `buildContext(table, context)` - 使用 `context.putContext(tag, key, value)` 准备模板上下文 Map
5. 实现 `getDefaultTemplateNodes()` - 定义默认模板路径和冲突解决方式
6. 实现 `onGenerated(table, context)` - 在 `typeMapping` 中缓存生成的类型全名供其他生成器引用
7. 实现 `generatorName(table, context)` - 返回生成工件的名称
8. 在 `GenAggregateTask.generateFiles()` 中注册，将其添加到 generators 列表

**针对设计生成（DesignContext）**：

1. 在 `com.only4.codegen.generators.design` 中创建实现 `DesignTemplateGenerator` 的类
2. 类似结构，但操作 `BaseDesign` 和 `DesignContext`
3. 在 `GenDesignTask.generateDesignFiles()` 中注册，将其添加到 generators 列表

### 向现有管道添加上下文构建器

**针对聚合上下文**：

1. 在 `com.only4.codegen.context.aggregate.builders` 中创建实现 `ContextBuilder<MutableAggregateContext>` 的类
2. 根据依赖关系设置 `order`（数值越小，执行越早）
3. 实现 `build(context: MutableAggregateContext)` 以填充上下文 Map
4. 在 `GenAggregateTask.buildGenerationContext()` 中注册，将其添加到 contextBuilders 列表

**针对设计上下文**：

1. 在 `com.only4.codegen.context.design.builders` 中创建实现 `ContextBuilder<MutableDesignContext>` 的类
2. 类似结构，但处理设计元数据
3. 在 `GenDesignTask.buildDesignContext()` 中注册，将其添加到 builders 列表

### 导入管理

`ImportManager` 系统（在 `com.only4.codegen.manager` 中）处理自动导入解析：
- `BaseImportManager` - 导入管理的基类
- `EntityImportManager` - 管理实体特定的导入，带冲突检测
- `SchemaImportManager`、`RepositoryImportManager`、`FactoryImportManager` 等 - 针对不同生成器的专用导入管理器
- 自动处理 Java/Kotlin 类型映射和通配符导入
- 在构建需要精确导入控制的复杂上下文时使用

## 关键文件参考

- `CodegenPlugin.kt` - 插件注册和任务设置（注册 genArch、genAggregate、genDesign 任务）
- `CodegenExtension.kt` - 配置 DSL（数据库、生成选项）
- `GenAggregateTask.kt` - 主要的聚合生成编排器，实现 MutableAggregateContext
- `GenDesignTask.kt` - 设计文件驱动生成编排器，实现 MutableDesignContext
- `GenArchTask.kt` - 基础架构脚手架任务
- `AbstractCodegenTask.kt` - 带渲染和模板别名逻辑的基础任务
- `PebbleTemplateRenderer.kt` - Pebble 模板渲染包装器
- 上下文接口 (`context/`)：
  - `BaseContext.kt` - 基础配置，通过 `putContext()` 扩展进行模板别名
  - `aggregate/AggregateContext.kt` - 包含所有聚合生成上下文数据的只读接口
  - `aggregate/MutableAggregateContext.kt` - 上下文构建期间使用的可变版本
  - `design/DesignContext.kt` - 包含所有设计生成上下文数据的只读接口
  - `design/MutableDesignContext.kt` - 上下文构建期间使用的可变版本
- 上下文构建器：
  - `context/aggregate/builders/` - 8 个构建器分阶段填充 AggregateContext
  - `context/design/builders/` - 4 个构建器分阶段填充 DesignContext
- 生成器：
  - `generators/aggregate/` - 10 个生成器从数据库生成领域层文件
  - `generators/design/` - 5 个生成器从设计生成应用/领域层文件
- 导入管理 (`manager/`)：多个专用 ImportManager 类
- SQL 工具 (`misc/`)：`SqlSchemaUtils`、MySQL/PostgreSQL 实现
- 其他工具 (`misc/`)：`NamingUtils`、`Inflector`、`TextUtils`、`ResourceUtils`、`SourceFileUtils`

## 模块结构

```
codegen-plugin/
├── plugin/                           # 主插件模块
│   └── src/main/kotlin/com/only4/codegen/
│       ├── CodegenPlugin.kt         # 插件入口点
│       ├── CodegenExtension.kt      # 配置 DSL
│       ├── GenAggregateTask.kt      # 主要的聚合生成任务
│       ├── GenDesignTask.kt         # 设计文件驱动生成任务
│       ├── GenArchTask.kt           # 架构生成基础任务
│       ├── AbstractCodegenTask.kt   # 带模板渲染的基础任务
│       ├── context/                 # 上下文接口和构建器
│       │   ├── BaseContext.kt       # 基础配置接口
│       │   ├── ContextBuilder.kt    # 泛型上下文构建器接口
│       │   ├── aggregate/           # 聚合上下文（数据库驱动）
│       │   │   ├── AggregateContext.kt     # 只读生成上下文
│       │   │   ├── MutableAggregateContext.kt  # 用于构建的可变上下文
│       │   │   └── builders/        # 聚合上下文构建器（8 个构建器）
│       │   │       ├── TableContextBuilder.kt
│       │   │       ├── EntityTypeContextBuilder.kt
│       │   │       ├── AnnotationContextBuilder.kt
│       │   │       ├── ModuleContextBuilder.kt
│       │   │       ├── RelationContextBuilder.kt
│       │   │       ├── EnumContextBuilder.kt
│       │   │       ├── AggregateContextBuilder.kt
│       │   │       └── TablePackageContextBuilder.kt
│       │   └── design/              # 设计上下文（KSP + 设计文件驱动）
│       │       ├── DesignContext.kt         # 只读设计上下文
│       │       ├── MutableDesignContext.kt  # 可变设计上下文
│       │       ├── builders/        # 设计上下文构建器（4 个构建器）
│       │       │   ├── DesignContextBuilder.kt
│       │       │   ├── KspMetadataContextBuilder.kt
│       │       │   ├── TypeMappingBuilder.kt
│       │       │   └── UnifiedDesignBuilder.kt
│       │       └── models/          # 设计元素模型
│       │           ├── DesignElement.kt
│       │           ├── BaseDesign.kt
│       │           ├── CommonDesign.kt
│       │           ├── DomainEventDesign.kt
│       │           ├── IntegrationEventDesign.kt
│       │           └── AggregateInfo.kt
│       ├── generators/              # 文件生成器
│       │   ├── aggregate/           # 聚合生成器（10 个生成器）
│       │   │   ├── AggregateTemplateGenerator.kt  # 生成器接口
│       │   │   ├── SchemaBaseGenerator.kt
│       │   │   ├── EnumGenerator.kt
│       │   │   ├── EntityGenerator.kt   # 核心实体生成器
│       │   │   ├── SchemaGenerator.kt
│       │   │   ├── SpecificationGenerator.kt
│       │   │   ├── FactoryGenerator.kt
│       │   │   ├── DomainEventGenerator.kt
│       │   │   ├── DomainEventHandlerGenerator.kt
│       │   │   ├── RepositoryGenerator.kt
│       │   │   └── AggregateGenerator.kt
│       │   └── design/              # 设计生成器（5 个生成器）
│       │       ├── DesignTemplateGenerator.kt  # 生成器接口
│       │       ├── CommandGenerator.kt
│       │       ├── QueryGenerator.kt
│       │       ├── DomainEventGenerator.kt
│       │       ├── DomainEventHandlerGenerator.kt
│       │       └── QueryHandlerGenerator.kt
│       ├── manager/                 # 导入管理（15+ 管理器）
│       │   ├── BaseImportManager.kt
│       │   ├── ImportManager.kt
│       │   ├── EntityImportManager.kt
│       │   ├── SchemaImportManager.kt
│       │   ├── RepositoryImportManager.kt
│       │   ├── FactoryImportManager.kt
│       │   ├── CommandImportManager.kt
│       │   ├── QueryImportManager.kt
│       │   └── ... (更多)
│       ├── misc/                    # 工具类
│       │   ├── SqlSchemaUtils.kt    # 抽象 SQL 工具
│       │   ├── SqlSchemaUtils4Mysql.kt
│       │   ├── SqlSchemaUtils4Postgresql.kt
│       │   ├── NamingUtils.kt       # 命名约定
│       │   ├── Inflector.kt         # 复数化
│       │   ├── TextUtils.kt
│       │   ├── ResourceUtils.kt
│       │   └── SourceFileUtils.kt
│       ├── pebble/                  # 模板渲染
│       │   ├── PebbleTemplateRenderer.kt
│       │   ├── PebbleConfig.kt
│       │   └── PebbleInitializer.kt
│       └── template/                # 模板模型
│           ├── Template.kt
│           ├── TemplateNode.kt
│           └── PathNode.kt
├── ksp-processor/                   # KSP 元数据处理器模块
│   └── src/main/kotlin/...
└── settings.gradle.kts
```

## 开发注意事项

### 架构模式

- **通用上下文系统**：所有上下文扩展 `BaseContext`，在 `ContextBuilder<T>` 中使用类型参数 `T`
- **两阶段模式**：每个生成任务都遵循 上下文构建 → 文件生成
- **基于顺序的执行**：构建器和生成器都使用 `order: Int` 进行排序
- **类型安全**：泛型接口（`ContextBuilder<T>`）确保编译时类型安全
- **关注点分离**：
  - 上下文层 = 数据结构（只读 + 可变）
  - 构建器层 = 数据收集/解析
  - 生成器层 = 文件生成
  - 任务层 = 工作流编排

### 代码生成管道

**当前实现**：
1. **数据库 → 领域** (`GenAggregateTask` + `AggregateContext` + `ContextBuilder<MutableAggregateContext>` + `AggregateTemplateGenerator`)
   - 从数据库元数据生成领域层代码
   - 输出：实体、枚举、Schema、规约、工厂、领域事件、Repository、聚合

2. **KSP + 设计文件 → 应用/领域** (`GenDesignTask` + `DesignContext` + `ContextBuilder<MutableDesignContext>` + `DesignTemplateGenerator`)
   - 从 KSP 元数据和设计文件生成应用/领域层代码
   - 输出：命令、查询、领域事件、事件处理器、查询处理器

**架构能力**：
3. **自定义生成管道** - 通用框架支持通过以下方式创建新管道：
   - 定义自定义上下文接口（扩展 `BaseContext`）
   - 为您的上下文类型实现 `ContextBuilder<T>`
   - 创建实现适当生成器接口的生成器
   - 在自定义任务中注册构建器和生成器

### 技术细节

- 代码库广泛使用带有惰性初始化的 Kotlin 属性
- 大多数配置值来自 `CodegenExtension`，并缓存在 `BaseContext.baseMap` 中
- 插件集成 Hibernate/JPA 注解用于实体生成
- 模板冲突解决支持："skip"、"warn"、"overwrite"
- 带有 `[cap4k-ddd-codegen-gradle-plugin:do-not-overwrite]` 标记的文件永远不会被覆盖
- **生成器顺序很重要**：order 数值越小越先执行（EnumGenerator 在 EntityGenerator 之前）
- **上下文构建器顺序很重要**：构建器按顺序运行，后续构建器可以使用先前构建器的数据
- **类型映射至关重要**：所有生成的类型都缓存在 `typeMapping` 中以便交叉引用
- **自定义代码保留**：EntityGenerator 使用标记 "【字段映射开始】" 和 "【字段映射结束】" 来分隔生成的字段和自定义代码
- **注解的状态机**：使用 `inAnnotationBlock` 标志区分类级别注解和字段注解
- **KSP 集成**：GenDesignTask 从 `build/generated/ksp/main/resources/metadata` 读取 KSP 生成的元数据
- **设计标签别名**：GenAggregateTask 和 GenDesignTask 都使用标签别名映射来规范化元素类型名称

## 发布工件

插件发布到阿里云 Maven 仓库，包含以下工件：

1. **`com.only4:plugin:0.1.0-SNAPSHOT`** - 主插件实现
2. **`com.only4:ksp-processor:0.1.0-SNAPSHOT`** - KSP 元数据处理器
3. **`com.only4:com.only4.codegen.gradle.plugin:0.1.0-SNAPSHOT`** - Gradle 插件标记工件

用户可以这样应用插件：
```kotlin
plugins {
    id("com.only4.codegen") version "0.1.0-SNAPSHOT"
}
```

## 可用的 Gradle 任务

- **`genArch`** - 生成项目架构结构
- **`genAggregate`** - 从数据库生成领域层代码（实体、枚举、Schema 等）
- **`genDesign`** - 从设计文件和 KSP 元数据生成应用/领域层代码（命令、查询、事件）

