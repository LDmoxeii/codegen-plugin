package com.only4.codegen.engine.output

/**
 * 生成结果定义（与输出管理器对接）。
 */
data class GenerationResult(
    val fileName: String,
    val content: String,
    val packageName: String? = null,
    val type: OutputType = OutputType.ENTITY,
    val metadata: Map<String, Any?> = emptyMap()
)

enum class OutputType {
    ENTITY, REPOSITORY, SERVICE, DTO, ENUM, CONFIGURATION
}

