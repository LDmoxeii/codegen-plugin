package com.only.codegen.generators.design

import com.only.codegen.context.design.DesignContext
import com.only.codegen.context.design.QueryDesign
import com.only.codegen.misc.concatPackage
import com.only.codegen.template.TemplateNode
import org.gradle.api.logging.Logging
import java.io.File

class QueryGenerator : DesignTemplateGenerator {
    private val logger = Logging.getLogger(QueryGenerator::class.java)
    override val tag: String = "query"
    override val order: Int = 10

    override fun shouldGenerate(design: Any, context: DesignContext) = design is QueryDesign

    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is QueryDesign)
        val contextMap = mutableMapOf<String, Any?>()
        contextMap.putContext(tag, "Name", design.name, context)
        contextMap.putContext(tag, "Query", design.name, context)
        contextMap.putContext(tag, "Request", design.requestName, context)
        contextMap.putContext(tag, "Response", design.responseName, context)
        contextMap.putContext(tag, "Comment", design.desc, context)
        contextMap.putContext(tag, "CommentEscaped", design.desc.replace(Regex("\\r\\n|[\\r\\n]"), " "), context)
        contextMap.putContext(tag, "path", design.packagePath.replace(".", File.separator), context)
        contextMap.putContext(tag, "package", if (design.packagePath.isNotBlank()) ".${design.packagePath}" else "", context)
        if (design.aggregate != null) contextMap.putContext(tag, "Aggregate", design.aggregate, context)
        contextMap["modulePath"] = context.applicationPath
        contextMap["templatePackage"] = "application.queries"
        return contextMap
    }

    override fun getDefaultTemplateNode() = TemplateNode(
        type = "file", name = "{{ Name }}.kt", conflict = "skip", tag = tag,
        encoding = null, pattern = null, templatePath = "templates/application/query/Query.peb"
    )

    override fun onGenerated(design: Any, context: DesignContext) {
        if (design is QueryDesign) {
            val fullPackage = concatPackage(context.getString("basePackage"), "application.queries", design.packagePath)
            context.typeMapping[design.name] = concatPackage(fullPackage, design.name)
            logger.lifecycle("Generated query: ${context.typeMapping[design.name]}")
        }
    }

    private fun MutableMap<String, Any?>.putContext(tag: String, variable: String, value: Any, context: DesignContext) {
        val key = "$tag.$variable"
        val aliases = context.templateAliasMap[key] ?: listOf(variable)
        aliases.forEach { this[it] = value }
    }
}
