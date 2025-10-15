package com.only.codegen.generators.design

import com.only.codegen.context.design.DesignContext
import com.only.codegen.context.design.models.DomainEventDesign
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.refPackage
import com.only.codegen.template.TemplateNode
import org.gradle.api.logging.Logging

class DomainEventGenerator : DesignTemplateGenerator {

    private val logger = Logging.getLogger(DomainEventGenerator::class.java)

    override val tag: String = "domain_event"
    override val order: Int = 30

    override fun shouldGenerate(design: Any, context: DesignContext): Boolean {
        if (design !is DomainEventDesign) return false
        if (context.typeMapping.containsKey(generatorName(design, context))) return false
        return true
    }

    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is DomainEventDesign) { "Design must be DomainEventDesign" }

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            val fullEntityType = typeMapping[design.entity]!!

            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(concatPackage(refPackage(design.`package`), refPackage("events"))))

            resultContext.putContext(tag, "Name", generatorName(design, context))
            resultContext.putContext(tag, "DomainEvent", generatorName(design, context))

            resultContext.putContext(tag, "Entity", design.entity)
            resultContext.putContext(tag, "fullEntityType", fullEntityType)
            resultContext.putContext(tag, "Aggregate", design.aggregate)

            resultContext.putContext(tag, "persist", design.persist.toString())

            resultContext.putContext(tag, "Comment", design.desc)
        }

        return resultContext
    }

    override fun generatorFullName(design: Any, context: DesignContext): String {
        require(design is DomainEventDesign)
        with(context) {
            val basePackage = getString("basePackage")
            val templatePackage = refPackage(templatePackage[tag] ?: "")
            val `package` = refPackage(concatPackage(refPackage(design.`package`), refPackage("events")))

            return "$basePackage$templatePackage$`package`${refPackage(generatorName(design, context))}"
        }
    }

    override fun generatorName(design: Any, context: DesignContext): String {
        require(design is DomainEventDesign)
        var name = design.name

        if (!name.endsWith("Evt") && !name.endsWith("Event")) {
            name += "DomainEvent"
        }

        return name
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@DomainEventGenerator.tag
                name = "{{ DomainEvent }}.kt"
                format = "resource"
                data = "templates/domain_event.kt.peb"
                conflict = "skip"
            }
        )
    }

    override fun onGenerated(design: Any, context: DesignContext) {
        if (design is DomainEventDesign) {
            val fullName = generatorFullName(design, context)
            context.typeMapping[generatorName(design, context)] = fullName
            logger.lifecycle("Generated domain event: $fullName")
        }
    }
}
