package com.only.codegen.generators

import com.only.codegen.AbstractCodegenTask
import com.only.codegen.context.EntityContext
import com.only.codegen.generators.manager.EntityImportManager
import com.only.codegen.misc.*
import com.only.codegen.misc.SqlSchemaUtils.LEFT_QUOTES_4_ID_ALIAS
import com.only.codegen.misc.SqlSchemaUtils.RIGHT_QUOTES_4_ID_ALIAS
import com.only.codegen.template.TemplateNode
import java.io.File

/**
 * 实体文件生成器
 */
class EntityGenerator : TemplateGenerator {
    override val tag = "entity"
    override val order = 20

    private val generated = mutableSetOf<String>()

    override fun shouldGenerate(table: Map<String, Any?>, context: EntityContext): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false

        val tableName = SqlSchemaUtils.getTableName(table)
        val columns = context.columnsMap[tableName] ?: return false
        val ids = resolveIdColumns(columns)

        return ids.isNotEmpty() && !(generated.contains(tableName))
    }

    override fun buildContext(table: Map<String, Any?>, context: EntityContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val columns = context.columnsMap[tableName]!!
        val aggregate = context.resolveAggregateWithModule(tableName)
        val fullEntityPackage = context.tablePackageMap[tableName]!!

        val entityType = context.entityTypeMap[tableName]!!
        val ids = resolveIdColumns(columns)

        // 创建 ImportManager
        val importManager = EntityImportManager()
        importManager.addBaseImports()

        val identityType = if (ids.size != 1) "Long" else SqlSchemaUtils.getColumnType(ids[0])
        if (context.typeMapping.containsKey(identityType)) {
            importManager.add(context.typeMapping[identityType]!!)
        }

        // 处理基类
        var baseClass: String? = null
        when {
            SqlSchemaUtils.isAggregateRoot(table) && context.getString("rootEntityBaseClass").isNotBlank() -> {
                baseClass = context.getString("rootEntityBaseClass")
            }

            context.getString("entityBaseClass").isNotBlank() -> {
                baseClass = context.getString("entityBaseClass")
            }
        }

        baseClass?.let {
            baseClass = it
                .replace("\${Entity}", entityType)
                .replace("\${IdentityType}", identityType)
        }

        val extendsClause = if (baseClass?.isNotBlank() == true) " : $baseClass()" else ""
        val implementsClause = if (SqlSchemaUtils.isValueObject(table)) ", ValueObject<$identityType>" else ""

        // 收集自定义内容
        val existingImportLines = mutableListOf<String>()
        val annotationLines = mutableListOf<String>()
        val customerLines = mutableListOf<String>()

        // 处理现有文件的自定义内容
        val filePath = resolveSourceFile(
            context.getString("domainModulePath"),
            fullEntityPackage,
            entityType,
        )
        processEntityCustomerSourceFile(filePath, existingImportLines, annotationLines, customerLines, context)
        processAnnotationLines(table, columns, annotationLines, context, ids)

        existingImportLines.forEach { line ->
            importManager.add(line)
        }

        // 准备列数据
        val columnDataList = columns.map { column ->
            prepareColumnData(
                table, column,
                ids, context, importManager
            )
        }

        // 准备关系数据
        val relationDataList = prepareRelationData(table, context.relationsMap, context.tablePackageMap, context)

        // 1. 检查软删除字段
        val deletedField = context.getString("deletedField")
        val hasSoftDelete = deletedField.isNotBlank() && SqlSchemaUtils.hasColumn(deletedField, columns)
        importManager.addIfNeeded(
            hasSoftDelete,
            "org.hibernate.annotations.SQLDelete",
            "org.hibernate.annotations.Where"
        )

        // 2. 检查是否需要 ID 生成器
        val needsIdGenerator = ids.size == 1 &&
                !SqlSchemaUtils.isValueObject(table) &&
                resolveEntityIdGenerator(table, context).isNotEmpty()
        importManager.addIfNeeded(
            needsIdGenerator,
            "org.hibernate.annotations.GenericGenerator"
        )

        // 3. 检查是否有集合关系（OneToMany, ManyToMany）
        val hasCollectionRelation = context.relationsMap[tableName]?.values?.any {
            val relationType = it.split(";")[0]
            relationType in listOf("OneToMany", "ManyToMany", "*OneToMany", "*ManyToMany")
        } == true
        importManager.addIfNeeded(
            hasCollectionRelation,
            "org.hibernate.annotations.Fetch",
            "org.hibernate.annotations.FetchMode"
        )

        // 4. 检查是否是 ValueObject
        importManager.addIfNeeded(
            SqlSchemaUtils.isValueObject(table),
            "com.only4.cap4k.ddd.core.domain.aggregate.ValueObject"
        )

        // 生成最终的 import 列表
        val finalImports = importManager.toImportLines()


        // 准备注释行
        val commentLines = SqlSchemaUtils.getComment(table)
            .split(Regex(AbstractCodegenTask.PATTERN_LINE_BREAK))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                if (line.endsWith(";")) line.dropLast(1).trim() else line
            }
            .filter { it.isNotEmpty() }

        // 构建上下文
        val resultContext = context.baseMap.toMutableMap()
        with(context) {
            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(aggregatesPackage))
            resultContext.putContext(tag, "package", refPackage(aggregate))

            resultContext.putContext(tag, "Entity", entityType)

            resultContext.putContext(tag, "entityType", entityType)
            resultContext.putContext(tag, "extendsClause", extendsClause)
            resultContext.putContext(tag, "implementsClause", implementsClause)
            resultContext.putContext(tag, "columns", columnDataList)
            resultContext.putContext(tag, "relations", relationDataList)
            resultContext.putContext(tag, "annotationLines", annotationLines)
            resultContext.putContext(tag, "customerLines", customerLines)
            resultContext.putContext(tag, "imports", finalImports)
            resultContext.putContext(tag, "commentLines", commentLines)
        }

        return resultContext
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@EntityGenerator.tag
            name = "{{ Entity }}.kt"
            format = "resource"
            data = "entity"
            conflict = "overwrite"
        }
    }

    override fun onGenerated(
        table: Map<String, Any?>,
        context: EntityContext,
    ) {
        with(context) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = resolveAggregateWithModule(tableName)
            val entityType = entityTypeMap[tableName]!!

            val basePackage = getString("basePackage")
            val templatePackage = refPackage(aggregatesPackage)
            val `package` = refPackage(aggregate)

            val fullEntityType = "$basePackage${templatePackage}${`package`}${refPackage(entityType)}"
            typeMapping[entityType] = fullEntityType
            generated.add(tableName)
        }
    }

    private fun resolveIdColumns(columns: List<Map<String, Any?>>): List<Map<String, Any?>> {
        return columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }
    }

    private fun resolveSourceFile(
        baseDir: String,
        packageName: String,
        className: String,
    ): String {
        val packagePath = packageName.replace(".", File.separator)
        return "$baseDir${File.separator}src${File.separator}main${File.separator}kotlin${File.separator}$packagePath${File.separator}$className.kt"
    }

    private fun resolveEntityIdGenerator(table: Map<String, Any?>, context: EntityContext): String {
        with(context) {
            return when {
                SqlSchemaUtils.hasIdGenerator(table) -> {
                    SqlSchemaUtils.getIdGenerator(table)
                }

                SqlSchemaUtils.isValueObject(table) -> {
                    getString("idGenerator4ValueObject").ifBlank {
                        "com.only4.cap4k.ddd.domain.repo.Md5HashIdentifierGenerator"
                    }
                }

                else -> {
                    getString("idGenerator")
                }
            }
        }
    }

    private fun isColumnNeedGenerate(
        table: Map<String, Any?>,
        column: Map<String, Any?>,
        relations: Map<String, Map<String, String>>,
    ): Boolean {
        val tableName = SqlSchemaUtils.getTableName(table)
        val columnName = SqlSchemaUtils.getColumnName(column)

        if (SqlSchemaUtils.isIgnore(column)) return false

        if (!SqlSchemaUtils.isAggregateRoot(table)) {
            val parent = SqlSchemaUtils.getParent(table)
            val refMatchesParent = SqlSchemaUtils.hasReference(column) &&
                    parent.equals(SqlSchemaUtils.getReference(column), ignoreCase = true)
            val fkNameMatches = columnName.equals("${parent}_id", ignoreCase = true)
            if (refMatchesParent || fkNameMatches) return false
        }

        if (relations.containsKey(tableName)) {
            for (entry in relations[tableName]!!.entries) {
                val refInfos = entry.value.split(";")
                when (refInfos[0]) {
                    "ManyToOne", "OneToOne" -> if (columnName.equals(refInfos[1], ignoreCase = true)) {
                        return false
                    }

                    "PLACEHOLDER" -> if (columnName.equals(refInfos[1], ignoreCase = true)) {
                        return false
                    }
                }
            }
        }
        return true
    }

    private fun isReadOnlyColumn(column: Map<String, Any?>, context: EntityContext): Boolean {
        with(context) {
            if (SqlSchemaUtils.hasReadOnly(column)) return true

            val columnName = SqlSchemaUtils.getColumnName(column).lowercase()
            val readonlyFields = getString("readonlyFields")

            return readonlyFields.isNotBlank() && readonlyFields
                .lowercase()
                .split(Regex(AbstractCodegenTask.PATTERN_SPLITTER))
                .any { pattern -> columnName.matches(pattern.replace("%", ".*").toRegex()) }
        }
    }

    private fun isVersionColumn(column: Map<String, Any?>, context: EntityContext) = with(context) {
        SqlSchemaUtils.getColumnName(column) == getString("versionField")
    }

    private fun isIdColumn(column: Map<String, Any?>) = SqlSchemaUtils.isColumnPrimaryKey(column)

    private fun generateFieldComment(column: Map<String, Any?>, context: EntityContext): List<String> {
        val fieldName = SqlSchemaUtils.getColumnName(column)
        val fieldType = SqlSchemaUtils.getColumnType(column)

        with(context) {
            return buildList {
                add("/**")

                SqlSchemaUtils.getComment(column)
                    .split(Regex(AbstractCodegenTask.PATTERN_LINE_BREAK))
                    .filter { it.isNotEmpty() }
                    .forEach { add(" * $it") }

                if (SqlSchemaUtils.hasEnum(column)) {
                    val enumMap = enumConfigMap[fieldType] ?: enumConfigMap[SqlSchemaUtils.getType(column)]
                    enumMap?.entries?.forEach { (key, value) ->
                        add(" * $key:${value[0]}:${value[1]}")
                    }
                }

                if (fieldName == getString("versionField")) {
                    add(" * 数据版本（支持乐观锁）")
                }

                if (getBoolean("generateDbType")) {
                    add(" * ${SqlSchemaUtils.getColumnDbType(column)}")
                }

                add(" */")
            }
        }
    }

    private fun processEntityCustomerSourceFile(
        filePath: String,
        importLines: MutableList<String>,
        annotationLines: MutableList<String>,
        customerLines: MutableList<String>,
        context: EntityContext,
    ): Boolean {
        val file = File(filePath)
        if (file.exists()) {
            val content = file.readText(charset(context.getString("outputEncoding")))
            val lines = content.replace("\r\n", "\n").split("\n")

            var startMapperLine = 0
            var endMapperLine = 0
            var startClassLine = 0

            for (i in 1 until lines.size) {
                val line = lines[i]
                when {
                    annotationLines.isEmpty() && startClassLine == 0 && line.trim().startsWith("import ") -> {
                        importLines.add(line.trim().removePrefix("import").trim())
                    }

                    (line.trim().startsWith("@") || annotationLines.isNotEmpty()) && startClassLine == 0 -> {
                        annotationLines.add(line)
                    }

                    line.trim().startsWith("class") && startClassLine == 0 -> {
                        startClassLine = i
                    }

                    line.contains("【字段映射开始】") -> {
                        startMapperLine = i
                    }

                    line.contains("【字段映射结束】") -> {
                        endMapperLine = i
                    }

                    startMapperLine == 0 || endMapperLine > 0 -> {
                        customerLines.add(line)
                    }
                }
            }

            // 处理customerLines，移除末尾的大括号
            for (i in customerLines.size - 1 downTo 0) {
                val line = customerLines[i]
                if (line.contains("}")) {
                    customerLines.removeAt(i)
                    if (!line.equals("}", ignoreCase = true)) {
                        customerLines.add(i, line.take(line.lastIndexOf("}")))
                    }
                    break
                }
                customerLines.removeAt(i)
            }

            if (startMapperLine == 0 || endMapperLine == 0) {
                return false
            }

            file.delete()
        }
        return true
    }

    private fun processAnnotationLines(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        annotationLines: MutableList<String>,
        context: EntityContext,
        ids: List<Map<String, Any?>>,
    ) {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = context.entityTypeMap[tableName] ?: ""

        // 移除并重新添加 @Aggregate 注解
        removeText(annotationLines, """@Aggregate\(.*\)""")

        val cleanedComment = SqlSchemaUtils.getComment(table)
            .replace(Regex(AbstractCodegenTask.PATTERN_LINE_BREAK), "\\\\n")
            .replace("\"", "\\\"")
            .replace(";", "，")

        addIfNone(
            annotationLines,
            """@Aggregate\(.*\)""",
            """@Aggregate(aggregate = "${toUpperCamelCase(context.resolveAggregateWithModule(tableName))}", name = "$entityType", root = ${
                SqlSchemaUtils.isAggregateRoot(
                    table
                )
            }, type = ${if (SqlSchemaUtils.isValueObject(table)) "Aggregate.TYPE_VALUE_OBJECT" else "Aggregate.TYPE_ENTITY"}, description = "$cleanedComment")"""
        ) { _, _ -> 0 }

        addIfNone(annotationLines, """@Entity(\(.*\))?""", "@Entity")

        if (ids.size > 1) {
            addIfNone(
                annotationLines,
                """@IdClass\(.*\)""",
                "@IdClass(${entityType}.${AbstractCodegenTask.DEFAULT_MUL_PRI_KEY_NAME}::class)"
            )
        }

        addIfNone(
            annotationLines,
            """@Table\(.*\)?""",
            "@Table(name = \"$LEFT_QUOTES_4_ID_ALIAS$tableName$RIGHT_QUOTES_4_ID_ALIAS\")"
        )

        addIfNone(annotationLines, """@DynamicInsert(\(.*\))?""", "@DynamicInsert")
        addIfNone(annotationLines, """@DynamicUpdate(\(.*\))?""", "@DynamicUpdate")

        // 处理软删除相关注解
        val deletedField = context.getString("deletedField")
        val versionField = context.getString("versionField")

        if (deletedField.isNotBlank() && SqlSchemaUtils.hasColumn(deletedField, columns)) {
            if (ids.isEmpty()) {
                throw RuntimeException("实体缺失【主键】：$tableName")
            }

            val idFieldName = if (ids.size == 1) {
                toLowerCamelCase(SqlSchemaUtils.getColumnName(ids[0])) ?: SqlSchemaUtils.getColumnName(ids[0])
            } else {
                "(${
                    ids.joinToString(", ") {
                        toLowerCamelCase(SqlSchemaUtils.getColumnName(it)) ?: SqlSchemaUtils.getColumnName(
                            it
                        )
                    }
                })"
            }

            val idFieldValue = if (ids.size == 1) "?" else "(${ids.joinToString(", ") { "?" }})"

            if (SqlSchemaUtils.hasColumn(versionField, columns)) {
                addIfNone(
                    annotationLines,
                    """@SQLDelete\(.*\)?""",
                    """@SQLDelete(sql = "update $LEFT_QUOTES_4_ID_ALIAS$tableName$RIGHT_QUOTES_4_ID_ALIAS set $LEFT_QUOTES_4_ID_ALIAS$deletedField$RIGHT_QUOTES_4_ID_ALIAS = $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS where $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS = $idFieldValue and $LEFT_QUOTES_4_ID_ALIAS$versionField$RIGHT_QUOTES_4_ID_ALIAS = ?")"""
                )
            } else {
                addIfNone(
                    annotationLines,
                    """@SQLDelete\(.*\)?""",
                    """@SQLDelete(sql = "update $LEFT_QUOTES_4_ID_ALIAS$tableName$RIGHT_QUOTES_4_ID_ALIAS set $LEFT_QUOTES_4_ID_ALIAS$deletedField$RIGHT_QUOTES_4_ID_ALIAS = $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS where $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS = $idFieldValue")"""
                )
            }

            addIfNone(
                annotationLines,
                """@Where\(.*\)?""",
                """@Where(clause = "$LEFT_QUOTES_4_ID_ALIAS$deletedField$RIGHT_QUOTES_4_ID_ALIAS = 0")"""
            )
        }


    }

    private fun prepareColumnData(
        table: Map<String, Any?>,
        column: Map<String, Any?>,
        ids: List<Map<String, Any?>>,
        context: EntityContext,
        importManager: EntityImportManager,
    ): Map<String, Any?> {
        val columnName = SqlSchemaUtils.getColumnName(column)
        val columnType = SqlSchemaUtils.getColumnType(column)

        val needGenerate = isColumnNeedGenerate(table, column, context.relationsMap) ||
                columnName == context.getString("versionField")

        if (!needGenerate) {
            return mapOf("needGenerate" to false)
        }

        var updatable = true
        var insertable = true

        if (SqlSchemaUtils.getColumnType(column).contains("Date")) {
            updatable = !SqlSchemaUtils.isAutoUpdateDateColumn(column)
            insertable = !SqlSchemaUtils.isAutoInsertDateColumn(column)
        }

        if (isReadOnlyColumn(column, context)) {
            insertable = false
            updatable = false
        }

        if (SqlSchemaUtils.hasIgnoreInsert(column)) {
            insertable = false
        }

        if (SqlSchemaUtils.hasIgnoreUpdate(column)) {
            updatable = false
        }

        val comments = generateFieldComment(column, context)
        val comment = comments.joinToString("\n") { "    $it" }

        val annotations = mutableListOf<String>()

        if (isIdColumn(column)) {
            annotations.add("@Id")
            if (ids.size == 1) {
                val entityIdGenerator = resolveEntityIdGenerator(table, context)
                when {
                    SqlSchemaUtils.isValueObject(table) -> {
                        // 不使用ID生成器
                    }

                    entityIdGenerator.isNotEmpty() -> {
                        annotations.add("@GeneratedValue(generator = \"$entityIdGenerator\")")
                        annotations.add("@GenericGenerator(name = \"$entityIdGenerator\", strategy = \"$entityIdGenerator\")")
                    }

                    else -> {
                        annotations.add("@GeneratedValue(strategy = GenerationType.IDENTITY)")
                    }
                }
            }
        }

        if (isVersionColumn(column, context)) {
            annotations.add("@Version")
        }

        if (SqlSchemaUtils.hasEnum(column)) {
            if (context.typeMapping.containsKey(columnType)) {
                importManager.add(context.typeMapping[columnType]!!)
            }
            annotations.add("@Convert(converter = $columnType.Converter::class)")
        }

        val leftQuote = LEFT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
        val rightQuote = RIGHT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")

        if (!updatable || !insertable) {
            annotations.add("@Column(name = \"$leftQuote$columnName$rightQuote\", insertable = $insertable, updatable = $updatable)")
        } else {
            annotations.add("@Column(name = \"$leftQuote$columnName$rightQuote\")")
        }

        val fieldName = toLowerCamelCase(columnName) ?: columnName
        val defaultJavaLiteral = SqlSchemaUtils.getColumnDefaultLiteral(column)
        val defaultValue = " = $defaultJavaLiteral"

        return mapOf(
            "needGenerate" to true,
            "columnName" to columnName,
            "fieldName" to fieldName,
            "fieldType" to columnType,
            "defaultValue" to defaultValue,
            "comment" to comment,
            "annotations" to annotations
        )
    }

    private fun prepareRelationData(
        table: Map<String, Any?>,
        relations: Map<String, Map<String, String>>,
        tablePackageMap: Map<String, String>,
        context: EntityContext,
    ): List<Map<String, Any?>> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val result = mutableListOf<Map<String, Any?>>()

        if (!relations.containsKey(tableName)) {
            return result
        }

        for ((refTableName, relationInfo) in relations[tableName]!!) {
            val refInfos = relationInfo.split(";")
            val navTable = context.tableMap[refTableName] ?: continue

            if (refInfos[0] == "PLACEHOLDER") {
                continue
            }

            val fetchType = when {
                relationInfo.endsWith(";LAZY") -> "LAZY"
                SqlSchemaUtils.hasLazy(navTable) -> if (SqlSchemaUtils.isLazy(navTable, false)) "LAZY" else "EAGER"
                else -> "EAGER"
            }

            val relation = refInfos[0]
            val joinColumn = refInfos[1]
            val leftQuote = LEFT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
            val rightQuote = RIGHT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")

            val annotations = mutableListOf<String>()
            var fieldName = ""
            var fieldType = ""
            var defaultValue = ""
            var hasLoadMethod = false
            var fullEntityType = ""
            val entityType = context.entityTypeMap[refTableName] ?: ""

            when (relation) {
                "OneToMany" -> {
                    annotations.add("@${relation}(cascade = [CascadeType.ALL], fetch = FetchType.$fetchType, orphanRemoval = true)")
                    annotations.add("@Fetch(FetchMode.SUBSELECT)")
                    annotations.add("@JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false)")

                    val countIsOne = SqlSchemaUtils.countIsOne(navTable)

                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    fullEntityType = "$entityPackage.$entityType"
                    fieldName = Inflector.pluralize(toLowerCamelCase(entityType) ?: entityType)
                    fieldType = "MutableList<$fullEntityType>"
                    defaultValue = " = mutableListOf()"
                    hasLoadMethod = countIsOne
                }

                "*ManyToOne" -> {
                    annotations.add("@${relation.replace("*", "")}(cascade = [], fetch = FetchType.$fetchType)")
                    annotations.add("@JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false, insertable = false, updatable = false)")

                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    fullEntityType = "$entityPackage.$entityType"
                    fieldName = toLowerCamelCase(entityType) ?: entityType
                    fieldType = "$fullEntityType?"
                    defaultValue = " = null"
                }

                "ManyToOne" -> {
                    annotations.add("@${relation}(cascade = [], fetch = FetchType.$fetchType)")
                    annotations.add("@JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false)")

                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    fullEntityType = "$entityPackage.$entityType"
                    fieldName = toLowerCamelCase(entityType) ?: entityType
                    fieldType = "$fullEntityType?"
                    defaultValue = " = null"
                }

                "OneToOne" -> {
                    annotations.add("@${relation}(cascade = [], fetch = FetchType.$fetchType)")
                    annotations.add("@JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false)")

                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    fullEntityType = "$entityPackage.$entityType"
                    fieldName = toLowerCamelCase(entityType) ?: entityType
                    fieldType = "$fullEntityType?"
                    defaultValue = " = null"
                }

                "ManyToMany" -> {
                    annotations.add("@${relation}(cascade = [], fetch = FetchType.$fetchType)")
                    annotations.add("@Fetch(FetchMode.SUBSELECT)")
                    val joinTableName = refInfos[3]
                    val inverseJoinColumn = refInfos[2]
                    annotations.add(
                        "@JoinTable(name = \"$leftQuote$joinTableName$rightQuote\", " +
                                "joinColumns = [JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false)], " +
                                "inverseJoinColumns = [JoinColumn(name = \"$leftQuote$inverseJoinColumn$rightQuote\", nullable = false)])"
                    )

                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    fullEntityType = "$entityPackage.$entityType"
                    fieldName = Inflector.pluralize(toLowerCamelCase(entityType) ?: entityType)
                    fieldType = "MutableList<$fullEntityType>"
                    defaultValue = " = mutableListOf()"
                }

                "*ManyToMany" -> {
                    val entityTypeName = context.entityTypeMap[tableName] ?: ""
                    val fieldNameFromTable = Inflector.pluralize(toLowerCamelCase(entityTypeName) ?: entityTypeName)
                    annotations.add(
                        "@${
                            relation.replace(
                                "*",
                                ""
                            )
                        }(mappedBy = \"$fieldNameFromTable\", cascade = [], fetch = FetchType.$fetchType)"
                    )
                    annotations.add("@Fetch(FetchMode.SUBSELECT)")

                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    fullEntityType = "$entityPackage.$entityType"
                    fieldName = Inflector.pluralize(toLowerCamelCase(entityType) ?: entityType)
                    fieldType = "MutableList<$fullEntityType>"
                    defaultValue = " = mutableListOf()"
                }
            }

            result.add(
                mapOf(
                    "relation" to relation,
                    "fieldName" to fieldName,
                    "fieldType" to fieldType,
                    "defaultValue" to defaultValue,
                    "annotations" to annotations,
                    "hasLoadMethod" to hasLoadMethod,
                    "entityType" to entityType,
                    "fullEntityType" to fullEntityType
                )
            )
        }

        return result
    }
}
