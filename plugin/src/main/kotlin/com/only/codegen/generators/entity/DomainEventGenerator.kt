package com.only.codegen.generators.entity

import com.only.codegen.AbstractCodegenTask
import com.only.codegen.context.entity.EntityContext
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.refPackage
import com.only.codegen.misc.toUpperCamelCase
import com.only.codegen.template.TemplateNode

/**
 * 领域事件文件生成器
 * 为聚合根生成领域事件基类
 */
class DomainEventGenerator : EntityTemplateGenerator {
    override val tag = "domain_event"
    override val order = 30

    companion object {
        private const val DEFAULT_DOMAIN_EVENT_PACKAGE = "events"
    }

    private fun generateDomainEventName(eventName: String): String =
        (toUpperCamelCase(eventName) ?: eventName).let { base ->
            if (base.endsWith("Event") || base.endsWith("Evt")) base else "${base}DomainEvent"
        }

    override fun shouldGenerate(table: Map<String, Any?>, context: EntityContext): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false

        if (!SqlSchemaUtils.isAggregateRoot(table)) return false

        val domainEvents = SqlSchemaUtils.getDomainEvents(table)

        return domainEvents.any { domainEventInfo ->
            val infos = domainEventInfo.split(":")
            !(context.typeMapping.containsKey(generateDomainEventName(infos[0])))
        }
    }

    override fun buildContext(table: Map<String, Any?>, context: EntityContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = context.resolveAggregateWithModule(tableName)

        val entityType = context.entityTypeMap[tableName]!!
        val fullEntityType = context.typeMapping[entityType]!!

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag]!!))
            resultContext.putContext(tag, "package", refPackage(aggregate))

            resultContext.putContext(tag, "DEFAULT_DOMAIN_EVENT_PACKAGE", DEFAULT_DOMAIN_EVENT_PACKAGE)
            resultContext.putContext(tag, "DomainEvent", generatorName(table, context))

            resultContext.putContext(tag, "fullEntityType", fullEntityType)
            resultContext.putContext(tag, "Entity", entityType)

            resultContext.putContext(tag, "persist", context.getBoolean("domainEventPersist", false))
            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
        }

        // 准备注释行
        val commentLines = SqlSchemaUtils.getComment(table)
            .split(Regex(AbstractCodegenTask.PATTERN_LINE_BREAK))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                if (line.endsWith(";")) line.dropLast(1).trim() else line
            }
            .filter { it.isNotEmpty() }

        with(context) {
            resultContext.putContext(tag, "commentLines", commentLines)
        }

        return resultContext
    }

    override fun generatorFullName(
        table: Map<String, Any?>,
        context: EntityContext
    ): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = context.resolveAggregateWithModule(tableName)

        val templatePackage = refPackage(context.templatePackage[tag]!!)
        val `package` = refPackage(aggregate)

        val fullDomainEventType =
            "${context.getString("basePackage")}${templatePackage}${`package`}.${DEFAULT_DOMAIN_EVENT_PACKAGE}${
                refPackage(
                    generatorName(table, context)
                )
            }"
        return fullDomainEventType
    }

    override fun generatorName(
        table: Map<String, Any?>,
        context: EntityContext
    ): String {
        return SqlSchemaUtils.getDomainEvents(table).first { domainEventInfo ->
            val infos = domainEventInfo.split(":")
            val eventName = generateDomainEventName(infos[0])
            !context.typeMapping.containsKey(eventName)
        }
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@DomainEventGenerator.tag
            name = "{{ DEFAULT_DOMAIN_EVENT_PACKAGE }}{{ SEPARATOR }}{{ DomainEvent }}.kt"
            format = "resource"
            data = "templates/domain_event.peb"
            conflict = "skip" // 领域事件基类通常包含业务逻辑，不覆盖已有文件
        }
    }

    override fun onGenerated(table: Map<String, Any?>, context: EntityContext) {
        with(context) {
            typeMapping[generatorName(table, context)] = generatorFullName(table, context)
        }
    }
}
