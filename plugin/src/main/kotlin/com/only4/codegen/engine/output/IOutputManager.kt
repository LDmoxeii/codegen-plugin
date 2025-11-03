package com.only4.codegen.engine.output

/**
 * 输出管理接口：负责将生成结果写出或缓存。
 */
interface IOutputManager {
    fun write(result: GenerationResult)
    fun createDirectoryStructure(packageName: String)
    fun cleanOutput()
    fun getGeneratedFiles(): List<String>
}

