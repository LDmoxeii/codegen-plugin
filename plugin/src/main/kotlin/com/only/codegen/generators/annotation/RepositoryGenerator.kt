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

        val rootEntityType = aggregateRoot.simpleName  // 如 "User"
        val fullRootEntityType = aggregateRoot.fullName  // 如 "com.example.domain.aggregates.user.User"
        val identityType = aggregateInfo.identityType  // 如 "Long" 或 "UserId"

        val repositoryName = buildRepositoryName(aggregateName, context)  // 如 "UserRepository"

        val imports = RepositoryImportManager()
        imports.add(fullRootEntityType)
        val fullIdType = context.typeMapping[identityType]
        imports.addIfNeeded(fullIdType == null) { identityType }
        imports.addIfNeeded(fullIdType != null) { fullIdType!!}
        imports.addIfNeeded(context.getBoolean("repositorySupportQuerydsl"), "org.springframework.data.querydsl.QuerydslPredicateExecutor")

        val supportQuerydsl = context.getBoolean("repositorySupportQuerydsl", false)
        val parentInterface = if (supportQuerydsl) {
            """JpaRepository<$rootEntityType, $identityType>, JpaSpecificationExecutor<$rootEntityType>, 
                |   QuerydslPredicateExecutor<$rootEntityType>""".trimMargin()
        } else {
            "JpaRepository<$rootEntityType, $identityType>, JpaSpecificationExecutor<$rootEntityType>"
        }

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", adapterPath)
            resultContext.putContext(tag, "package", AGGREGATE_REPOSITORY_PACKAGE)
            resultContext.putContext(tag, "templatePackage", refPackage(AGGREGATE_REPOSITORY_PACKAGE))

            resultContext.putContext(tag, "Aggregate", aggregateName)
            resultContext.putContext(tag, "AggregateRoot", rootEntityType)

            resultContext.putContext(tag, "Entity", rootEntityType)
            resultContext.putContext(tag, "IdentityType", identityType)

            resultContext.putContext(tag, "Repository", renderString(repositoryName, mapOf("Entity" to rootEntityType)))
            resultContext.putContext(tag, "parentInterface", parentInterface)

            resultContext.putContext(tag, "IdType", identityType)
            resultContext.putContext(tag, "imports", imports.toImportLines())

            resultContext.putContext(tag, "supportQuerydsl", supportQuerydsl)

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
        val repositoryName = buildRepositoryName(aggregateName, context)

        val basePackage = context.getString("basePackage")
        val fullRepositoryType = "$basePackage.$AGGREGATE_REPOSITORY_PACKAGE.$repositoryName"
        context.typeMapping[repositoryName] = fullRepositoryType

        generated.add(aggregateName)
    }

    private fun buildRepositoryName(aggregateName: String, context: AnnotationContext): String {
        val template = context.getString("repositoryNameTemplate", "{{ Aggregate }}Repository")
        return renderString(template, mapOf("Aggregate" to aggregateName))
    }
}
