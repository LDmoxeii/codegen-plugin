package com.only4.codegen.generators.design

import com.only4.codegen.context.design.DesignContext
import com.only4.codegen.context.design.models.DomainEventDesign
import com.only4.codegen.manager.DomainEventImportManager
import com.only4.codegen.misc.concatPackage
import com.only4.codegen.misc.refPackage
import com.only4.codegen.misc.toUpperCamelCase
import com.only4.codegen.template.TemplateNode
import org.gradle.api.logging.Logging

class DomainEventGenerator : DesignTemplateGenerator {

    private val logger = Logging.getLogger(DomainEventGenerator::class.java)

    override val tag: String = "domain_event"
    override val order: Int = 10

    context(ctx: DesignContext)
    override fun shouldGenerate(design: Any): Boolean {
        if (design !is DomainEventDesign) return false
        if (ctx.typeMapping.containsKey(generatorName(design))) return false
        return true
    }

    context(ctx: DesignContext)
    override fun buildContext(design: Any): Map<String, Any?> {
        require(design is DomainEventDesign) { "Design must be DomainEventDesign" }

        val fullEntityType = ctx.typeMapping[design.entity]!!

        // 创建 ImportManager
        val importManager = DomainEventImportManager()
        importManager.addBaseImports()
        importManager.add(fullEntityType)
        val resultContext = ctx.baseMap.toMutableMap()

        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(ctx.templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(concatPackage(refPackage(design.`package`), refPackage("events"))))

            resultContext.putContext(tag, "Name", generatorName(design))
            resultContext.putContext(tag, "DomainEvent", generatorName(design))

            resultContext.putContext(tag, "Entity", design.entity)
            resultContext.putContext(tag, "Aggregate", design.aggregate)

            resultContext.putContext(tag, "persist", design.persist.toString())

            resultContext.putContext(tag, "Comment", design.desc)

            // 添加 imports
            resultContext.putContext(tag, "imports", importManager.toImportLines())
        }

        return resultContext
    }

    context(ctx: DesignContext)
    override fun generatorFullName(design: Any): String {
        require(design is DomainEventDesign)
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val `package` = refPackage(concatPackage(refPackage(design.`package`), refPackage("events")))

        return "$basePackage$templatePackage$`package`${refPackage(generatorName(design))}"
    }

    context(ctx: DesignContext)
    override fun generatorName(design: Any): String {
        require(design is DomainEventDesign)
        var name = design.name

        if (!name.endsWith("Evt") && !name.endsWith("Event")) {
            name += "DomainEvent"
        }

        return toUpperCamelCase(name)!!
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

    context(ctx: DesignContext)
    override fun onGenerated(design: Any) {
        if (design is DomainEventDesign) {
            val fullName = generatorFullName(design)
            ctx.typeMapping[generatorName(design)] = fullName
            logger.lifecycle("Generated domain event: $fullName")
        }
    }
}
