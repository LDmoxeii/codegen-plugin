# Segment Fragment Generator Design

## Overview

This document describes the design for **Segment Fragment Generator** - a template fragment composition system that enables modular template construction through reusable segments.

## Problem Statement

Current template structure has duplicated logic for rendering collections (columns, relations, methods). These fragments are:
- Embedded directly in file templates
- Difficult to reuse across multiple templates
- Hard to test independently
- Complex to maintain when logic changes
- Cannot preserve user customizations (annotations, comments, method bodies)

## Design Goals

1. **Fragment Reusability** - Segments can be shared across multiple file templates
2. **Context Isolation** - Each segment builds its own context independently
3. **Lazy Rendering** - Segments don't generate files, only cache context
4. **Ordered Execution** - Segments execute before their dependent file generators
5. **Pebble Integration** - Segments are consumed via Pebble macros in file templates
6. **User Code Preservation** - Segments can extract and preserve user customizations

## Architecture

### Segment Execution Flow

```
Phase 1: Segment Context Building (order < 20)
  Generator.shouldGenerate() → Generator.buildContext()
  ↓
  (if type == "segment") → Cache context via onGenerated()
  ↓
  Context cached by key: "parentTag:tableName:segmentVar"

Phase 2: File Context Building (order >= 20)
  Generator.buildContext()
  ↓
  Merge cached segment contexts (filter by "parentTag:tableName:")
  ↓
  Context passed to template renderer

Phase 3: Template Rendering (only for type == "file")
  Skip rendering if type == "segment"
  ↓
  File template imports segment macros
  ↓
  Loops over segment data and renders via macros
```

### Node Type Hierarchy

```
PathNode (type property)
├── root     → Project root
├── dir      → Directory (sets templatePackage)
├── segment  → Fragment (builds context, caches via onGenerated(), no file output)
└── file     → File (merges segment contexts, renders output)
```

### Tag Convention for Segments

Segments use composite tag format: `parentTag:segmentVar`

```
Generator 1 (Segment):
  tag = "entity:field"
  type = "segment"
  order = 15
  ↓ onGenerated() caches context
  key = "entity:user_table:field"
  value = { "columns": [...] }

Generator 2 (Segment):
  tag = "entity:relation"
  type = "segment"
  order = 16
  ↓ onGenerated() caches context
  key = "entity:user_table:relation"
  value = { "relations": [...] }

Generator 3 (File):
  tag = "entity"
  type = "file"
  order = 20
  ↓ buildContext() merges:
  - entity:user_table:field → columns
  - entity:user_table:relation → relations
  ↓ render() generates User.kt
```

## Core Design Changes

### 1. Unified Generator Interface (No Changes Needed)

File and Segment generators use the **same interface** `AggregateTemplateGenerator`:

```kotlin
interface AggregateTemplateGenerator {
    val tag: String    // For segments: "parentTag:segmentVar", for files: "parentTag"
    val order: Int     // Segments: < 20, Files: >= 20

    fun shouldGenerate(table: Map<String, Any?>, context: AggregateContext): Boolean
    fun buildContext(table: Map<String, Any?>, context: AggregateContext): Map<String, Any?>
    fun getDefaultTemplateNodes(): List<TemplateNode>
    fun onGenerated(table: Map<String, Any?>, context: AggregateContext)
    fun generatorName(table: Map<String, Any?>, context: AggregateContext): String
}
```

**Key differences**:
- **Segment**: `tag = "entity:field"`, returns `TemplateNode(type="segment")`
- **File**: `tag = "entity"`, returns `TemplateNode(type="file")`

### 2. Extended PathNode

```kotlin
open class PathNode {
    var type: String? = null  // "root" | "dir" | "file" | "segment"
    var tag: String? = null   // For segments: "parentTag:segmentVar"
    var name: String? = null

    /**
     * Cached context data
     * - Used by all node types (not limited to segments)
     * - Segments: cache fragment context
     * - Files: cache for debugging/logging
     */
    var cachedContext: Map<String, Any?>? = null

    // ... existing properties
}
```

