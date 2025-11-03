package com.only4.codegen.engine.generation.common

import com.only4.codegen.manager.ClientImportManager
import com.only4.codegen.manager.CommandImportManager
import com.only4.codegen.manager.QueryHandlerImportManager
import com.only4.codegen.manager.DomainEventImportManager
import com.only4.codegen.manager.QueryImportManager

object V2Imports {

    fun command(): List<String> =
        CommandImportManager().apply { addBaseImports() }.toImportLines()

    fun client(): List<String> =
        ClientImportManager().apply { addBaseImports() }.toImportLines()

    fun query(designName: String): List<String> {
        val qt = QueryImportManager.inferQueryType(designName)
        return QueryImportManager(qt).apply { addBaseImports() }.toImportLines()
    }

    fun queryHandler(designName: String, queryFullName: String?): List<String> {
        val qt = QueryHandlerImportManager.inferQueryType(designName)
        val mgr = QueryHandlerImportManager(qt).apply {
            addBaseImports()
            if (queryFullName != null) add(queryFullName)
        }
        return mgr.toImportLines()
    }

    fun domainEvent(entityFullName: String?): List<String> {
        val mgr = DomainEventImportManager().apply {
            addBaseImports()
            if (entityFullName != null) add(entityFullName)
        }
        return mgr.toImportLines()
    }
}
