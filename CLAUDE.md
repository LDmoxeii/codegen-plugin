# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Gradle plugin for code generation from database schemas. It generates Kotlin domain entities, enums, and other DDD (Domain-Driven Design) artifacts using Pebble templates. The plugin supports both MySQL and PostgreSQL databases.

**Plugin ID**: `com.only4.codegen`
**Code Package**: `com.only4.codegen`
**Maven GroupId**: `com.only4`

## Build & Test Commands

### Build the plugin
```bash
./gradlew build
```

### Run tests
```bash
./gradlew test
```

### Publish to local Maven repository (for testing in other projects)
```bash
./gradlew publishToMavenLocal
```

### Clean build artifacts
```bash
./gradlew clean
```

## Key Architecture

### Extensible Plugin Framework

The plugin follows a **highly extensible architecture** that allows creating new code generation pipelines by composing:
- **Different data sources** (database metadata, KSP annotations, design files, etc.)
- **Custom context types** (EntityContext, AnnotationContext, or user-defined)
- **Specialized builders and generators** (extending generic base interfaces)

This design enables scenarios like:
- Database-driven domain generation (current: `GenEntityTask`)
- Annotation-driven infrastructure generation (planned: `GenAnnotationTask`)
- Design file-driven application layer generation (example: `GenDesignTask`)
- **KSP metadata + template + custom design context** (future extensibility)

### Two-Phase Execution Model

All generation tasks follow a consistent two-phase pattern:

1. **Phase 1: Context Building** - Collects metadata from data sources into typed contexts
   - Context builders implement `ContextBuilder<T>` interface with generic type parameter
   - Builders run in order (controlled by `order` property)
   - Each builder fills specific maps in the context (e.g., `tableMap`, `classMap`, `aggregateMap`)
   - All dependencies are resolved before generation starts

2. **Phase 2: File Generation** - Generates files using the built context
   - Generators implement generator interfaces (e.g., `TemplateGenerator`, `AnnotationTemplateGenerator`)
   - Generators run in order based on `order` property
   - Each generator implements `shouldGenerate()`, `buildContext()`, and `getDefaultTemplateNode()`
   - Templates are rendered using Pebble template engine
   - Generated types are cached in `typeMapping` for cross-referencing

### Core Interfaces

The plugin uses **generic, composable interfaces** that enable different code generation pipelines:

**Context Layer** (`com.only4.codegen.context`):
- `BaseContext` - Base configuration and template aliasing system (shared by all contexts)
- `AggregateContext : BaseContext` - Database-driven generation context (tables, columns, relations, enums)
- `DesignContext : BaseContext` - Design file-driven generation context (KSP metadata, design elements)
- `MutableAggregateContext` - Mutable version of AggregateContext for context building phase
- `MutableDesignContext` - Mutable version of DesignContext for context building phase

**Builder Layer** (`com.only4.codegen.context`):
- `ContextBuilder<T>` - **Generic base interface** with type parameter `T`
  - Defines `order: Int` property for execution sequencing
  - Defines `build(context: T)` method for filling context data
  - **Enables type-safe builder composition for different context types**

**Aggregate Context Builders** (`com.only4.codegen.context.aggregate.builders`) - for database-driven generation:
- Builders execute in order:
  - `TableContextBuilder` (order=10) - Collects table and column metadata from database
  - `EntityTypeContextBuilder` (order=20) - Determines entity class names from table names
  - `AnnotationContextBuilder` (order=20) - Processes table/column annotations and metadata
  - `ModuleContextBuilder` (order=20) - Resolves multi-module structure if enabled
  - `RelationContextBuilder` (order=20) - Analyzes foreign key relationships between tables
  - `EnumContextBuilder` (order=20) - Extracts enum definitions from table comments
  - `AggregateContextBuilder` (order=30) - Identifies aggregates and aggregate roots
  - `TablePackageContextBuilder` (order=40) - Determines package structure for each table
- Each builder populates specific maps in `AggregateContext`

**Design Context Builders** (`com.only4.codegen.context.design.builders`) - for design file-driven generation:
- Builders execute in order:
  - `DesignContextBuilder` (order=10) - Loads design elements from JSON files
  - `KspMetadataContextBuilder` (order=15) - Loads aggregate metadata from KSP processor output
  - `TypeMappingBuilder` (order=18) - Builds type mapping from KSP metadata
  - `UnifiedDesignBuilder` (order=20) - Unified parsing for all design types (commands, queries, events)

