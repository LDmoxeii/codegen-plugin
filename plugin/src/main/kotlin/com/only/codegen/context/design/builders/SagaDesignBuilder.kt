package com.only.codegen.context.design.builders

import com.only.codegen.context.design.DesignContextBuilder
import com.only.codegen.context.design.MutableDesignContext
import com.only.codegen.context.design.SagaDesign
import com.only.codegen.misc.toUpperCamelCase
import org.gradle.api.logging.Logging

class SagaDesignBuilder : DesignContextBuilder {
    private val logger = Logging.getLogger(SagaDesignBuilder::class.java)
    override val order: Int = 20

    override fun build(context: MutableDesignContext) {
        val sagaElements = context.designElementMap["saga"] ?: emptyList()
        sagaElements.forEach { element ->
            try {
                val parts = element.name.split(".")
                val packagePath = if (parts.size > 1) parts.dropLast(1).joinToString(".") else (element.aggregate ?: "")
                val rawName = parts.lastOrNull() ?: element.name
                var sagaName = toUpperCamelCase(rawName).orEmpty()
                if (!sagaName.endsWith("Saga")) sagaName += "Saga"
                val fullName = if (packagePath.isNotBlank()) "$packagePath.$sagaName" else sagaName

                val sagaDesign = SagaDesign(
                    name = sagaName,
                    fullName = fullName,
                    packagePath = packagePath,
                    aggregate = element.aggregate,
                    desc = element.desc,
                    requestName = "${sagaName}Request",
                    responseName = "${sagaName}Response"
                )
                context.sagaDesignMap[sagaDesign.fullName] = sagaDesign
            } catch (e: Exception) {
                logger.error("Failed to build saga design for: ${element.name}", e)
            }
        }
        logger.lifecycle("Built ${context.sagaDesignMap.size} saga designs")
    }
}
