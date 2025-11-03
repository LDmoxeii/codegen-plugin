package com.only4.codegen.engine.generation.design

data class DesignV2Context(
    val basePackage: String,
    val modulePath: String,
    val packageName: String,
    val className: String,
    val description: String,
    val outputEncoding: String,
)

