package com.only.codegen.generators.design

import com.only.codegen.context.design.DesignContext
import com.only.codegen.context.design.QueryDesign
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.refPackage
import com.only.codegen.template.TemplateNode
import org.gradle.api.logging.Logging

class QueryGenerator : DesignTemplateGenerator {

    private val logger = Logging.getLogger(QueryGenerator::class.java)

    override val tag: String = "query"
    override val order: Int = 10

    companion object {
        const val QUERY_PACKAGE = "application.queries"
    }

    override fun shouldGenerate(design: Any, context: DesignContext): Boolean {
        if (design !is QueryDesign) return false
        if (context.typeMapping.containsKey(generatorName(design, context))) return false
        return true
    }

    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is QueryDesign)

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", applicationPath)
            resultContext.putContext(tag, "package", QUERY_PACKAGE)
            resultContext.putContext(tag, "templatePackage", refPackage(QUERY_PACKAGE))

            resultContext.putContext(tag, "Name", design.name)
            resultContext.putContext(tag, "Query", design.name)
            resultContext.putContext(tag, "Request", design.requestName)
            resultContext.putContext(tag, "Response", design.responseName)
            resultContext.putContext(tag, "Comment", design.desc)
            resultContext.putContext(tag, "CommentEscaped", design.desc.replace(Regex("\\r\\n|[\\r\\n]"), " "))

            if (design.aggregate != null) {
                resultContext.putContext(tag, "Aggregate", design.aggregate)
            }
        }

        return resultContext
    }

    override fun generatorFullName(design: Any, context: DesignContext): String {
        require(design is QueryDesign)
        val basePackage = context.getString("basePackage")
        val fullPackage = concatPackage(basePackage, QUERY_PACKAGE, design.packagePath)
        return concatPackage(fullPackage, design.name)
    }

    override fun generatorName(design: Any, context: DesignContext): String {
        require(design is QueryDesign)
        return design.name
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@QueryGenerator.tag
            name = "{{ Name }}.kt"
            format = "resource"
            data = "templates/application/query/Query.peb"
            conflict = "skip"
        }
    }

    override fun onGenerated(design: Any, context: DesignContext) {
        if (design is QueryDesign) {
            val fullName = generatorFullName(design, context)
            context.typeMapping[generatorName(design, context)] = fullName
            logger.lifecycle("Generated query: $fullName")
        }
    }
}
