package com.only.codegen

import com.only.codegen.context.aggregate.*
import com.only.codegen.generators.aggregate.AggregateTemplateGenerator
import com.only.codegen.generators.aggregate.RepositoryGenerator
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.resolvePackageDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.regex.Pattern

open class GenAggregateTask : GenArchTask(), MutableAnnotationContext {

    @Internal
    override val aggregateMap: MutableMap<String, AggregateInfo> = mutableMapOf()

    @get:Internal
    override val sourceRoots: List<String>
        get() = extension.get().annotation.sourceRoots.get()

    @get:Internal
    override val scanPackages: List<String>
        get() = extension.get().annotation.scanPackages.get()

    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()

        genAnnotation()
    }

    private fun genAnnotation() {

        val metadataPath = resolveMetadataPath()
        if (!metadataPath.exists()) {
            logger.warn("KSP metadata path not found: ${metadataPath.absolutePath}")
            return
        }

        val context = buildGenerationContext(metadataPath.absolutePath)

        if (context.aggregateMap.isEmpty()) {
            logger.warn("No aggregates found in metadata")
            return
        }

        logger.lifecycle("Found ${context.aggregateMap.size} aggregates, starting generation...")
        generateFiles(context)
    }

    private fun resolveMetadataPath(): File {
        val configuredPath = extension.get().annotation.metadataPath.orNull
        if (!configuredPath.isNullOrBlank()) {
            return File(configuredPath)
        }

        val ext = extension.get()
        if (ext.multiModule.get()) {
            val domainModuleName = "${projectName.get()}${ext.moduleNameSuffix4Domain.get()}"
            val domainModulePath = File(projectDir.get(), domainModuleName)

            val domainKspPath = File(domainModulePath, "build/generated/ksp/main/resources/metadata")

            if (domainKspPath.exists()) {
                return domainKspPath
            }

            val projectRoot = File(projectDir.get())
            val subModules = projectRoot.listFiles { file ->
                file.isDirectory && file.name.startsWith(projectName.get())
            }?.toList() ?: emptyList()

            for (subModule in subModules) {
                val kspPath = File(subModule, "build/generated/ksp/main/resources/metadata")
                if (kspPath.exists()) {
                    return kspPath
                }
            }

            return domainKspPath
        }

        return File(projectDir.get(), "build/generated/ksp/main/resources/metadata")
    }

    private fun buildGenerationContext(metadataPath: String): AnnotationContext {
        logger.lifecycle("Building annotation context from: $metadataPath")

        val contextBuilders = listOf(
            KspMetadataContextBuilder(metadataPath),  // order=10 - 读取并转换 KSP 元数据
            TypeMappingBuilder(),                     // order=20 - 收集类型映射
        )

        contextBuilders
            .sortedBy { it.order }
            .forEach { builder ->
                logger.lifecycle("Running builder: ${builder.javaClass.simpleName} (order=${builder.order})")
                builder.build(this)
            }

        logger.lifecycle("Context built: ${aggregateMap.size} aggregates")
        return this
    }

    private fun generateFiles(context: AnnotationContext) {
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
        context: AnnotationContext,
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
