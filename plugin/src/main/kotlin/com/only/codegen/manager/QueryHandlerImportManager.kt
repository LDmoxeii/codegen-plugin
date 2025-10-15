package com.only.codegen.manager

/**
 * Query 生成器的 Import 管理器
 */
class QueryHandlerImportManager : BaseImportManager() {
    override fun addBaseImports() {
        requiredImports.add("com.only4.cap4k.ddd.core.application.RequestParam")
    }
}