### 3. Segment Context Cache (in AbstractCodegenTask)

```kotlin
abstract class AbstractCodegenTask : GenArchTask() {

    /**
     * Global segment context cache
     * Key: "parentTag:tableName:segmentVar"
     * Value: segment context map
     */
    private val segmentContextCache = mutableMapOf<String, Map<String, Any?>>()

    protected fun getSegmentContext(key: String): Map<String, Any?>? {
        return segmentContextCache[key]
    }

    protected fun putSegmentContext(key: String, context: Map<String, Any?>) {
        segmentContextCache[key] = context
    }

    protected fun clearSegmentCache() {
        segmentContextCache.clear()
    }

    protected fun mergeSegmentContexts(
        parentTag: String,
        tableName: String,
        baseContext: MutableMap<String, Any?>
    ) {
        segmentContextCache.entries
            .filter { it.key.startsWith("$parentTag:$tableName:") }
            .forEach { (cacheKey, segmentContext) ->
                val segmentVar = cacheKey.substringAfterLast(":")
                // Merge each segment context item
                segmentContext.forEach { (key, value) ->
                    baseContext.putContext(parentTag, key, value)
                }
            }
    }
}
```

## Implementation Pattern

### Example 1: Field Segment for Entity

**FieldSegmentGenerator.kt:**
```kotlin
class FieldSegmentGenerator : AggregateTemplateGenerator {
    override val tag = "entity:field"  // Composite tag: parentTag:segmentVar
    override val order = 15  // Before EntityGenerator (order=20)

    override fun shouldGenerate(table: Map<String, Any?>, context: AggregateContext): Boolean {
        val tableName = SqlSchemaUtils.getTableName(table)
        return context.columnsMap.containsKey(tableName)
    }

    override fun buildContext(table: Map<String, Any?>, context: AggregateContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val columns = context.columnsMap[tableName]!!
        val importManager = EntityImportManager()

        val columnDataList = columns.map { column ->
            prepareColumnData(table, column, context, importManager)
        }

        // Return context that will be cached
        return mapOf(
            "columns" to columnDataList,
            "imports" to importManager.toImportLines()
        )
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "segment"  // Key: marks as segment
                tag = this@FieldSegmentGenerator.tag
                format = "resource"
                data = "templates/segments/field.kt.peb"
            }
        )
    }

    override fun onGenerated(table: Map<String, Any?>, context: AggregateContext) {
        // Cache segment context here (parallel with file generator's onGenerated)
        val tableName = SqlSchemaUtils.getTableName(table)
        val parentTag = tag.substringBefore(":")
        val segmentVar = tag.substringAfter(":")
        val cacheKey = "$parentTag:$tableName:$segmentVar"

        // Get context from cachedContext or rebuild
        val templateNode = getDefaultTemplateNodes().first()
        val segmentContext = templateNode.cachedContext ?: buildContext(table, context)

        context.putSegmentContext(cacheKey, segmentContext)
    }

    override fun generatorName(table: Map<String, Any?>, context: AggregateContext): String {
        return "field-segment"
    }
}
```

**templates/segments/field.kt.peb:**
```twig
{% macro render(column) %}
{%- if column.needGenerate %}

{{ column.comment }}
{%- for annotation in column.annotations %}
    {{ annotation }}
{%- endfor %}
    var {{ column.fieldName }}: {{ column.fieldType }}{{ column.defaultValue }},
{%- endif %}
{% endmacro %}
```

### Example 2: EntityGenerator Integration