**Generator Layer**:
- `AggregateTemplateGenerator` (`com.only4.codegen.generators.aggregate`) - Interface for aggregate/database-driven generators
  - Properties: `tag: String`, `order: Int`
  - Methods: `shouldGenerate()`, `buildContext()`, `getDefaultTemplateNodes()`, `onGenerated()`, `generatorName()`
- `DesignTemplateGenerator` (`com.only4.codegen.generators.design`) - Interface for design-driven generators
  - Similar structure but operates on `BaseDesign` and `DesignContext`

**Aggregate Generators** (`com.only4.codegen.generators.aggregate`) - for database-driven generation:
- `SchemaBaseGenerator` (order=10) - Generates SchemaBase base class for metadata tracking
- `EnumGenerator` (order=10) - Generates enum classes, tracks generated enums to avoid duplicates
- `EntityGenerator` (order=20) - Generates entity classes with full DDD support, handles custom code preservation
- `SpecificationGenerator` (order=30) - Generates Specification base classes for domain specifications
- `FactoryGenerator` (order=30) - Generates Factory classes for aggregate root creation
- `DomainEventGenerator` (order=30) - Generates domain event classes for event-driven architecture
- `DomainEventHandlerGenerator` (order=30) - Generates domain event handler/subscriber classes
- `RepositoryGenerator` (order=30) - Generates Repository interfaces and adapters
- `AggregateGenerator` (order=40) - Generates aggregate wrapper classes for aggregate root management
- `SchemaGenerator` (order=50) - Generates Schema classes (similar to JPA Metamodel) for type-safe queries

**Design Generators** (`com.only4.codegen.generators.design`) - for design file-driven generation:
- `CommandGenerator` (order=10) - Generates command classes
- `QueryGenerator` (order=10) - Generates query classes
- `DomainEventGenerator` (order=10) - Generates domain event classes
- `DomainEventHandlerGenerator` (order=20) - Generates domain event handler classes
- `QueryHandlerGenerator` (order=20) - Generates query handler classes

### Tasks

**Task Hierarchy** - All tasks extend from a common base with template rendering capabilities:

```
AbstractCodegenTask                  # Base: Pebble rendering + template aliasing
    └── GenArchTask                  # Architecture scaffolding
        ├── GenAggregateTask         # Database → Domain layer (Entity-driven)
        └── GenDesignTask            # KSP + Design files → Application/Domain layer
```

**GenArchTask** - Architecture scaffolding base task
- Location: `com.only4.codegen.GenArchTask`
- Reads architecture templates and creates directory structure
- Base class providing common scaffolding functionality
- Can be extended for custom generation scenarios
- Implements `renderTemplate()` for template node management

**GenAggregateTask** - Database-driven domain generation
- Location: `com.only4.codegen.GenAggregateTask`
- Data source: Database metadata via JDBC
- Context: Implements `MutableAggregateContext`
- Workflow: `genEntity()` → `buildGenerationContext()` → `generateFiles()`
- Output: Domain entities, enums, schemas, specifications, factories, domain events, repositories, aggregates

**GenDesignTask** - Design file-driven generation
- Location: `com.only4.codegen.GenDesignTask`
- Data sources:
  - KSP metadata from `build/generated/ksp/main/resources/metadata`
  - Design JSON files from project configuration
- Context: Implements `MutableDesignContext`
- Workflow: `genDesign()` → `buildDesignContext()` → `generateDesignFiles()`
- Output: Commands, queries, domain events, event handlers, query handlers
- Supported design element types:
  - **Application Layer**: Command, Query, Integration Events
  - **Domain Layer**: Domain Events, Domain Event Handlers
- Design tag alias system for element type normalization (e.g., "cmd", "command", "commands" → "command")

### Template Alias System

The plugin uses a sophisticated template aliasing system (`BaseContext.putContext()` extension) that automatically maps variables to multiple naming conventions:
- Example: `putContext("entity", "Entity", "User")` maps to "Entity", "entity", "ENTITY", "entityType", etc.
- Defined in `AbstractCodegenTask.templateAliasMap` (300+ lines of mappings)
- Allows templates to use any naming convention

