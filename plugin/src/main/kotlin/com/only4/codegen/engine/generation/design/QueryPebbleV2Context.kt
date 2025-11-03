package com.only4.codegen.engine.generation.design

data class QueryPebbleV2Context(
    val finalPackage: String,
    val basePackage: String,
    val templatePackageRef: String,
    val packageRef: String,
    val queryName: String,
    val comment: String,
    val date: String,
    val imports: List<String>,
    val templateResource: String,
)