**EntityGenerator.kt (refactored):**
```kotlin
class EntityGenerator : AggregateTemplateGenerator {
    override val tag = "entity"  // Parent tag
    override val order = 20

    override fun buildContext(table: Map<String, Any?>, context: AggregateContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = context.entityTypeMap[tableName]!!
        val aggregate = context.resolveAggregateWithModule(tableName)

        val resultContext = context.baseMap.toMutableMap()

        // 1. Merge all segment contexts first
        context.mergeSegmentContexts(tag, tableName, resultContext)

        // 2. Add file-specific context
        resultContext.putContext(tag, "Entity", entityType)
        resultContext.putContext(tag, "package", refPackage(aggregate))
        resultContext.putContext(tag, "extendsClause", resolveExtendsClause(table, context))
        resultContext.putContext(tag, "implementsClause", resolveImplementsClause(table))

        // 3. Process existing file for custom code
        val customerLines = extractCustomCode(table, context)
        resultContext.putContext(tag, "customerLines", customerLines)

        return resultContext
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"  // Key: marks as file
                tag = this@EntityGenerator.tag
                name = "{{ Entity }}.kt"
                format = "resource"
                data = "templates/entity.kt.peb"
                conflict = "overwrite"
            }
        )
    }

    override fun onGenerated(table: Map<String, Any?>, context: AggregateContext) {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = context.entityTypeMap[tableName]!!

        // Cache generated type for other generators
        context.typeMapping[entityType] = generatorFullName(table, context)
    }
}
```

**templates/entity.kt.peb (refactored):**
```twig
{% import "templates/segments/field.kt.peb" as field %}
{% import "templates/segments/relation.kt.peb" as relation %}

package {{ basePackage }}{{ templatePackage }}{{ package }}

{%- for import in imports %}
{{ import }}
{%- endfor %}

{%- for line in annotationLines %}
{{ line }}
{%- endfor %}
class {{ entityType }}{{ extendsClause }}{{ implementsClause }} (
    // 【字段映射开始】
{%- for column in columns %}
{{ field.render(column) }}
{%- endfor %}
) {
{%- if relations is not empty %}
{%- for rel in relations %}
{{ relation.render(rel) }}
{%- endfor %}
{%- endif %}

    // 【字段映射结束】
{%- if customerLines is not empty %}
{%- for line in customerLines %}
{{ line }}
{%- endfor %}
{%- endif %}
}
```

## Task Execution Changes

### GenAggregateTask.generateFiles() Flow

```kotlin
fun generateFiles() {
    val generators = listOf(
        // Segment generators (order < 20)
        FieldSegmentGenerator(),           // tag = "entity:field", order = 15
        RelationSegmentGenerator(),        // tag = "entity:relation", order = 16

        // File generators (order >= 20)
        EntityGenerator(),                 // tag = "entity", order = 20
        SchemaGenerator()                  // tag = "schema", order = 50
    ).sortedBy { it.order }

    for (table in tableMap.values) {
        val tableName = SqlSchemaUtils.getTableName(table)

        for (generator in generators) {
            if (!generator.shouldGenerate(table, this)) continue

            // 1. Build context (same for all generators)
            val tableContext = generator.buildContext(table, this)

            // 2. Get template nodes
            val templateNodes = generator.getDefaultTemplateNodes()

            // 3. Render templates
            templateNodes.forEach { templateNode ->
                when (templateNode.type) {
                    "segment" -> {
                        // Skip rendering, just cache context
                        templateNode.cachedContext = tableContext
                        logger.quiet("Cached segment: ${templateNode.tag}")
                    }

                    "file" -> {
                        // Merge segment contexts before file generation
                        val parentTag = generator.tag
                        val mergedContext = tableContext.toMutableMap()
                        mergeSegmentContexts(parentTag, tableName, mergedContext)

                        // Render file
                        forceRender(templateNode.resolve(mergedContext), mergedContext)
                    }

                    else -> {
                        // dir, root, etc.
                        forceRender(templateNode.resolve(tableContext), tableContext)
                    }
                }
            }

            // 4. Post-generation callback
            generator.onGenerated(table, this)
        }

        // Clear segment cache after each table
        clearSegmentCache()
    }
}
```

### AbstractCodegenTask.render() - No Changes

```kotlin
protected fun render(
    templateNode: TemplateNode,
    tableContext: Map<String, Any?>,
    options: Map<String, Any?> = emptyMap()
) {
    // Existing implementation unchanged
    // Segments are skipped in generateFiles(), not here
    when (templateNode.type) {
        "dir" -> createDirectory(...)
        "file" -> writeFile(...)
        "segment" -> {
            // This branch never executes because segments are skipped in generateFiles()
        }
    }
}
```

