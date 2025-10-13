# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Gradle plugin for code generation from database schemas. It generates Kotlin domain entities, enums, and other DDD (Domain-Driven Design) artifacts using Pebble templates. The plugin supports both MySQL and PostgreSQL databases.

**Plugin ID**: `com.only.codegen`

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

**Context Layer** (`com.only.codegen.context`):
- `BaseContext` - Base configuration and template aliasing system (shared by all contexts)
- `EntityContext : BaseContext` - Database-driven generation context (tables, columns, relations, enums)
- `AnnotationContext : BaseContext` - Annotation-driven generation context (classes, annotations, aggregates)
- `MutableEntityContext` - Mutable version of EntityContext for context building phase
- `MutableAnnotationContext` - Mutable version of AnnotationContext for context building phase

**Builder Layer** (`com.only.codegen.context.builders`):
- `ContextBuilder<T>` - **Generic base interface** with type parameter `T`
  - Defines `order: Int` property for execution sequencing
  - Defines `build(context: T)` method for filling context data
  - **Enables type-safe builder composition for different context types**
- `EntityContextBuilder : ContextBuilder<MutableEntityContext>` - Specialized for entity generation
- `AggregateContextBuilder : ContextBuilder<MutableAnnotationContext>` - Specialized for annotation-based generation

**Entity Context Builders** (for database-driven generation):
- Builders execute in order:
  - `TableContextBuilder` (order=10) - Collects table and column metadata from database
  - `EntityTypeContextBuilder` (order=20) - Determines entity class names from table names
  - `AnnotationContextBuilder` (order=20) - Processes table/column annotations and metadata
  - `ModuleContextBuilder` (order=20) - Resolves multi-module structure if enabled
  - `RelationContextBuilder` (order=20) - Analyzes foreign key relationships between tables
  - `EnumContextBuilder` (order=20) - Extracts enum definitions from table comments
  - `AggregateContextBuilder` (order=30) - Identifies aggregates and aggregate roots
  - `TablePackageContextBuilder` (order=40) - Determines package structure for each table
- Each builder populates specific maps in `EntityContext`

**Generator Layer** (`com.only.codegen.generators`):
- `TemplateGenerator` - Interface for entity/database-driven generators
  - Properties: `tag: String`, `order: Int`
  - Methods: `shouldGenerate()`, `buildContext()`, `getDefaultTemplateNode()`, `onGenerated()`
- `AnnotationTemplateGenerator` - Interface for annotation-driven generators (planned)
  - Similar structure but operates on `AggregateInfo` and `AnnotationContext`

**Entity Generators** (for database-driven generation, order 10-40):
- `SchemaBaseGenerator` (order=10) - Generates SchemaBase base class for metadata tracking
- `EnumGenerator` (order=10) - Generates enum classes, tracks generated enums to avoid duplicates
- `EntityGenerator` (order=20) - Generates entity classes with full DDD support, handles custom code preservation
- `SchemaGenerator` (order=30) - Generates Schema classes (similar to JPA Metamodel) for type-safe queries
- `SpecificationGenerator` (order=30) - Generates Specification base classes for domain specifications
- `FactoryGenerator` (order=30) - Generates Factory classes for aggregate root creation
- `DomainEventGenerator` (order=30) - Generates domain event classes for event-driven architecture
- `DomainEventHandlerGenerator` (order=40) - Generates domain event handler/subscriber classes
- `AggregateGenerator` (order=40) - Generates aggregate wrapper classes for aggregate root management

### Tasks

**Task Hierarchy** - All tasks extend from a common base with template rendering capabilities:

```
AbstractCodegenTask                  # Base: Pebble rendering + template aliasing
    ├── GenArchTask                  # Architecture scaffolding
    │   ├── GenDesignTask            # Design file-driven generation (Case study)
    │   └── [Custom tasks...]        # User-extensible
    ├── GenEntityTask                # Database → Domain layer
    └── GenAnnotationTask (planned)  # Annotations → Infrastructure layer
```

**GenEntityTask** - Database-driven domain generation
- Location: `com.only.codegen.GenEntityTask`
- Data source: Database metadata via JDBC
- Context: Implements `MutableEntityContext`
- Workflow: `genEntity()` → `buildGenerationContext()` → `generateFiles()`
- Output: Domain entities, enums, schemas, specifications, factories, domain events

**GenArchTask** - Architecture scaffolding base task
- Reads architecture templates and creates directory structure
- Base class providing common scaffolding functionality
- Can be extended for custom generation scenarios

**GenDesignTask** - Design file-driven generation (Case study in `Case/GenDesignTask.kt`)
- Location: `Case/GenDesignTask.kt` (example implementation demonstrating framework extensibility)
- Data source: Declarative design files (text-based DSL)
- Generates DDD design elements from design declarations
- Demonstrates **alternative data source integration pattern**
- Supported design element types:
  - **Application Layer**: Command, Saga, Query, Client (Anti-Corruption Layer), Integration Events
  - **Domain Layer**: Domain Events, Specifications, Factories, Domain Services
