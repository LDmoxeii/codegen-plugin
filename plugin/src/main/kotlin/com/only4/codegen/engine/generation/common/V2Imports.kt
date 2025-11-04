package com.only4.codegen.engine.generation.common

import com.only4.codegen.manager.ClientImportManager
import com.only4.codegen.manager.CommandImportManager
import com.only4.codegen.manager.QueryHandlerImportManager
import com.only4.codegen.manager.DomainEventImportManager
import com.only4.codegen.manager.ClientHandlerImportManager
import com.only4.codegen.manager.DomainEventHandlerImportManager
import com.only4.codegen.manager.QueryImportManager
import com.only4.codegen.manager.EnumImportManager
import com.only4.codegen.manager.SchemaBaseImportManager
import com.only4.codegen.manager.SchemaImportManager
import com.only4.codegen.manager.TranslationImportManager

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

    fun clientHandler(clientFullName: String?): List<String> {
        val mgr = ClientHandlerImportManager().apply {
            addBaseImports()
            if (clientFullName != null) add(clientFullName)
        }
        return mgr.toImportLines()
    }

    fun domainEventHandler(eventFullName: String?): List<String> {
        val mgr = DomainEventHandlerImportManager().apply {
            addBaseImports()
            if (eventFullName != null) add(eventFullName)
        }
        return mgr.toImportLines()
    }

    fun enumImports(): List<String> = EnumImportManager().apply { addBaseImports() }.toImportLines()

    fun factory(fullEntityType: String): List<String> =
        com.only4.codegen.manager.FactoryImportManager().apply {
            addBaseImports(); add(fullEntityType)
        }.toImportLines()

    fun specification(): List<String> =
        com.only4.codegen.manager.SpecificationImportManager().apply { addBaseImports() }.toImportLines()

    fun aggregate(fullFactoryType: String): List<String> =
        com.only4.codegen.manager.AggregateImportManager().apply {
            addBaseImports(); add(fullFactoryType)
        }.toImportLines()

    fun repository(fullRootEntity: String, fullIdType: String?, supportQuerydsl: Boolean): List<String> =
        com.only4.codegen.manager.RepositoryImportManager().apply {
            addBaseImports(); add(fullRootEntity); if (fullIdType != null) add(fullIdType)
            if (supportQuerydsl) {
                add("org.springframework.data.querydsl.QuerydslPredicateExecutor")
                add("com.only4.cap4k.ddd.domain.repo.querydsl.AbstractQuerydslRepository")
            }
        }.toImportLines()

    // Aggregate: translation (enum translator)
    fun translation(vararg extras: String): List<String> =
        TranslationImportManager().apply {
            addBaseImports()
            extras.forEach { add(it) }
        }.toImportLines()

    // Aggregate: schema base helpers
    fun schemaBase(): List<String> =
        SchemaBaseImportManager().apply { addBaseImports() }.toImportLines()

    // Aggregate: schema for entity with optional Querydsl support
    fun schema(
        schemaBasePackage: String,
        entityFullName: String,
        isAggregateRoot: Boolean,
        supportQuerydsl: Boolean,
        qEntityFullName: String? = null,
        aggregateFullName: String? = null,
    ): List<String> {
        val mgr = SchemaImportManager(schemaBasePackage).apply {
            addBaseImports()
            add(entityFullName)
            if (isAggregateRoot) add("com.only4.cap4k.ddd.domain.repo.JpaPredicate")
            if (isAggregateRoot && supportQuerydsl) {
                add("com.querydsl.core.types.OrderSpecifier")
                add("com.only4.cap4k.ddd.core.domain.aggregate.AggregatePredicate")
                add("com.only4.cap4k.ddd.domain.repo.querydsl.QuerydslPredicate")
            }
            if (isAggregateRoot && supportQuerydsl && qEntityFullName != null) add(qEntityFullName)
            if (isAggregateRoot && supportQuerydsl && aggregateFullName != null) add(aggregateFullName)
        }
        return mgr.toImportLines()
    }
}
