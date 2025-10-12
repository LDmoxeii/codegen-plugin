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

### Two-Phase Execution Model

The code generation process follows a two-phase pattern:

1. **Phase 1: Context Building** - Collects all metadata from database and configuration
   - Context builders run in order (controlled by `order` property)
   - Each builder fills specific maps in `EntityContext` (tables, columns, relations, enums, etc.)
   - All dependencies are resolved before generation starts

2. **Phase 2: File Generation** - Generates files using the built context
   - Generators run in order (EnumGenerator before EntityGenerator)
   - Each generator implements `shouldGenerate()`, `buildContext()`, and `getDefaultTemplateNode()`
   - Templates are rendered using Pebble template engine

### Core Interfaces

**Context Layer** (`com.only.codegen.context`):
- `BaseContext` - Base configuration and template aliasing system
- `EntityContext` - Read-only interface for all shared context data
- `MutableEntityContext` - Mutable version for context building phase

**Builder Layer** (`com.only.codegen.context.builders`):
- `ContextBuilder` - Interface with `order` and `build()` method
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
- `TemplateGenerator` - Interface with `tag`, `order`, and generation methods
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

**GenEntityTask** - Main task for entity generation
- Location: `com.only.codegen.GenEntityTask`
- Implements both `MutableEntityContext` (for building) and provides the execution flow
- Entry point: `genEntity()` method calls `buildGenerationContext()` then `generateFiles()`

**GenArchTask** - Generates project architecture structure
- Reads architecture template and creates directory structure
- Base class for other generation tasks

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

### Adding a New Generator

1. Create class implementing `TemplateGenerator` in `com.only.codegen.generators`
2. Set `tag` (e.g., "repository") and `order` (higher = later execution)
3. Implement `shouldGenerate()` - return true if this table needs this generator
4. Implement `buildContext()` - prepare template context map using `context.putContext(tag, key, value)`
5. Implement `getDefaultTemplateNode()` - define default template path and conflict resolution
6. Implement `onGenerated()` - cache generated type full name in `typeMapping` for reference by other generators
7. Register in `GenEntityTask.generateFiles()` by adding to generators list

### Adding a New Context Builder

1. Create class implementing `ContextBuilder` in `com.only.codegen.context.builders`
2. Set `order` based on dependencies (lower = earlier execution)
3. Implement `build()` to populate `MutableEntityContext` maps
4. Register in `GenEntityTask.buildGenerationContext()` by adding to contextBuilders list

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