## Advanced Example: Controller with Method Preservation

### Scenario

Generate a REST Controller where:
- Class structure is regenerated each time
- Method signatures are regenerated
- **User annotations, comments, and method bodies are preserved**

### ControllerMethodSegmentGenerator.kt

```kotlin
class ControllerMethodSegmentGenerator : AggregateTemplateGenerator {
    override val tag = "controller:method"
    override val order = 15

    override fun shouldGenerate(table: Map<String, Any?>, context: AggregateContext): Boolean {
        return SqlSchemaUtils.isAggregateRoot(table)
    }

    override fun buildContext(table: Map<String, Any?>, context: AggregateContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = context.entityTypeMap[tableName]!!

        // CRUD methods
        val methods = listOf(
            buildMethodContext(table, context, "findById", "GET", "/{id}"),
            buildMethodContext(table, context, "findAll", "GET", ""),
            buildMethodContext(table, context, "create", "POST", ""),
            buildMethodContext(table, context, "update", "PUT", "/{id}"),
            buildMethodContext(table, context, "delete", "DELETE", "/{id}")
        )

        return mapOf("methods" to methods)
    }

    private fun buildMethodContext(
        table: Map<String, Any?>,
        context: AggregateContext,
        methodName: String,
        httpMethod: String,
        pathPattern: String
    ): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = context.entityTypeMap[tableName]!!
        val controllerPackage = resolveControllerPackage(context, tableName)
        val controllerType = "${entityType}Controller"

        // Extract user customizations from existing source file
        val sourceFile = resolveControllerFile(context, controllerPackage, controllerType)
        val (userAnnotations, userComment, userBody) = extractUserCode(sourceFile, methodName)

        return mapOf(
            "methodName" to methodName,
            "httpMethod" to httpMethod,
            "pathPattern" to pathPattern,
            "entityType" to entityType,

            // User customizations (preserved across regenerations)
            "userAnnotations" to userAnnotations,
            "userComment" to userComment,
            "userBody" to userBody,

            // Generated defaults
            "defaultMapping" to "@${httpMethod}Mapping(\"$pathPattern\")",
            "returnType" to resolveReturnType(methodName, entityType)
        )
    }

    private fun extractUserCode(
        sourceFile: File,
        methodName: String
    ): Triple<List<String>, String?, String?> {
        if (!sourceFile.exists()) {
            return Triple(emptyList(), null, null)
        }

        val content = sourceFile.readText()
        val userAnnotations = mutableListOf<String>()
        var userComment: String? = null
        var userBody: String? = null

        // Regex to match: [KDoc] [Annotations] fun methodName(...) { body }
        val methodPattern = """
            (?:(/\*\*[\s\S]*?\*/)[\s\n]*)?                    # Optional KDoc
            ((?:@\w+(?:\([^)]*\))?[\s\n]*)+)?                 # Optional annotations
            fun\s+$methodName\s*\([^)]*\)\s*:\s*\S+\s*\{      # Method signature
            ([\s\S]*?)                                         # Method body
            \n\s*\}                                            # End brace
        """.trimIndent().toRegex(RegexOption.COMMENTS)

        val match = methodPattern.find(content)
        if (match != null) {
            // Extract KDoc comment
            userComment = match.groupValues[1].takeIf { it.isNotBlank() }

            // Extract user annotations (exclude generated HTTP mapping)
            val annotationBlock = match.groupValues[2]
            if (annotationBlock.isNotBlank()) {
                userAnnotations.addAll(
                    annotationBlock.split("\n")
                        .map { it.trim() }
                        .filter {
                            it.startsWith("@") &&
                            !it.matches(Regex("@(GET|POST|PUT|DELETE|PATCH)Mapping.*"))
                        }
                )
            }

            // Extract method body (exclude TODO placeholder)
            val bodyContent = match.groupValues[3].trim()
            if (bodyContent.isNotBlank() && !bodyContent.startsWith("TODO")) {
                userBody = bodyContent
            }
        }

        return Triple(userAnnotations, userComment, userBody)
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "segment"
                tag = this@ControllerMethodSegmentGenerator.tag
                format = "resource"
                data = "templates/segments/controller-method.kt.peb"
            }
        )
    }

    override fun onGenerated(table: Map<String, Any?>, context: AggregateContext) {
        val tableName = SqlSchemaUtils.getTableName(table)
        val parentTag = "controller"
        val segmentVar = "method"
        val cacheKey = "$parentTag:$tableName:$segmentVar"

        val templateNode = getDefaultTemplateNodes().first()
        val segmentContext = templateNode.cachedContext ?: buildContext(table, context)

        context.putSegmentContext(cacheKey, segmentContext)
    }

    override fun generatorName(table: Map<String, Any?>, context: AggregateContext): String {
        return "controller-method-segment"
    }
}
```

