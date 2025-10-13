# DesignTemplateGenerator 重构总结

## 重构动机

原实现存在以下问题:
1. **缺少 `generatorFullName()` 和 `generatorName()` 方法** - 不符合框架规范
2. **`getDefaultTemplateNode()` 实现不规范** - 直接使用构造函数而非 apply 块
3. **`buildContext()` 未使用 `context.putContext()` 扩展方法** - 无法利用模板别名系统
4. **缺少 `companion object` 常量** - 包路径硬编码在代码中
5. **`shouldGenerate()` 缺少重复检查** - 可能导致重复生成

## 重构参考

参考了现有的 `AggregateTemplateGenerator` 和 `RepositoryGenerator` 实现规范。

## 重构内容

### 1. 接口层重构

**原接口** (`DesignTemplateGenerator.kt`):
```kotlin
interface DesignTemplateGenerator {
    val tag: String
    val order: Int
    fun shouldGenerate(design: Any, context: DesignContext): Boolean
    fun buildContext(design: Any, context: DesignContext): Map<String, Any?>
    fun getDefaultTemplateNode(): TemplateNode
    fun onGenerated(design: Any, context: DesignContext) {}
}
```

**新增方法**:
```kotlin
fun generatorFullName(design: Any, context: DesignContext): String
fun generatorName(design: Any, context: DesignContext): String
```

### 2. 实现类重构模式

以 `CommandGenerator` 为例:

#### 2.1 添加 companion object 常量
```kotlin
companion object {
    const val COMMAND_PACKAGE = "application.commands"
}
```

#### 2.2 改进 shouldGenerate()
```kotlin
override fun shouldGenerate(design: Any, context: DesignContext): Boolean {
    if (design !is CommandDesign) return false

    // 避免重复生成
    if (context.typeMapping.containsKey(generatorName(design, context))) {
        return false
    }

    return true
}
```

#### 2.3 规范 buildContext()
```kotlin
override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
    require(design is CommandDesign)

    val resultContext = context.baseMap.toMutableMap()

    with(context) {
        // 使用 putContext() 扩展方法,支持模板别名
        resultContext.putContext(tag, "modulePath", applicationPath)
        resultContext.putContext(tag, "package", COMMAND_PACKAGE)
        resultContext.putContext(tag, "templatePackage", refPackage(COMMAND_PACKAGE))

        resultContext.putContext(tag, "Name", design.name)
        resultContext.putContext(tag, "Command", design.name)
        // ... 其他字段
    }

    return resultContext
}
```

#### 2.4 实现 generatorFullName() 和 generatorName()
```kotlin
override fun generatorFullName(design: Any, context: DesignContext): String {
    require(design is CommandDesign)
    val basePackage = context.getString("basePackage")
    val fullPackage = concatPackage(basePackage, COMMAND_PACKAGE, design.packagePath)
    return concatPackage(fullPackage, design.name)
}

override fun generatorName(design: Any, context: DesignContext): String {
    require(design is CommandDesign)
    return design.name
}
```

#### 2.5 规范 getDefaultTemplateNode()
```kotlin
override fun getDefaultTemplateNode(): TemplateNode {
    return TemplateNode().apply {
        type = "file"
        tag = this@CommandGenerator.tag
        name = "{{ Name }}.kt"
        format = "resource"
        data = "templates/application/command/Command.peb"
        conflict = "skip"
    }
}
```

#### 2.6 改进 onGenerated()
```kotlin
override fun onGenerated(design: Any, context: DesignContext) {
    if (design is CommandDesign) {
        val fullName = generatorFullName(design, context)
        context.typeMapping[generatorName(design, context)] = fullName
        logger.lifecycle("Generated command: $fullName")
    }
}
```

### 3. 已重构文件清单

✅ **接口层** (1个文件)
- `DesignTemplateGenerator.kt` - 新增 `generatorFullName()` 和 `generatorName()` 方法

✅ **实现层** (7个文件)
- `CommandGenerator.kt` - 完整重构
- `QueryGenerator.kt` - 完整重构
- `DomainEventGenerator.kt` - 完整重构
- `SagaGenerator.kt` - 完整重构
- `ClientGenerator.kt` - 完整重构
- `IntegrationEventGenerator.kt` - 完整重构
- `DomainServiceGenerator.kt` - 完整重构

## 关键改进点

### 1. 包路径管理
**改进前**:
```kotlin
contextMap["modulePath"] = context.applicationPath
contextMap["templatePackage"] = "application.commands"  // 硬编码
```

**改进后**:
```kotlin
companion object {
    const val COMMAND_PACKAGE = "application.commands"
}

resultContext.putContext(tag, "modulePath", applicationPath)
resultContext.putContext(tag, "package", COMMAND_PACKAGE)
resultContext.putContext(tag, "templatePackage", refPackage(COMMAND_PACKAGE))
```

### 2. 模板别名支持
**改进前**: 需要自己实现 `putContext()` 扩展方法
```kotlin
private fun MutableMap<String, Any?>.putContext(...) {
    val key = "$tag.$variable"
    val aliases = context.templateAliasMap[key] ?: listOf(variable)
    aliases.forEach { this[it] = value }
}
```

**改进后**: 直接使用 `BaseContext.putContext()` 扩展方法
```kotlin
with(context) {
    resultContext.putContext(tag, "Name", design.name)
    resultContext.putContext(tag, "Command", design.name)
}
```

### 3. 类型映射管理
**改进前**:
```kotlin
context.typeMapping[design.name] = fullName
```

**改进后**:
```kotlin
context.typeMapping[generatorName(design, context)] = generatorFullName(design, context)
```

### 4. TemplateNode 构建
**改进前**:
```kotlin
TemplateNode(
    type = "file",
    name = "{{ Name }}.kt",
    conflict = "skip",
    tag = tag,
    encoding = null,
    pattern = null,
    templatePath = "templates/..."
)
```

**改进后**:
```kotlin
TemplateNode().apply {
    type = "file"
    tag = this@CommandGenerator.tag
    name = "{{ Name }}.kt"
    format = "resource"
    data = "templates/application/command/Command.peb"
    conflict = "skip"
}
```

## 架构对比

| 对比项 | 重构前 | 重构后 |
|--------|--------|--------|
| **接口方法数** | 5个 | 7个 (+2) |
| **类型名称管理** | 直接使用 design.name | generatorName() + generatorFullName() |
| **包路径** | 硬编码字符串 | companion object 常量 |
| **模板别名** | 自定义扩展方法 | 使用 BaseContext.putContext() |
| **重复检查** | 无 | typeMapping 检查 |
| **TemplateNode** | 构造函数 | apply 块 |
| **代码一致性** | 与 EntityGenerator 不一致 | 完全一致 |

## 兼容性

- ✅ 保持原有接口签名不变 (新增方法不影响已有代码)
- ✅ `GenDesignTask` 无需修改 (仍使用原有方法调用)
- ✅ 向后兼容现有模板

## 未来扩展

现在所有 Generator 都遵循统一规范,便于:
1. 新增设计类型 (复制粘贴任一 Generator 即可)
2. 自动化测试 (接口方法规范化)
3. 代码生成器验证 (检查 typeMapping 重复)
4. IDE 重构支持 (方法签名统一)

---

**重构完成时间**: 2025-10-13
**重构文件数**: 8个
**新增代码行数**: ~100行
**删除代码行数**: ~50行
**净增长**: ~50行 (主要是规范化代码)
