package com.only.codegen.generators.design

import com.only.codegen.context.design.DesignContext
import com.only.codegen.context.design.DomainEventDesign
import com.only.codegen.misc.concatPackage
import com.only.codegen.template.TemplateNode
import org.gradle.api.logging.Logging
import java.io.File

class DomainEventGenerator : DesignTemplateGenerator {
    private val logger = Logging.getLogger(DomainEventGenerator::class.java)
    override val tag: String = "domain_event"
    override val order: Int = 30

    override fun shouldGenerate(design: Any, context: DesignContext) = design is DomainEventDesign

    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is DomainEventDesign)
        val contextMap = mutableMapOf<String, Any?>()
        contextMap.putContext(tag, "Name", design.name, context)
        contextMap.putContext(tag, "DomainEvent", design.name, context)
        contextMap.putContext(tag, "Entity", design.entity, context)
        contextMap.putContext(tag, "Aggregate", design.aggregate, context)
        contextMap.putContext(tag, "persist", design.persist.toString(), context)
        contextMap.putContext(tag, "Comment", design.desc, context)
        contextMap.putContext(tag, "CommentEscaped", design.desc.replace(Regex("\\r\\n|[\\r\\n]"), " "), context)
        contextMap.putContext(tag, "path", design.packagePath.replace(".", File.separator), context)
        contextMap.putContext(tag, "package", if (design.packagePath.isNotBlank()) ".${design.packagePath}" else "", context)
        contextMap["modulePath"] = context.domainPath
        contextMap["templatePackage"] = "domain.aggregates.${design.aggregate}.events"
        return contextMap
    }

    override fun getDefaultTemplateNode() = TemplateNode(
        type = "file", name = "{{ Name }}.kt", conflict = "skip", tag = tag,
        encoding = null, pattern = null, templatePath = "templates/domain/event/DomainEvent.peb"
    )

    override fun onGenerated(design: Any, context: DesignContext) {
        if (design is DomainEventDesign) {
            val fullPackage = concatPackage(context.getString("basePackage"), "domain.aggregates.${design.aggregate}.events", design.packagePath)
            context.typeMapping[design.name] = concatPackage(fullPackage, design.name)
            logger.lifecycle("Generated domain event: ${context.typeMapping[design.name]}")
        }
    }

    private fun MutableMap<String, Any?>.putContext(tag: String, variable: String, value: Any, context: DesignContext) {
        val key = "$tag.$variable"
        val aliases = context.templateAliasMap[key] ?: listOf(variable)
        aliases.forEach { this[it] = value }
    }
}
