package com.only.codegen.context

import com.only.codegen.template.TemplateNode

interface BaseContext {

    // === 基础配置 Map（包含所有项目、数据库、生成配置等） ===
    val baseMap: Map<String, Any?>

    // === 模块信息 ===
    val adapterPath: String
    val domainPath: String
    val applicationPath: String

    // === 模板信息 ===
    val templateNodeMap: MutableMap<String, MutableList<TemplateNode>>

    // === 模板别名映射 ===
    val templateAliasMap: Map<String, List<String>>

    // === baseMap 辅助访问方法 ===
    fun getString(key: String, default: String = ""): String = baseMap[key]?.toString() ?: default
    fun getBoolean(key: String, default: Boolean = false): Boolean =
        baseMap[key]?.toString()?.toBoolean() ?: default
    fun getInt(key: String, default: Int = 0): Int =
        baseMap[key]?.toString()?.toIntOrNull() ?: default

    fun MutableMap<String, Any?>.putContext(tag: String, variable: String, value: Any)
}

