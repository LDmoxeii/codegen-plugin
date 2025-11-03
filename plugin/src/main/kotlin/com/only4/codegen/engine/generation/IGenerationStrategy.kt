package com.only4.codegen.engine.generation

import com.only4.codegen.engine.output.GenerationResult

/**
 * 代码生成策略接口。输入为策略自定义的上下文类型 C。
 */
interface IGenerationStrategy<C> {
    fun generate(context: C): List<GenerationResult>
}