### Database Support

**SqlSchemaUtils** - Core utility for database metadata extraction
- Location: `com.only4.codegen.misc.SqlSchemaUtils`
- `SqlSchemaUtils4Mysql` - MySQL-specific implementation
- `SqlSchemaUtils4Postgresql` - PostgreSQL-specific implementation
- Helper methods: `isIgnore()`, `hasRelation()`, `hasEnum()`, `getTableName()`, `getType()`, etc.

## Configuration

The plugin is configured via the `codegen` extension in `build.gradle.kts`:

```kotlin
codegen {
    basePackage.set("com.example")
    multiModule.set(true)

    database {
        url.set("jdbc:mysql://localhost:3306/mydb")
        username.set("user")
        password.set("pass")
        schema.set("mydb")
        tables.set("table1,table2")  // Optional filter
    }

    generation {
        versionField.set("version")
        deletedField.set("deleted")
        entityBaseClass.set("BaseEntity")
        idGenerator.set("com.example.IdGenerator")
        // ... many more options
    }
}
```

## Important Patterns

### Type Mapping System

All generators implement an `onGenerated()` method that caches generated class full names in `context.typeMapping`:
- Purpose: Allows later generators to reference previously generated classes
- Pattern: `typeMapping[typeName] = fullQualifiedClassName`
- Examples:
  - `EntityGenerator`: `typeMapping["User"] = "com.example.domain.aggregates.user.User"`
  - `SchemaGenerator`: `typeMapping["SUser"] = "com.example.domain.aggregates.user.SUser"`
  - `FactoryGenerator`: `typeMapping["UserFactory"] = "com.example.domain.aggregates.user.factory.UserFactory"`
  - `SpecificationGenerator`: `typeMapping["UserSpecification"] = "com.example.domain.aggregates.user.specs.UserSpecification"`
  - `AggregateGenerator`: `typeMapping["UserAggregate"] = "com.example.domain.aggregates.user.UserAggregate"`

### Custom Code Preservation in EntityGenerator

The `EntityGenerator.processEntityCustomerSourceFile()` method preserves user-written code:
- Collects import statements before annotations
- Collects class-level annotations (stops at `class` keyword using `inAnnotationBlock` flag)
- Preserves custom code outside the "【字段映射开始】" and "【字段映射结束】" markers
- **Critical**: Uses state machine with `inAnnotationBlock` to avoid collecting field-level annotations
- Regenerated fields are placed between the markers, custom methods preserved outside

## Framework Extensibility Guide

### Creating a New Generation Pipeline

To create a new code generation pipeline (e.g., KSP metadata + templates + custom design):

**Step 1: Define Custom Context**
```kotlin
// 1. Define read-only context interface
interface MyCustomContext : BaseContext {
    val myDataMap: Map<String, MyData>
    // ... other context data
}

// 2. Define mutable version for building
interface MutableMyCustomContext : MyCustomContext {
    override val myDataMap: MutableMap<String, MyData>
}
```

**Step 2: Create Context Builders**
```kotlin
// Implement ContextBuilder<T> with your context type
class MyDataBuilder : ContextBuilder<MutableMyCustomContext> {
    override val order: Int = 10

    override fun build(context: MutableMyCustomContext) {
        // Parse your data source (KSP, files, etc.)
        // Fill context.myDataMap
    }
}
```

**Step 3: Create Generators**
```kotlin
// Implement generator interface
class MyCustomGenerator(private val context: MyCustomContext) : TemplateGenerator {
    override val tag = "mycustom"
    override val order = 20

    override fun shouldGenerate(table: Map<String, Any?>): Boolean {
        // Decision logic
    }

    override fun buildContext(table: Map<String, Any?>): MutableMap<String, Any?> {
        // Build template context using context.putContext()
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        // Define template path and conflict handling
    }

    override fun onGenerated(table: Map<String, Any?>) {
        // Cache generated types in typeMapping
    }
}
```

