package com.only.codegen.generators.aggregate

import com.only.codegen.context.aggregate.AggregateContext
import com.only.codegen.manager.DomainEventImportManager
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.refPackage
import com.only.codegen.misc.toUpperCamelCase
import com.only.codegen.template.TemplateNode

/**
 * 领域事件文件生成器
 * 为聚合根生成领域事件基类
 */
class DomainEventGenerator : AggregateTemplateGenerator {
    override val tag = "domain_event"
    override val order = 30

    companion object {
        private const val DEFAULT_DOMAIN_EVENT_PACKAGE = "events"
    }

    private fun generateDomainEventName(eventName: String): String =
        (toUpperCamelCase(eventName) ?: eventName).let { base ->
            if (base.endsWith("Event") || base.endsWith("Evt")) base else "${base}DomainEvent"
        }

    override fun shouldGenerate(table: Map<String, Any?>, context: AggregateContext): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false

        if (!SqlSchemaUtils.isAggregateRoot(table)) return false

        val domainEvents = SqlSchemaUtils.getDomainEvents(table)

        return domainEvents.any { _ ->
            val currentDomainEvent = generatorName(table, context)
            currentDomainEvent.isNotBlank() && !(context.typeMapping.containsKey(currentDomainEvent))
        }
    }

    override fun buildContext(table: Map<String, Any?>, context: AggregateContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = context.resolveAggregateWithModule(tableName)

        val entityType = context.entityTypeMap[tableName]!!
        val fullEntityType = context.typeMapping[entityType]!!

        // 创建 ImportManager
        val importManager = DomainEventImportManager()
        importManager.addBaseImports()
        importManager.add(fullEntityType)

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag]!!))
            resultContext.putContext(tag, "package", concatPackage(refPackage(aggregate), refPackage(DEFAULT_DOMAIN_EVENT_PACKAGE)))

            resultContext.putContext(tag, "DomainEvent", generatorName(table, context))

            resultContext.putContext(tag, "Entity", entityType)

            resultContext.putContext(tag, "persist", context.getBoolean("domainEventPersist", false))
            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)

            resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))

            // 添加 imports
            resultContext.putContext(tag, "imports", importManager.toImportLines())
        }


        return resultContext
    }

    override fun generatorFullName(
        table: Map<String, Any?>,
        context: AggregateContext
    ): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = context.resolveAggregateWithModule(tableName)

        val basePackage = context.getString("basePackage")
        val templatePackage = refPackage(context.templatePackage[tag]!!)
        val `package` = refPackage(aggregate)

        val fullDomainEventType = "$basePackage${templatePackage}${`package`}.${DEFAULT_DOMAIN_EVENT_PACKAGE}${
            refPackage(
                generatorName(
                    table,
                    context
                )
            )
        }"
        return fullDomainEventType
    }

    override fun generatorName(
        table: Map<String, Any?>,
        context: AggregateContext
    ): String {
        return SqlSchemaUtils.getDomainEvents(table).map { domainEventInfo ->
            val infos = domainEventInfo.split(":")
            generateDomainEventName(infos[0])
        }.firstOrNull { domainEvent ->
            !context.typeMapping.containsKey(domainEvent)
        } ?: ""
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@DomainEventGenerator.tag
                name = "{{ DomainEvent }}.kt"
                format = "resource"
                data = "templates/domain_event.kt.peb"
                conflict = "skip" // 领域事件基类通常包含业务逻辑，不覆盖已有文件
            }
        )
    }

    override fun onGenerated(table: Map<String, Any?>, context: AggregateContext) {
        context.typeMapping[generatorName(table, context)] = generatorFullName(table, context)
    }
}
