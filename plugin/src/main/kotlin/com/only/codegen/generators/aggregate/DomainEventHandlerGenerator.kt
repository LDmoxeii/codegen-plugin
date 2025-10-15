package com.only.codegen.generators.aggregate

import com.only.codegen.context.aggregate.AggregateContext
import com.only.codegen.manager.DomainEventHandlerImportManager
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.refPackage
import com.only.codegen.misc.toUpperCamelCase
import com.only.codegen.template.TemplateNode

/**
 * 领域事件处理器文件生成器
 * 为聚合根生成领域事件订阅者（处理器）基类
 */
class DomainEventHandlerGenerator : AggregateTemplateGenerator {
    override val tag = "domain_event_handler"
    override val order = 40

    private fun generateDomainEventName(eventName: String): String =
        (toUpperCamelCase(eventName) ?: eventName).let { base ->
            if (base.endsWith("Event") || base.endsWith("Evt")) base else "${base}DomainEvent"
        }

    override fun shouldGenerate(table: Map<String, Any?>, context: AggregateContext): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false

        if (!SqlSchemaUtils.isAggregateRoot(table)) return false

        return generatorName(table, context).isNotBlank() && !context.typeMapping.containsKey(
            generatorName(
                table,
                context
            )
        )
    }

    override fun buildContext(table: Map<String, Any?>, context: AggregateContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = context.resolveAggregateWithModule(tableName)

        val domainEvent = generatorName(table, context).replace("Subscriber", "")
        val fullDomainEventType = context.typeMapping[domainEvent]!!

        // 创建 ImportManager
        val importManager = DomainEventHandlerImportManager()
        importManager.addBaseImports()
        importManager.add(fullDomainEventType)

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", applicationPath)
            resultContext.putContext(tag, "templatePackage", refPackage(context.templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(aggregate))

            resultContext.putContext(tag, "DomainEvent", domainEvent)

            resultContext.putContext(tag, "DomainEventHandler", generatorName(table, context))

            resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))

            // 添加 imports
            resultContext.putContext(tag, "imports", importManager.toImportLines())
        }


        return resultContext
    }

    override fun generatorFullName(
        table: Map<String, Any?>,
        context: AggregateContext,
    ): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = context.resolveAggregateWithModule(tableName)

        val basePackage = context.getString("basePackage")
        val templatePackage = refPackage(context.templatePackage[tag] ?: "")
        val `package` = refPackage(aggregate)

        return "$basePackage${templatePackage}${`package`}${refPackage(generatorName(table, context))}"
    }

    override fun generatorName(
        table: Map<String, Any?>,
        context: AggregateContext,
    ): String {
        return SqlSchemaUtils.getDomainEvents(table)
            .map { domainEventInfo ->
                val infos = domainEventInfo.split(":")
                val eventName = generateDomainEventName(infos[0])
                "${eventName}Subscriber"
            }
            .firstOrNull { domainEventHandler ->
                !context.typeMapping.containsKey(domainEventHandler)
            } ?: ""
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@DomainEventHandlerGenerator.tag
                name = "{{ DomainEventHandler }}.kt"
                format = "resource"
                data = "templates/domain_event_handler.kt.peb"
                conflict = "skip" // 事件处理器包含业务逻辑，不覆盖已有文件
            }
        )
    }

    override fun onGenerated(table: Map<String, Any?>, context: AggregateContext) {
        context.typeMapping[generatorName(table, context)] = generatorFullName(table, context)
    }
}