### templates/segments/controller-method.kt.peb

```twig
{% macro render(method) %}

    {# User KDoc comment or default #}
    {% if method.userComment is not empty %}
{{ method.userComment }}
    {% else %}
    /**
     * {{ method.methodName | capitalize }} {{ method.entityType }}
     */
    {% endif %}

    {# User custom annotations (preserved) #}
    {%- for annotation in method.userAnnotations %}
    {{ annotation }}
    {%- endfor %}

    {# Generated HTTP mapping (always regenerated) #}
    {{ method.defaultMapping }}
    fun {{ method.methodName }}(): {{ method.returnType }} {
        {# User method body or TODO #}
        {% if method.userBody is not empty %}
{{ method.userBody | indent(8) }}
        {% else %}
        TODO("Implement {{ method.methodName }}")
        {% endif %}
    }
{% endmacro %}
```

### ControllerFileGenerator.kt

```kotlin
class ControllerFileGenerator : AggregateTemplateGenerator {
    override val tag = "controller"
    override val order = 20

    override fun buildContext(table: Map<String, Any?>, context: AggregateContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = context.entityTypeMap[tableName]!!

        val resultContext = context.baseMap.toMutableMap()

        // File-level context (class definition)
        resultContext.putContext(tag, "Controller", "${entityType}Controller")
        resultContext.putContext(tag, "Entity", entityType)
        resultContext.putContext(tag, "path", entityType.lowercase())

        // Note: methods context will be merged by mergeSegmentContexts()

        return resultContext
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@ControllerFileGenerator.tag
                name = "{{ Controller }}.kt"
                format = "resource"
                data = "templates/controller.kt.peb"
                conflict = "overwrite"
            }
        )
    }

    override fun onGenerated(table: Map<String, Any?>, context: AggregateContext) {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = context.entityTypeMap[tableName]!!
        val controllerType = "${entityType}Controller"

        context.typeMapping[controllerType] = generatorFullName(table, context)
    }
}
```

### templates/controller.kt.peb

```twig
{% import "templates/segments/controller-method.kt.peb" as method %}

package {{ basePackage }}{{ templatePackage }}{{ package }}

import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity

/**
 * REST Controller for {{ Entity }}
 *
 * @author codegen
 * @date {{ date }}
 */
@RestController
@RequestMapping("/api/{{ path }}")
class {{ Controller }} {

{%- for m in methods %}
{{ method.render(m) }}
{%- endfor %}
}
```

### Generated Output Example

**First generation (no existing file):**
```kotlin
@RestController
@RequestMapping("/api/user")
class UserController {

    /**
     * FindById User
     */
    @GETMapping("/{id}")
    fun findById(): ResponseEntity<User> {
        TODO("Implement findById")
    }

    // ... other methods
}
```

**User edits the file:**
```kotlin
@RestController
@RequestMapping("/api/user")
class UserController {

    /**
     * 根据ID查询用户
     * 支持缓存
     */
    @Cacheable("users")  // <-- User added
    @GETMapping("/{id}")
    fun findById(): ResponseEntity<User> {
        // <-- User implemented
        return ResponseEntity.ok(userService.findById(id))
    }
}
```

