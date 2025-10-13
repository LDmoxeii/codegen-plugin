# GenDesignTask 使用指南

## 概述

`GenDesignTask` 是一个全新的设计驱动代码生成器,支持从 JSON 设计文件生成 DDD 应用层和领域层代码。

## 核心特性

- **灵活的数据源**: 支持 JSON 设计文件 + KSP 聚合元信息混合使用
- **弱实体关联**: 设计不强制依赖聚合根,支持跨实体查询、独立服务等场景
- **两阶段执行**: Context Building → File Generation
- **完全融入现有框架**: 复用 `BaseContext`、`ContextBuilder<T>`、Generator 接口

## 配置示例

### build.gradle.kts

```kotlin
codegen {
    basePackage.set("com.example.demo")
    multiModule.set(true)

    // 配置设计文件
    designFiles.from("design/_gen.json", "design/extra.json")

    // 配置 KSP 元数据目录 (可选,用于关联聚合信息)
    kspMetadataDir.set(layout.buildDirectory.dir("generated/ksp/main/resources").get().asFile.absolutePath)

    // 设计文件编码
    designEncoding.set("UTF-8")
}
```

## JSON 设计文件格式

### design/_gen.json

```json
{
  "cmd": [
    {
      "aggregate": "category",
      "name": "category.CreateCategory",
      "desc": "创建分类"
    },
    {
      "name": "order.CreateOrder",
      "desc": "创建订单"
    }
  ],
  "qry": [
    {
      "name": "category.GetCategoryTree",
      "desc": "获取分类树形结构",
      "metadata": {
        "crossAggregate": true,
        "includes": ["category", "product"]
      }
    },
    {
      "aggregate": "category",
      "name": "getCategoryList",
      "desc": "获取分类列表"
    }
  ],
  "de": [
    {
      "aggregate": "category",
      "name": "category.CategoryCreated",
      "desc": "分类已创建",
      "metadata": {
        "persist": true
      }
    }
  ],
  "saga": [
    {
      "name": "order.OrderCreationSaga",
      "desc": "订单创建流程编排"
    }
  ],
  "cli": [
    {
      "name": "payment.PaymentGatewayClient",
      "desc": "支付网关客户端"
    }
  ],
  "svc": [
    {
      "name": "order.OrderPricingService",
      "desc": "订单定价服务",
      "aggregate": null
    }
  ]
}
```

## 设计元素类型

| 类型缩写 | 完整名称 | 描述 | 是否强关联聚合 |
|---------|---------|------|---------------|
| `cmd` | Command | 命令 | 可选 |
| `qry` | Query | 查询 | 可选 |
| `saga` | Saga | 流程编排 | 可选 |
| `cli` | Client | 防腐层客户端 | 可选 |
| `ie` | IntegrationEvent | 集成事件 | 可选 |
| `de` | DomainEvent | 领域事件 | **必须** |
| `svc` | DomainService | 领域服务 | 可选 |

## 执行任务

```bash
# 生成设计元素代码
./gradlew genDesign

# 清理 + 生成
./gradlew clean genDesign
```

## 生成的文件结构

```
project-application/
├── src/main/kotlin/com/example/demo/
│   └── application/
│       ├── commands/
│       │   └── category/
│       │       ├── CreateCategoryCmd.kt
│       │       └── CreateCategoryRequest.kt, CreateCategoryResponse.kt
│       ├── queries/
│       │   └── category/
│       │       ├── GetCategoryTreeQry.kt
│       │       └── GetCategoryTreeRequest.kt, GetCategoryTreeResponse.kt
│       ├── sagas/
│       │   └── order/
│       │       └── OrderCreationSaga.kt
│       └── clients/
│           └── payment/
│               └── PaymentGatewayCli.kt

project-domain/
├── src/main/kotlin/com/example/demo/
│   └── domain/
│       ├── aggregates/
│       │   └── category/
│       │       └── events/
│       │           └── CategoryCreatedDomainEvent.kt
│       └── services/
│           └── order/
│               └── OrderPricingService.kt
```

## 设计字段说明

### 基本字段

- **type**: 设计类型缩写 (cmd/qry/saga/cli/ie/de/svc)
- **name**: 相对类名
  - 格式1: `package.path.ClassName` (如 `category.CreateCategory`)
  - 格式2: `ClassName` (如 `CreateCategory`)
- **aggregate**: 可选聚合名 (领域事件必填)
- **desc**: 描述信息

### metadata 扩展字段

可以在 `metadata` 中添加自定义字段:

```json
{
  "qry": [
    {
      "name": "MultiAggregateQuery",
      "desc": "跨聚合查询",
      "metadata": {
        "crossAggregate": true,
        "includes": ["order", "product", "customer"],
        "cacheTime": 300,
        "async": true
      }
    }
  ]
}
```

## 与 KSP 元数据集成

如果配置了 `kspMetadataDir`,生成器会自动加载聚合元信息:

```json
// aggregates.json (由 KSP 生成)
[
  {
    "name": "category",
    "fullName": "com.example.demo.domain.aggregates.category.Category",
    "packageName": "com.example.demo.domain.aggregates.category",
    "idType": "Long",
    "aggregateRoot": {
      "name": "Category",
      "fullName": "com.example.demo.domain.aggregates.category.Category",
      "isAggregateRoot": true,
      "idType": "Long"
    }
  }
]
```

在命令/领域事件生成时,会自动关联聚合元数据,提供 ID 类型等信息。

## 架构优势

1. **高度解耦**: 不依赖数据库,不依赖实体,纯设计驱动
2. **易于扩展**: 新增设计类型只需添加 Builder + Generator
3. **复用框架**: 共享 typeMapping、模板别名系统
4. **灵活组合**: 可与 GenEntityTask、GenArchTask 并行使用

## 注意事项

1. **领域事件必须关联聚合**: `de` 类型的 `aggregate` 字段不能为空
2. **名称规范**: 类名会自动添加后缀 (Cmd/Qry/Saga/Cli/Service/DomainEvent)
3. **模板路径**: 默认模板路径见各 Generator 的 `getDefaultTemplateNode()`
4. **冲突处理**: 默认 `conflict = "skip"`,已存在的文件不会被覆盖

## 下一步扩展

- 添加更多设计类型 (Specification, Factory, ...)
- 支持模板自定义路径配置
- 支持更丰富的 metadata 解析
- 支持设计文件热加载

---

**最后更新**: 2025-10-13
