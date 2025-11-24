package com.only4.codegen.context.design.models

/**
 * API Payload 字段定义
 */
data class PayloadField(
    val name: String,
    val type: String? = null,
    val defaultValue: String? = null,
    val nullable: Boolean = false,
)
