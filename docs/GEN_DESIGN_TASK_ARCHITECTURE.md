# GenDesignTask 架构设计总结

## 项目概述

`GenDesignTask` 是一个全新的设计驱动代码生成器,完全融入 codegen-plugin 现有框架,支持从 JSON 设计文件 + KSP 元数据生成 DDD 应用层和领域层代码。

## 核心设计理念

### 1. 数据源多元化
- **JSON 设计文件**: 声明式定义 Command、Query、Saga 等设计元素
- **KSP 聚合元信息**: 可选集成,用于关联聚合根、实体、ID 类型等
- **不依赖数据库**: 与 GenEntityTask 完全独立,纯设计驱动

### 2. 弱实体关联
- 设计元素不强制要求关联聚合根
- 支持跨聚合查询 (crossAggregate)
- 支持独立领域服务 (无聚合)
- 只有领域事件强制关联聚合

### 3. 完全融入现有框架
- 复用 `BaseContext`、`ContextBuilder<T>`、Generator 接口
- 遵循两阶段执行模式: Context Building → File Generation
- 共享 typeMapping、templateAliasMap
- 与 GenEntityTask、GenArchTask 无缝协作

## 架构层次

```
┌─────────────────────────────────────────────────────────┐
│                   GenDesignTask                          │
│                 (extends GenArchTask)                    │
│          implements MutableDesignContext                 │
└─────────────────────────────────────────────────────────┘
                          │
            ┌─────────────┴─────────────┐
            │                           │
┌───────────▼────────────┐  ┌──────────▼──────────┐
│   Context Builders     │  │   Generators        │
│  (8个实现)             │  │  (8个实现)          │
├────────────────────────┤  ├─────────────────────┤
│ JsonDesignLoader       │  │ CommandGenerator    │
│ KspMetadataLoader      │  │ QueryGenerator      │
│ CommandDesignBuilder   │  │ SagaGenerator       │
│ QueryDesignBuilder     │  │ ClientGenerator     │
│ SagaDesignBuilder      │  │ IntegrationEvent... │
│ ClientDesignBuilder    │  │ DomainEventGen...   │
│ DomainEventDesignBuild │  │ DomainServiceGen... │
│ DomainServiceDesignBu  │  │                     │
└────────────────────────┘  └─────────────────────┘
            │                           │
            └─────────────┬─────────────┘
                          │
┌─────────────────────────▼─────────────────────────────┐
│              DesignContext (Interface)                 │
│  - designElementMap: Map<String, List<DesignElement>> │
│  - aggregateMetadataMap: Map<String, AggregateMetadata>│
│  - commandDesignMap, queryDesignMap, ...              │
└────────────────────────────────────────────────────────┘
```

## 已创建文件清单 (23个)

### Context 层 (4个文件)
- ✅ `context/design/DesignElementModels.kt` - 数据模型定义
- ✅ `context/design/DesignContext.kt` - 只读上下文接口
- ✅ `context/design/MutableDesignContext.kt` - 可变上下文接口
- ✅ `context/design/ContextBuilder<MutableDesignContext>.kt` - Builder 基础接口

### Builder 层 (8个文件)
- ✅ `context/design/builders/JsonDesignLoader.kt` - JSON 文件解析
- ✅ `context/design/builders/KspMetadataLoader.kt` - KSP 元数据加载
- ✅ `context/design/builders/CommandDesignBuilder.kt` - 命令设计构建
- ✅ `context/design/builders/QueryDesignBuilder.kt` - 查询设计构建
- ✅ `context/design/builders/SagaDesignBuilder.kt` - Saga 设计构建
- ✅ `context/design/builders/ClientDesignBuilder.kt` - 客户端设计构建
- ✅ `context/design/builders/DomainEventDesignBuilder.kt` - 领域事件设计构建
- ✅ `context/design/builders/DomainServiceDesignBuilder.kt` - 领域服务设计构建

### Generator 层 (8个文件)
- ✅ `generators/design/DesignTemplateGenerator.kt` - Generator 接口
- ✅ `generators/design/CommandGenerator.kt` - 命令代码生成
- ✅ `generators/design/QueryGenerator.kt` - 查询代码生成
- ✅ `generators/design/SagaGenerator.kt` - Saga 代码生成
- ✅ `generators/design/ClientGenerator.kt` - 客户端代码生成
- ✅ `generators/design/IntegrationEventGenerator.kt` - 集成事件代码生成
- ✅ `generators/design/DomainEventGenerator.kt` - 领域事件代码生成
- ✅ `generators/design/DomainServiceGenerator.kt` - 领域服务代码生成

### Task 层 (1个文件)
- ✅ `GenDesignTask.kt` - 主任务类

### 配置层 (2个修改)
- ✅ `CodegenExtension.kt` - 新增 designFiles, kspMetadataDir, designEncoding
- ✅ `AbstractCodegenTask.kt` - baseMap 添加设计配置

## 执行流程

