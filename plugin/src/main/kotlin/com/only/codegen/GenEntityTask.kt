package com.only.codegen

import com.only.codegen.misc.*
import com.only.codegen.misc.SqlSchemaUtils.LEFT_QUOTES_4_ID_ALIAS
import com.only.codegen.misc.SqlSchemaUtils.RIGHT_QUOTES_4_ID_ALIAS
import com.only.codegen.misc.SqlSchemaUtils.hasColumn
import com.only.codegen.template.TemplateNode
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.BufferedWriter
import java.io.File
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 生成实体类任务
 */
open class GenEntityTask : GenArchTask() {

    companion object {
        private const val DEFAULT_SCHEMA_PACKAGE = "meta"
        private const val DEFAULT_SPEC_PACKAGE = "specs"
        private const val DEFAULT_FAC_PACKAGE = "factory"
        private const val DEFAULT_ENUM_PACKAGE = "enums"
        private const val DEFAULT_DOMAIN_EVENT_PACKAGE = "events"
        private const val DEFAULT_SCHEMA_BASE_CLASS_NAME = "Schema"
    }

    @Internal
    val tableMap = mutableMapOf<String, Map<String, Any?>>()

    @Internal
    val columnsMap = mutableMapOf<String, List<Map<String, Any?>>>()

    @Internal
    val relations = mutableMapOf<String, Map<String, String>>()

    @Internal
    val tablePackageMap = mutableMapOf<String, String>()

    @Internal
    val tableModuleMap = mutableMapOf<String, String>()

    @Internal
    val tableAggregateMap = mutableMapOf<String, String>()

    @Internal
    val enumConfigMap = mutableMapOf<String, Map<Int, Array<String>>>()

    @Internal
    val enumPackageMap = mutableMapOf<String, String>()

    @Internal
    val enumTableNameMap = mutableMapOf<String, String>()

    @Internal
    val entityTypeMap = mutableMapOf<String, String>()

    @Internal
    val annotationsCache = mutableMapOf<String, Map<String, String>>()

    @Internal
    var dbType = "mysql"

    @Internal
    var aggregatesPath = ""

    @Internal
    var schemaPath = ""

    @Internal
    var subscriberPath = ""

    @Internal
    val templateNodeMap = mutableMapOf<String, MutableList<TemplateNode>>()

    fun alias4Design(name: String): String = when (name.lowercase()) {
        "entity", "aggregate", "entities", "aggregates" -> "aggregate"
        "schema", "schemas" -> "schema"
        "enum", "enums" -> "enum"
        "enumitem", "enum_item" -> "enum_item"
        "factories", "factory", "fac" -> "factory"
        "specifications", "specification", "specs", "spec", "spe" -> "specification"
        "domain_events", "domain_event", "d_e", "de" -> "domain_event"
        "domain_event_handlers", "domain_event_handler", "d_e_h", "deh", "domain_event_subscribers", "domain_event_subscriber", "d_e_s", "des" -> "domain_event_handler"
        "domain_service", "service", "svc" -> "domain_service"
        else -> name
    }

