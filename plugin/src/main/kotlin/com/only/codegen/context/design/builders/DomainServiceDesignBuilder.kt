package com.only.codegen.context.design.builders

import com.only.codegen.context.design.DesignContextBuilder
import com.only.codegen.context.design.DomainServiceDesign
import com.only.codegen.context.design.MutableDesignContext
import com.only.codegen.misc.toUpperCamelCase
import org.gradle.api.logging.Logging

class DomainServiceDesignBuilder : DesignContextBuilder {
    private val logger = Logging.getLogger(DomainServiceDesignBuilder::class.java)
    override val order: Int = 20

    override fun build(context: MutableDesignContext) {
        val serviceElements = context.designElementMap["svc"] ?: emptyList()
        serviceElements.forEach { element ->
            try {
                val parts = element.name.split(".")
                val packagePath = if (parts.size > 1) parts.dropLast(1).joinToString(".") else (element.aggregate ?: "")
                val rawName = parts.lastOrNull() ?: element.name
                var serviceName = toUpperCamelCase(rawName).orEmpty()
                if (!serviceName.endsWith("Service")) serviceName += "Service"
                val fullName = if (packagePath.isNotBlank()) "$packagePath.$serviceName" else serviceName

                val serviceDesign = DomainServiceDesign(
                    name = serviceName,
                    fullName = fullName,
                    packagePath = packagePath,
                    aggregate = element.aggregate,
                    desc = element.desc
                )
                context.domainServiceDesignMap[serviceDesign.fullName] = serviceDesign
            } catch (e: Exception) {
                logger.error("Failed to build domain service design for: ${element.name}", e)
            }
        }
        logger.lifecycle("Built ${context.domainServiceDesignMap.size} domain service designs")
    }
}
