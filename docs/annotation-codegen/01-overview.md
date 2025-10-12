# 1. 方案概述

[← 返回目录](README.md) | [下一章：核心架构设计 →](02-architecture.md)

---

## 1.1 设计目标

本方案旨在为 codegen-plugin 添加一个独立的、基于 KSP 注解处理的代码生成子系统，与现有的基于数据库的代码生成系统（GenEntityTask）并行存在，共享核心架构模式但使用独立的上下文体系。

### 核心目标

- **保持架构一致性**：延续 Context + ContextBuilder + Generator 的三层架构
- **独立性**：与 EntityContext 解耦，创建独立的 AnnotationContext 体系
- **可扩展性**：支持多种基于注解的代码生成场景（Repository、Service、Controller、Mapper 等）
- **灵活性**：可单独运行，不依赖数据库连接
- **可组合性**：可与 GenEntityTask 配合使用，形成完整的代码生成流水线
- **类型安全**：使用 KSP 获得编译器级别的准确性和完整类型信息

## 1.2 适用场景

| 场景 | 生成内容 | 扫描注解 | 输出位置 |
|------|---------|---------|---------|
| Repository 生成 | JPA Repository 接口和实现 | `@Aggregate(root=true)` | adapter 层 |
| Service 生成 | Application Service | `@Aggregate(root=true)` | application 层 |
| Controller 生成 | REST Controller | `@Aggregate(root=true)` + `@RestResource` | adapter/web 层 |
| Mapper 生成 | DTO Mapper | `@Mappable` | application/dto 层 |
| Query 生成 | Query DSL | `@Entity` | domain 层 |

### 使用示例

假设有以下领域模型：

```kotlin
@Aggregate(aggregate = "User", root = true)
@Entity
class User(
    @Id
    var id: Long = 0L,
    var name: String? = null,
    var email: String? = null
)
```

**生成的 Repository：**

```kotlin
@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findById(id: Long): User?
    fun existsById(id: Long): Boolean
    fun deleteById(id: Long)
}
```

**生成的 Service：**

```kotlin
@Service
@Transactional
class UserService(
    private val userRepository: UserRepository
) {
    fun create(entity: User): User
    fun findById(id: Long): User?
    fun update(entity: User): User
    fun delete(id: Long)
    fun findAll(): List<User>
}
```

## 1.3 KSP vs 数据库生成对比

| 维度 | GenEntityTask | GenAnnotationTask（KSP） |
|------|---------------|-------------------------|
| **数据源** | 数据库元数据 | 编译时注解信息 |
| **上下文** | EntityContext | AnnotationContext |
| **主要 Map** | tableMap, columnsMap, relationsMap | classMap, annotationMap, aggregateMap |
| **扫描目标** | 数据库表 | Kotlin 源代码 |
| **依赖** | JDBC 驱动 | KSP API |
| **运行时机** | 编译前（读取数据库） | 编译时（KSP 处理器） |
| **生成位置** | domain 层 | adapter/application 层 |
| **准确性** | 数据库约束级别 | 编译器级别（类型安全） |
| **类型信息** | 有限（从数据库推断） | 完整（编译器类型系统） |
| **性能** | 依赖数据库连接速度 | 编译时处理，支持增量编译 |
| **IDE 集成** | 无 | 完整 IDE 支持，实时错误检查 |

### 为什么选择 KSP？

1. **类型安全**
   - KSP 提供编译器级别的类型信息
   - 避免字符串解析错误
   - 完整支持泛型、继承等复杂类型

2. **性能优越**
   - 比 KAPT 快 2 倍以上
   - 支持增量编译，只处理变更的文件
   - 零运行时开销

3. **Kotlin 原生**
   - 完全支持 Kotlin 特性
   - 不需要生成 Java Stub
   - 与 Kotlin 编译器深度集成

4. **IDE 支持**
   - IntelliJ IDEA 原生支持
   - 实时错误检查
   - 代码导航和自动补全

## 1.4 与现有系统的关系

### 互补关系

```
┌─────────────────┐         ┌──────────────────┐
│   Database      │         │   Domain Code    │
│   (Tables)      │         │   (Entities)     │
└────────┬────────┘         └────────┬─────────┘
         │                           │
         │ GenEntityTask             │ GenAnnotationTask
         │ (读取数据库)              │ (扫描注解)
         ▼                           ▼
┌─────────────────┐         ┌──────────────────┐
│  Domain Layer   │         │ Adapter/App      │
│  - Entity       │    ──>  │ - Repository     │
│  - Enum         │         │ - Service        │
│  - Schema       │         │ - Controller     │
└─────────────────┘         └──────────────────┘
```

### 完整的代码生成流水线

```bash
# Step 1: 从数据库生成领域模型
./gradlew genEntity

# 输出：
# - domain/User.kt
# - domain/SUser.kt (Schema)
# - domain/UserStatus.kt (Enum)

# Step 2: 基于领域模型生成基础设施代码
./gradlew genAnnotation

# 输出：
# - adapter/UserRepository.kt
# - application/UserService.kt
# - adapter/web/UserController.kt
```

## 1.5 设计原则

### 1. 架构一致性

延续现有的三层架构模式：

```
Context (数据模型)
   ↓
ContextBuilder (数据采集)
   ↓
Generator (代码生成)
```

### 2. 独立性

- AnnotationContext 与 EntityContext 完全独立
- 使用独立的 Builder 接口（AnnotationContextBuilder）
- 使用独立的 Generator 接口（AnnotationTemplateGenerator）

### 3. 复用性

- 复用 BaseContext 的配置和路径管理
- 复用 AbstractCodegenTask 的模板渲染逻辑
- 复用现有的工具方法（resolvePackageDirectory 等）

### 4. 可扩展性

- 易于添加新的 Generator（只需实现接口）
- 易于添加新的 Builder（只需实现接口）
- 支持自定义模板

### 5. 类型安全

- 利用 KSP 获得完整的类型信息
- 避免字符串解析和类型推断
- 编译时错误检查

## 1.6 方案优势

### 架构优势

- ✅ 统一的设计模式
- ✅ 清晰的职责分离
- ✅ 高度可扩展
- ✅ 独立性强

### 技术优势

- ✅ 类型安全
- ✅ 性能优越
- ✅ IDE 集成
- ✅ 灵活性高

### 开发优势

- ✅ 学习成本低
- ✅ 调试友好
- ✅ 测试简单
- ✅ 维护容易

---

[← 返回目录](README.md) | [下一章：核心架构设计 →](02-architecture.md)
