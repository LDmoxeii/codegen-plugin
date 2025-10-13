package com.only.codegen.generators.annotation

import com.only.codegen.context.annotation.AggregateInfo
import com.only.codegen.context.annotation.AnnotationContext
import com.only.codegen.manager.RepositoryImportManager
import com.only.codegen.misc.refPackage
import com.only.codegen.pebble.PebbleTemplateRenderer.renderString
import com.only.codegen.template.TemplateNode

class RepositoryGenerator : AnnotationTemplateGenerator {

    override val tag = "repository"
    override val order = 10

    private val generated = mutableSetOf<String>()

    companion object {
        const val AGGREGATE_REPOSITORY_PACKAGE = "adapter.domain.repositories"
    }

    override fun shouldGenerate(aggregateInfo: AggregateInfo, context: AnnotationContext): Boolean {
        if (!aggregateInfo.aggregateRoot.isAggregateRoot) return false

        if (generated.contains(aggregateInfo.name)) return false

        val generateRepository = context.getBoolean("generateRepository", true)
        return generateRepository
    }

    override fun buildContext(aggregateInfo: AggregateInfo, context: AnnotationContext): Map<String, Any?> {
        val aggregateRoot = aggregateInfo.aggregateRoot
        val aggregateName = aggregateInfo.name

        val fullRootEntityType = aggregateRoot.fullName  // 如 "com.example.domain.aggregates.user.User"
        val identityType = aggregateInfo.identityType  // 如 "Long" 或 "UserId"

        val repositoryNameTemplate = context.getString("repositoryNameTemplate")

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

            resultContext.putContext(tag, "Repository", renderString(repositoryNameTemplate, resultContext))

            val comment = "Repository for $aggregateName aggregate"
            resultContext.putContext(tag, "Comment", comment)
        }

        return resultContext
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
        val aggregateName = aggregateInfo.name
        val repositoryNameTemplate = context.getString("repositoryNameTemplate")  // 如 "UserRepository"

        val repositoryName = renderString(repositoryNameTemplate, mapOf("Aggregate" to aggregateName))

        val basePackage = context.getString("basePackage")
        val fullRepositoryType = "$basePackage.$AGGREGATE_REPOSITORY_PACKAGE.$repositoryName"
        context.typeMapping[repositoryName] = fullRepositoryType

        generated.add(aggregateName)
    }
}
