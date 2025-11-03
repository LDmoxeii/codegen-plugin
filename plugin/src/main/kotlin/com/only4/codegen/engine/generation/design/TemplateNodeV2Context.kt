package com.only4.codegen.engine.generation.design

import com.only4.codegen.template.TemplateNode

data class TemplateNodeV2Context(
    val finalPackage: String,
    val tag: String,
    val genName: String,
    val ctxTop: List<TemplateNode>,
    val defTop: List<TemplateNode>,
    val model: Map<String, Any?>,
)

