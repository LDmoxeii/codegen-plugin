package com.only4.codegen.engine.config

/**
 * 模板配置占位定义，后续按需扩展。
 */
data class TemplateConfig(
    val encoding: String = "UTF-8",
    val strictMode: Boolean = false
)

