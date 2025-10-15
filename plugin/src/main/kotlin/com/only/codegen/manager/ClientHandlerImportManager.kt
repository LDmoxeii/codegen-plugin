package com.only.codegen.manager

/**
 * ClientHandler 生成器的 Import 管理器
 */
class ClientHandlerImportManager : BaseImportManager() {
    override fun addBaseImports() {
        requiredImports.add("org.slf4j.LoggerFactory")
        requiredImports.add("org.springframework.stereotype.Service")
        requiredImports.add("com.only4.cap4k.ddd.application.distributed.client.DistributedClient")
    }
}
