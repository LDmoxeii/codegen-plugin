package com.only.codegen.generators.design

import com.only.codegen.context.design.DesignContext
import com.only.codegen.context.design.DomainEventDesign
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.refPackage
import com.only.codegen.template.TemplateNode
import org.gradle.api.logging.Logging

class DomainEventGenerator : DesignTemplateGenerator {

    private val logger = Logging.getLogger(DomainEventGenerator::class.java)

    override val tag: String = "domain_event"
    override val order: Int = 30

    companion object {
        const val DOMAIN_EVENT_PACKAGE = "domain.aggregates"
    }

    override fun shouldGenerate(design: Any, context: DesignContext): Boolean {
        if (design !is DomainEventDesign) return false
        if (context.typeMapping.containsKey(generatorName(design, context))) return false
        return true
    }

    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is DomainEventDesign)

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            val eventPackage = "$DOMAIN_EVENT_PACKAGE.${design.aggregate}.events"

            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "package", eventPackage)
            resultContext.putContext(tag, "templatePackage", refPackage(eventPackage))

            resultContext.putContext(tag, "Name", design.name)
            resultContext.putContext(tag, "DomainEvent", design.name)
            resultContext.putContext(tag, "Entity", design.entity)
            resultContext.putContext(tag, "Aggregate", design.aggregate)
            resultContext.putContext(tag, "persist", design.persist.toString())
            resultContext.putContext(tag, "Comment", design.desc)
            resultContext.putContext(tag, "CommentEscaped", design.desc.replace(Regex("\\r\\n|[\\r\\n]"), " "))
        }

        return resultContext
    }

    override fun generatorFullName(design: Any, context: DesignContext): String {
        require(design is DomainEventDesign)
        val basePackage = context.getString("basePackage")
        val fullPackage = concatPackage(basePackage, DOMAIN_EVENT_PACKAGE, design.aggregate, "events", design.packagePath)
        return concatPackage(fullPackage, design.name)
    }

    override fun generatorName(design: Any, context: DesignContext): String {
        require(design is DomainEventDesign)
        return design.name
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@DomainEventGenerator.tag
            name = "{{ Name }}.kt"
            format = "resource"
            data = "templates/domain/event/DomainEvent.peb"
            conflict = "skip"
        }
    }

    override fun onGenerated(design: Any, context: DesignContext) {
        if (design is DomainEventDesign) {
            val fullName = generatorFullName(design, context)
            context.typeMapping[generatorName(design, context)] = fullName
            logger.lifecycle("Generated domain event: $fullName")
        }
    }
}
