# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指导。

## 项目概述

这是一个从数据库模式生成代码的 Gradle 插件。它使用 Pebble 模板生成 Kotlin 领域实体、枚举和其他 DDD（领域驱动设计）工件。该插件支持 MySQL 和 PostgreSQL 数据库。

**插件 ID**: `com.only.codegen`

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
- 数据库驱动的领域生成（当前：`GenEntityTask`）
- 注解驱动的基础设施生成（计划中：`GenAnnotationTask`）
- 设计文件驱动的应用层生成（示例：`GenDesignTask`）
- **KSP 元数据 + 模板 + 自定义设计上下文**（未来扩展性）

### 两阶段执行模型

所有生成任务遵循一致的两阶段模式：

1. **阶段 1：上下文构建** - 从数据源收集元数据到类型化上下文中
   - 上下文构建器实现带有泛型类型参数的 `ContextBuilder<T>` 接口
   - 构建器按顺序运行（由 `order` 属性控制）
   - 每个构建器填充上下文中的特定 Map（如 `tableMap`、`classMap`、`aggregateMap`）
   - 在生成开始前解决所有依赖关系

2. **阶段 2：文件生成** - 使用构建好的上下文生成文件
   - 生成器实现生成器接口（如 `TemplateGenerator`、`AnnotationTemplateGenerator`）
   - 生成器根据 `order` 属性按顺序运行
   - 每个生成器实现 `shouldGenerate()`、`buildContext()` 和 `getDefaultTemplateNode()`
   - 使用 Pebble 模板引擎渲染模板
   - 生成的类型缓存在 `typeMapping` 中以便交叉引用

### 核心接口

插件使用**通用、可组合的接口**，支持不同的代码生成管道：

**上下文层** (`com.only.codegen.context`):
- `BaseContext` - 基础配置和模板别名系统（所有上下文共享）
- `EntityContext : BaseContext` - 数据库驱动生成上下文（表、列、关系、枚举）
- `AnnotationContext : BaseContext` - 注解驱动生成上下文（类、注解、聚合）
- `MutableEntityContext` - EntityContext 的可变版本，用于上下文构建阶段
- `MutableAnnotationContext` - AnnotationContext 的可变版本，用于上下文构建阶段

**构建器层** (`com.only.codegen.context.builders`)：
- `ContextBuilder<T>` - **泛型基础接口**，带有类型参数 `T`
  - 定义 `order: Int` 属性用于执行排序
  - 定义 `build(context: T)` 方法用于填充上下文数据
  - **支持为不同上下文类型进行类型安全的构建器组合**
- `EntityContextBuilder : ContextBuilder<MutableEntityContext>` - 专门用于实体生成
- `AggregateContextBuilder : ContextBuilder<MutableAnnotationContext>` - 专门用于基于注解的生成

**实体上下文构建器**（用于数据库驱动生成）：
- 构建器按顺序执行：
  - `TableContextBuilder` (order=10) - 从数据库收集表和列元数据
  - `EntityTypeContextBuilder` (order=20) - 从表名确定实体类名
  - `AnnotationContextBuilder` (order=20) - 处理表/列注解和元数据
  - `ModuleContextBuilder` (order=20) - 如果启用，解析多模块结构
  - `RelationContextBuilder` (order=20) - 分析表之间的外键关系
  - `EnumContextBuilder` (order=20) - 从表注释中提取枚举定义
  - `AggregateContextBuilder` (order=30) - 识别聚合和聚合根
  - `TablePackageContextBuilder` (order=40) - 确定每个表的包结构
- 每个构建器填充 `EntityContext` 中的特定 Map

**生成器层** (`com.only.codegen.generators`):
- `TemplateGenerator` - 实体/数据库驱动生成器接口
  - 属性：`tag: String`、`order: Int`
  - 方法：`shouldGenerate()`、`buildContext()`、`getDefaultTemplateNode()`、`onGenerated()`
- `AnnotationTemplateGenerator` - 注解驱动生成器接口（计划中）
  - 类似结构，但操作 `AggregateInfo` 和 `AnnotationContext`

