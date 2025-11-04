基于唯一约束的校验器 + 查询 + 查询处理器 生成设计

本文档描述在 GenAggregateTask + AggregateTemplateGenerator 框架下，基于数据库“唯一约束/唯一索引”自动生成校验器、查询、查询处理器的设计与落地步骤。

**目标**
- 对每张表自动识别唯一约束；对每个唯一约束生成：
  - 应用层校验器（类级注解）
  - 应用层查询（Exists 判定）
  - 适配层查询处理器（Exists 查询实现）
- 统一命名：Unique + 实体名 + 唯一列后缀（例如：UniqueCategoryCode / UniqueCategoryCodeQry / UniqueCategoryCodeQryHandler）。
- 模板选择规则：pattern = 以 Unique 开头（^Unique.*$），并为此 pattern 提供专用模板，避免落到现有通用骨架模板。

**数据来源与上下文**
- 使用 information_schema 视图（MySQL/PostgreSQL）：
  - 表级唯一约束：`information_schema.table_constraints`（constraint_type = 'UNIQUE'）
  - 约束列明细：`information_schema.key_column_usage`（按 constraint_name + table_name 关联，按 ordinal_position 排序）
- 过滤策略：
  - 排除主键（PRIMARY）与主键等价的唯一约束。
  - 保留复合唯一约束并维护列顺序。
  - 若唯一约束包含逻辑删除列（配置项 deletedField），生成时只在 Handler where 中使用，不作为请求参数（可选，见“逻辑删除处理”）。
- 上下文新增：
  - 在 AggregateContext/MutableAggregateContext 增加 `uniqueConstraintsMap: Map<String, List<UniqueConstraint>>`。
  - UniqueConstraint 结构（Map 表示即可，保持现有风格）：
    - constraintName: String
    - columns: List<{ columnName: String, ordinal: Int }>
- 构建器新增：
  - `context/aggregate/builders/UniqueConstraintContextBuilder.kt`
  - order=25（在 Table/Column/Relation 之后，Aggregate 之前），负责填充 `uniqueConstraintsMap`。

**命名规范**
- 实体名：使用 `ctx.entityTypeMap[tableName]`（如 Category）。
- 唯一名后缀：将唯一列名转为 UpperCamelCase 并按顺序拼接（code → Code；tenant_id+name → TenantIdName）。
- 产物命名：
  - 校验器：Unique{Entity}{Cols}
  - 查询：Unique{Entity}{Cols}Qry
  - 查询处理器：Unique{Entity}{Cols}QryHandler
- 排除 ID 参数：`exclude{Entity}Id`（如 excludeCategoryId）。主键名与类型从列元数据推断（默认 Long）。

**生成流程接入**
- 在 `codegen-plugin/plugin/src/main/kotlin/com/only4/codegen/GenAggregateTask.kt`：
  - `buildGenerationContext()` 注册 `UniqueConstraintContextBuilder`（order=25）。
  - `generateFiles()` 注册 3 个生成器（按顺序）：
    - UniqueQueryGenerator（order≈15）
    - UniqueQueryHandlerGenerator（order≈20）
    - UniqueValidatorGenerator（order≈28）
- 三个生成器实现 `AggregateTemplateGenerator`，采用“多次迭代同一表”的模式（参考 DomainEvent* 系列）：
  - `shouldGenerate(table)`：在 `uniqueConstraintsMap[table]` 中找出第一个尚未生成的目标名（`!ctx.typeMapping.containsKey(name)`）。
  - `generatorName(table)`：返回下一个未生成目标名。
  - `onGenerated(table)`：将类名与 FQN 写入 `ctx.typeMapping`（供后续导入）。

**包路径与模块定位**
- UniqueQueryGenerator：
  - modulePath = `ctx.applicationPath`
  - templatePackage = `refPackage(ctx.templatePackage["query"])`
  - package = `refPackage(ctx.resolveAggregateWithModule(tableName))`（如 `.category`）
- UniqueQueryHandlerGenerator：
  - modulePath = `ctx.adapterPath`
  - templatePackage = `refPackage(ctx.templatePackage["query_handler"])`
  - package = `refPackage(ctx.resolveAggregateWithModule(tableName))`
- UniqueValidatorGenerator：
  - modulePath = `ctx.applicationPath`
  - templatePackage = `refPackage(ctx.templatePackage["validator"])`（通常 application.validator）
  - package = ""（与示例一致，平铺在 application/validator）

**模板与 pattern（新增）**
- 在 `codegen-plugin/plugin/src/main/resources/templates/` 新增：
  - `unique_query.kt.peb`（pattern=^Unique.*$）
  - `unique_query_handler.kt.peb`（pattern=^Unique.*$）
  - `unique_validator.kt.peb`（pattern=^Unique.*$）
- 每个生成器的 `getDefaultTemplateNodes()` 增加对应条目：
  - name = `{{ Query }}.kt` / `{{ QueryHandler }}.kt` / `{{ Validator }}.kt`
  - format = resource，conflict = skip。

**模板上下文字段（关键）**
- 通用：
  - Entity：实体名（如 Category）
  - Aggregate：聚合（如 category）
  - Comment：表注释
  - imports：ImportManager + 动态导入
- UniqueQuery（Unique{Entity}{Cols}Qry）：
  - Query：完整查询名
  - RequestParams：唯一列参数（name/type），如 `[{name: code, type: String}]`
  - ExcludeIdParam：`{name: excludeCategoryId, type: Long?}`
