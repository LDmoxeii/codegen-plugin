package com.only.codegen.generators.design

import com.only.codegen.context.design.DesignContext
import com.only.codegen.template.TemplateNode

/**
 * 设计模板生成器接口
 *
 * 所有设计元素生成器必须实现此接口
 */
interface DesignTemplateGenerator {
    /**
     * 生成器标签 (用于匹配模板节点)
     */
    val tag: String

    /**
     * 生成顺序 (数字越小越先执行)
     */
    val order: Int

    /**
     * 判断是否应该为此设计生成代码
     */
    fun shouldGenerate(design: Any, context: DesignContext): Boolean

    /**
     * 构建模板上下文
     *
     * @param design 设计对象 (CommandDesign, QueryDesign, ...)
     * @param context 设计上下文
     * @return 模板变量 Map
     */
    fun buildContext(design: Any, context: DesignContext): Map<String, Any?>

    /**
     * 获取默认模板节点
     */
    fun getDefaultTemplateNode(): TemplateNode

    /**
     * 生成完成后的回调
     * 可用于缓存生成的类型到 typeMapping
     */
    fun onGenerated(design: Any, context: DesignContext) {}
}