**Step 4: Create Task**
```kotlin
open class MyCustomTask : AbstractCodegenTask(), MutableMyCustomContext {
    // Implement context properties
    override val myDataMap: MutableMap<String, MyData> = mutableMapOf()

    @TaskAction
    fun generate() {
        // Phase 1: Build context
        val builders = listOf(MyDataBuilder(), /* ... */)
        builders.sortedBy { it.order }.forEach { it.build(this) }

        // Phase 2: Generate files
        val generators = listOf(MyCustomGenerator(this), /* ... */)
        generators.sortedBy { it.order }.forEach { /* generate */ }
    }
}
```

### Adding a Generator to Existing Pipeline

**For Aggregate Generation (AggregateContext)**:

1. Create class implementing `AggregateTemplateGenerator` in `com.only4.codegen.generators.aggregate`
2. Set `tag` (e.g., "repository") and `order` (higher = later execution)
3. Implement `shouldGenerate(table, context)` - return true if this table needs this generator
4. Implement `buildContext(table, context)` - prepare template context map using `context.putContext(tag, key, value)`
5. Implement `getDefaultTemplateNodes()` - define default template paths and conflict resolution
6. Implement `onGenerated(table, context)` - cache generated type full name in `typeMapping` for reference by other generators
7. Implement `generatorName(table, context)` - return the name of the generated artifact
8. Register in `GenAggregateTask.generateFiles()` by adding to generators list

**For Design Generation (DesignContext)**:

1. Create class implementing `DesignTemplateGenerator` in `com.only4.codegen.generators.design`
2. Similar structure but operates on `BaseDesign` and `DesignContext`
3. Register in `GenDesignTask.generateDesignFiles()` by adding to generators list

### Adding a Context Builder to Existing Pipeline

**For Aggregate Context**:

1. Create class implementing `ContextBuilder<MutableAggregateContext>` in `com.only4.codegen.context.aggregate.builders`
2. Set `order` based on dependencies (lower = earlier execution)
3. Implement `build(context: MutableAggregateContext)` to populate context maps
4. Register in `GenAggregateTask.buildGenerationContext()` by adding to contextBuilders list

**For Design Context**:

1. Create class implementing `ContextBuilder<MutableDesignContext>` in `com.only4.codegen.context.design.builders`
2. Similar structure but works with design metadata
3. Register in `GenDesignTask.buildDesignContext()` by adding to builders list

### Import Management

The `ImportManager` system (in `com.only4.codegen.manager`) handles automatic import resolution:
- `BaseImportManager` - Base class for import management
- `EntityImportManager` - Manages entity-specific imports with collision detection
- `SchemaImportManager`, `RepositoryImportManager`, `FactoryImportManager`, etc. - Specialized import managers for different generators
- Automatically handles Java/Kotlin type mappings and wildcard imports
- Use when building complex contexts that need precise import control

## Key Files Reference

- `CodegenPlugin.kt` - Plugin registration and task setup (registers genArch, genAggregate, genDesign tasks)
- `CodegenExtension.kt` - Configuration DSL (database, generation options)
- `GenAggregateTask.kt` - Main aggregate generation orchestrator, implements MutableAggregateContext
- `GenDesignTask.kt` - Design file-driven generation orchestrator, implements MutableDesignContext
- `GenArchTask.kt` - Base architecture scaffolding task
- `AbstractCodegenTask.kt` - Base task with rendering and template alias logic
- `PebbleTemplateRenderer.kt` - Pebble template rendering wrapper
- Context interfaces (`context/`):
  - `BaseContext.kt` - Base configuration with template aliasing via `putContext()` extension
  - `aggregate/AggregateContext.kt` - Read-only interface with all aggregate generation context data
  - `aggregate/MutableAggregateContext.kt` - Mutable version used during context building
  - `design/DesignContext.kt` - Read-only interface with all design generation context data
  - `design/MutableDesignContext.kt` - Mutable version used during context building
- Context builders:
  - `context/aggregate/builders/` - 8 builders that populate AggregateContext in phases
  - `context/design/builders/` - 4 builders that populate DesignContext in phases
- Generators:
  - `generators/aggregate/` - 10 generators that produce domain layer files from database
  - `generators/design/` - 5 generators that produce application/domain layer files from design
- Import management (`manager/`): Multiple specialized ImportManager classes
- SQL utilities (`misc/`): `SqlSchemaUtils`, MySQL/PostgreSQL implementations
- Other utilities (`misc/`): `NamingUtils`, `Inflector`, `TextUtils`, `ResourceUtils`, `SourceFileUtils`

