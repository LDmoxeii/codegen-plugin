package com.only.codegen.generators

import com.only.codegen.AbstractCodegenTask
import com.only.codegen.context.EntityContext
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.refPackage
import com.only.codegen.misc.toUpperCamelCase
import com.only.codegen.template.TemplateNode

/**
 * 领域事件处理器文件生成器
 * 为聚合根生成领域事件订阅者（处理器）基类
 */
class DomainEventHandlerGenerator : TemplateGenerator {
    override val tag = "domain_event_handler"
    override val order = 40

    @Volatile
    private lateinit var currentDomainEventHandler: String

    private val generated = mutableSetOf<String>()

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
            "${generateDomainEventName(infos[0])}Subscriber" !in generated
        }
    }

    override fun buildContext(table: Map<String, Any?>, context: EntityContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = context.resolveAggregateWithModule(tableName)

        var domainEvent = ""

        SqlSchemaUtils.getDomainEvents(table).firstOrNull { domainEventInfo ->
            val infos = domainEventInfo.split(":")
            val eventName = generateDomainEventName(infos[0])
            val handlerName = "${eventName}Subscriber"
            if (!generated.contains(handlerName)) {
                currentDomainEventHandler = "${eventName}Subscriber"
                domainEvent = eventName
                true
            } else false
        }

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", applicationPath)
            resultContext.putContext(tag, "templatePackage", refPackage(context.subscriberPackage))
            resultContext.putContext(tag, "package", refPackage(aggregate))

            resultContext.putContext(tag, "fullDomainEventType", typeMapping[domainEvent]!!)

            resultContext.putContext(tag, "DomainEventHandler", currentDomainEventHandler)
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
            tag = this@DomainEventHandlerGenerator.tag
            name = "{{ DomainEventHandler }}.kt"
            format = "resource"
            data = "domain_event_handler"
            conflict = "skip" // 事件处理器包含业务逻辑，不覆盖已有文件
        }
    }

    override fun onGenerated(table: Map<String, Any?>, context: EntityContext) {
        with(context) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = context.resolveAggregateWithModule(tableName)

            val templatePackage = refPackage(context.aggregatesPackage)
            val `package` = refPackage(aggregate)

            val fullDomainEventType =
                "${getString("basePackage")}${templatePackage}${`package`}${refPackage(currentDomainEventHandler)}"
            typeMapping[currentDomainEventHandler] = fullDomainEventType
            generated.add(currentDomainEventHandler)
        }
    }
}