**实体生成器**（用于数据库驱动生成，order 10-40）：
- `SchemaBaseGenerator` (order=10) - 生成 SchemaBase 基类用于元数据跟踪
- `EnumGenerator` (order=10) - 生成枚举类，跟踪已生成的枚举以避免重复
- `EntityGenerator` (order=20) - 生成具有完整 DDD 支持的实体类，处理自定义代码保留
- `SchemaGenerator` (order=30) - 生成 Schema 类（类似于 JPA Metamodel）用于类型安全查询
- `SpecificationGenerator` (order=30) - 生成规约基类用于领域规约
- `FactoryGenerator` (order=30) - 生成工厂类用于聚合根创建
- `DomainEventGenerator` (order=30) - 生成领域事件类用于事件驱动架构
- `DomainEventHandlerGenerator` (order=40) - 生成领域事件处理器/订阅者类
- `AggregateGenerator` (order=40) - 生成聚合封装类用于聚合根管理

### 任务

**任务层次结构** - 所有任务从共同的基类扩展，具有模板渲染能力：

```
AbstractCodegenTask                  # 基类：Pebble 渲染 + 模板别名
    ├── GenArchTask                  # 架构脚手架
    │   ├── GenDesignTask            # 设计文件驱动生成（案例研究）
    │   └── [自定义任务...]          # 用户可扩展
    ├── GenEntityTask                # 数据库 → 领域层
    └── GenAnnotationTask（计划中）  # 注解 → 基础设施层
```

**GenEntityTask** - 数据库驱动的领域生成
- 位置：`com.only.codegen.GenEntityTask`
- 数据源：通过 JDBC 获取数据库元数据
- 上下文：实现 `MutableEntityContext`
- 工作流：`genEntity()` → `buildGenerationContext()` → `generateFiles()`
- 输出：领域实体、枚举、Schema、规约、工厂、领域事件

**GenArchTask** - 架构脚手架基础任务
- 读取架构模板并创建目录结构
- 提供通用脚手架功能的基类
- 可以扩展用于自定义生成场景

**GenDesignTask** - 设计文件驱动生成（案例研究，位于 `Case/GenDesignTask.kt`）
- 位置：`Case/GenDesignTask.kt`（示例实现，演示框架可扩展性）
- 数据源：声明式设计文件（基于文本的 DSL）
- 从设计声明生成 DDD 设计元素
- 演示**替代数据源集成模式**
- 支持的设计元素类型：
  - **应用层**：Command、Saga、Query、Client（防腐层）、集成事件
  - **领域层**：领域事件、规约、工厂、领域服务
- 设计格式：`element_type:ElementName:param1:param2:...`
- 功能特性：
  - 元素类型规范化的别名系统（如 "cmd"、"command"、"commands" → "command"）
  - 用于过滤设计的正则表达式模式匹配
  - 针对每种元素类型的专门渲染方法
- 关键方法：
  - `resolveLiteralDesign(design: String)` - 将设计文件解析为结构化 Map
  - `alias4Design(name: String)` - 规范化元素类型名称
  - `renderAppLayerCommand()`、`renderDomainLayerDomainEvent()` 等 - 元素特定渲染
- **架构启示**：展示如何通过以下方式创建自定义生成任务：
  1. 扩展 `GenArchTask`
  2. 为替代数据源实现自定义解析逻辑
  3. 复用模板渲染基础设施
  4. 定义元素特定的上下文构建

### 模板别名系统

插件使用复杂的模板别名系统（`BaseContext.putContext()` 扩展函数），自动将变量映射到多种命名约定：
- 示例：`putContext("entity", "Entity", "User")` 映射到 "Entity"、"entity"、"ENTITY"、"entityType" 等
- 在 `AbstractCodegenTask.templateAliasMap` 中定义（300+ 行映射）
- 允许模板使用任何命名约定

### 数据库支持

**SqlSchemaUtils** - 数据库元数据提取的核心工具
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

**针对实体生成（EntityContext）**：

1. 在 `com.only.codegen.generators` 中创建实现 `TemplateGenerator` 的类
2. 设置 `tag`（例如 "repository"）和 `order`（数值越大，执行越晚）
3. 实现 `shouldGenerate()` - 如果此表需要此生成器则返回 true
4. 实现 `buildContext()` - 使用 `context.putContext(tag, key, value)` 准备模板上下文 Map
5. 实现 `getDefaultTemplateNode()` - 定义默认模板路径和冲突解决方式
6. 实现 `onGenerated()` - 在 `typeMapping` 中缓存生成的类型全名供其他生成器引用
7. 在 `GenEntityTask.generateFiles()` 中注册，将其添加到 generators 列表

