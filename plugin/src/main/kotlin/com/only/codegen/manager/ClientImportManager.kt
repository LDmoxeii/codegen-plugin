package com.only.codegen.manager

/**
 * Client 生成器的 Import 管理器
 */
class ClientImportManager : BaseImportManager() {
    override fun addBaseImports() {
        requiredImports.add("com.only4.cap4k.ddd.application.RequestParam")
    }
}