## Module Structure

```
codegen-plugin/
├── plugin/                           # Main plugin module
│   └── src/main/kotlin/com/only4/codegen/
│       ├── CodegenPlugin.kt         # Plugin entry point
│       ├── CodegenExtension.kt      # Configuration DSL
│       ├── GenAggregateTask.kt      # Main aggregate generation task
│       ├── GenDesignTask.kt         # Design file-driven generation task
│       ├── GenArchTask.kt           # Architecture generation base task
│       ├── AbstractCodegenTask.kt   # Base task with template rendering
│       ├── context/                 # Context interfaces and builders
│       │   ├── BaseContext.kt       # Base configuration interface
│       │   ├── ContextBuilder.kt    # Generic context builder interface
│       │   ├── aggregate/           # Aggregate context (database-driven)
│       │   │   ├── AggregateContext.kt     # Read-only generation context
│       │   │   ├── MutableAggregateContext.kt  # Mutable context for building
│       │   │   └── builders/        # Aggregate context builders (8 builders)
│       │   │       ├── TableContextBuilder.kt
│       │   │       ├── EntityTypeContextBuilder.kt
│       │   │       ├── AnnotationContextBuilder.kt
│       │   │       ├── ModuleContextBuilder.kt
│       │   │       ├── RelationContextBuilder.kt
│       │   │       ├── EnumContextBuilder.kt
│       │   │       ├── AggregateContextBuilder.kt
│       │   │       └── TablePackageContextBuilder.kt
│       │   └── design/              # Design context (KSP + design file driven)
│       │       ├── DesignContext.kt         # Read-only design context
│       │       ├── MutableDesignContext.kt  # Mutable design context
│       │       ├── builders/        # Design context builders (4 builders)
│       │       │   ├── DesignContextBuilder.kt
│       │       │   ├── KspMetadataContextBuilder.kt
│       │       │   ├── TypeMappingBuilder.kt
│       │       │   └── UnifiedDesignBuilder.kt
│       │       └── models/          # Design element models
│       │           ├── DesignElement.kt
│       │           ├── BaseDesign.kt
│       │           ├── CommonDesign.kt
│       │           ├── DomainEventDesign.kt
│       │           ├── IntegrationEventDesign.kt
│       │           └── AggregateInfo.kt
│       ├── generators/              # File generators
│       │   ├── aggregate/           # Aggregate generators (10 generators)
│       │   │   ├── AggregateTemplateGenerator.kt  # Generator interface
│       │   │   ├── SchemaBaseGenerator.kt
│       │   │   ├── EnumGenerator.kt
│       │   │   ├── EntityGenerator.kt   # Core entity generator
│       │   │   ├── SchemaGenerator.kt
│       │   │   ├── SpecificationGenerator.kt
│       │   │   ├── FactoryGenerator.kt
│       │   │   ├── DomainEventGenerator.kt
│       │   │   ├── DomainEventHandlerGenerator.kt
│       │   │   ├── RepositoryGenerator.kt
│       │   │   └── AggregateGenerator.kt
│       │   └── design/              # Design generators (5 generators)
│       │       ├── DesignTemplateGenerator.kt  # Generator interface
│       │       ├── CommandGenerator.kt
│       │       ├── QueryGenerator.kt
│       │       ├── DomainEventGenerator.kt
│       │       ├── DomainEventHandlerGenerator.kt
│       │       └── QueryHandlerGenerator.kt
│       ├── manager/                 # Import management (15+ managers)
│       │   ├── BaseImportManager.kt
│       │   ├── ImportManager.kt
│       │   ├── EntityImportManager.kt
│       │   ├── SchemaImportManager.kt
│       │   ├── RepositoryImportManager.kt
│       │   ├── FactoryImportManager.kt
│       │   ├── CommandImportManager.kt
│       │   ├── QueryImportManager.kt
│       │   └── ... (and more)
│       ├── misc/                    # Utilities
│       │   ├── SqlSchemaUtils.kt    # Abstract SQL utilities
│       │   ├── SqlSchemaUtils4Mysql.kt
│       │   ├── SqlSchemaUtils4Postgresql.kt
│       │   ├── NamingUtils.kt       # Naming conventions
│       │   ├── Inflector.kt         # Pluralization
│       │   ├── TextUtils.kt
│       │   ├── ResourceUtils.kt
│       │   └── SourceFileUtils.kt
│       ├── pebble/                  # Template rendering
│       │   ├── PebbleTemplateRenderer.kt
│       │   ├── PebbleConfig.kt
│       │   └── PebbleInitializer.kt
│       └── template/                # Template model
│           ├── Template.kt
│           ├── TemplateNode.kt
│           └── PathNode.kt
├── ksp-processor/                   # KSP metadata processor module
│   └── src/main/kotlin/...
└── settings.gradle.kts
```

