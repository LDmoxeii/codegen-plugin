package com.only.codegen.generators

import com.only.codegen.context.EntityContext
import com.only.codegen.template.TemplateNode

/**
 * 模板文件生成器
 */
interface TemplateGenerator {
    /**
     * 模板标签（entity, enum, factory 等）
     */
    val tag: String

    /**
     * 执行顺序（数字越小越先执行，用于处理依赖关系）
     */
    val order: Int

    /**
     * 判断是否需要为该表生成此模板
     */
    fun shouldGenerate(table: Map<String, Any?>, context: EntityContext): Boolean

    /**
     * 构建模板上下文
     * @return 单个上下文 Map 或包含 "items" 键的 Map（用于批量生成）
     */
    fun buildContext(table: Map<String, Any?>, context: EntityContext): Map<String, Any?>

    /**
     * 获取默认模板节点
     */
    fun getDefaultTemplateNode(): TemplateNode

    /**
     * 生成文件后的回调（可选，用于收集信息或日志）
     */
    fun onGenerated(table: Map<String, Any?>, context: EntityContext) {}
}
