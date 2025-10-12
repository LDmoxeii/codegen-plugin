package com.only.codegen.generators.annotation

import com.only.codegen.AbstractCodegenTask
import com.only.codegen.context.annotation.AggregateInfo
import com.only.codegen.context.annotation.AnnotationContext
import com.only.codegen.misc.refPackage
import com.only.codegen.template.TemplateNode

/**
 * Repository 文件生成器
 *
 * 为每个聚合根生成对应的 JPA Repository 接口
 *
 * **生成规则**：
 * - 只为聚合根生成 Repository
 * - 位于 adapter 模块的 domain.repositories 包下
 * - 继承 JpaRepository<Entity, ID>
 * - 可选支持 QueryDSL（如果配置了 repositorySupportQuerydsl）
 *
 * **示例输出**：
 * ```kotlin
 * package com.example.adapter.domain.repositories
 *
 * import com.example.domain.aggregates.user.User
 * import org.springframework.data.jpa.repository.JpaRepository
 * import org.springframework.stereotype.Repository
 *
 * @Repository
 * interface UserRepository : JpaRepository<User, Long>
 * ```
 */
class RepositoryGenerator : AnnotationTemplateGenerator {

    override val tag = "repository"
    override val order = 10  // Repository 最先生成，依赖最少

    private val generated = mutableSetOf<String>()

    override fun shouldGenerate(aggregateInfo: AggregateInfo, context: AnnotationContext): Boolean {
        // 只为聚合根生成 Repository
        if (!aggregateInfo.aggregateRoot.isAggregateRoot) return false

        // 检查是否已生成（避免重复）
        if (generated.contains(aggregateInfo.name)) return false

        // 检查配置是否启用 Repository 生成（默认启用）
        val generateRepository = context.getBoolean("generateRepository", true)
        return generateRepository
    }

    override fun buildContext(aggregateInfo: AggregateInfo, context: AnnotationContext): Map<String, Any?> {
        val aggregateRoot = aggregateInfo.aggregateRoot
        val aggregateName = aggregateInfo.name

        // 1. 准备基本信息
        val rootEntityType = aggregateRoot.simpleName  // 如 "User"
        val fullRootEntityType = aggregateRoot.fullName  // 如 "com.example.domain.aggregates.user.User"
        val identityType = aggregateInfo.identityType  // 如 "Long" 或 "UserId"

        // 2. 准备 Repository 信息
        val repositoryName = buildRepositoryName(aggregateName, context)  // 如 "UserRepository"
        val repositoryPackage = buildRepositoryPackage(context)  // 如 "adapter.domain.repositories"

        // 3. 准备导入列表
        val imports = buildImports(fullRootEntityType, identityType, context)

        // 4. 准备父接口信息
        val supportQuerydsl = context.getBoolean("repositorySupportQuerydsl", false)
        val parentInterface = if (supportQuerydsl) {
            "JpaRepository<$rootEntityType, $identityType>, QuerydslPredicateExecutor<$rootEntityType>"
        } else {
            "JpaRepository<$rootEntityType, $identityType>"
        }

        // 5. 构建模板上下文
        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            // 模块路径
            resultContext.putContext(tag, "modulePath", adapterPath)

            // 包信息
            resultContext.putContext(tag, "package", repositoryPackage)
            resultContext.putContext(tag, "templatePackage", refPackage(repositoryPackage))

            // 聚合信息
            resultContext.putContext(tag, "Aggregate", aggregateName)
            resultContext.putContext(tag, "AggregateRoot", rootEntityType)

            // Repository 信息
            resultContext.putContext(tag, "Repository", repositoryName)
            resultContext.putContext(tag, "parentInterface", parentInterface)

            // ID 类型
            resultContext.putContext(tag, "IdType", identityType)

            // 导入列表
            resultContext.putContext(tag, "imports", imports)

            // 注释信息
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
            data = "repository"
            conflict = "skip"  // Repository 通常不需要覆盖，用户可能会添加自定义方法
        }
    }

    override fun onGenerated(aggregateInfo: AggregateInfo, context: AnnotationContext) {
        val aggregateName = aggregateInfo.name
        val repositoryName = buildRepositoryName(aggregateName, context)
        val repositoryPackage = buildRepositoryPackage(context)

        // 更新 typeMapping，供后续生成器引用
        val basePackage = context.getString("basePackage")
        val fullRepositoryType = "$basePackage.$repositoryPackage.$repositoryName"
        context.typeMapping[repositoryName] = fullRepositoryType

        // 记录已生成
        generated.add(aggregateName)
    }

    /**
     * 构建 Repository 名称
     *
     * 根据配置的模板生成，默认为 "{Aggregate}Repository"
     */
    private fun buildRepositoryName(aggregateName: String, context: AnnotationContext): String {
        val template = context.getString("repositoryNameTemplate", "{Aggregate}Repository")
        return template.replace("{Aggregate}", aggregateName)
    }

    /**
     * 构建 Repository 包路径
     *
     * 默认为 "adapter.domain.repositories"
     */
    private fun buildRepositoryPackage(context: AnnotationContext): String {
        return AbstractCodegenTask.AGGREGATE_REPOSITORY_PACKAGE
    }

    /**
     * 构建导入列表
     */
    private fun buildImports(
        fullRootEntityType: String,
        identityType: String,
        context: AnnotationContext,
    ): List<String> {
        val imports = mutableListOf<String>()

        // 1. 聚合根实体
        imports.add(fullRootEntityType)

        // 2. ID 类型（如果是自定义类型）
        val fullIdType = context.typeMapping[identityType]
        if (fullIdType != null && !fullIdType.startsWith("java.lang")) {
            imports.add(fullIdType)
        }

        // 3. Spring Data JPA
        imports.add("org.springframework.data.jpa.repository.JpaRepository")
        imports.add("org.springframework.stereotype.Repository")

        // 4. QueryDSL（如果启用）
        val supportQuerydsl = context.getBoolean("repositorySupportQuerydsl", false)
        if (supportQuerydsl) {
            imports.add("org.springframework.data.querydsl.QuerydslPredicateExecutor")
        }

        return imports.sorted()
    }
}
