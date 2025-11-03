package com.only4.codegen.engine.generation.design

data class HandlerV2Context(
    val finalPackage: String,
    val className: String,
    val description: String,
    val imports: List<String>,
    val annotations: List<String> = listOf("@Service"),
    val implements: String, // e.g. ": Query<Foo.Request, Foo.Response>"
    val methodSignature: String, // e.g. "override fun exec(request: Foo.Request): Foo.Response"
    val methodBody: String = "return TODO()"
)