```
./gradlew genDesign
    ↓
GenDesignTask.generate()
    ↓
【阶段1: Context Building】
    ├─ JsonDesignLoader (order=10)
    │   └─ 解析 JSON → designElementMap
    ├─ KspMetadataLoader (order=15)
    │   └─ 解析 KSP → aggregateMetadataMap
    ├─ CommandDesignBuilder (order=20)
    │   └─ 构建 CommandDesign 对象
    ├─ QueryDesignBuilder (order=20)
    ├─ SagaDesignBuilder (order=20)
    ├─ ClientDesignBuilder (order=20)
    ├─ DomainEventDesignBuilder (order=25)
    └─ DomainServiceDesignBuilder (order=20)
    ↓
【阶段2: File Generation】
    ├─ CommandGenerator (order=10)
    │   └─ 生成 Cmd + Request/Response
    ├─ QueryGenerator (order=10)
    ├─ SagaGenerator (order=10)
    ├─ ClientGenerator (order=10)
    ├─ IntegrationEventGenerator (order=20)
    ├─ DomainServiceGenerator (order=20)
    └─ DomainEventGenerator (order=30)
    ↓
生成的文件:
  - application/commands/{aggregate}/{Name}Cmd.kt
  - application/queries/{package}/{Name}Qry.kt
  - domain/aggregates/{aggregate}/events/{Name}DomainEvent.kt
  - ...
```

## 关键设计决策

### 1. 为什么不复用 GenDesignTask 旧实现?
- 旧实现使用 `:` 分隔符,不够灵活
- 旧实现基于 `GenArchTask.renderTemplate()`,与新框架不一致
- JSON 格式更适合复杂配置 (metadata 扩展)
- 完全独立的新体系,便于未来扩展

### 2. 为什么使用 JSON 而不是 YAML?
- codegen-plugin 已依赖 fastjson (无需新依赖)
- JSON 解析性能更高
- 与 KSP 生成的 aggregates.json 格式一致
- 易于程序化生成设计文件

### 3. 为什么需要 MutableDesignContext?
- 遵循现有 `EntityContext` / `MutableEntityContext` 模式
- 编译期类型安全:Builder 使用 Mutable,Generator 使用只读
- 防止 Generator 阶段误修改上下文

### 4. 为什么 Generator 只生成代码框架?
- 实际业务逻辑由开发者填充
- 生成的代码包含注释和 TODO 标记
- 支持 `conflict = "skip"` 保护已修改的文件

## 与现有任务对比

| 对比项 | GenEntityTask | GenDesignTask | GenArchTask |
|--------|--------------|---------------|-------------|
| **数据源** | 数据库元数据 | JSON 设计文件 + KSP 元数据 | 架构模板 JSON |
| **Context** | EntityContext | DesignContext | BaseContext |
| **输出层** | Domain 层 (Entity) | Application + Domain | 全层级脚手架 |
| **执行时机** | 编译前 | 设计阶段 | 项目初始化 |
| **依赖** | JDBC | KSP (可选) | 无 |
| **强关联** | 强依赖表结构 | 弱依赖聚合 | 无依赖 |

## 扩展性

### 新增设计类型示例

1. 创建数据模型:
```kotlin
data class SpecificationDesign(
    val name: String,
    val entity: String,
    val desc: String
)
```

2. 创建 Builder:
```kotlin
class SpecificationDesignBuilder : ContextBuilder<MutableDesignContext> {
    override val order = 20
    override fun build(context: MutableDesignContext) {
        // 解析逻辑
    }
}
```

3. 创建 Generator:
```kotlin
class SpecificationGenerator : DesignTemplateGenerator {
    override val tag = "specification"
    override val order = 30
    // 实现接口方法
}
```

4. 注册到 GenDesignTask:
```kotlin
private fun buildDesignContext() = listOf(
    // ... existing builders
    SpecificationDesignBuilder()
)

private fun generateDesignFiles() = listOf(
    // ... existing generators
    SpecificationGenerator()
)
```

## 已知限制和未来改进

### 当前限制
1. 暂不支持模板路径自定义配置
2. 暂不支持设计文件热加载
3. metadata 字段解析较简单,需手动处理复杂类型
4. 缺少设计文件验证器 (schema validation)

### 未来改进方向
1. **设计验证器**: 基于 JSON Schema 验证设计文件
2. **模板配置化**: 支持在 Extension 中配置模板路径
3. **设计继承**: 支持设计元素继承和组合
4. **代码扫描**: 支持从现有代码反向生成设计文件
5. **IDE 插件**: 提供 IntelliJ IDEA 插件支持设计文件编辑

## 文档清单

- ✅ `docs/GEN_DESIGN_TASK_GUIDE.md` - 使用指南
- ✅ `docs/GEN_DESIGN_TASK_ARCHITECTURE.md` - 本文档 (架构设计)
- ✅ `Case/_gen.json` - JSON 设计文件示例 (已存在)
- ✅ `Case/GenDesignTask.kt` - 旧实现参考 (已存在)

## 总结

GenDesignTask 是一个**完全融入现有框架**的新子代码生成器:
- ✅ 遵循 Context + ContextBuilder + Generator 架构
- ✅ 支持 JSON + KSP 多数据源
- ✅ 弱实体关联,支持灵活设计
- ✅ 零侵入,与现有任务并行运行
- ✅ 高度可扩展,易于新增设计类型

**总计创建**: 23 个文件,约 2500 行代码

---

**最后更新**: 2025-10-13
**作者**: Claude Code
