package com.only.codegen.generators

import com.only.codegen.AbstractCodegenTask
import com.only.codegen.context.EntityContext
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.refPackage
import com.only.codegen.misc.toUpperCamelCase
import com.only.codegen.template.TemplateNode
import java.io.File

/**
 * 领域事件文件生成器
 * 为聚合根生成领域事件基类
 */
class DomainEventGenerator : TemplateGenerator {
    override val tag = "domain_event"
    override val order = 35

    @Volatile
    private lateinit var currentDomainEvent: String

    private val generated = mutableSetOf<String>()

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

        return domainEvents.any {domainEventInfo ->
            val infos = domainEvents.toString().split(":")
            generateDomainEventName(infos[0]) !in generated
        }
    }

    override fun buildContext(table: Map<String, Any?>, context: EntityContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = context.resolveAggregateWithModule(tableName)

        val entityType = context.entityTypeMap[tableName]!!
        val fullEntityPackage = context.typeRemapping[entityType]!!

        SqlSchemaUtils.getDomainEvents(table).firstOrNull { domainEventInfo ->
            val infos = domainEventInfo.split(":")
            val eventName = generateDomainEventName(infos[0])
            if (!generated.contains(eventName)) {
                currentDomainEvent = eventName
                true
            } else false
        }

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "DEFAULT_SPEC_PACKAGE", DEFAULT_DOMAIN_EVENT_PACKAGE)

            resultContext.putContext(tag, "path", aggregate.replace(".", File.separator))
            resultContext.putContext(tag, "templatePackage", refPackage(context.aggregatesPackage))
            resultContext.putContext(tag, "package",refPackage(aggregate))
            resultContext.putContext(tag, "fullEntityType", fullEntityPackage)

            resultContext.putContext(tag, "DomainEvent", currentDomainEvent)
            resultContext.putContext(tag, "persist", context.getBoolean("domainEventPersist", false))

            resultContext.putContext(tag, "Entity", entityType)
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

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@DomainEventGenerator.tag
            name = "{{ path }}{{ SEPARATOR }}{{ DEFAULT_DOMAIN_EVENT_PACKAGE }{{ SEPARATOR }}{{ DomainEvent }}.kt"
            format = "resource"
            data = "domain_event"
            conflict = "skip" // 领域事件基类通常包含业务逻辑，不覆盖已有文件
        }
    }

    override fun onGenerated(table: Map<String, Any?>, context: EntityContext) {
        with(context) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = context.resolveAggregateWithModule(tableName)

            val templatePackage = refPackage(context.aggregatesPackage)
            val `package` = refPackage(aggregate)

            val fullDomainEventType = "${getString("basePackage")}${templatePackage}${`package`}.${currentDomainEvent}"
            typeRemapping[currentDomainEvent] = fullDomainEventType
            generated.add(currentDomainEvent)
        }
    }
}
