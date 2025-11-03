package com.only4.codegen.engine.generation.aggregate

data class EnumV2Context(
    val basePackage: String,
    val aggregate: String,
    val enumName: String,
    val items: List<EnumItem>,
    val enumValueField: String,
    val enumNameField: String,
)

data class EnumItem(
    val value: Int,
    val name: String,
    val desc: String,
)