- Design format: `element_type:ElementName:param1:param2:...`
- Features:
  - Alias system for element type normalization (e.g., "cmd", "command", "commands" → "command")
  - Regex pattern matching for filtering designs
  - Specialized rendering methods per element type
- Key methods:
  - `resolveLiteralDesign(design: String)` - Parses design file into structured map
  - `alias4Design(name: String)` - Normalizes element type names
  - `renderAppLayerCommand()`, `renderDomainLayerDomainEvent()`, etc. - Element-specific rendering
- **Architecture lesson**: Shows how to create custom generation tasks by:
  1. Extending `GenArchTask`
  2. Implementing custom parsing logic for alternative data sources
  3. Reusing template rendering infrastructure
  4. Defining element-specific context building

### Template Alias System

The plugin uses a sophisticated template aliasing system (`BaseContext.putContext()` extension) that automatically maps variables to multiple naming conventions:
- Example: `putContext("entity", "Entity", "User")` maps to "Entity", "entity", "ENTITY", "entityType", etc.
- Defined in `AbstractCodegenTask.templateAliasMap` (300+ lines of mappings)
- Allows templates to use any naming convention

### Database Support

**SqlSchemaUtils** - Core utility for database metadata extraction
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

**For Entity Generation (EntityContext)**:

1. Create class implementing `TemplateGenerator` in `com.only.codegen.generators`
2. Set `tag` (e.g., "repository") and `order` (higher = later execution)
3. Implement `shouldGenerate()` - return true if this table needs this generator
4. Implement `buildContext()` - prepare template context map using `context.putContext(tag, key, value)`
5. Implement `getDefaultTemplateNode()` - define default template path and conflict resolution
6. Implement `onGenerated()` - cache generated type full name in `typeMapping` for reference by other generators
7. Register in `GenEntityTask.generateFiles()` by adding to generators list

**For Annotation Generation (AnnotationContext)**:

1. Create class implementing `AnnotationTemplateGenerator`
2. Similar structure but operates on `AggregateInfo` and `AnnotationContext`
3. Register in `GenAnnotationTask` (when implemented)

### Adding a Context Builder to Existing Pipeline

**For Entity Context**:

1. Create class implementing `EntityContextBuilder : ContextBuilder<MutableEntityContext>`
2. Set `order` based on dependencies (lower = earlier execution)
3. Implement `build(context: MutableEntityContext)` to populate context maps
4. Register in `GenEntityTask.buildGenerationContext()` by adding to contextBuilders list

**For Annotation Context**:

1. Create class implementing `AggregateContextBuilder : ContextBuilder<MutableAnnotationContext>`
2. Similar structure but works with annotation metadata
3. Register in `GenAnnotationTask` (when implemented)

### Import Management

The `ImportManager` system (in `com.only.codegen.generators.manager`) handles automatic import resolution:
- `EntityImportManager` - Manages entity-specific imports with collision detection
- Automatically handles Java/Kotlin type mappings and wildcard imports
- Use when building complex contexts that need precise import control

## Key Files Reference

- `CodegenPlugin.kt` - Plugin registration and task setup
- `CodegenExtension.kt` - Configuration DSL (database, generation options)
- `GenEntityTask.kt` - Main entity generation orchestrator, implements MutableEntityContext
- `AbstractCodegenTask.kt` - Base task with rendering and template alias logic
- `PebbleTemplateRenderer.kt` - Pebble template rendering wrapper
- Context interfaces (`context/`):
  - `BaseContext.kt` - Base configuration with template aliasing via `putContext()` extension
  - `EntityContext.kt` - Read-only interface with all generation context data
  - `MutableEntityContext.kt` - Mutable version used during context building
- Context builders (`context/builders/`): 8 builders that populate EntityContext in phases
- Generators (`generators/`): 10 generators that produce files in order
- Import management (`generators/manager/`): `ImportManager` and `EntityImportManager`
- SQL utilities (`misc/`): `SqlSchemaUtils`, MySQL/PostgreSQL implementations
- Documentation: `重构进度报告.md`, `EntityGenerator实现计划.md` (in Chinese)

## Module Structure