- UniqueQueryHandler（Unique{Entity}{Cols}QryHandler）：
  - Query：查询名（含 Qry）
  - whereClauses：
    - `where(table.<prop> eq request.<prop>)`（每个唯一列）
    - `where(table.id ne? request.exclude{Entity}Id)`（主键名按列元数据映射；默认 id）
  - 依赖导入：`KSqlClient`、`eq`、``ne?``、`exists`
  - ShareModel 导入（Jimmer 投影扩展）：
    - `{{ basePackage }}{{ refPackage(ctx.templatePackage["query"]) }}._share.model.{Entity}`
    - `..._share.model.{field}`、`..._share.model.id` 等扩展属性
- UniqueValidator（类级注解）：
  - Validator：注解名（Unique{Entity}{Cols}）
  - Target：`AnnotationTarget.CLASS`
  - 注解参数：
    - 为每个唯一列生成 `{prop}Field: String = "<prop>"`
    - 为排除 ID 生成 `{entityLower}IdField: String = "<entityLower>Id"`
  - 运行逻辑：
    - 通过 `kotlin.reflect.full.memberProperties` 读取参数值
    - 唯一列值均为空/空白则放行（避免空输入误判）
    - 读取排除 ID（可空）
    - 调用 `Mediator.queries.send({{ Query }}.Request(...))`，返回 `!result.exists`
  - 导入：`com.only4.cap4k.ddd.core.Mediator`、`kotlin.reflect.full.memberProperties`、唯一查询类型 FQN

**逻辑删除处理（可选增强）**
- 若 `deletedField` 存在并出现在唯一约束内：
  - 不暴露为请求参数；
  - 在 Handler where 中加入 `where(table.deleted eq false)`（或依赖全局过滤策略）。

**示例映射**
- 表：`category`，唯一约束：(`code`)
  - 生成：
    - 应用层校验器：`only-danmuku/only-danmuku-application/src/main/kotlin/edu/only4/danmuku/application/validator/UniqueCategoryCode.kt`
    - 应用层查询：`application/queries/category/UniqueCategoryCodeQry.kt`
    - 适配层处理器：`only-danmuku/only-danmuku-adapter/src/main/kotlin/edu/only4/danmuku/adapter/application/queries/category/CategoryExistsByCodeQryHandler.kt`
  - Handler where：
    - `where(table.code eq request.code)`
    - `where(table.id ne? request.excludeCategoryId)`

注：示例工程中名称不一致，但在本方案中将统一规范为 `Unique + 实体名 + 列名`。

**边界与限制**
- 复合主键：默认 `exclude{Entity}Id` 类型 Long?。若检测到复合主键，可回退为不生成排除 ID（或生成 Key 类型，后续扩展）。
- 多个唯一约束：逐个生成；通过 `ctx.typeMapping` 去重（与 DomainEvent* 多迭代模式一致）。
- 命名冲突：采用列名拼接降低冲突概率。
- 表达式索引/函数索引：暂不支持；后续可按方言增加解析。
- 视图与物化视图：不参与生成。

**实施清单**
1) 新增上下文构建器：
   - `codegen-plugin/plugin/src/main/kotlin/com/only4/codegen/context/aggregate/builders/UniqueConstraintContextBuilder.kt`
2) 上下文接口扩展：
   - `codegen-plugin/plugin/src/main/kotlin/com/only4/codegen/context/aggregate/AggregateContext.kt`
   - `codegen-plugin/plugin/src/main/kotlin/com/only4/codegen/context/aggregate/MutableAggregateContext.kt`
3) 任务编排：
   - `GenAggregateTask.buildGenerationContext()` 注册 UniqueConstraintContextBuilder（order=25）
   - `GenAggregateTask.generateFiles()` 注册 3 个生成器（唯一查询/处理器/校验器）
4) 新增生成器：
   - `generators/aggregate/unique/UniqueQueryGenerator.kt`
   - `generators/aggregate/unique/UniqueQueryHandlerGenerator.kt`
   - `generators/aggregate/unique/UniqueValidatorGenerator.kt`
5) 新增模板：
   - `plugin/src/main/resources/templates/unique_query.kt.peb`
   - `plugin/src/main/resources/templates/unique_query_handler.kt.peb`
   - `plugin/src/main/resources/templates/unique_validator.kt.peb`

**验收标准**
- 任意存在唯一约束的表，均生成对应的校验器/查询/查询处理器文件，类名与包路径符合命名规范。
- 生成的 Handler 包含完整 where 逻辑（唯一列相等 + 可选地排除 ID + 可选的 deleted 过滤）。
- 模板选择由 `pattern=^Unique.*$` 精确命中，不影响既有通用骨架模板。
- 不重复生成（`ctx.typeMapping` 去重有效）。

**后续扩展**
- 方言层面扩展对表达式索引、部分索引、过滤索引的解析与适配。
- 复合主键支持统一 Key 类型或可配置的排除字段。
- 允许通过注解/配置重写默认生成的查询/校验逻辑。

**参考**
- 现有生成器：
  - `codegen-plugin/plugin/src/main/kotlin/com/only4/codegen/generators/design/QueryGenerator.kt`
  - `codegen-plugin/plugin/src/main/kotlin/com/only4/codegen/generators/design/QueryHandlerGenerator.kt`
  - `codegen-plugin/plugin/src/main/kotlin/com/only4/codegen/generators/design/ValidatorGenerator.kt`
- 示例实现：
  - `only-danmuku/only-danmuku-application/src/main/kotlin/edu/only4/danmuku/application/validator/UniqueCategoryCode.kt`
  - `only-danmuku/only-danmuku-adapter/src/main/kotlin/edu/only4/danmuku/adapter/application/queries/category/CategoryExistsByCodeQryHandler.kt`