**Regeneration (preserves user code):**
```kotlin
@RestController
@RequestMapping("/api/user")
class UserController {

    /**
     * 根据ID查询用户          <-- User comment preserved
     * 支持缓存
     */
    @Cacheable("users")          <-- User annotation preserved
    @GETMapping("/{id}")         <-- Generated mapping updated
    fun findById(): ResponseEntity<User> {
        return ResponseEntity.ok(userService.findById(id))  <-- User body preserved
    }
}
```
## Benefits

1. **Unified Interface** - File and Segment generators use the same interface, differentiated only by tag format and template type
2. **Separation of Concerns** - Segments handle fragment logic, files handle composition
3. **Testability** - Each segment can be tested independently
4. **Reusability** - Same segment (e.g., "field") can be used in Entity, DTO, VO templates
5. **Maintainability** - Fragment logic changes in one place
6. **Performance** - Segments build context once, cached for all dependent files
7. **User Code Preservation** - Segments can extract and preserve user customizations across regenerations
8. **Simplicity** - No new interfaces, minimal changes to existing code

## Summary of Changes

### New Additions
1. `PathNode.cachedContext` - Stores context for all node types
2. `AbstractCodegenTask.segmentContextCache` - Global cache for segment contexts
3. `AbstractCodegenTask.mergeSegmentContexts()` - Merges cached segment contexts into file context
4. Segment generators use composite tag: `"parentTag:segmentVar"`

### Modified Components
1. `GenAggregateTask.generateFiles()` - Skip rendering for segments, cache context, merge before file generation
2. File generators call `mergeSegmentContexts()` in `buildContext()` or before rendering

### Unchanged Components
1. `AggregateTemplateGenerator` interface - No changes
2. `AbstractCodegenTask.render()` - No changes (segments skipped in generateFiles)
3. `PathNode` - Only adds `cachedContext` property (universal for all types)
4. Existing file generators - Still work without segments

## Migration Path

### Phase 1: Add Infrastructure (1 day)
- Add `cachedContext` to `PathNode`
- Add `segmentContextCache` and helper methods to `AbstractCodegenTask`
- Update `GenAggregateTask.generateFiles()` to handle segments

### Phase 2: Extract Entity Segments (1-2 days)
- Create `FieldSegmentGenerator` (extract field logic from EntityGenerator)
- Create `RelationSegmentGenerator` (extract relation logic from EntityGenerator)
- Create segment templates: `field.kt.peb`, `relation.kt.peb`
- Refactor `EntityGenerator.buildContext()` to call `mergeSegmentContexts()`
- Update `entity.kt.peb` to import and use macros

### Phase 3: Test and Validate (1 day)
- Test with existing database schemas
- Verify generated entities match previous output
- Test custom code preservation in EntityGenerator

### Phase 4: Create Controller Generators (2-3 days)
- Create `ControllerMethodSegmentGenerator` with user code extraction
- Create `ControllerFileGenerator`
- Create templates: `controller-method.kt.peb`, `controller.kt.peb`
- Test method preservation across regenerations

### Phase 5: Expand to Other Generators (ongoing)
- Apply segment pattern to Schema, Factory, Repository, etc.
- Create reusable segments for common patterns

## Design Constraints

1. **Tag Convention**: Segments use `"parentTag:segmentVar"` format
2. **Execution Order**: Segment generators MUST have `order < 20`
3. **No File Output**: Segments never call `forceRender()` (skipped in generateFiles)
4. **Cache Scope**: Segment contexts are table-scoped (cleared after each table)
5. **Context Merge**: File generators call `mergeSegmentContexts()` to get segment data
6. **Macro Import**: Segment macros are explicitly imported in file templates

## Open Questions

**Q1: Should segmentVar be extracted from tag or stored separately?**
- **Decision**: Extracted from tag via `substringAfter(":")` - simpler, no new fields

**Q2: Should mergeSegmentContexts() be called automatically or explicitly?**
- **Decision**: Explicit call in file generator's `buildContext()` - more control

**Q3: How to handle segments without parent files?**
- **Decision**: Not supported - segments must have corresponding file generator

**Q4: Should cachedContext be nullable or provide default empty map?**
- **Decision**: Nullable - explicit about whether context was cached