## Development Notes

### Architecture Patterns

- **Generic Context System**: All contexts extend `BaseContext` and use type parameter `T` in `ContextBuilder<T>`
- **Two-Phase Pattern**: Every generation task follows Context Building → File Generation
- **Order-based Execution**: Both builders and generators use `order: Int` for sequencing
- **Type Safety**: Generic interfaces (`ContextBuilder<T>`) ensure compile-time type safety
- **Separation of Concerns**:
  - Context Layer = Data structures (read-only + mutable)
  - Builder Layer = Data collection/parsing
  - Generator Layer = File generation
  - Task Layer = Workflow orchestration

### Code Generation Pipelines

**Current Implementations**:
1. **Database → Domain** (`GenAggregateTask` + `AggregateContext` + `ContextBuilder<MutableAggregateContext>` + `AggregateTemplateGenerator`)
   - Generates domain layer code from database metadata
   - Output: Entities, Enums, Schemas, Specifications, Factories, Domain Events, Repositories, Aggregates

2. **KSP + Design Files → Application/Domain** (`GenDesignTask` + `DesignContext` + `ContextBuilder<MutableDesignContext>` + `DesignTemplateGenerator`)
   - Generates application/domain layer code from KSP metadata and design files
   - Output: Commands, Queries, Domain Events, Event Handlers, Query Handlers

**Architecture Capabilities**:
3. **Custom Generation Pipelines** - The generic framework supports creating new pipelines by:
   - Defining custom Context interfaces (extending `BaseContext`)
   - Implementing `ContextBuilder<T>` for your context type
   - Creating generators implementing the appropriate generator interface
   - Registering builders and generators in your custom task

### Technical Details

- The codebase uses extensive Kotlin properties with lazy initialization
- Most configuration values come from `CodegenExtension` and are cached in `BaseContext.baseMap`
- The plugin integrates with Hibernate/JPA annotations for entity generation
- Template conflict resolution supports: "skip", "warn", "overwrite"
- Files with `[cap4k-ddd-codegen-gradle-plugin:do-not-overwrite]` marker are never overwritten
- **Generator order matters**: Lower order numbers execute first (EnumGenerator before EntityGenerator)
- **Context builder order matters**: Builders run in sequence, later builders can use data from earlier ones
- **Type mapping is crucial**: All generated types are cached in `typeMapping` for cross-referencing
- **Custom code preservation**: EntityGenerator uses markers "【字段映射开始】" and "【字段映射结束】" to separate generated fields from custom code
- **State machine for annotations**: Uses `inAnnotationBlock` flag to distinguish class-level annotations from field annotations
- **KSP Integration**: GenDesignTask reads KSP-generated metadata from `build/generated/ksp/main/resources/metadata`
- **Design Tag Aliasing**: Both GenAggregateTask and GenDesignTask use tag alias maps to normalize element type names

## Published Artifacts

The plugin publishes to Aliyun Maven repository with the following artifacts:

1. **`com.only4:plugin:0.1.0-SNAPSHOT`** - Main plugin implementation
2. **`com.only4:ksp-processor:0.1.0-SNAPSHOT`** - KSP metadata processor
3. **`com.only4:com.only4.codegen.gradle.plugin:0.1.0-SNAPSHOT`** - Gradle plugin marker artifact

Users can apply the plugin using:
```kotlin
plugins {
    id("com.only4.codegen") version "0.1.0-SNAPSHOT"
}
```

## Available Gradle Tasks

- **`genArch`** - Generate project architecture structure
- **`genAggregate`** - Generate domain layer code (entities, enums, schemas, etc.) from database
- **`genDesign`** - Generate application/domain layer code (commands, queries, events) from design files and KSP metadata
