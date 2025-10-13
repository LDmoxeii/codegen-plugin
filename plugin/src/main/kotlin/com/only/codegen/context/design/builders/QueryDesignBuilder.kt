package com.only.codegen.context.design.builders

import com.only.codegen.context.design.DesignContextBuilder
import com.only.codegen.context.design.MutableDesignContext
import com.only.codegen.context.design.QueryDesign
import com.only.codegen.misc.toUpperCamelCase
import org.gradle.api.logging.Logging

/**
 * 查询设计构建器
 *
 * Order: 20
 * 职责: 解析查询设计元素,生成 QueryDesign 对象
 */
class QueryDesignBuilder : DesignContextBuilder {

    private val logger = Logging.getLogger(QueryDesignBuilder::class.java)

    override val order: Int = 20

    override fun build(context: MutableDesignContext) {
        val queryElements = context.designElementMap["qry"] ?: emptyList()

        queryElements.forEach { element ->
            try {
                val queryDesign = buildQueryDesign(element, context)
                context.queryDesignMap[queryDesign.fullName] = queryDesign
            } catch (e: Exception) {
                logger.error("Failed to build query design for: ${element.name}", e)
            }
        }

        logger.lifecycle("Built ${context.queryDesignMap.size} query designs")
    }

    private fun buildQueryDesign(
        element: com.only.codegen.context.design.DesignElement,
        context: MutableDesignContext
    ): QueryDesign {
        // 解析 name: "category.GetCategoryTree" 或 "GetCategoryList"
        val parts = element.name.split(".")
        val packagePath = if (parts.size > 1) {
            parts.dropLast(1).joinToString(".")
        } else {
            element.aggregate ?: ""
        }

        val rawName = parts.lastOrNull() ?: element.name
        var queryName = toUpperCamelCase(rawName).orEmpty()

        // 自动添加 Qry 后缀
        if (!queryName.endsWith("Qry") && !queryName.endsWith("Query")) {
            queryName += "Qry"
        }

        val fullName = if (packagePath.isNotBlank()) {
            "$packagePath.$queryName"
        } else {
            queryName
        }

        val requestName = "${queryName}Request"
        val responseName = "${queryName}Response"

        // 从 metadata 解析跨聚合查询信息
        val crossAggregate = element.metadata["crossAggregate"]?.toString()?.toBoolean() ?: false
        val includes = when (val includesValue = element.metadata["includes"]) {
            is List<*> -> includesValue.mapNotNull { it?.toString() }
            is String -> includesValue.split(",").map { it.trim() }
            else -> emptyList()
        }

        return QueryDesign(
            name = queryName,
            fullName = fullName,
            packagePath = packagePath,
            aggregate = element.aggregate,
            desc = element.desc,
            requestName = requestName,
            responseName = responseName,
            crossAggregate = crossAggregate,
            includes = includes
        )
    }
}
