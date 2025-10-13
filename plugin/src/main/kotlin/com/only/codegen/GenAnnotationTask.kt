package com.only.codegen

import com.only.codegen.context.annotation.*
import com.only.codegen.generators.annotation.AnnotationTemplateGenerator
import com.only.codegen.generators.annotation.RepositoryGenerator
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.resolvePackageDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

open class GenAnnotationTask : GenArchTask(), MutableAnnotationContext {

    @Internal
    override val classMap: MutableMap<String, ClassInfo> = mutableMapOf()

    @Internal
    override val annotationMap: MutableMap<String, MutableList<AnnotationInfo>> = mutableMapOf()

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
        // 设置 renderFileSwitch = false，只初始化 Pebble 引擎，不生成架构文件
        renderFileSwitch = false
        super.generate()  // 初始化 Pebble 模板引擎

        // 执行注解代码生成
        genAnnotation()
    }

    private fun genAnnotation() {
        logger.lifecycle("Starting annotation-based code generation...")

        val metadataPath = resolveMetadataPath()
        if (!metadataPath.exists()) {
            logger.warn("KSP metadata not found at: ${metadataPath.absolutePath}")
            logger.warn("Please run KSP processor first to generate metadata")
            return
        }

        val context = buildGenerationContext(metadataPath.absolutePath)

        if (context.aggregateMap.isEmpty()) {
            logger.warn("No aggregates found in metadata")
            return
        }

        logger.lifecycle("Found ${context.aggregateMap.size} aggregates")
        generateFiles(context)

        logger.lifecycle("Annotation-based code generation completed")
    }

    private fun resolveMetadataPath(): File {
        val configuredPath = extension.get().annotation.metadataPath.orNull
        if (!configuredPath.isNullOrBlank()) {
            return File(configuredPath)
        }

        // 多模块项目：优先查找 domain 模块
        val ext = extension.get()
        if (ext.multiModule.get()) {
            val domainModuleName = "${projectName.get()}${ext.moduleNameSuffix4Domain.get()}"
            val domainModulePath = File(projectDir.get(), domainModuleName)

            // KSP 默认将 resources 输出到 build/generated/ksp/main/resources/
            val domainKspPath = File(domainModulePath, "build/generated/ksp/main/resources/metadata")

            if (domainKspPath.exists()) {
                logger.info("Found KSP metadata in domain module: ${domainKspPath.absolutePath}")
                return domainKspPath
            }

            // 如果 domain 模块没有，尝试查找其他子模块
            val projectRoot = File(projectDir.get())
            val subModules = projectRoot.listFiles { file ->
                file.isDirectory && file.name.startsWith(projectName.get())
            }?.toList() ?: emptyList()

            for (subModule in subModules) {
                val kspPath = File(subModule, "build/generated/ksp/main/resources/metadata")
                if (kspPath.exists()) {
                    logger.info("Found KSP metadata in module ${subModule.name}: ${kspPath.absolutePath}")
                    return kspPath
                }
            }

            // 没找到就返回 domain 模块的默认路径（即使不存在，让后续逻辑处理）
            logger.warn("KSP metadata not found in any submodule, returning domain module default path")
            return domainKspPath
        }

        // 单模块项目：项目根目录的 build/generated/ksp/main/resources/metadata/
        return File(projectDir.get(), "build/generated/ksp/main/resources/metadata")
    }

    private fun buildGenerationContext(metadataPath: String): AnnotationContext {
        val contextBuilders = listOf(
            KspMetadataContextBuilder(metadataPath),  // order=10 - 读取元数据
            AggregateInfoBuilder(),                   // order=20 - 聚合信息
            IdentityTypeBuilder(),                    // order=30 - ID 类型映射
        )

        contextBuilders
            .sortedBy { it.order }
            .forEach { builder ->
                logger.lifecycle("Building context: ${builder.javaClass.simpleName}")
                builder.build(this)
                // 输出调试信息
                logger.lifecycle("  - classMap size: ${classMap.size}")
                logger.lifecycle("  - aggregateMap size: ${aggregateMap.size}")
            }

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
                logger.lifecycle("Generating files: ${generator.tag}")
                generateForAggregates(generator, context)
            }
    }

    private fun generateForAggregates(
        generator: AnnotationTemplateGenerator,
        context: AnnotationContext,
    ) {
        val aggregates = context.aggregateMap.values.toList()

        aggregates.forEach { aggregateInfo ->
            if (!generator.shouldGenerate(aggregateInfo, context)) {
                logger.debug("Skipping ${generator.tag} for aggregate: ${aggregateInfo.name}")
                return@forEach
            }

            logger.lifecycle("Generating ${generator.tag} for aggregate: ${aggregateInfo.name}")

            val aggregateContext = generator.buildContext(aggregateInfo, context)
            val templateNodes = context.templateNodeMap
                .getOrDefault(generator.tag, listOf(generator.getDefaultTemplateNode()))

            templateNodes.forEach { templateNode ->
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