**针对注解生成（AnnotationContext）**：

1. 创建实现 `AnnotationTemplateGenerator` 的类
2. 类似结构，但操作 `AggregateInfo` 和 `AnnotationContext`
3. 在 `GenAnnotationTask` 中注册（实现后）

### 向现有管道添加上下文构建器

**针对实体上下文**：

1. 创建实现 `EntityContextBuilder : ContextBuilder<MutableEntityContext>` 的类
2. 根据依赖关系设置 `order`（数值越小，执行越早）
3. 实现 `build(context: MutableEntityContext)` 以填充上下文 Map
4. 在 `GenEntityTask.buildGenerationContext()` 中注册，将其添加到 contextBuilders 列表

**针对注解上下文**：

1. 创建实现 `AggregateContextBuilder : ContextBuilder<MutableAnnotationContext>` 的类
2. 类似结构，但处理注解元数据
3. 在 `GenAnnotationTask` 中注册（实现后）

### 导入管理

`ImportManager` 系统（在 `com.only.codegen.generators.manager` 中）处理自动导入解析：
- `EntityImportManager` - 管理实体特定的导入，带冲突检测
- 自动处理 Java/Kotlin 类型映射和通配符导入
- 在构建需要精确导入控制的复杂上下文时使用

## 关键文件参考

- `CodegenPlugin.kt` - 插件注册和任务设置
- `CodegenExtension.kt` - 配置 DSL（数据库、生成选项）
- `GenEntityTask.kt` - 主要的实体生成编排器，实现 MutableEntityContext
- `AbstractCodegenTask.kt` - 带渲染和模板别名逻辑的基础任务
- `PebbleTemplateRenderer.kt` - Pebble 模板渲染包装器
- 上下文接口 (`context/`)：
  - `BaseContext.kt` - 基础配置，通过 `putContext()` 扩展进行模板别名
  - `EntityContext.kt` - 包含所有生成上下文数据的只读接口
  - `MutableEntityContext.kt` - 上下文构建期间使用的可变版本
- 上下文构建器 (`context/builders/`)：8 个构建器分阶段填充 EntityContext
- 生成器 (`generators/`)：10 个生成器按顺序生成文件
- 导入管理 (`generators/manager/`)：`ImportManager` 和 `EntityImportManager`
- SQL 工具 (`misc/`)：`SqlSchemaUtils`、MySQL/PostgreSQL 实现
- 文档：`重构进度报告.md`、`EntityGenerator实现计划.md`（中文）

## 模块结构

