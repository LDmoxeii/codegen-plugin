# EntityGenerator 实现计划（已执行）

## 📊 依赖分析结果

### 已有数据（在 EntityContext 中）
- ✅ `tableMap` - 表信息
- ✅ `columnsMap` - 列信息
- ✅ `tablePackageMap` - 表包路径
- ✅ `entityTypeMap` - 实体类型名
- ✅ `tableModuleMap` - 表模块映射
- ✅ `tableAggregateMap` - 表聚合映射
- ✅ `relationsMap` - 关系映射
- ✅ `annotationsMap` - 注解映射（未使用）
- ✅ `enumConfigMap` - 枚举配置
- ✅ `enumPackageMap` - 枚举包路径
- ✅ `entityClassExtraImports` - 额外导入
- ✅ `aggregatesPackage` - 聚合包路径
- ✅ `resolveAggregateWithModule(tableName)` - 方法已有

### 缺失数据处理方案
1. **主键列信息** - `resolveIdColumns(columns)`
   - ✅ 解决方案：在 EntityGenerator 中实现简单过滤逻辑

2. **注解处理逻辑**
   - `processAnnotationLines(table, columns, annotationLines)`
   - `processEntityCustomerSourceFile(...)` - 处理已有文件的自定义内容
   - ✅ 解决方案：移植到 EntityGenerator 内部

3. **列数据准备**
   - `prepareColumnData(table, column, ids, relations, enums)`
   - ✅ 解决方案：移植到 EntityGenerator 内部

4. **关系数据准备**
   - `prepareRelationData(table, relations, tablePackageMap)`
   - ✅ 解决方案：移植到 EntityGenerator 内部

**结论**：不需要创建新的 ContextBuilder，所有逻辑在 EntityGenerator 内部完成。

---

## 📝 实施计划

### 步骤 1：创建 EntityGenerator.kt ✅

**位置**：`codegen-plugin/plugin/src/main/kotlin/com/only/codegen/generators/EntityGenerator.kt`

**实现策略**：
- 将 `buildEntityContext` 的逻辑完整移植到 EntityGenerator 中
- 将所有辅助方法也移植进来
- 所有 `extension.get()` 改为 `context.getString/getBoolean/getInt`

**核心方法**：

| 方法名 | 说明 | 状态 |
|--------|------|------|
| `shouldGenerate` | 判断是否生成实体 | ✅ |
| `buildContext` | 构建实体上下文 | ✅ |
| `getDefaultTemplateNode` | 获取默认模板 | ✅ |
| `resolveIdColumns` | 过滤主键列 | ✅ |
| `resolveEntityPackage` | 解析实体包路径 | ✅ |
| `resolveSourceFile` | 解析源文件路径 | ✅ |
| `resolveEntityIdGenerator` | 解析 ID 生成器 | ✅ |
| `isColumnNeedGenerate` | 判断列是否生成 | ✅ |
| `isReadOnlyColumn` | 判断只读列 | ✅ |
| `isVersionColumn` | 判断版本列 | ✅ |
| `isIdColumn` | 判断主键列 | ✅ |
| `processEntityCustomerSourceFile` | 处理自定义代码 | ✅ |
| `processAnnotationLines` | 处理注解 | ✅ |
| `generateFieldComment` | 生成字段注释 | ✅ |
| `prepareColumnData` | 准备列数据 | ✅ |
| `prepareRelationData` | 准备关系数据 | ✅ |

**文件大小**：~700 行

---

### 步骤 2：在 GenEntityTask 中注册 ⏳

修改 `GenEntityTask.kt:135-145` 的 `generateFiles` 方法：

```kotlin
private fun generateFiles(context: EntityContext) {
    val generators = listOf(
        EnumGenerator(),
        EntityGenerator(),  // 新增
    )

    generators.sortedBy { it.order }
        .forEach { generator ->
            logger.lifecycle("Generating files: ${generator.tag}")
            generateForTables(generator, context)
        }
}
```

---

### 步骤 3：更新进度报告 ⏳

更新 `重构进度报告.md`：
- EntityGenerator: ❌ → ✅
- 模板生成器完成度: 50% → 100%
- 总体进度: 62% → ~85%

---

## 🔑 关键设计决策

### 1. 不创建新的 ContextBuilder

**理由**：
- 主键列、注解处理等都是实体生成特有逻辑
- 不应污染全局 Context
- EntityGenerator 完全自包含

### 2. 完整移植而非委托

**理由**：
- 避免对 task 的依赖
- 保持 Generator 的独立性
- 便于测试和维护

### 3. 使用 context.baseMap 访问配置

**映射关系**：
- `extension.get().generation.versionField.get()` → `context.getString("versionField")`
- `extension.get().generation.readonlyFields.get()` → `context.getString("readonlyFields")`
- `extension.get().generation.entityBaseClass.get()` → `context.getString("entityBaseClass")`
- `extension.get().generation.rootEntityBaseClass.get()` → `context.getString("rootEntityBaseClass")`
- `extension.get().generation.idGenerator.get()` → `context.getString("idGenerator")`
- `extension.get().generation.idGenerator4ValueObject.get()` → `context.getString("idGenerator4ValueObject")`
- `extension.get().generation.generateDbType.get()` → `context.getBoolean("generateDbType")`
- `extension.get().outputEncoding.get()` → `context.getString("outputEncoding")`
- `extension.get().basePackage.get()` → `context.getString("basePackage")`

---

## ⚠️ 注意事项

1. **Inflector 依赖**
   - 使用 `Inflector.pluralize()` 处理复数形式
   - 已在 imports 中引入

2. **SqlSchemaUtils 工具类**
   - 所有数据库元数据解析依赖此类
   - 静态方法调用

3. **文件操作**
   - `processEntityCustomerSourceFile` 需要读取已有文件
   - 使用 `File` API 和 `charset()` 函数

4. **模板别名映射**
   - `putContext()` 方法使用模板别名系统
   - 自动处理多种命名风格

---

## 📦 文件清单

| 文件 | 状态 | 说明 |
|------|------|------|
| `EntityGenerator.kt` | ✅ 已创建 | ~700行，包含所有辅助方法 |
| `GenEntityTask.kt` | ⏳ 待修改 | 注册 EntityGenerator |
| `重构进度报告.md` | ⏳ 待更新 | 更新完成状态 |

---

## 🎯 执行状态

- [x] 步骤 1：创建 EntityGenerator.kt
- [ ] 步骤 2：在 GenEntityTask 中注册
- [ ] 步骤 3：更新重构进度报告

---

**创建时间**：2025-01-10 18:00
**最后更新**：2025-01-10 18:05
