package com.only.codegen

import com.only.codegen.context.aggregate.*
import com.only.codegen.context.aggregate.builders.KspMetadataContextBuilder
import com.only.codegen.context.aggregate.models.AggregateInfo
import com.only.codegen.generators.aggregate.AggregateTemplateGenerator
import com.only.codegen.generators.aggregate.RepositoryGenerator
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.resolvePackageDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.regex.Pattern

open class GenAggregateTask : GenArchTask(), MutableAggregateContext {

    @Internal
    override val aggregateMap: MutableMap<String, AggregateInfo> = mutableMapOf()

    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()

        genAnnotation()
    }

    private fun genAnnotation() {

        val metadataPath = resolveMetadataPath()
        if (!metadataPath.exists()) {
            return
        }

        val context = buildGenerationContext(metadataPath.absolutePath)

        if (context.aggregateMap.isEmpty()) {
            return
        }

        generateFiles(context)
    }

    private fun resolveMetadataPath(): File {
            val domainModulePath = File(getString("domainModulePath"))
            val domainKspPath = File(domainModulePath, "build/generated/ksp/main/resources/metadata")
            return domainKspPath
    }

    private fun buildGenerationContext(metadataPath: String): AggregateContext {
        val contextBuilders = listOf(
            KspMetadataContextBuilder(metadataPath),  // order=10 - 读取并转换 KSP 元数据
            TypeMappingBuilder(),                     // order=20 - 收集类型映射
        )

        contextBuilders
            .sortedBy { it.order }
            .forEach { builder ->
                builder.build(this)
            }

        return this
    }

    private fun generateFiles(context: AggregateContext) {
        val generators = listOf(
            RepositoryGenerator(),  // order=10 - Repository 接口
            // ServiceGenerator(),   // order=20 - Service 类（已排除）
            // ControllerGenerator(), // order=30 - Controller 类（未实现）
        )

        generators.sortedBy { it.order }
            .forEach { generator ->
                logger.lifecycle("Generating ${generator.tag} files...")
                generateForAggregates(generator, context)
            }
    }

    private fun generateForAggregates(
        generator: AggregateTemplateGenerator,
        context: AggregateContext,
    ) {
        val aggregates = context.aggregateMap.values.toMutableList()

        while (aggregates.isNotEmpty()) {
            val aggregateInfo = aggregates.first()

            if (!generator.shouldGenerate(aggregateInfo, context)) {
                aggregates.removeFirst()
                continue
            }

            val aggregateContext = generator.buildContext(aggregateInfo, context)
            val templateNodes = context.templateNodeMap
                .getOrDefault(generator.tag, listOf(generator.getDefaultTemplateNode()))

            templateNodes
                .filter { templateNode ->
                    templateNode.pattern.isBlank() || Pattern.compile(templateNode.pattern).asPredicate()
                        .test(generator.generatorName(aggregateInfo, context))
                }
                .forEach { templateNode ->
                    val pathNode = templateNode.deepCopy().resolve(aggregateContext)
                    forceRender(
                        pathNode,
                        resolvePackageDirectory(
                            aggregateContext["modulePath"].toString(),
                            concatPackage(
                                getString("basePackage"),
                                aggregateContext["templatePackage"].toString()
                            )
                        )
                    )
                }

            generator.onGenerated(aggregateInfo, context)
        }
    }
}
