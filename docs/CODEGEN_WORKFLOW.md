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
│  │  GenArch     │   │  GenEntity   │   │ GenAggregate   │ │
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
│ 3. GenAggregate - 注解代码生成                        │
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
| **AggregateContextBuilder** | 注解上下文构建器接口 `AggregateContextBuilder : ContextBuilder<AggregateContext>` |
| **TemplateGenerator** | 实体代码生成器接口，按 order 顺序生成文件 |
| **AggregateTemplateGenerator** | 注解代码生成器接口，用于基于注解的代码生成 |
| **typeMapping** | 类型映射缓存，存储全限定类名，供后续引用 |
| **AggregateContext** | 基于注解的上下文，从 KSP 元数据构建 |

## 附录

### 关键文件清单

- `AbstractCodegenTask.kt` - 抽象基类，基础上下文和渲染逻辑
- `GenArchTask.kt` - 脚手架生成任务
- `GenEntityTask.kt` - 实体生成任务
- `GenAggregateTask.kt` - 注解代码生成任务
- `Template.kt` - 模板对象
- `PathNode.kt` - 脚手架节点
- `TemplateNode.kt` - 模板节点
- `PebbleTemplateRenderer.kt` - Pebble 模板引擎
- `context/builders/` - 上下文构建器（ContextBuilder、EntityContextBuilder、AggregateContextBuilder）
- `generators/` - 代码生成器（TemplateGenerator、AggregateTemplateGenerator）

### 核心接口层次结构

```
codegen.core.context
├── BaseContext                    // 基础上下文接口
├── EntityContext : BaseContext    // 实体生成上下文
└── AggregateContext : BaseContext // 注解生成上下文

codegen.core.context.builders
├── ContextBuilder<T : BaseContext>           // 上下文构建器基础接口
├── EntityContextBuilder : ContextBuilder<EntityContext>     // 实体上下文构建器
└── AggregateContextBuilder : ContextBuilder<AggregateContext> // 注解上下文构建器

codegen.core.generators
├── TemplateGenerator              // 实体代码生成器接口
└── AggregateTemplateGenerator    // 注解代码生成器接口
```

### 相关文档

- `ksp-processor/README.md` - KSP Processor 详细文档
- `ksp-processor/KSP_RESOLVER_API.md` - Resolver API 参考
- `ksp-processor/KSP_CODEGENERATOR_API.md` - CodeGenerator API 参考
- `ksp-processor/KSP_SYMBOL_TYPES.md` - 符号类型参考

---

**最后更新**: 2025-10-13
