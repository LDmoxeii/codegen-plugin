package com.only.codegen.context.builders

import com.only.codegen.context.MutableEntityContext

/**
 * 上下文构建器（负责收集和处理数据）
 */
interface ContextBuilder {
    /**
     * 构建顺序（数字越小越先执行）
     */
    val order: Int

    /**
     * 构建上下文信息
     */
    fun build(context: MutableEntityContext)
}
