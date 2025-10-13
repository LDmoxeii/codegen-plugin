package com.only.codegen.generators.aggregate

import com.only.codegen.context.aggregate.AggregateInfo
import com.only.codegen.context.aggregate.AnnotationContext
import com.only.codegen.manager.RepositoryImportManager
import com.only.codegen.misc.refPackage
import com.only.codegen.pebble.PebbleTemplateRenderer.renderString
import com.only.codegen.template.TemplateNode

class RepositoryGenerator : AggregateTemplateGenerator {

    override val tag = "repository"
    override val order = 10

    companion object {
        const val AGGREGATE_REPOSITORY_PACKAGE = "adapter.domain.repositories"
    }

    override fun shouldGenerate(aggregateInfo: AggregateInfo, context: AnnotationContext): Boolean {
        if (!aggregateInfo.aggregateRoot.isAggregateRoot) return false

        if (context.typeMapping.containsKey(generatorName(aggregateInfo, context))) return false

        return true
    }

    override fun buildContext(aggregateInfo: AggregateInfo, context: AnnotationContext): Map<String, Any?> {
        val aggregateRoot = aggregateInfo.aggregateRoot
        val aggregateName = aggregateInfo.name

        val fullRootEntityType = aggregateRoot.fullName  // 如 "com.example.domain.aggregates.user.User"
        val identityType = aggregateInfo.identityType  // 如 "Long" 或 "UserId"

        val imports = RepositoryImportManager()
        imports.addBaseImports()
        imports.add(fullRootEntityType)
        val fullIdType = context.typeMapping[identityType]
        imports.addIfNeeded(fullIdType != null) { fullIdType!!}
        val supportQuerydsl = context.getBoolean("repositorySupportQuerydsl")
        imports.addIfNeeded(supportQuerydsl,
            "org.springframework.data.querydsl.QuerydslPredicateExecutor",
            "com.only4.cap4k.ddd.domain.repo.querydsl.AbstractQuerydslRepository"
        )

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", adapterPath)
            resultContext.putContext(tag, "package", AGGREGATE_REPOSITORY_PACKAGE)
            resultContext.putContext(tag, "templatePackage", refPackage(AGGREGATE_REPOSITORY_PACKAGE))

            resultContext.putContext(tag, "supportQuerydsl", supportQuerydsl)
            resultContext.putContext(tag, "imports", imports.toImportLines())
            resultContext.putContext(tag, "Aggregate", aggregateName)
            resultContext.putContext(tag, "IdentityType", identityType)

            resultContext.putContext(tag, "Repository", generatorName(aggregateInfo, context))

            val comment = "Repository for $aggregateName aggregate"
            resultContext.putContext(tag, "Comment", comment)
        }

        return resultContext
    }

    override fun generatorFullName(
        aggregateInfo: AggregateInfo,
        context: AnnotationContext
    ): String {
        val aggregateName = aggregateInfo.name
        val repositoryNameTemplate = context.getString("repositoryNameTemplate")  // 如 "UserRepository"
        val repositoryName = renderString(repositoryNameTemplate, mapOf("Aggregate" to aggregateName))

        val basePackage = context.getString("basePackage")
        return "$basePackage.$AGGREGATE_REPOSITORY_PACKAGE.$repositoryName"
    }

    override fun generatorName(
        aggregateInfo: AggregateInfo,
        context: AnnotationContext
    ): String {
        val aggregateName = aggregateInfo.name
        val repositoryNameTemplate = context.getString("repositoryNameTemplate")

        return renderString(repositoryNameTemplate, mapOf("Aggregate" to aggregateName))
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@RepositoryGenerator.tag
            name = "{{ Repository }}.kt"
            format = "resource"
            data = "templates/repository.peb"
            conflict = "overwrite"
        }
    }

    override fun onGenerated(aggregateInfo: AggregateInfo, context: AnnotationContext) {
        context.typeMapping[generatorName(aggregateInfo, context)] = generatorFullName(aggregateInfo, context)
    }
}
