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

### 两阶段执行模型

代码生成过程遵循两阶段模式：

1. **阶段 1：上下文构建** - 从数据库和配置中收集所有元数据
   - 上下文构建器按顺序运行（由 `order` 属性控制）
   - 每个构建器填充 `EntityContext` 中的特定 Map（表、列、关系、枚举等）
   - 在生成开始前解决所有依赖关系

2. **阶段 2：文件生成** - 使用构建好的上下文生成文件
   - 生成器按顺序运行（EnumGenerator 在 EntityGenerator 之前）
   - 每个生成器实现 `shouldGenerate()`、`buildContext()` 和 `getDefaultTemplateNode()`
   - 使用 Pebble 模板引擎渲染模板

### 核心接口

**上下文层** (`com.only.codegen.context`):
- `BaseContext` - 基础配置和模板别名系统
- `EntityContext` - 所有共享上下文数据的只读接口
- `MutableEntityContext` - 上下文构建阶段的可变版本

**构建器层** (`com.only.codegen.context.builders`):
- `ContextBuilder` - 带有 `order` 和 `build()` 方法的接口
- 构建器按顺序执行：TableContextBuilder (10) → RelationContextBuilder (40) → EnumContextBuilder (50)
- 每个构建器填充 `EntityContext` 中的特定 Map

**生成器层** (`com.only.codegen.generators`):
- `TemplateGenerator` - 带有 `tag`、`order` 和生成方法的接口
- `EnumGenerator` (order=10) - 生成枚举类，跟踪已生成的枚举以避免重复
- `EntityGenerator` (order=20) - 生成具有完整 DDD 支持的实体类（约700行，自包含）

### 任务

**GenEntityTask** - 实体生成的主任务
- 位置：`com.only.codegen.GenEntityTask`
- 同时实现 `MutableEntityContext`（用于构建）并提供执行流程
- 入口点：`genEntity()` 方法调用 `buildGenerationContext()` 然后调用 `generateFiles()`

**GenArchTask** - 生成项目架构结构
- 读取架构模板并创建目录结构
- 其他生成任务的基类

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

### 添加新的生成器

1. 在 `com.only.codegen.generators` 中创建实现 `TemplateGenerator` 的类
2. 设置 `tag`（例如 "repository"）和 `order`（数值越大，执行越晚）
3. 实现 `shouldGenerate()` - 如果此表需要此生成器则返回 true
4. 实现 `buildContext()` - 使用 `context.putContext(tag, key, value)` 准备模板上下文 Map
5. 实现 `getDefaultTemplateNode()` - 定义默认模板路径和冲突解决方式
6. 在 `GenEntityTask.generateFiles()` 中注册，将其添加到 generators 列表

### 添加新的上下文构建器

1. 在 `com.only.codegen.context.builders` 中创建实现 `ContextBuilder` 的类
2. 根据依赖关系设置 `order`（数值越小，执行越早）
3. 实现 `build()` 以填充 `MutableEntityContext` 的 Map
4. 在 `GenEntityTask.buildGenerationContext()` 中注册，将其添加到 contextBuilders 列表

### 导入管理

`ImportManager` 系统（在 `com.only.codegen.generators.manager` 中）处理自动导入解析：
- `EntityImportManager` - 管理实体特定的导入，带冲突检测
- 自动处理 Java/Kotlin 类型映射和通配符导入
- 在构建需要精确导入控制的复杂上下文时使用

## 关键文件参考

- `CodegenPlugin.kt` - 插件注册和任务设置
- `CodegenExtension.kt` - 配置 DSL（数据库、生成选项）
- `GenEntityTask.kt` - 主要的实体生成编排器
- `AbstractCodegenTask.kt` - 带渲染和模板别名逻辑的基础任务
- `PebbleTemplateRenderer.kt` - Pebble 模板渲染包装器
- `重构进度报告.md` - 详细的重构进度报告
- `EntityGenerator实现计划.md` - EntityGenerator 实现计划

## 模块结构

```
codegen-plugin/
├── plugin/                           # 主插件模块
│   └── src/main/kotlin/com/only/codegen/
│       ├── CodegenPlugin.kt         # 插件入口点
│       ├── CodegenExtension.kt      # 配置
│       ├── GenEntityTask.kt         # 主任务
│       ├── AbstractCodegenTask.kt   # 基础任务
│       ├── context/                 # 上下文接口
│       │   ├── BaseContext.kt
│       │   ├── EntityContext.kt
│       │   ├── MutableEntityContext.kt
│       │   └── builders/           # 上下文构建器
│       ├── generators/              # 文件生成器
│       │   ├── TemplateGenerator.kt
│       │   ├── EnumGenerator.kt
│       │   ├── EntityGenerator.kt
│       │   └── manager/            # 导入管理
│       ├── misc/                    # 工具类
│       │   ├── SqlSchemaUtils.kt
│       │   ├── NamingUtils.kt
│       │   └── Inflector.kt
│       ├── pebble/                  # 模板渲染
│       └── template/                # 模板模型
└── settings.gradle.kts
```

## 开发注意事项

- 代码库广泛使用带有惰性初始化的 Kotlin 属性
- 大多数配置值来自 `CodegenExtension`，并缓存在 `BaseContext.baseMap` 中
- 插件集成 Hibernate/JPA 注解用于实体生成
- 模板冲突解决支持："skip"、"warn"、"overwrite"
- 带有 `[cap4k-ddd-codegen-gradle-plugin:do-not-overwrite]` 标记的文件永远不会被覆盖
