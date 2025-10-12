package com.only.codegen.generators.annotation

import com.only.codegen.context.annotation.AggregateInfo
import com.only.codegen.context.annotation.AnnotationContext
import com.only.codegen.template.TemplateNode

/**
 * 基于注解的模板文件生成器接口
 *
 * 与 TemplateGenerator 独立，专门用于注解驱动的代码生成
 *
 * @see com.only.codegen.generators.TemplateGenerator
 */
interface AnnotationTemplateGenerator {

    /**
     * 模板标签（repository, service 等）
     */
    val tag: String

    /**
     * 执行顺序（数字越小越先执行，用于处理依赖关系）
     *
     * 推荐值：
     * - 10: Repository 层（依赖最少）
     * - 20: Service 层（依赖 Repository）
     * - 30: Controller 层（依赖 Service）
     */
    val order: Int

    /**
     * 判断是否需要为该聚合生成此模板
     *
     * @param aggregateInfo 聚合信息（包含聚合根、实体、值对象等）
     * @param context 注解上下文（包含所有类信息、注解信息等）
     * @return true 表示需要生成，false 表示跳过
     */
    fun shouldGenerate(aggregateInfo: AggregateInfo, context: AnnotationContext): Boolean

    /**
     * 构建模板上下文
     *
     * @param aggregateInfo 聚合信息
     * @param context 注解上下文
     * @return 模板上下文 Map，将传递给 Pebble 模板引擎
     *
     * 示例返回值：
     * ```
     * mapOf(
     *   "aggregate" to "User",
     *   "repository" to "UserRepository",
     *   "package" to "com.example.adapter.domain.repositories",
     *   "imports" to listOf("com.example.domain.aggregates.user.User")
     * )
     * ```
     */
    fun buildContext(aggregateInfo: AggregateInfo, context: AnnotationContext): Map<String, Any?>

    /**
     * 获取默认模板节点
     *
     * 定义模板文件路径、输出路径、冲突处理策略等
     *
     * @return TemplateNode 模板节点配置
     */
    fun getDefaultTemplateNode(): TemplateNode

    /**
     * 生成文件后的回调（可选）
     *
     * 用途：
     * - 更新 typeMapping（将生成的类型全名记录下来，供后续生成器引用）
     * - 记录生成日志
     * - 收集统计信息
     *
     * @param aggregateInfo 聚合信息
     * @param context 注解上下文
     */
    fun onGenerated(aggregateInfo: AggregateInfo, context: AnnotationContext) {}
}
