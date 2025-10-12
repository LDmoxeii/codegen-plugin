# 2. 核心架构设计

[← 上一章：方案概述](01-overview.md) | [返回目录](README.md) | [下一章：核心接口和类设计 →](03-core-interfaces.md)

---

## 2.1 整体架构图

### 完整架构视图

```
┌─────────────────────────────────────────────────────────────────┐
│                    Gradle Plugin Layer                           │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │              GenAnnotationTask                              │ │
│  │  - 触发 KSP 编译                                            │ │
│  │  - 读取 KSP 输出的元数据                                    │ │
│  │  - 构建 AnnotationContext                                  │ │
│  │  - 调用 Generator 生成代码                                 │ │
│  │                                                             │ │
│  │  ┌───────────────────────────────────────────────────────┐ │ │
│  │  │     MutableAnnotationContext                          │ │ │
│  │  │  - classMap: Map<String, ClassInfo>                  │ │ │
│  │  │  - annotationMap: Map<String, List<AnnotationInfo>>  │ │ │
│  │  │  - aggregateMap: Map<String, AggregateInfo>          │ │ │
│  │  │  - typeMapping: Map<String, String>                  │ │ │
│  │  └───────────────────────────────────────────────────────┘ │ │
│  │                              ↑                              │ │
│  │                              │ implements                   │ │
│  │  ┌───────────────────────────────────────────────────────┐ │ │
│  │  │                   BaseContext                         │ │ │
│  │  │  - baseMap: Map<String, Any?>                        │ │ │
│  │  │  - templateNodeMap: Map<String, List<TemplateNode>> │ │ │
│  │  │  - adapterPath: String （AbstractCodegenTask 提供）  │ │ │
│  │  │  - applicationPath: String （AbstractCodegenTask）   │ │ │
│  │  │  - domainPath: String （AbstractCodegenTask 提供）   │ │ │
│  │  │  - aggregatesPath: String （GenEntityTask 提供）     │ │ │
│  │  │  - schemaPath: String                                │ │ │
│  │  │  - subscriberPath: String                            │ │ │
│  │  └───────────────────────────────────────────────────────┘ │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              ↓
        ┌────────────────────────────────────────┐
        │      Phase 1: Context Building          │
        │  ┌──────────────────────────────────┐  │
        │  │  KspMetadataContextBuilder       │  │
        │  │  - order = 10                    │  │
        │  │  - 读取 KSP 生成的元数据 JSON    │  │
        │  │  - 填充 classMap                 │  │
        │  └──────────────────────────────────┘  │
        │  ┌──────────────────────────────────┐  │
        │  │  AggregateInfoBuilder            │  │
        │  │  - order = 20                    │  │
        │  │  - resolveAggregates()           │  │
        │  │  - identifyAggregateRoots()      │  │
        │  └──────────────────────────────────┘  │
        │  ┌──────────────────────────────────┐  │
        │  │  IdentityTypeBuilder             │  │
        │  │  - order = 30                    │  │
        │  │  - resolveIdentityTypes()        │  │
        │  └──────────────────────────────────┘  │
        └────────────────────────────────────────┘
                              ↓
        ┌────────────────────────────────────────┐
        │      Phase 2: File Generation           │
        │  ┌──────────────────────────────────┐  │
        │  │  RepositoryGenerator             │  │
        │  │  - order = 10                    │  │
        │  │  - shouldGenerate()              │  │
        │  │  - buildContext()                │  │
        │  │  - onGenerated()                 │  │
        │  └──────────────────────────────────┘  │
        │  ┌──────────────────────────────────┐  │
        │  │  ServiceGenerator                │  │
        │  │  - order = 20                    │  │
        │  └──────────────────────────────────┘  │
        │  ┌──────────────────────────────────┐  │
        │  │  ControllerGenerator             │  │
        │  │  - order = 30                    │  │
        │  └──────────────────────────────────┘  │
        └────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    KSP Processor Layer                           │
│                    (编译时独立运行)                              │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                 AnnotationProcessor                         │ │
│  │  - 扫描 @Aggregate, @Entity, @Id 等注解                    │ │
│  │  - 提取类型信息、字段信息                                   │ │
│  │  - 解析注解属性                                            │ │
│  │  - 生成元数据 JSON 文件                                    │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              ↓
        ┌────────────────────────────────────┐
        │  元数据 JSON 文件                   │
        │  build/generated/ksp/main/metadata/│
        │  - aggregates.json                 │
        │  - entities.json                   │
        └────────────────────────────────────┘
```

## 2.2 关键设计决策

### 2.2.1 为什么在 KSP Processor 中只生成元数据而不直接生成代码？

**原因：**

1. **架构一致性**
   - 保持 Context + Builder + Generator 模式
   - 与 GenEntityTask 保持一致的设计风格
   - 便于理解和维护

2. **灵活性**
   - Processor 只负责元数据提取
   - 生成逻辑在 Gradle Plugin 中，便于调整
   - 可以动态修改生成策略，无需重新编译

