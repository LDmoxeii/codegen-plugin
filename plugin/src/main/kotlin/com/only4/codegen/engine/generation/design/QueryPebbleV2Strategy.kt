package com.only4.codegen.engine.generation.design

import com.only4.codegen.engine.generation.IGenerationStrategy
import com.only4.codegen.engine.output.GenerationResult
import com.only4.codegen.engine.output.OutputType
import com.only4.codegen.pebble.PebbleTemplateRenderer

class QueryPebbleV2Strategy : IGenerationStrategy<QueryPebbleV2Context> {
    override fun generate(context: QueryPebbleV2Context): List<GenerationResult> {
        val model = mapOf(
            "basePackage" to context.basePackage,
            "templatePackage" to context.templatePackageRef,
            "package" to context.packageRef,
            "Query" to context.queryName,
            "Comment" to context.comment,
            "date" to context.date,
            "imports" to context.imports,
        )
        val content = PebbleTemplateRenderer.renderString(loadResource(context.templateResource), model)
        return listOf(GenerationResult(
            fileName = "${context.queryName}.kt",
            content = content,
            packageName = context.finalPackage,
            type = OutputType.CONFIGURATION,
        ))
    }

    private fun loadResource(path: String): String {
        val clean = path.removePrefix("/").replace('\\', '/')
        val cl = Thread.currentThread().contextClassLoader
        val stream = cl.getResourceAsStream(clean)
            ?: javaClass.classLoader.getResourceAsStream(clean)
            ?: error("Resource not found: $path")
        return stream.bufferedReader().use { it.readText() }
    }
}

