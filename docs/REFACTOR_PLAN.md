# 抽象化重构计划（Refactor Plan）

## 目标与范围
- 目标：将当前以 Pebble+模板树为中心的实现抽象为“提取器/策略/输出/配置/校验”分层，降低耦合、提高可测试性与扩展性，同时保持向后兼容与可渐进迁移。
- 范围：`plugin/` Gradle 插件与模板系统、`ksp-processor/` 元数据模型与解析、构建与发布流程、基础文档。

## 里程碑与交付
- M1 基础卫生与兼容层（0.2.x）
  - 统一 JSON 库；外置别名；抽取模板合并器；最小化全局状态；构建配置与缓存输入/输出标注。
- M2 引入抽象引擎（0.3.0-SNAPSHOT）
  - 定义并落地接口层：`IMetadataExtractor`/`IGenerationStrategy`/`IOutputManager`/`IConfigurationManager`/`IMetadataValidator`。（已创建接口骨架）
  - 加入开关：`codegen.generation.engine=legacy|v2`，默认 `legacy`。（已在 `CodegenExtension` 中新增属性）
  - [x] 最小闭环：在 Design 流水线中，当 engine=`v2` 时，使用 `V2ValidatorStrategy` + `FileOutputManager` 生成 `validator` 类（其余保持 legacy）。
- M3 渐进迁移与增强测试（0.3.x）
  - 在“枚举/DTO”等有限域试点 KotlinPoet 生成策略；完善单元/集成测试与基准用例。
- M4 文档与发布（0.3.x）
  - 更新文档与迁移指南；发布版本与变更日志。

## 技术方案与任务清单
- 基础卫生
  - [x] 统一 JSON：移除 `fastjson`，全量改用 `gson`（影响：`GenArchTask` 的 `JSON.parseObject` → `Gson`）。
  - [x] 数据库驱动改为可选：将 `mysql`/`postgresql` 从实现依赖调整为可选/由使用方提供（或 `runtimeOnly`）。
  - [ ] 全局状态收敛：
    - [ ] 将 `PebbleInitializer` 使用限制在渲染入口，避免测试干扰。
    - [x] 替换 `PathNode` 的 `ThreadLocal` 目录依赖为上下文 `templateBaseDir`，并移除调用点的 `setDirectory`。
  - [ ] Gradle 任务可缓存化：
    - [x] 标注关键输入：`genArch` 的 `@InputFile`（模板），`genDesign` 的 `@InputFiles`（设计文件）。
    - [x] 标注输出目录（模块根目录）。
    - [x] 清理内部 API 依赖。
    - [ ] 评估 `@CacheableTask` 与 Worker API。
  - [x] 清理 `org.gradle.internal.*` 直接导入（已移除未使用/可替代用法）。
- 模板系统
  - [x] 抽取通用“模板选择与合并”组件（`TemplateMerger`），替换 `GenAggregateTask`/`GenDesignTask` 内重复逻辑。
  - [x] 别名外置：引入 `resources/aliases/{aggregate,design}.json`，并提供 `CodegenExtension` 覆盖入口。
- 抽象引擎
  - [ ] 定义接口：
    - [ ] `IMetadataExtractor`（DB/KSP 两实现）；
    - [ ] `IGenerationStrategy`（`PebbleStrategy`、`KotlinPoetStrategy`）；
    - [ ] `IOutputManager`（`FileOutputManager`、`MemoryOutputManager`）；
    - [ ] `IConfigurationManager`、`IMetadataValidator`。
  - [ ] 适配层：在现有任务中注入适配器，保留 legacy 路径。
  - [ ] 增加特性开关与回退策略（`legacy` ↔ `v2`）。
- 测试与质量
  - [ ] 单测：`TemplateMerger`、别名映射、`OutputManager`、`PebbleInitializer` 线程安全；
  - [ ] 集成测试：样例 `design.json` + 伪 DB 元数据，验证三任务端到端；
  - [ ] 基准与快照：关键模板输出加入快照对比。
- 文档与样例
  - [ ] 增补 `docs/`：抽象层架构、接口示例、迁移指南；
  - [x] 在 `AGENTS.md` 增加开发者提示与常用命令片段（别名配置）。

## 风险与回滚
- 风险：
  - 模板合并行为变化导致生成差异；构建缓存启用后与全局状态冲突；第三方依赖升级风险。
- 缓解与回滚：
  - 提供特性开关（默认 legacy），保留旧路径；以“模块/模板”为单位渐进迁移；引入输出快照比对；必要时一键切回 `legacy`。

## 验收标准
- 构建：`./gradlew :plugin:build :ksp-processor:build` 绿色，开启 `--build-cache` 无不稳定。
- 行为：在给定样例输入下，`legacy` 与 `v2` 生成结果等价（或经过审阅的预期差异）。
- 质量：关键模块单测覆盖率提升（≥70% for merger/strategy/output），端到端集成测试通过。

## 追踪与协作
- 建议以 Issue/PR 驱动（标签：`area:templates`、`engine:v2`、`task:cacheable`）。
- 参考代码定位：
  - 任务入口：`plugin/src/main/kotlin/com/only4/codegen/GenArchTask.kt`、`GenAggregateTask.kt`、`GenDesignTask.kt`
  - 模板与渲染：`plugin/src/main/kotlin/com/only4/codegen/template`、`pebble/`
  - 配置：`plugin/src/main/kotlin/com/only4/codegen/CodegenExtension.kt`
  - 构建：`plugin/build.gradle.kts`、`ksp-processor/build.gradle.kts`

> 注：当前已在新分支进行重构，请以小步提交与可回滚 PR 合并，优先完成 M1 以降低后续风险。