3. **可测试性**
   - 元数据和生成逻辑分离
   - 易于单独测试
   - 可以模拟不同的元数据场景

4. **复用性**
   - 可以复用现有的模板系统
   - 可以复用现有的类型映射系统
   - 可以复用现有的路径解析逻辑

### 2.2.2 KSP Processor 的职责边界

**负责的事情：**
- ✅ 扫描注解（@Aggregate, @Entity, @Id 等）
- ✅ 提取类型信息（类名、包名、字段类型等）
- ✅ 解析注解属性（aggregate, root, type 等）
- ✅ 生成元数据 JSON（aggregates.json, entities.json）

**不负责的事情：**
- ❌ **不生成**最终代码（由 Gradle Plugin 负责）
- ❌ **不处理**模板渲染（由 PebbleTemplateRenderer 负责）
- ❌ **不解析**业务逻辑（只提取结构信息）
- ❌ **不管理**文件路径（由 Context 和 Task 负责）

### 2.2.3 路径配置复用策略

**问题**：如何让 AnnotationContext 复用现有的路径配置逻辑？

**解决方案：**

1. **复用 AbstractCodegenTask 中已有的模块路径**
   ```kotlin
   // AbstractCodegenTask 中已有实现（无需修改）
   override val adapterPath: String by lazy {
       extension.get().adapterPath
   }
   override val applicationPath: String by lazy {
       extension.get().applicationPath
   }
   override val domainPath: String by lazy {
       extension.get().domainPath
   }
   ```

2. **将 EntityContext 中的包路径下放到 BaseContext**
   - `aggregatesPath` - 聚合根包的绝对路径
   - `schemaPath` - Schema 包的绝对路径
   - `subscriberPath` - 订阅者包的绝对路径
   - `aggregatesPackage`, `schemaPackage`, `subscriberPackage` - 对应的包名

3. **实现位置**：
   - **模块路径**：`AbstractCodegenTask` 中已有实现（通过 `extension` 获取）
   - **包路径**：`GenEntityTask` 中已有实现，提升到 `BaseContext` 接口
   - **GenAnnotationTask**：继承 `BaseContext`，直接使用这些路径

**优势：**
- ✅ 无需重复编写路径解析逻辑
- ✅ 两个 Task 使用相同的路径计算规则
- ✅ 配置统一，易于维护

### 2.2.4 ContextBuilder 接口解耦

**问题**：现有的 `ContextBuilder` 接口硬编码了 `MutableEntityContext`

```kotlin
// 现有接口（耦合了 EntityContext）
interface ContextBuilder {
    val order: Int
    fun build(context: MutableEntityContext)  // ❌ 硬编码
}
```

**解决方案**：创建独立的 `AnnotationContextBuilder` 接口

```kotlin
// 新接口（独立的）
interface AnnotationContextBuilder {
    val order: Int
    fun build(context: MutableAnnotationContext)  // ✅ 使用独立的上下文
}
```

**优势：**
- ✅ 完全解耦，两个系统互不影响
- ✅ 清晰的职责分离
- ✅ 易于理解和维护
- ✅ 避免类型转换和强制转型

### 2.2.5 generateForClasses 使用 while 循环模式

**问题**：一个类可能需要被多个 Generator 处理，或者一个 Generator 需要多次检查同一个类

**解决方案**：参考 `GenEntityTask.generateForTables` 的 while 循环模式

```kotlin
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

        // 生成代码...

        generator.onGenerated(classInfo, context)
    }
}
```

**优势：**
- ✅ 允许 Generator 多次检查同一个类
- ✅ Generator 通过 `shouldGenerate()` 控制是否继续生成
- ✅ Generator 通过 `onGenerated()` 标记已生成，避免无限循环
- ✅ 与 `GenEntityTask` 保持一致的模式

## 2.3 与 GenEntityTask 的对比

| 层次 | GenEntityTask | GenAnnotationTask | 共享 |
|------|---------------|-------------------|------|
| **Task** | GenEntityTask | GenAnnotationTask | GenArchTask (基类) |
| **Context** | EntityContext | AnnotationContext | BaseContext (基类) |
| **Builder** | ContextBuilder | AnnotationContextBuilder | 接口独立 |
| **Generator** | TemplateGenerator | AnnotationTemplateGenerator | 接口独立 |
| **Template** | entity.peb, enum.peb | repository.peb, service.peb | Pebble 引擎 |
| **路径配置** | 继承 BaseContext | 继承 BaseContext | BaseContext 路径属性 |
| **循环模式** | while + removeFirst() | while + removeFirst() | 相同模式 |

### 代码复用点

1. **BaseContext** - 基础配置和模板别名系统
2. **TemplateNode/PathNode** - 模板节点模型
3. **PebbleTemplateRenderer** - 模板渲染引擎
4. **AbstractCodegenTask** - 模板别名映射和渲染逻辑
5. **工具方法** - `resolvePackageDirectory`, `concatPackage` 等

