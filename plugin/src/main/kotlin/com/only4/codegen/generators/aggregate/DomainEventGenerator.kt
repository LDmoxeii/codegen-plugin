package com.only4.codegen.generators.aggregate

import com.only4.codegen.context.aggregate.AggregateContext
import com.only4.codegen.manager.DomainEventImportManager
import com.only4.codegen.misc.SqlSchemaUtils
import com.only4.codegen.misc.concatPackage
import com.only4.codegen.misc.refPackage
import com.only4.codegen.misc.toUpperCamelCase
import com.only4.codegen.template.TemplateNode

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

    context(ctx: AggregateContext)
    override fun shouldGenerate(table: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false

        if (!SqlSchemaUtils.isAggregateRoot(table)) return false

        val domainEvents = SqlSchemaUtils.getDomainEvents(table)

        return domainEvents.any { _ ->
            val currentDomainEvent = generatorName(table)
            currentDomainEvent.isNotBlank() && !(ctx.typeMapping.containsKey(currentDomainEvent))
        }
    }

    context(ctx: AggregateContext)
    override fun buildContext(table: Map<String, Any?>): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = ctx.resolveAggregateWithModule(tableName)

        val entityType = ctx.entityTypeMap[tableName]!!
        val fullEntityType = ctx.typeMapping[entityType]!!

        // 创建 ImportManager
        val importManager = DomainEventImportManager()
        importManager.addBaseImports()
        importManager.add(fullEntityType)

        val resultContext = ctx.baseMap.toMutableMap()

        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(ctx.templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", concatPackage(refPackage(aggregate), refPackage(DEFAULT_DOMAIN_EVENT_PACKAGE)))

            resultContext.putContext(tag, "DomainEvent", generatorName(table))

            resultContext.putContext(tag, "Entity", entityType)

            resultContext.putContext(tag, "persist", ctx.getBoolean("domainEventPersist", false))
            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)

            resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))

            // 添加 imports
            resultContext.putContext(tag, "imports", importManager.toImportLines())
        }


        return resultContext
    }

    context(ctx: AggregateContext)
    override fun generatorFullName(
        table: Map<String, Any?>
    ): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = ctx.resolveAggregateWithModule(tableName)

        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val `package` = refPackage(aggregate)

        val fullDomainEventType = "$basePackage${templatePackage}${`package`}.${DEFAULT_DOMAIN_EVENT_PACKAGE}${
            refPackage(
                generatorName(
                    table
                )
            )
        }"
        return fullDomainEventType
    }

    context(ctx: AggregateContext)
    override fun generatorName(
        table: Map<String, Any?>
    ): String {
        return SqlSchemaUtils.getDomainEvents(table).map { domainEventInfo ->
            val infos = domainEventInfo.split(":")
            generateDomainEventName(infos[0])
        }.firstOrNull { domainEvent ->
            !ctx.typeMapping.containsKey(domainEvent)
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

    context(ctx: AggregateContext)
    override fun onGenerated(table: Map<String, Any?>) {
        ctx.typeMapping[generatorName(table)] = generatorFullName(table)
    }
}
