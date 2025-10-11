package com.only.codegen

import com.only.codegen.context.EntityContext
import com.only.codegen.context.MutableEntityContext
import com.only.codegen.context.builders.*
import com.only.codegen.generators.EnumGenerator
import com.only.codegen.generators.TemplateGenerator
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.resolvePackage
import com.only.codegen.misc.resolvePackageDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * 生成实体类任务
 */
open class GenEntityTask : GenArchTask(), MutableEntityContext {
    override val dbType: String = "dbType"

    @Internal
    override lateinit var aggregatesPath: String

    @Internal
    override lateinit var schemaPath: String

    @Internal
    override lateinit var subscriberPath: String

    @Internal
    override val aggregatesPackage: String = resolvePackage(
        "${aggregatesPath}${File.separator}X.kt"
    ).substring(getString("basePackage").length + 1)

    @Internal
    override val schemaPackage: String = resolvePackage(
        "${schemaPath}${File.separator}X.kt"
    ).substring(getString("basePackage").length + 1)

    @Internal
    override val subscriberPackage: String = resolvePackage(
        "${subscriberPath}${File.separator}X.kt"
    ).substring(getString("basePackage").length + 1)

    @Internal
    override val tableMap: MutableMap<String, Map<String, Any?>> = mutableMapOf()

    @Internal
    override val columnsMap: MutableMap<String, List<Map<String, Any?>>> = mutableMapOf()

    @Internal
    override val relationsMap: MutableMap<String, Map<String, String>> = mutableMapOf()

    @Internal
    override val tablePackageMap: MutableMap<String, String> = mutableMapOf()

    @Internal
    override val entityTypeMap: MutableMap<String, String> = mutableMapOf()

    @Internal
    override val tableModuleMap: MutableMap<String, String> = mutableMapOf()

    @Internal
    override val tableAggregateMap: MutableMap<String, String> = mutableMapOf()

    @Internal
    override val annotationsMap: MutableMap<String, Map<String, String>> = mutableMapOf()

    @Internal
    override val entityClassExtraImports: List<String> = mutableListOf()

    @Internal
    override val enumConfigMap: MutableMap<String, Map<Int, Array<String>>> = mutableMapOf()

    @Internal
    override val enumPackageMap: MutableMap<String, String> = mutableMapOf()

    @Internal
    override val enumTableNameMap: MutableMap<String, String> = mutableMapOf()
    override fun resolveAggregateWithModule(tableName: String): String {
        val module = tableModuleMap[tableName]
        return if (!(module.isNullOrBlank())) {
            concatPackage(module, tableAggregateMap[tableName]!!)
        } else {
            tableAggregateMap[tableName]!!
        }
    }

    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()
        SqlSchemaUtils.context = this

        genEntity()
    }

    fun genEntity() {
        val context = buildGenerationContext()

        if (context.tableMap.isEmpty()) {
            logger.warn("No tables found in database")
            return
        }

        generateFiles(context)
    }

    private fun buildGenerationContext(): EntityContext {

        val contextBuilders = listOf(
            TableContextBuilder(),
            EntityTypeContextBuilder(),
            ModuleContextBuilder(),
            AggregateContextBuilder(),
            TablePackageContextBuilder(),
            AnnotationContextBuilder(),
            RelationContextBuilder(),
            EnumContextBuilder(),
            EnumPackageContextBuilder()
        )

        contextBuilders
            .sortedBy { it.order }
            .forEach { builder ->
                logger.lifecycle("Building context: ${builder.javaClass.simpleName}")
                builder.build(this)
            }

        return this
    }


    private fun generateFiles(context: EntityContext) {
        val generators = listOf(
            EnumGenerator(),
        )

        generators.sortedBy { it.order }
            .forEach { generator ->
                logger.lifecycle("Generating files: ${generator.tag}")
                generateForTables(generator, context)
            }
    }

    private fun generateForTables(
        generator: TemplateGenerator,
        context: EntityContext
    ) {
        val tables = context.tableMap.values.toMutableList()

        while (tables.isNotEmpty()) {
            val table = tables.first()

            if (!generator.shouldGenerate(table, context)) {
                tables.removeFirst()
                continue
            }

            val tableContext = generator.buildContext(table, context)
            val templateNodes = context.templateNodeMap
                .getOrDefault(generator.tag, listOf(generator.getDefaultTemplateNode()))

            templateNodes.forEach { templateNode ->
                val pathNode = templateNode.deepCopy().resolve(tableContext)
                forceRender(
                    pathNode,
                    resolvePackageDirectory(
                        project.projectDir.absolutePath,
                        concatPackage(
                            getString("basePackage"),
                            tableContext["templatePackage"].toString(),
                            tableContext["package"].toString()
                        )
                    )
                )
            }
            generator.onGenerated(table, context)
        }
    }

}