### 独立性保证

1. **独立的 Context 体系**
   - EntityContext 和 AnnotationContext 互不依赖
   - 各自维护独立的数据结构

2. **独立的 Builder 体系**
   - ContextBuilder 和 AnnotationContextBuilder 完全独立
   - 各自的 Builder 不共享状态

3. **独立的 Generator 体系**
   - TemplateGenerator 和 AnnotationTemplateGenerator 完全独立
   - 各自的 Generator 接口不同

4. **独立的执行流程**
   - 可单独运行，互不影响
   - 可组合使用，形成流水线

## 2.4 数据流图

### 完整的数据流向

```
数据库                      领域代码
  │                           │
  │ GenEntityTask             │ GenAnnotationTask
  │ (读取表结构)              │ (KSP扫描注解)
  ▼                           ▼
TableContextBuilder        KspMetadataContextBuilder
  │                           │
  │ 填充 tableMap             │ 读取 aggregates.json
  │ 填充 columnsMap           │ 填充 classMap
  │ 填充 relationsMap         │ 填充 annotationMap
  ▼                           ▼
EntityContext              AnnotationContext
  │                           │
  │ EntityGenerator           │ RepositoryGenerator
  │ EnumGenerator             │ ServiceGenerator
  │ SchemaGenerator           │ ControllerGenerator
  ▼                           ▼
Domain 层代码              Adapter/Application 层代码
  - User.kt                   - UserRepository.kt
  - UserStatus.kt             - UserService.kt
  - SUser.kt                  - UserController.kt
```

## 2.5 类图

### Context 层类图

```
┌─────────────────────┐
│    BaseContext      │
│  (interface)        │
├─────────────────────┤
│ + baseMap           │
│ + templateNodeMap   │
│ + typeMapping       │
│ + adapterPath       │
│ + applicationPath   │
│ + domainPath        │
│ + aggregatesPath    │
│ + schemaPath        │
└──────────┬──────────┘
           │
           │ extends
           │
    ┌──────┴───────┬──────────────────┐
    │              │                  │
┌───▼────────┐  ┌──▼────────────┐  ┌─▼──────────────┐
│EntityContext│  │AnnotationContext│  │  (其他Context)  │
└────────────┘  └─────────────────┘  └────────────────┘
```

### Builder 层类图

```
┌─────────────────────────┐        ┌──────────────────────────┐
│   ContextBuilder        │        │ AnnotationContextBuilder │
│   (interface)           │        │   (interface)            │
├─────────────────────────┤        ├──────────────────────────┤
│ + order: Int            │        │ + order: Int             │
│ + build(               │        │ + build(                 │
│     MutableEntityContext)│        │     MutableAnnotationContext)│
└──────────┬──────────────┘        └──────────┬───────────────┘
           │                                  │
           │ implements                       │ implements
           │                                  │
    ┌──────┴──────┐                  ┌───────┴────────┐
    │             │                  │                │
┌───▼──────┐  ┌──▼────────┐  ┌─────▼──────┐  ┌─────▼────────┐
│TableContext│  │Enum       │  │KspMetadata│  │AggregateInfo│
│Builder   │  │ContextBuilder│  │ContextBuilder│  │Builder   │
└──────────┘  └───────────┘  └────────────┘  └──────────────┘
```

## 2.6 时序图

### 代码生成执行时序

```
用户                GenAnnotationTask    KspMetadataContextBuilder    AggregateInfoBuilder    RepositoryGenerator
│                          │                       │                          │                        │
│ ./gradlew genAnnotation │                       │                          │                        │
├─────────────────────────>│                       │                          │                        │
│                          │                       │                          │                        │
│                          │ build(context)        │                          │                        │
│                          ├──────────────────────>│                          │                        │
│                          │                       │ 读取 aggregates.json     │                        │
│                          │                       │ 填充 classMap            │                        │
│                          │<──────────────────────┤                          │                        │
│                          │                       │                          │                        │
│                          │ build(context)        │                          │                        │
│                          ├──────────────────────────────────────────────────>│                        │
│                          │                       │                          │ 识别聚合根              │
│                          │                       │                          │ 填充 aggregateMap      │
│                          │<──────────────────────────────────────────────────┤                        │
│                          │                       │                          │                        │
│                          │ generateForClasses()  │                          │                        │
│                          ├─────────────────────────────────────────────────────────────────────────>│
│                          │                       │                          │                        │ shouldGenerate()
│                          │                       │                          │                        │ buildContext()
│                          │                       │                          │                        │ 渲染模板
│                          │                       │                          │                        │ 生成文件
│                          │                       │                          │                        │ onGenerated()
│                          │<─────────────────────────────────────────────────────────────────────────┤
│                          │                       │                          │                        │
│<─────────────────────────┤                       │                          │                        │
│ 生成完成                  │                       │                          │                        │
```

---

[← 上一章：方案概述](01-overview.md) | [返回目录](README.md) | [下一章：核心接口和类设计 →](03-core-interfaces.md)