```
codegen-plugin/
├── plugin/                           # 主插件模块
│   └── src/main/kotlin/com/only/codegen/
│       ├── CodegenPlugin.kt         # 插件入口点
│       ├── CodegenExtension.kt      # 配置 DSL
│       ├── GenEntityTask.kt         # 主要的实体生成任务
│       ├── GenArchTask.kt           # 架构生成基础任务
│       ├── AbstractCodegenTask.kt   # 带模板渲染的基础任务
│       ├── context/                 # 上下文接口和构建器
│       │   ├── BaseContext.kt       # 基础配置接口
│       │   ├── EntityContext.kt     # 只读生成上下文
│       │   ├── MutableEntityContext.kt  # 用于构建的可变上下文
│       │   └── builders/            # 上下文构建器（8 个构建器）
│       │       ├── ContextBuilder.kt
│       │       ├── TableContextBuilder.kt
│       │       ├── EntityTypeContextBuilder.kt
│       │       ├── AnnotationContextBuilder.kt
│       │       ├── ModuleContextBuilder.kt
│       │       ├── RelationContextBuilder.kt
│       │       ├── EnumContextBuilder.kt
│       │       ├── AggregateContextBuilder.kt
│       │       └── TablePackageContextBuilder.kt
│       ├── generators/              # 文件生成器（10 个生成器）
│       │   ├── TemplateGenerator.kt # 生成器接口
│       │   ├── SchemaBaseGenerator.kt
│       │   ├── EnumGenerator.kt
│       │   ├── EntityGenerator.kt   # 核心实体生成器（约 720 行）
│       │   ├── SchemaGenerator.kt
│       │   ├── SpecificationGenerator.kt
│       │   ├── FactoryGenerator.kt
│       │   ├── DomainEventGenerator.kt
│       │   ├── DomainEventHandlerGenerator.kt
│       │   ├── AggregateGenerator.kt
│       │   └── manager/             # 导入管理
│       │       ├── ImportManager.kt
│       │       └── EntityImportManager.kt
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
│       │   ├── PebbleInitializer.kt
│       │   └── CompositeLoader.kt
│       └── template/                # 模板模型
│           ├── Template.kt
│           ├── TemplateNode.kt
│           └── PathNode.kt
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
1. **数据库 → 领域** (`GenEntityTask` + `EntityContext` + `EntityContextBuilder` + `TemplateGenerator`)
2. **设计文件 → 应用/领域** (`GenDesignTask` - 案例研究)

**计划中**：
3. **注解 → 基础设施** (`GenAnnotationTask` + `AnnotationContext` + `AggregateContextBuilder` + `AnnotationTemplateGenerator`)

**未来扩展性**：
4. **KSP + 模板 + 自定义设计**（用户定义的上下文 + 构建器 + 生成器）
5. 通过通用框架实现任意数据源组合

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

## 未来方向：基于注解的代码生成

**⚠️ 重要：这是规划的未来实现方向**

计划开发一个新的基于注解的代码生成子系统，与现有的数据库驱动生成互补。完整技术设计详见 `ANNOTATION_BASED_CODEGEN_DESIGN.md`。

### 核心概念

**GenAnnotationTask** - 用于基于注解生成的新任务（与 GenEntityTask 并行）
- 扫描已生成的领域代码（实体类）中的注解
- 使用 **AnnotationContext**（独立于 EntityContext）
- 生成基础设施层代码（Repository、Service、Controller、Mapper）
- 遵循相同的 Context + ContextBuilder + Generator 架构模式

### 架构对比

| 方面 | GenEntityTask | GenAnnotationTask（计划中） |
|------|---------------|---------------------------|
| **数据源** | 数据库元数据 | 源代码注解 |
| **上下文** | EntityContext | AnnotationContext |
| **主要 Map** | tableMap, columnsMap, relationsMap | classMap, annotationMap, aggregateMap |
| **扫描目标** | 数据库表 | .kt 文件 |
| **依赖** | JDBC 驱动 | 正则表达式解析（零新增依赖） |
| **运行时机** | 编译前（读取数据库） | 领域生成后（读取生成的代码） |
| **输出位置** | Domain 层 | Adapter/Application 层 |

### 规划组件

**上下文层**：
- `AnnotationContext` - 只读接口，包含 classMap、annotationMap、aggregateMap
- `MutableAnnotationContext` - 构建阶段使用的可变版本
- 继承 `BaseContext` 以获得配置和模板别名功能

**构建器层**：
- `AnnotationContextBuilder` (order=10) - 扫描 .kt 文件并使用正则表达式解析注解
- `AggregateInfoBuilder` (order=20) - 识别聚合和聚合根
- `IdentityTypeBuilder` (order=30) - 从 @Id 注解解析 ID 类型

**生成器层**：
- `RepositoryGenerator` (order=10) - 生成 JPA Repository 接口
- `ServiceGenerator` (order=20) - 生成应用服务
- `ControllerGenerator` (order=30) - 生成 REST 控制器
- `MapperGenerator` (order=40) - 生成 DTO 映射器

### 使用示例

```bash
# 步骤 1：从数据库生成领域层
./gradlew genEntity

# 步骤 2：从注解生成基础设施层
./gradlew genAnnotation

# 或：一键生成所有代码
./gradlew genAll
```

### 设计原则

1. **架构一致性**：保持 Context + ContextBuilder + Generator 模式
2. **独立性**：完全解耦于 EntityContext
3. **零新增依赖**：使用正则表达式解析（在 GenRepositoryTask 案例中已验证）
4. **灵活性**：可独立运行或与 GenEntityTask 组合使用
5. **可扩展性**：易于为不同的基础设施组件添加新生成器

完整的实现细节、迁移路径和代码示例，请参考 `ANNOTATION_BASED_CODEGEN_DESIGN.md`。

