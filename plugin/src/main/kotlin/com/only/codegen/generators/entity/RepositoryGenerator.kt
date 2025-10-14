package com.only.codegen.generators.entity

import com.only.codegen.context.entity.EntityContext
import com.only.codegen.manager.RepositoryImportManager
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.refPackage
import com.only.codegen.pebble.PebbleTemplateRenderer.renderString
import com.only.codegen.template.TemplateNode

/**
 * Repository 生成器
 * 为聚合根生成 JPA Repository 接口及其适配器实现
 */
class RepositoryGenerator : EntityTemplateGenerator {

    override val tag = "repository"
    override val order = 30

    companion object {
        const val ENTITY_REPOSITORY_PACKAGE = "adapter.domain.repositories"
    }

    override fun shouldGenerate(table: Map<String, Any?>, context: EntityContext): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false

        if (!SqlSchemaUtils.isAggregateRoot(table)) return false

        if (context.typeMapping.containsKey(generatorName(table, context))) return false

        return true
    }

    override fun buildContext(table: Map<String, Any?>, context: EntityContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = context.entityTypeMap[tableName]!!

        val fullRootEntityType = context.typeMapping[entityType]!!

        val columns = context.columnsMap[tableName]!!
        val ids = columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }
        val identityType = if (ids.size != 1) "Long" else SqlSchemaUtils.getColumnType(ids[0])

        val imports = RepositoryImportManager()
        imports.addBaseImports()
        imports.add(fullRootEntityType)

        val fullIdType = context.typeMapping[identityType]
        if (fullIdType != null) {
            imports.add(fullIdType)
        }

        val supportQuerydsl = context.getBoolean("repositorySupportQuerydsl")
        if (supportQuerydsl) {
            imports.add("org.springframework.data.querydsl.QuerydslPredicateExecutor")
            imports.add("com.only4.cap4k.ddd.domain.repo.querydsl.AbstractQuerydslRepository")
        }

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", adapterPath)
            resultContext.putContext(tag, "package", ENTITY_REPOSITORY_PACKAGE)
            resultContext.putContext(tag, "templatePackage", refPackage(ENTITY_REPOSITORY_PACKAGE))

            resultContext.putContext(tag, "supportQuerydsl", supportQuerydsl)
            resultContext.putContext(tag, "imports", imports.toImportLines())
            resultContext.putContext(tag, "Aggregate", entityType)
            resultContext.putContext(tag, "IdentityType", identityType)

            resultContext.putContext(tag, "Repository", generatorName(table, context))

            val comment = "Repository for $entityType aggregate"
            resultContext.putContext(tag, "Comment", comment)
        }

        return resultContext
    }

    override fun generatorFullName(
        table: Map<String, Any?>,
        context: EntityContext
    ): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = context.entityTypeMap[tableName]!!
        val repositoryNameTemplate = context.getString("repositoryNameTemplate")
        val repositoryName = renderString(repositoryNameTemplate, mapOf("Aggregate" to entityType))

        val basePackage = context.getString("basePackage")
        return "$basePackage.$ENTITY_REPOSITORY_PACKAGE.$repositoryName"
    }

    override fun generatorName(
        table: Map<String, Any?>,
        context: EntityContext
    ): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = context.entityTypeMap[tableName]!!
        val repositoryNameTemplate = context.getString("repositoryNameTemplate")

        return renderString(repositoryNameTemplate, mapOf("Aggregate" to entityType))
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@RepositoryGenerator.tag
            name = "{{ Repository }}.kt"
            format = "resource"
            data = "templates/repository.kt.peb"
            conflict = "overwrite"
        }
    }

    override fun onGenerated(table: Map<String, Any?>, context: EntityContext) {
        context.typeMapping[generatorName(table, context)] = generatorFullName(table, context)
    }
}