    override fun renderTemplate(
        templateNodes: List<TemplateNode>,
        parentPath: String,
    ) {
        templateNodes.forEach { templateNode ->
            val alias = alias4Design(templateNode.tag!!)
            when (alias) {
                "aggregate" -> aggregatesPath = parentPath
                "schema_base" -> schemaPath = parentPath
                "domain_event_handler" -> subscriberPath = parentPath
            }

            templateNodeMap.getOrPut(alias) { mutableListOf() }.add(templateNode)
        }
    }

    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()
        genEntity()
    }

    fun resolveDatabaseConfig(): Triple<String, String, String> {
        val ext = extension.get()
        return Triple(
            ext.database.url.get(),
            ext.database.username.get(),
            ext.database.password.get()
        )
    }

    fun resolveTables(): List<Map<String, Any?>> {
        SqlSchemaUtils.loadLogger(logger)
        val (url, username, password) = resolveDatabaseConfig()
        return SqlSchemaUtils.resolveTables(url, username, password)
    }

    fun resolveColumns(): List<Map<String, Any?>> {
        SqlSchemaUtils.loadLogger(logger)
        val (url, username, password) = resolveDatabaseConfig()
        return SqlSchemaUtils.resolveColumns(url, username, password)
    }

    fun resolveEntityType(tableName: String): String =
        entityTypeMap.getOrPut(tableName) {
            val table = tableMap[tableName]!!
            val type = SqlSchemaUtils.getType(table).takeIf { it.isNotBlank() }
                ?: toUpperCamelCase(tableName)
                ?: throw RuntimeException("实体类名未生成")
            type
        }

    fun resolveModule(tableName: String): String =
        tableModuleMap.computeIfAbsent(tableName) {
            var result = ""
            generateSequence(tableMap[tableName]) { currentTable ->
                val parent = SqlSchemaUtils.getParent(currentTable)
                if (parent.isBlank()) null else tableMap[parent]
            }.forEach { table ->
                val module = SqlSchemaUtils.getModule(table)
                val tableNameStr = SqlSchemaUtils.getTableName(table)
                logger.info("尝试${if (table == tableMap[tableName]) "" else "父表"}模块: $tableNameStr ${module.ifBlank { "[缺失]" }}")

                if (SqlSchemaUtils.isAggregateRoot(table) || module.isNotBlank()) {
                    result = module
                    return@forEach
                }
            }
            logger.info("模块解析结果: $tableName ${result.ifBlank { "[无]" }}")
            result
        }

    fun resolveAggregate(tableName: String): String =
        tableAggregateMap.computeIfAbsent(tableName) {
            var aggregateRootTableName = tableName
            var result = ""

            generateSequence(tableMap[tableName]) { currentTable ->
                val parent = SqlSchemaUtils.getParent(currentTable)
                if (parent.isBlank()) null else {
                    tableMap[parent]?.also {
                        aggregateRootTableName = SqlSchemaUtils.getTableName(it)
                    }
                }
            }.forEach { table ->
                val aggregate = SqlSchemaUtils.getAggregate(table)
                val tableNameStr = SqlSchemaUtils.getTableName(table)
                logger.info("尝试${if (table == tableMap[tableName]) "" else "父表"}聚合: $tableNameStr ${aggregate.ifBlank { "[缺失]" }}")

                if (SqlSchemaUtils.isAggregateRoot(table) || aggregate.isNotBlank()) {
                    result = aggregate.takeIf { it.isNotBlank() }
                        ?: (toSnakeCase(resolveEntityType(aggregateRootTableName)) ?: "")
                    return@forEach
                }
            }

            if (result.isBlank()) {
                result = toSnakeCase(resolveEntityType(aggregateRootTableName)) ?: ""
            }

            logger.info("聚合解析结果: $tableName ${result.ifBlank { "[缺失]" }}")
            result
        }

    fun resolveAggregatesPath(): String {
        if (aggregatesPath.isNotBlank()) return aggregatesPath

        return resolvePackageDirectory(
            getDomainModulePath(),
            "${extension.get().basePackage.get()}.${AGGREGATE_PACKAGE}"
        )
    }

    fun resolveAggregateWithModule(tableName: String): String {
        val module = resolveModule(tableName)
        return if (module.isNotBlank()) {
            concatPackage(module, resolveAggregate(tableName))
        } else {
            resolveAggregate(tableName)
        }
    }

    fun resolveAggregatesPackage(): String {
        return resolvePackage(
            "${resolveAggregatesPath()}${File.separator}X.kt"
        ).substring(extension.get().basePackage.get().length + 1)
    }

    fun resolveEntityFullPackage(table: Map<String, Any?>, basePackage: String, baseDir: String): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val packageName = concatPackage(basePackage, resolveEntityPackage(tableName))
        return packageName
    }

    fun resolveEntityPackage(tableName: String): String {
        val module = resolveModule(tableName)
        val aggregate = resolveAggregate(tableName)
        return concatPackage(resolveAggregatesPackage(), module, aggregate.lowercase())
    }

    fun resolveRelationTable(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
    ): Map<String, Map<String, String>> {
        val result = mutableMapOf<String, MutableMap<String, String>>()
        val tableName = SqlSchemaUtils.getTableName(table)

        if (SqlSchemaUtils.isIgnore(table)) return result

        // 聚合内部关系 OneToMany
        // OneToOne关系也用OneToMany实现，避免持久化存储结构变更
        if (!SqlSchemaUtils.isAggregateRoot(table)) {
            val parent = SqlSchemaUtils.getParent(table)
            result.putIfAbsent(parent, mutableMapOf())

            var rewrited = false // 是否显式声明引用字段
            columns.forEach { column ->
                if (SqlSchemaUtils.hasReference(column)) {
                    if (parent.equals(SqlSchemaUtils.getReference(column), ignoreCase = true)) {
                        val lazy = SqlSchemaUtils.isLazy(
                            column,
                            "LAZY".equals(extension.get().generation.fetchType.get(), ignoreCase = true)
                        )
                        val columnName = SqlSchemaUtils.getColumnName(column)

                        // 在父表中记录子表的OneToMany关系
                        result[parent]!!.putIfAbsent(
                            tableName,
                            "OneToMany;$columnName${if (lazy) ";LAZY" else ""}"
                        )

                        // 处理子表对父表的引用
                        result.putIfAbsent(tableName, mutableMapOf())
                        val parentRelation = if (extension.get().generation.generateParent.get()) {
                            "*ManyToOne;$columnName${if (lazy) ";LAZY" else ""}"
                        } else {
                            "PLACEHOLDER;$columnName" // 使用占位符，防止聚合间关系误判
                        }
                        result[tableName]!!.putIfAbsent(parent, parentRelation)

                        rewrited = true
                    }
                }
            }
            if (!rewrited) {
                val column = columns.firstOrNull {
                    SqlSchemaUtils.getColumnName(it).equals("${parent}_id", ignoreCase = true)
                }
                if (column != null) {
                    val lazy = SqlSchemaUtils.isLazy(
                        column,
                        "LAZY".equals(extension.get().generation.fetchType.get(), ignoreCase = true)
                    )
                    val columnName = SqlSchemaUtils.getColumnName(column)

                    // 在父表中记录子表的OneToMany关系
                    result[parent]!!.putIfAbsent(
                        tableName,
                        "OneToMany;$columnName${if (lazy) ";LAZY" else ""}"
                    )

                    // 处理子表对父表的引用
                    result.putIfAbsent(tableName, mutableMapOf())
                    val parentRelation = if (extension.get().generation.generateParent.get()) {
                        "*ManyToOne;$columnName${if (lazy) ";LAZY" else ""}"
                    } else {
                        "PLACEHOLDER;$columnName" // 使用占位符，防止聚合间关系误判
                    }
                    result[tableName]!!.putIfAbsent(parent, parentRelation)
                }
            }
        }

        // 聚合之间关系
        if (SqlSchemaUtils.hasRelation(table)) {
            // ManyToMany
            var owner = ""
            var beowned = ""
            var joinCol = ""
            var inverseJoinColumn = ""
            var ownerLazy = false

            columns.forEach { column ->
                if (SqlSchemaUtils.hasReference(column)) {
                    val refTableName = SqlSchemaUtils.getReference(column)
                    result.putIfAbsent(refTableName, mutableMapOf())
                    val lazy = SqlSchemaUtils.isLazy(
                        column,
                        "LAZY".equals(extension.get().generation.fetchType.get(), ignoreCase = true)
                    )
                    if (owner.isEmpty()) {
                        ownerLazy = lazy
                        owner = refTableName
                        joinCol = SqlSchemaUtils.getColumnName(column)
                    } else {
                        beowned = refTableName
                        inverseJoinColumn = SqlSchemaUtils.getColumnName(column)
                        result[beowned]!!.putIfAbsent(
                            owner,
                            "*ManyToMany;$inverseJoinColumn${if (lazy) ";LAZY" else ""}"
                        )
                    }
                }
            }
            if (owner.isNotEmpty() && beowned.isNotEmpty()) {
                result[owner]!!.putIfAbsent(
                    beowned,
                    "ManyToMany;$joinCol;$inverseJoinColumn;$tableName${if (ownerLazy) ";LAZY" else ""}"
                )
            }
        }

        // 处理显式关系配置
        columns.forEach { column ->
            val colRel = SqlSchemaUtils.getRelation(column)
            val colName = SqlSchemaUtils.getColumnName(column)
            var refTableName: String? = null
            val lazy = SqlSchemaUtils.isLazy(
                column,
                "LAZY".equals(extension.get().generation.fetchType.get(), ignoreCase = true)
            )

            if (colRel.isNotBlank() || SqlSchemaUtils.hasReference(column)) {
                when (colRel) {
                    "OneToOne", "1:1" -> {
                        refTableName = SqlSchemaUtils.getReference(column)
                        result.putIfAbsent(tableName, mutableMapOf())
                        result[tableName]!!.putIfAbsent(
                            refTableName,
                            "OneToOne;$colName${if (lazy) ";LAZY" else ""}"
                        )
                    }

                    "ManyToOne", "*:1" -> {
                        refTableName = SqlSchemaUtils.getReference(column)
                        result.putIfAbsent(tableName, mutableMapOf())
                        result[tableName]!!.putIfAbsent(
                            refTableName,
                            "ManyToOne;$colName${if (lazy) ";LAZY" else ""}"
                        )
                    }

                    else -> {
                        // 默认处理为 ManyToOne
                        if (SqlSchemaUtils.hasReference(column)) {
                            refTableName = SqlSchemaUtils.getReference(column)
                            result.putIfAbsent(tableName, mutableMapOf())
                            result[tableName]!!.putIfAbsent(
                                refTableName,
                                "ManyToOne;$colName${if (lazy) ";LAZY" else ""}"
                            )
                        }
                    }
                }
            }
        }

        return result
    }

    fun resolveSchemaPath(): String {
        if (schemaPath.isNotBlank()) return schemaPath

        return resolvePackageDirectory(
            getDomainModulePath(),
            "${extension.get().basePackage.get()}.${getEntitySchemaOutputPackage()}"
        )
    }

    fun resolveSchemaPackage(): String {
        return resolvePackage(
            "${resolveSchemaPath()}${File.separator}X.kt"
        ).substring(
            if (extension.get().basePackage.get().isBlank()) 0 else (extension.get().basePackage.get().length + 1)
        )
    }

    fun resolveIdColumns(columns: List<Map<String, Any?>>): List<Map<String, Any?>> {
        return columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }
    }

    fun resolveEntityIdGenerator(table: Map<String, Any?>): String {
        return when {
            SqlSchemaUtils.hasIdGenerator(table) -> {
                SqlSchemaUtils.getIdGenerator(table)
            }

            SqlSchemaUtils.isValueObject(table) -> {
                extension.get().generation.idGenerator4ValueObject.get().ifBlank {
                    // ValueObject 值对象 默认使用MD5
                    "com.only4.cap4k.ddd.domain.repo.Md5HashIdentifierGenerator"
                }
            }

            else -> {
                extension.get().generation.idGenerator.get().ifBlank {
                    ""
                }
            }
        }
    }

    fun processTablesAndColumns(tables: List<Map<String, Any?>>, allColumns: List<Map<String, Any?>>) {
        val maxTableNameLength = tables.maxOfOrNull { SqlSchemaUtils.getTableName(it).length } ?: 20

        tables.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)
            val tableColumns = allColumns.filter { column ->
                SqlSchemaUtils.isColumnInTable(column, table)
            }.sortedBy { SqlSchemaUtils.getOrdinalPosition(it) }

            tableMap[tableName] = table
            columnsMap[tableName] = tableColumns

            logger.info(String.format("%" + maxTableNameLength + "s   %s", "", SqlSchemaUtils.getComment(table)))
            logger.info(
                String.format(
                    "%" + maxTableNameLength + "s : (%s)",
                    tableName,
                    tableColumns.joinToString(", ") { column ->
                        "${SqlSchemaUtils.getColumnDbDataType(column)} ${SqlSchemaUtils.getColumnName(column)}"
                    }
                )
            )
        }
    }

    fun processTableRelations() {
        tableMap.values.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)
            val tableColumns = columnsMap[tableName]!!

            val relationTable = resolveRelationTable(table, tableColumns)

            relationTable.forEach { (key, value) ->
                relations.merge(key, value) { existing, new ->
                    existing.toMutableMap().apply { putAll(new) }
                }
            }

            tablePackageMap[tableName] = resolveEntityFullPackage(
                table,
                extension.get().basePackage.get(),
                getDomainModulePath()
            )
        }
    }

    fun processEnumConfigurations() {
        tableMap.values.forEach { table ->
            if (SqlSchemaUtils.isIgnore(table)) return@forEach

            val tableName = SqlSchemaUtils.getTableName(table)
            val tableColumns = columnsMap[tableName]!!

            tableColumns.forEach { column ->
                if (SqlSchemaUtils.hasEnum(column) && !SqlSchemaUtils.isIgnore(column)) {
                    val enumConfig = SqlSchemaUtils.getEnum(column)
                    if (enumConfig.isNotEmpty()) {
                        val enumType = SqlSchemaUtils.getType(column)
                        enumConfigMap[enumType] = enumConfig

                        val enumPackage = buildString {
                            val packageName = templateNodeMap["enum"]
                                ?.takeIf { it.isNotEmpty() }
                                ?.get(0)?.name
                                ?.takeIf { it.isNotBlank() }
                                ?: DEFAULT_ENUM_PACKAGE

                            if (packageName.isNotBlank()) {
                                append(".$packageName")
                            }
                        }

                        enumPackageMap[enumType] =
                            "${extension.get().basePackage.get()}.${resolveEntityPackage(tableName)}$enumPackage"
                        enumTableNameMap[enumType] = tableName
                    }
                }
            }
        }
    }

    fun isColumnNeedGenerate(
        table: Map<String, Any?>,
        column: Map<String, Any?>,
        relations: Map<String, Map<String, String?>?>,
    ): Boolean {
        val tableName: String = SqlSchemaUtils.getTableName(table)
        val columnName: String = SqlSchemaUtils.getColumnName(column)
        if (SqlSchemaUtils.isIgnore(column)) {
            return false
        }
        if (SqlSchemaUtils.isIgnore(column)) {
            return false
        }

        if (!SqlSchemaUtils.isAggregateRoot(table)) {
            val parent = SqlSchemaUtils.getParent(table)
            val refMatchesParent = SqlSchemaUtils.hasReference(column) &&
                    parent.equals(SqlSchemaUtils.getReference(column), ignoreCase = true)
            val fkNameMatches = columnName.equals("${parent}_id", ignoreCase = true)
            if (refMatchesParent || fkNameMatches) return false
        }

        if (relations.containsKey(tableName)) {
            for (entry in relations[tableName]!!.entries) {
                val refInfos = entry.value!!.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                when (refInfos[0]) {
                    "ManyToOne", "OneToOne" -> if (columnName.equals(refInfos[1], ignoreCase = true)) {
                        return false
                    }

                    "PLACEHOLDER" -> if (columnName.equals(refInfos[1], ignoreCase = true)) {
                        return false // PLACEHOLDER 关系的字段不生成
                    }

                    else -> {}
                }
            }
        }
        return true
    }

    fun isReadOnlyColumn(column: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.hasReadOnly(column)) return true

        val columnName = SqlSchemaUtils.getColumnName(column).lowercase()
        val readonlyFields = extension.get().generation.readonlyFields.get()

        return readonlyFields.isNotBlank() && readonlyFields
            .lowercase()
            .split(PATTERN_SPLITTER.toRegex())
            .any { pattern -> columnName.matches(pattern.replace("%", ".*").toRegex()) }
    }

    fun isVersionColumn(column: Map<String, Any?>): Boolean {
        return SqlSchemaUtils.getColumnName(column) == extension.get().generation.versionField.get()
    }

    fun isIdColumn(column: Map<String, Any?>): Boolean {
        return SqlSchemaUtils.isColumnPrimaryKey(column)
    }

    fun genEntity() {
        SqlSchemaUtils.task = this

        val (url, _, _) = resolveDatabaseConfig()
        dbType = SqlSchemaUtils.recognizeDbType(url)
        SqlSchemaUtils.processSqlDialect(dbType)

        val tables = resolveTables()
        if (tables.isEmpty()) {
            return
        }

        processTablesAndColumns(tables, resolveColumns())
        processTableRelations()
        processEnumConfigurations()

        generateEnums(tablePackageMap)
        generateEntities(relations, tablePackageMap)
    }

    fun generateEnums(tablePackageMap: Map<String, String>) {
        enumConfigMap.forEach { (enumType, enumConfig) ->
            writeEnumSourceFile(
                enumConfig, enumType,
                extension.get().generation.enumValueField.get(),
                extension.get().generation.enumNameField.get(),
                tablePackageMap, getDomainModulePath()
            )
        }
    }

    fun generateEntities(relations: Map<String, Map<String, String>>, tablePackageMap: Map<String, String>) {
        val ext = extension.get()
        if (ext.generation.generateSchema.get()) {
            writeSchemaBaseSourceFile(getDomainModulePath())
        }

        tableMap.values.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)
            val tableColumns = columnsMap[tableName]!!

            writeEntitySourceFile(
                table, tableColumns, tablePackageMap, relations,
                getDomainModulePath()
            )
        }
    }

    fun processEntityCustomerSourceFile(
        filePath: String,
        importLines: MutableList<String>,
        annotationLines: MutableList<String>,
        customerLines: MutableList<String>,
    ): Boolean {
        val file = File(filePath)
        if (file.exists()) {
            val content = file.readText(charset(extension.get().outputEncoding.get()))
            val lines = content.replace("\r\n", "\n").split("\n")

            var startMapperLine = 0
            var endMapperLine = 0
            var startClassLine = 0

            for (i in 1 until lines.size) {
                val line = lines[i]
                when {
                    line.contains("【字段映射开始】") -> {
                        startMapperLine = i
                    }

                    line.contains("【字段映射结束】") -> {
                        endMapperLine = i
                    }

                    line.trim().startsWith("class") && startClassLine == 0 -> {
                        startClassLine = i
                    }

                    (line.trim().startsWith("@") || annotationLines.isNotEmpty()) && startClassLine == 0 -> {
                        annotationLines.add(line)
                        logger.debug("[annotation] $line")
                    }

                    annotationLines.isEmpty() && startClassLine == 0 -> {
                        importLines.add(line)
                        logger.debug("[import] $line")
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
                        customerLines.add(i, line.substring(0, line.lastIndexOf("}")))
                    }
                    break
                }
                customerLines.removeAt(i)
            }

            customerLines.forEach { line ->
                logger.debug("[customer] $line")
            }

            if (startMapperLine == 0 || endMapperLine == 0) {
                return false
            }

            file.delete()
        }
        return true
    }

    fun processAnnotationLines(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        annotationLines: MutableList<String>,
    ) {
        val tableName = SqlSchemaUtils.getTableName(table)

        // 移除并重新添加 @Aggregate 注解
        removeText(annotationLines, """@Aggregate\(.*\)""")

        // 处理注释：移除可能导致问题的特殊字符
        val cleanedComment = SqlSchemaUtils.getComment(table)
            .replace(Regex(PATTERN_LINE_BREAK), "\\\\n")
            .replace("\"", "\\\"")  // 转义双引号
            .replace(";", "，")  // 将分号替换为中文逗号

        addIfNone(
            annotationLines,
            """@Aggregate\(.*\)""",
            """@Aggregate(aggregate = "${toUpperCamelCase(resolveAggregateWithModule(tableName))}", name = "${
                resolveEntityType(
                    tableName
                )
            }", root = ${SqlSchemaUtils.isAggregateRoot(table)}, type = ${if (SqlSchemaUtils.isValueObject(table)) "Aggregate.TYPE_VALUE_OBJECT" else "Aggregate.TYPE_ENTITY"}, description = "$cleanedComment")"""
        ) { _, _ -> 0 }

        // 添加 JPA 基本注解
        addIfNone(annotationLines, """@Entity(\(.*\))?""", "@Entity")

        val ids = resolveIdColumns(columns)
        if (ids.size > 1) {
            addIfNone(
                annotationLines,
                """@IdClass(\(.*\))""",
                "@IdClass(${resolveEntityType(tableName)}.${DEFAULT_MUL_PRI_KEY_NAME}::class)"
            )
        }

        addIfNone(
            annotationLines,
            """@Table(\(.*\))?""",
            "@Table(name = \"$LEFT_QUOTES_4_ID_ALIAS$tableName$RIGHT_QUOTES_4_ID_ALIAS\")"
        )

        addIfNone(annotationLines, """@DynamicInsert(\(.*\))?""", "@DynamicInsert")
        addIfNone(annotationLines, """@DynamicUpdate(\(.*\))?""", "@DynamicUpdate")

        // 处理软删除相关注解
        val ext = extension.get()
        val deletedField = ext.generation.deletedField.get()
        val versionField = ext.generation.versionField.get()

        if (deletedField.isNotBlank() && hasColumn(deletedField, columns)) {
            if (ids.isEmpty()) {
                throw RuntimeException("实体缺失【主键】：$tableName")
            }

            val idFieldName = if (ids.size == 1) {
                toLowerCamelCase(SqlSchemaUtils.getColumnName(ids[0]))
                    ?: SqlSchemaUtils.getColumnName(ids[0])
            } else {
                "(${
                    ids.joinToString(", ") {
                        toLowerCamelCase(SqlSchemaUtils.getColumnName(it)) ?: SqlSchemaUtils.getColumnName(
                            it
                        )
                    }
                })"
            }

            val idFieldValue = if (ids.size == 1) "?" else "(" + ids.joinToString(", ") { "?" } + ")"

            if (hasColumn(versionField, columns)) {
                addIfNone(
                    annotationLines,
                    """@SQLDelete(\(.*\))?""",
                    """@SQLDelete(sql = "update $LEFT_QUOTES_4_ID_ALIAS$tableName$RIGHT_QUOTES_4_ID_ALIAS set $LEFT_QUOTES_4_ID_ALIAS$deletedField$RIGHT_QUOTES_4_ID_ALIAS = $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS where $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS = $idFieldValue and $LEFT_QUOTES_4_ID_ALIAS$versionField$RIGHT_QUOTES_4_ID_ALIAS = ?")"""
                )
            } else {
                addIfNone(
                    annotationLines,
                    """@SQLDelete(\(.*\))?""",
                    """@SQLDelete(sql = "update $LEFT_QUOTES_4_ID_ALIAS$tableName$RIGHT_QUOTES_4_ID_ALIAS set $LEFT_QUOTES_4_ID_ALIAS$deletedField$RIGHT_QUOTES_4_ID_ALIAS = $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS where $LEFT_QUOTES_4_ID_ALIAS$idFieldName$RIGHT_QUOTES_4_ID_ALIAS = $idFieldValue")"""
                )
            }

            addIfNone(
                annotationLines,
                """@Where(\(.*\))?""",
                """@Where(clause = "$LEFT_QUOTES_4_ID_ALIAS$deletedField$RIGHT_QUOTES_4_ID_ALIAS = 0")"""
            )
        }
    }

    fun writeEntitySourceFile(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        tablePackageMap: Map<String, String>,
        relations: Map<String, Map<String, String>>,
        baseDir: String,
    ) {
        val tag = "entity"
        val tableName = SqlSchemaUtils.getTableName(table)

        if (SqlSchemaUtils.isIgnore(table)) return
        if (SqlSchemaUtils.hasRelation(table)) return
        val ids = resolveIdColumns(columns)
        if (ids.isEmpty()) return

        // 收集上下文
        val context = buildEntityContext(table, columns, tablePackageMap, relations, baseDir)

        // 获取模板节点
        val entityTemplateNodes = if (templateNodeMap.containsKey(tag)) {
            templateNodeMap[tag]!!
        } else {
            listOf(resolveDefaultEntityTemplateNode())
        }

        // 渲染并写入文件
        for (templateNode in entityTemplateNodes) {
            val pathNode = templateNode.deepCopy().resolve(context)
            forceRender(
                pathNode,
                resolvePackageDirectory(
                    baseDir,
                    concatPackage(
                        extension.get().basePackage.get(),
                        context["templatePackage"].toString(),
                        context["package"].toString()
                    )
                )
            )
        }

        logger.info("已生成实体文件：$tableName")
    }

    fun writeEnumSourceFile(
        enumConfig: Map<Int, Array<String>>,
        enumClassName: String,
        enumValueField: String,
        enumNameField: String,
        tablePackageMap: Map<String, String>,
        baseDir: String,
    ) {
        val tag = "enum"
        val tableName = enumTableNameMap[enumClassName] ?: return
        val aggregate = resolveAggregateWithModule(tableName)

        val entityFullPackage = tablePackageMap[tableName] ?: return
        val entityType = resolveEntityType(tableName)
        val entityVar = toLowerCamelCase(entityType) ?: entityType

        val enumItems = enumConfig
            .toSortedMap()
            .map { (value, arr) ->
                mapOf(
                    "value" to value,
                    "name" to (arr[0]),
                    "desc" to (arr[1])
                )
            }

        val context = getEscapeContext().toMutableMap().apply {
            put("DEFAULT_ENUM_PACKAGE", DEFAULT_ENUM_PACKAGE)

            putContext(tag, "templatePackage", refPackage(resolveAggregatesPackage()))
            putContext(tag, "package", refPackage(aggregate))
            putContext(tag, "path", aggregate.replace(".", File.separator))
            putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
            putContext(tag, "Comment", "")
            putContext(tag, "CommentEscaped", "")
            putContext(tag, "entityPackage", refPackage(entityFullPackage, extension.get().basePackage.get()))
            putContext(tag, "Entity", entityType)
            putContext(tag, "AggregateRoot", this["Entity"]!!)
            putContext(tag, "EntityVar", entityVar)
            putContext(tag, "Enum", enumClassName)
            putContext(tag, "EnumValueField", enumValueField)
            putContext(tag, "EnumNameField", enumNameField)

            putContext(tag, "EnumItems", enumItems)
        }

        val enumTemplateNodes = if (templateNodeMap.containsKey(tag)) {
            templateNodeMap[tag]!!
        } else {
            listOf(resolveDefaultEnumTemplateNode())
        }

        for (templateNode in enumTemplateNodes) {
            val pathNode = templateNode.deepCopy().resolve(context)
            forceRender(
                pathNode,
                resolvePackageDirectory(
                    baseDir,
                    concatPackage(
                        extension.get().basePackage.get(),
                        context["templatePackage"].toString()
                    )
                )
            )
        }
    }

    fun writeSchemaBaseSourceFile(baseDir: String) {
        val tag = "schema_base"
        val schemaFullPackage = concatPackage(extension.get().basePackage.get(), resolveSchemaPackage())

        val context = getEscapeContext().toMutableMap().apply {
            putContext(
                tag,
                "templatePackage",
                refPackage(schemaFullPackage, extension.get().basePackage.get()),
            )
            putContext(tag, "SchemaBase", DEFAULT_SCHEMA_BASE_CLASS_NAME)
        }

        val schemaBaseTemplateNodes = if (templateNodeMap.containsKey(tag)) {
            templateNodeMap[tag]!!
        } else {
            listOf(resolveDefaultSchemaBaseTemplateNode())
        }

        for (templateNode in schemaBaseTemplateNodes) {
            val pathNode = templateNode.deepCopy().resolve(context)
            forceRender(
                pathNode,
                resolvePackageDirectory(
                    baseDir,
                    concatPackage(
                        extension.get().basePackage.get(),
                        context["templatePackage"].toString()
                    )
                )
            )
        }
    }

    fun generateFieldComment(column: Map<String, Any?>): List<String> {
        val fieldName = SqlSchemaUtils.getColumnName(column)
        val fieldType = SqlSchemaUtils.getColumnType(column)

        return buildList {
            add("/**")

            SqlSchemaUtils.getComment(column)
                .split(PATTERN_LINE_BREAK.toRegex())
                .filter { it.isNotEmpty() }
                .forEach { add(" * $it") }

            if (SqlSchemaUtils.hasEnum(column)) {
                logger.info("获取枚举 java类型：$fieldName -> $fieldType")
                val enumMap = enumConfigMap[fieldType] ?: enumConfigMap[SqlSchemaUtils.getType(column)]
                enumMap?.entries?.forEach { (key, value) ->
                    add(" * $key:${value[0]}:${value[1]}")
                }
            }

            if (fieldName == extension.get().generation.versionField.get()) {
                add(" * 数据版本（支持乐观锁）")
            }

            if (extension.get().generation.generateDbType.get()) {
                add(" * ${SqlSchemaUtils.getColumnDbType(column)}")
            }

            add(" */")
        }
    }

    fun resolveDefaultEnumTemplateNode(): TemplateNode {

        return TemplateNode().apply {
            type = "file"
            tag = "enum"
            name = "{{ path }}{{ SEPARATOR }}{{ DEFAULT_ENUM_PACKAGE }}{{ SEPARATOR }}{{ Enum }}.kt"
            format = "resouce"
            data = "enum"
            conflict = "overwrite"
        }
    }

    fun resolveDefaultSchemaBaseTemplateNode(): TemplateNode {

        return TemplateNode().apply {
            type = "file"
            tag = "schema_base"
            name = "{{ SchemaBase }}.kt"
            format = "resouce"
            data = "schema_base"
            conflict = "overwrite"
        }
    }

    fun resolveDefaultEntityTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = "entity"
            name = "{{ Entity }}.kt"
            format = "resouce"
            data = "entity"
            conflict = "overwrite"
        }
    }

    /**
     * 准备列数据供模板使用
     */
    fun prepareColumnData(
        table: Map<String, Any?>,
        column: Map<String, Any?>,
        ids: List<Map<String, Any?>>,
        relations: Map<String, Map<String, String>>,
        enums: MutableList<String>,
    ): Map<String, Any?> {
        val columnName = SqlSchemaUtils.getColumnName(column)
        val columnType = SqlSchemaUtils.getColumnType(column)

        // 检查是否需要生成该列
        val needGenerate = isColumnNeedGenerate(table, column, relations) ||
                columnName == extension.get().generation.versionField.get()

        if (!needGenerate) {
            return mapOf("needGenerate" to false)
        }

        // 计算 updatable 和 insertable
        var updatable = true
        var insertable = true

        if (SqlSchemaUtils.getColumnType(column).contains("Date")) {
            updatable = !SqlSchemaUtils.isAutoUpdateDateColumn(column)
            insertable = !SqlSchemaUtils.isAutoInsertDateColumn(column)
        }

        if (isReadOnlyColumn(column)) {
            insertable = false
            updatable = false
        }

        if (SqlSchemaUtils.hasIgnoreInsert(column)) {
            insertable = false
        }

        if (SqlSchemaUtils.hasIgnoreUpdate(column)) {
            updatable = false
        }

        // 生成注释
        val comments = generateFieldComment(column)
        val comment = comments.joinToString("\n") { "    $it" }

        // 生成注解列表
        val annotations = mutableListOf<String>()

        // ID annotation
        if (isIdColumn(column)) {
            annotations.add("@Id")
            if (ids.size == 1) {
                val entityIdGenerator = resolveEntityIdGenerator(table)
                when {
                    SqlSchemaUtils.isValueObject(table) -> {
                        // 不使用ID生成器
                    }

                    entityIdGenerator.isNotEmpty() -> {
                        annotations.add("@GeneratedValue(generator = \"$entityIdGenerator\")")
                        annotations.add("@GenericGenerator(name = \"$entityIdGenerator\", strategy = \"$entityIdGenerator\")")
                    }

                    else -> {
                        // 无ID生成器 使用数据库自增
                        annotations.add("@GeneratedValue(strategy = GenerationType.IDENTITY)")
                    }
                }
            }
        }

        // Version annotation
        if (isVersionColumn(column)) {
            annotations.add("@Version")
        }

        // Enum converter annotation
        if (SqlSchemaUtils.hasEnum(column)) {
            enums.add(columnType)
            annotations.add("@Convert(converter = $columnType.Converter::class)")
        }

        // Column annotation
        val leftQuote = LEFT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")
        val rightQuote = RIGHT_QUOTES_4_ID_ALIAS.replace("\"", "\\\"")

        if (!updatable || !insertable) {
            annotations.add("@Column(name = \"$leftQuote$columnName$rightQuote\", insertable = $insertable, updatable = $updatable)")
        } else {
            annotations.add("@Column(name = \"$leftQuote$columnName$rightQuote\")")
        }

        // Property declaration with default value
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

    /**
     * 准备关系数据供模板使用
     */
    fun prepareRelationData(
        table: Map<String, Any?>,
        relations: Map<String, Map<String, String>>,
        tablePackageMap: Map<String, String>,
    ): List<Map<String, Any?>> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val result = mutableListOf<Map<String, Any?>>()

        if (!relations.containsKey(tableName)) {
            return result
        }

        for ((refTableName, relationInfo) in relations[tableName]!!) {
            val refInfos = relationInfo.split(";")
            val navTable = tableMap[refTableName] ?: continue

            // 跳过占位符关系
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
            var entityType = ""
            var fullEntityType = ""

            when (relation) {
                "OneToMany" -> {
                    // 专属聚合内关系
                    annotations.add("@${relation}(cascade = [CascadeType.ALL], fetch = FetchType.$fetchType, orphanRemoval = true)")
                    annotations.add("@Fetch(FetchMode.SUBSELECT)")
                    annotations.add("@JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false)")

                    val countIsOne = SqlSchemaUtils.countIsOne(navTable)
                    entityType = resolveEntityType(refTableName)
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

                    entityType = resolveEntityType(refTableName)
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    fullEntityType = "$entityPackage.$entityType"
                    fieldName = toLowerCamelCase(entityType) ?: entityType
                    fieldType = "$fullEntityType?"
                    defaultValue = " = null"
                }

                "ManyToOne" -> {
                    annotations.add("@${relation}(cascade = [], fetch = FetchType.$fetchType)")
                    annotations.add("@JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false)")

                    entityType = resolveEntityType(refTableName)
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    fullEntityType = "$entityPackage.$entityType"
                    fieldName = toLowerCamelCase(entityType) ?: entityType
                    fieldType = "$fullEntityType?"
                    defaultValue = " = null"
                }

                "*OneToMany" -> {
                    // 当前不会用到，无法控制集合数量规模
                    val entityTypeName = resolveEntityType(tableName)
                    val fieldNameFromTable = toLowerCamelCase(entityTypeName) ?: entityTypeName
                    annotations.add(
                        "@${
                            relation.replace(
                                "*",
                                ""
                            )
                        }(mappedBy = \"$fieldNameFromTable\", cascade = [], fetch = FetchType.$fetchType)"
                    )
                    annotations.add("@Fetch(FetchMode.SUBSELECT)")

                    entityType = resolveEntityType(refTableName)
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    fullEntityType = "$entityPackage.$entityType"
                    fieldName = Inflector.pluralize(toLowerCamelCase(entityType) ?: entityType)
                    fieldType = "MutableList<$fullEntityType>"
                    defaultValue = " = mutableListOf()"
                }

                "OneToOne" -> {
                    annotations.add("@${relation}(cascade = [], fetch = FetchType.$fetchType)")
                    annotations.add("@JoinColumn(name = \"$leftQuote$joinColumn$rightQuote\", nullable = false)")

                    entityType = resolveEntityType(refTableName)
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    fullEntityType = "$entityPackage.$entityType"
                    fieldName = toLowerCamelCase(entityType) ?: entityType
                    fieldType = "$fullEntityType?"
                    defaultValue = " = null"
                }

                "*OneToOne" -> {
                    val entityTypeName = resolveEntityType(tableName)
                    val fieldNameFromTable = toLowerCamelCase(entityTypeName) ?: entityTypeName
                    annotations.add(
                        "@${
                            relation.replace(
                                "*",
                                ""
                            )
                        }(mappedBy = \"$fieldNameFromTable\", cascade = [], fetch = FetchType.$fetchType)"
                    )

                    entityType = resolveEntityType(refTableName)
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

                    entityType = resolveEntityType(refTableName)
                    val entityPackage = tablePackageMap[refTableName] ?: ""
                    fullEntityType = "$entityPackage.$entityType"
                    fieldName = Inflector.pluralize(toLowerCamelCase(entityType) ?: entityType)
                    fieldType = "MutableList<$fullEntityType>"
                    defaultValue = " = mutableListOf()"
                }

                "*ManyToMany" -> {
                    val entityTypeName = resolveEntityType(tableName)
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

                    entityType = resolveEntityType(refTableName)
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

    fun buildEntityContext(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        tablePackageMap: Map<String, String>,
        relations: Map<String, Map<String, String>>,
        baseDir: String,
    ): Map<String, Any?> {
        val tag = "entity"
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = resolveEntityType(tableName)
        val entityFullPackage = tablePackageMap[tableName] ?: ""
        val ids = resolveIdColumns(columns)

        val identityType = if (ids.size != 1) "Long" else SqlSchemaUtils.getColumnType(ids[0])

        var baseClass: String? = null
        when {
            SqlSchemaUtils.isAggregateRoot(table) && extension.get().generation.rootEntityBaseClass.get()
                .isNotBlank() -> {
                baseClass = extension.get().generation.rootEntityBaseClass.get()
            }

            extension.get().generation.entityBaseClass.get().isNotBlank() -> {
                baseClass = extension.get().generation.entityBaseClass.get()
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
        val importLines = mutableListOf<String>()
        val annotationLines = mutableListOf<String>()
        val customerLines = mutableListOf<String>()
        val enums = mutableListOf<String>()

        // 处理现有文件的自定义内容
        val filePath = resolveSourceFile(baseDir, entityFullPackage, entityType)
        processEntityCustomerSourceFile(filePath, importLines, annotationLines, customerLines)
        processAnnotationLines(table, columns, annotationLines)

        // 准备列数据
        val columnDataList = columns.map { column ->
            prepareColumnData(table, column, ids, relations, enums)
        }

        // 准备关系数据
        val relationDataList = prepareRelationData(table, relations, tablePackageMap)

        // 准备 imports
        val entityClassExtraImports = getEntityClassExtraImports().toMutableList()
        if (SqlSchemaUtils.isValueObject(table)) {
            val idx = entityClassExtraImports.indexOf("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate")
            if (idx > 0) {
                entityClassExtraImports.add(idx, "com.only4.cap4k.ddd.core.domain.aggregate.ValueObject")
            } else {
                entityClassExtraImports.add("com.only4.cap4k.ddd.core.domain.aggregate.ValueObject")
            }
        }

        // 准备注释行
        val commentLines = SqlSchemaUtils.getComment(table)
            .split(Regex(PATTERN_LINE_BREAK))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                // 移除末尾的分号（如果有）
                if (line.endsWith(";")) line.substring(0, line.length - 1).trim() else line
            }
            .filter { it.isNotEmpty() }

        // 构建上下文
        val context = getEscapeContext().toMutableMap().apply {
            val fullPackage = resolveEntityPackage(tableName) // 例如: domain.aggregates.category
            val aggregatesPackage = resolveAggregatesPackage() // 例如: domain.aggregates

            // 计算 package 相对于 aggregatesPackage 的路径
            val relativePackage = if (fullPackage.startsWith(aggregatesPackage)) {
                val relative = fullPackage.substring(aggregatesPackage.length)
                if (relative.startsWith(".")) relative else ".$relative"
            } else {
                ".$fullPackage"
            }

            putContext(tag, "templatePackage", ".$aggregatesPackage") // 带点号
            putContext(tag, "package", relativePackage) // 相对路径
            putContext(tag, "path", resolveEntityPackage(tableName).replace(".", File.separator))
            putContext(tag, "Entity", entityType)
            putContext(tag, "entityType", entityType)
            putContext(tag, "extendsClause", extendsClause)
            putContext(tag, "implementsClause", implementsClause)
            putContext(tag, "columns", columnDataList)
            putContext(tag, "relations", relationDataList)
            putContext(tag, "annotationLines", annotationLines)
            putContext(tag, "customerLines", customerLines)
            putContext(tag, "imports", entityClassExtraImports)
            putContext(tag, "commentLines", commentLines)
        }

        return context
    }
}
