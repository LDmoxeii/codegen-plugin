package com.only.codegen.context

interface EntityContext : BaseContext {

    // === 数据库信息 ===
    val dbType: String

    // === 路径和包信息继承自 BaseContext ===
    // aggregatesPath, schemaPath, subscriberPath
    // aggregatesPackage, schemaPackage, subscriberPackage

    // === 表信息 ===
    val tableMap: Map<String, Map<String, Any?>>
    val columnsMap: Map<String, List<Map<String, Any?>>>
    val tablePackageMap: Map<String, String>
    val entityTypeMap: Map<String, String>
    val tableModuleMap: Map<String, String>
    val tableAggregateMap: Map<String, String>
    val annotationsMap : Map<String, Map<String, String>>
    val relationsMap: Map<String, Map<String, String>>

    // === 默认导入配置 ===
    val entityClassExtraImports: List<String>

    // === 枚举信息 ===
    val enumConfigMap: Map<String, Map<Int, Array<String>>>
    val enumPackageMap: Map<String, String>

    fun resolveAggregateWithModule(tableName: String): String
}