```
codegen-plugin/
├── plugin/                           # Main plugin module
│   └── src/main/kotlin/com/only/codegen/
│       ├── CodegenPlugin.kt         # Plugin entry point
│       ├── CodegenExtension.kt      # Configuration DSL
│       ├── GenEntityTask.kt         # Main entity generation task
│       ├── GenArchTask.kt           # Architecture generation base task
│       ├── AbstractCodegenTask.kt   # Base task with template rendering
│       ├── context/                 # Context interfaces and builders
│       │   ├── BaseContext.kt       # Base configuration interface
│       │   ├── EntityContext.kt     # Read-only generation context
│       │   ├── MutableEntityContext.kt  # Mutable context for building
│       │   └── builders/            # Context builders (8 builders)
│       │       ├── ContextBuilder.kt
│       │       ├── TableContextBuilder.kt
│       │       ├── EntityTypeContextBuilder.kt
│       │       ├── AnnotationContextBuilder.kt
│       │       ├── ModuleContextBuilder.kt
│       │       ├── RelationContextBuilder.kt
│       │       ├── EnumContextBuilder.kt
│       │       ├── AggregateContextBuilder.kt
│       │       └── TablePackageContextBuilder.kt
│       ├── generators/              # File generators (10 generators)
│       │   ├── TemplateGenerator.kt # Generator interface
│       │   ├── SchemaBaseGenerator.kt
│       │   ├── EnumGenerator.kt
│       │   ├── EntityGenerator.kt   # Core entity generator (~720 lines)
│       │   ├── SchemaGenerator.kt
│       │   ├── SpecificationGenerator.kt
│       │   ├── FactoryGenerator.kt
│       │   ├── DomainEventGenerator.kt
│       │   ├── DomainEventHandlerGenerator.kt
│       │   ├── AggregateGenerator.kt
│       │   └── manager/             # Import management
│       │       ├── ImportManager.kt
│       │       └── EntityImportManager.kt
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
│       │   ├── PebbleInitializer.kt
│       │   └── CompositeLoader.kt
│       └── template/                # Template model
│           ├── Template.kt
│           ├── TemplateNode.kt
│           └── PathNode.kt
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
1. **Database → Domain** (`GenEntityTask` + `EntityContext` + `EntityContextBuilder` + `TemplateGenerator`)
2. **Design Files → Application/Domain** (`GenDesignTask` - Case study)

**Planned**:
3. **Annotations → Infrastructure** (`GenAnnotationTask` + `AnnotationContext` + `AggregateContextBuilder` + `AnnotationTemplateGenerator`)

**Future Extensibility**:
4. **KSP + Templates + Custom Design** (user-defined context + builders + generators)
5. Any combination of data sources via generic framework

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

## Future Direction: Annotation-Based Code Generation

**⚠️ IMPORTANT: This is the planned future implementation direction**

A new annotation-based code generation subsystem is planned to complement the existing database-driven generation. See `ANNOTATION_BASED_CODEGEN_DESIGN.md` for complete technical design.

### Key Concepts

**GenAnnotationTask** - New task for annotation-based generation (parallel to GenEntityTask)
- Scans generated domain code (Entity classes) for annotations
- Uses **AnnotationContext** (independent from EntityContext)
- Generates infrastructure layer code (Repository, Service, Controller, Mapper)
- Follows the same Context + ContextBuilder + Generator architecture pattern

### Architecture Comparison

| Aspect | GenEntityTask | GenAnnotationTask (Planned) |
|--------|---------------|---------------------------|
| **Data Source** | Database metadata | Source code annotations |
| **Context** | EntityContext | AnnotationContext |
| **Key Maps** | tableMap, columnsMap, relationsMap | classMap, annotationMap, aggregateMap |
| **Scan Target** | Database tables | .kt files |
| **Dependencies** | JDBC driver | Regex parsing (zero new dependencies) |
| **Timing** | Before compilation (read database) | After domain generation (read generated code) |
| **Output** | Domain layer | Adapter/Application layer |

### Planned Components

**Context Layer**:
- `AnnotationContext` - Read-only interface with classMap, annotationMap, aggregateMap
- `MutableAnnotationContext` - Mutable version for building phase
- Inherits `BaseContext` for configuration and template aliasing

**Builder Layer**:
- `AnnotationContextBuilder` (order=10) - Scans .kt files and parses annotations using regex
- `AggregateInfoBuilder` (order=20) - Identifies aggregates and aggregate roots
- `IdentityTypeBuilder` (order=30) - Resolves ID types from @Id annotations

**Generator Layer**:
- `RepositoryGenerator` (order=10) - Generates JPA Repository interfaces
- `ServiceGenerator` (order=20) - Generates Application Services
- `ControllerGenerator` (order=30) - Generates REST Controllers
- `MapperGenerator` (order=40) - Generates DTO Mappers

### Usage Example

```bash
# Step 1: Generate domain layer from database
./gradlew genEntity

# Step 2: Generate infrastructure layer from annotations
./gradlew genAnnotation

# Or: One-command generation
./gradlew genAll
```

### Design Principles

1. **Architecture Consistency**: Maintains Context + ContextBuilder + Generator pattern
2. **Independence**: Completely decoupled from EntityContext
3. **Zero New Dependencies**: Uses regex parsing (validated in GenRepositoryTask case study)
4. **Flexibility**: Can run independently or composed with GenEntityTask
5. **Extensibility**: Easy to add new generators for different infrastructure components

For complete implementation details, migration path, and code examples, refer to `ANNOTATION_BASED_CODEGEN_DESIGN.md`.
