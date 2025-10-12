# 基于注解的代码生成器技术方案（KSP 方案）

## 📚 文档目录

本技术方案文档已按章节拆分，便于阅读和维护。

### 核心文档

1. **[方案概述](01-overview.md)**
   - 设计目标
   - 适用场景
   - KSP vs 数据库生成对比

2. **[核心架构设计](02-architecture.md)**
   - 整体架构图
   - 关键设计决策
   - 与 GenEntityTask 的对比

3. **[核心接口和类设计](03-core-interfaces.md)**
   - BaseContext 层调整
   - AnnotationContext 层
   - Builder 层
   - Generator 层
   - Task 层

4. **[KSP Processor 实现](04-ksp-processor.md)**
   - 项目结构
   - 依赖配置
   - AnnotationProcessor 实现
   - 元数据模型
   - 服务注册

5. **[配置和模板](05-configuration-templates.md)**
   - CodegenExtension 扩展
   - 使用示例
   - Repository 模板
   - Service 模板

6. **[执行流程](06-execution-flow.md)**
   - 典型使用场景
   - Task 依赖配置
   - 测试 KSP Processor
   - KSP 元数据示例

7. **[总结和展望](07-summary.md)**
   - 优势总结
   - 迁移路径
   - 风险和挑战
   - 未来扩展方向

## 📖 阅读建议

### 快速了解
如果您想快速了解本方案，建议按以下顺序阅读：
1. 方案概述（5分钟）
2. 核心架构设计（10分钟）
3. 执行流程（5分钟）

### 深入学习
如果您要实施本方案，建议完整阅读所有文档：
1. 从方案概述开始
2. 按顺序阅读各章节
3. 重点关注核心接口和KSP Processor实现
4. 参考迁移路径进行实施

### 关键决策点
在阅读过程中，特别注意以下关键设计决策：
- **路径配置复用**：如何复用 AbstractCodegenTask 和 GenEntityTask 的现有实现
- **接口解耦**：为什么创建独立的 AnnotationContextBuilder 接口
- **generateForClasses 实现**：为什么使用 while 循环模式

## 🎯 设计原则

1. **架构一致性**：延续 Context + Builder + Generator 三层架构
2. **独立性**：与 EntityContext 完全解耦
3. **可扩展性**：易于添加新的 Generator 和 Builder
4. **复用性**：最大化复用现有代码
5. **类型安全**：利用 KSP 获得编译器级别的准确性

## 🚀 快速开始

```bash
# 1. 添加 KSP 插件
plugins {
    id("com.google.devtools.ksp") version "1.9.20-1.0.14"
}

# 2. 添加 KSP Processor 依赖
dependencies {
    ksp("com.only:codegen-ksp-processor:1.0.0")
}

# 3. 配置代码生成
codegen {
    annotation {
        enabled.set(true)
        generateRepository.set(true)
        generateService.set(true)
    }
}

# 4. 生成代码
./gradlew genEntity genAnnotation
```

## 📝 版本历史

- **V2 (当前版本)** - 优化版
  - 修复路径配置，复用 AbstractCodegenTask 的现有实现
  - 创建独立的 AnnotationContextBuilder 接口
  - generateForClasses 参考 generateForTables 的 while 循环模式
  - 文档拆分为多个文件，便于阅读

- **V1** - 初始版本
  - 基础架构设计
  - KSP Processor 实现
  - Repository 和 Service 生成器

## 🤝 贡献指南

欢迎贡献！请遵循以下原则：
1. 保持与现有架构的一致性
2. 充分测试新功能
3. 更新相关文档
4. 提交清晰的 commit 信息

## 📧 联系方式

如有问题或建议，请：
1. 提交 Issue
2. 发起 Pull Request
3. 联系维护者

---

**最后更新**：2025-01-12
