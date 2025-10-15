# 设计类型的一对多生成器映射

## 概述

在代码生成系统中，某些设计类型需要生成多个相关文件。例如，当用户定义一个 `domain_event_handler` 时，实际上需要生成两个文件：
1. 领域事件类 (`DomainEvent`)
2. 领域事件处理器类 (`DomainEventHandler`)

为了简化用户使用，系统允许用户只定义一份设计数据，然后自动分发给多个生成器。

## 架构设计

### 核心配置

在 `UnifiedDesignBuilder` 中通过 `designTypeToGeneratorTags` 配置一对多映射：

```kotlin
private val designTypeToGeneratorTags = mapOf(
    "domain_event_handler" to listOf("domain_event", "domain_event_handler"),
    // 未来扩展示例：
    // "integration_event_handler" to listOf("integration_event", "integration_event_handler"),
    // "crud" to listOf("command", "query", "repository", "service"),
)
```

### 执行流程

```
用户定义设计
    ↓
designElementMap["domain_event_handler"] = [DesignElement]
    ↓
UnifiedDesignBuilder.build()
    ├─ 解析设计数据为 DomainEventDesign
    ├─ 查找 designTypeToGeneratorTags["domain_event_handler"]
    │  └─ 返回 ["domain_event", "domain_event_handler"]
    └─ 将同一个 DomainEventDesign 实例添加到：
        ├─ designMap["domain_event"]
        └─ designMap["domain_event_handler"]
    ↓
GenDesignTask.generateDesignFiles()
    ├─ DomainEventGenerator (tag="domain_event", order=30)
    │  └─ 从 designMap["domain_event"] 读取
    │     └─ 生成 UserCreatedDomainEvent.kt
    └─ DomainEventHandlerGenerator (tag="domain_event_handler", order=40)
       └─ 从 designMap["domain_event_handler"] 读取
          └─ 生成 UserCreatedDomainEventHandler.kt
```

## 使用示例

### 示例 1: 领域事件 + 处理器

**用户定义（JSON 或 DSL）：**
```json
{
  "domain_event_handler": [
    {
      "name": "UserCreated",
      "package": "user",
      "aggregates": ["User"],
      "metadata": {
        "entity": "User",
        "persist": true
      },
      "desc": "用户创建事件"
    }
  ]
}
```

**生成结果：**
1. `com.example.domain.aggregates.user.events.UserCreatedDomainEvent.kt`
2. `com.example.domain.aggregates.user.events.UserCreatedDomainEventHandler.kt`

### 示例 2: 未来扩展 - 集成事件 + 处理器

**配置：**
```kotlin
"integration_event_handler" to listOf("integration_event", "integration_event_handler")
```

**用户定义：**
```json
{
  "integration_event_handler": [
    {
      "name": "OrderCreated",
      "package": "order",
      "aggregates": ["Order"],
      "metadata": {
        "mqTopic": "order.created",
        "mqConsumer": "order-service"
      },
      "desc": "订单创建集成事件"
    }
  ]
}
```

**生成结果：**
1. `OrderCreatedIntegrationEvent.kt` - 集成事件类
2. `OrderCreatedIntegrationEventHandler.kt` - 集成事件处理器类

### 示例 3: 未来扩展 - CRUD 批量生成

**配置：**
```kotlin
"crud" to listOf("command", "query", "repository", "service")
```

**用户定义：**
```json
{
  "crud": [
    {
      "name": "User",
      "package": "user",
      "aggregates": ["User"],
      "desc": "用户 CRUD 操作"
    }
  ]
}
```

**生成结果：**
1. `CreateUserCmd.kt` - 创建命令
2. `GetUserQry.kt` - 查询
3. `UserRepository.kt` - 仓储接口
4. `UserService.kt` - 应用服务

## 实现细节

### 1. 数据模型复用

多个生成器共享同一个设计数据模型（例如 `DomainEventDesign`），避免重复定义：

```kotlin
// 错误做法：为每个生成器定义单独的数据模型
data class DomainEventDesign(...)
data class DomainEventHandlerDesign(...)  // ❌ 不必要

// 正确做法：复用同一个数据模型
data class DomainEventDesign(...)  // ✅ 两个生成器都使用这个
```

### 2. 生成器顺序

使用 `order` 属性控制生成器执行顺序，确保依赖关系正确：

```kotlin
DomainEventGenerator: order = 30          // 先生成事件类
DomainEventHandlerGenerator: order = 40   // 后生成处理器类（可能依赖事件类的类型信息）
```

### 3. 类型映射传递

先执行的生成器将生成的类型缓存到 `typeMapping`，后续生成器可以引用：

```kotlin
// DomainEventGenerator (order=30)
override fun onGenerated(design: Any, context: DesignContext) {
    context.typeMapping["UserCreatedDomainEvent"] =
        "com.example.domain.aggregates.user.events.UserCreatedDomainEvent"
}

// DomainEventHandlerGenerator (order=40)
override fun buildContext(design: Any, context: DesignContext) {
    val fullDomainEventType = context.typeMapping[domainEventName]  // ✅ 可以获取到
}
```

## 扩展指南

### 添加新的一对多映射

**步骤：**

1. 在 `designTypeToGeneratorTags` 中添加配置：

```kotlin
private val designTypeToGeneratorTags = mapOf(
    "domain_event_handler" to listOf("domain_event", "domain_event_handler"),
    "my_new_type" to listOf("generator1", "generator2", "generator3"),  // ← 新增
)
```

2. 创建对应的生成器类，每个生成器实现 `DesignTemplateGenerator` 接口：

```kotlin
class Generator1 : DesignTemplateGenerator {
    override val tag = "generator1"
    override val order = 10
    // ...
}

class Generator2 : DesignTemplateGenerator {
    override val tag = "generator2"
    override val order = 20
    // ...
}
```

3. 在 `GenDesignTask.generateDesignFiles()` 中注册生成器：

```kotlin
private fun generateDesignFiles(context: DesignContext) {
    val generators = listOf(
        Generator1(),
        Generator2(),
        Generator3(),
    )
    // ...
}
```

4. 在 `GenDesignTask.designTagAliasMap` 中添加别名（可选）：

```kotlin
override val designTagAliasMap = mapOf(
    "my_new_type" to "my_new_type",
    "mnt" to "my_new_type",  // 简写别名
)
```

## 优势

1. **用户友好** - 用户只需定义一份设计，即可生成多个相关文件
2. **配置驱动** - 通过配置映射，清晰表达"一对多"关系
3. **易于扩展** - 添加新的映射只需修改 `designTypeToGeneratorTags`
4. **类型安全** - 所有生成器都对设计数据类型进行校验
5. **依赖管理** - 通过 `order` 控制生成器执行顺序，确保 `typeMapping` 正确传递

## 注意事项

1. **生成器顺序很重要** - 如果生成器 B 依赖生成器 A 的输出（如类型信息），必须确保 A 的 `order` < B 的 `order`
2. **数据模型复用** - 多个生成器应该复用同一个设计数据模型，避免重复定义
3. **配置一致性** - `designTypeToGeneratorTags` 中的 tag 必须与生成器的 `tag` 属性匹配
4. **别名映射** - 如果使用别名，确保在 `designTagAliasMap` 中正确配置
