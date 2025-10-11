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
- Builders execute in order: TableContextBuilder (10) → RelationContextBuilder (40) → EnumContextBuilder (50)
- Each builder populates specific maps in `EntityContext`

**Generator Layer** (`com.only.codegen.generators`):
- `TemplateGenerator` - Interface with `tag`, `order`, and generation methods
- `EnumGenerator` (order=10) - Generates enum classes, tracks generated enums to avoid duplicates
- `EntityGenerator` (order=20) - Generates entity classes with full DDD support (~700 lines, self-contained)

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

### Adding a New Generator

1. Create class implementing `TemplateGenerator` in `com.only.codegen.generators`
2. Set `tag` (e.g., "repository") and `order` (higher = later execution)
3. Implement `shouldGenerate()` - return true if this table needs this generator
4. Implement `buildContext()` - prepare template context map using `context.putContext(tag, key, value)`
5. Implement `getDefaultTemplateNode()` - define default template path and conflict resolution
6. Register in `GenEntityTask.generateFiles()` by adding to generators list

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
- `GenEntityTask.kt` - Main entity generation orchestrator
- `AbstractCodegenTask.kt` - Base task with rendering and template alias logic
- `PebbleTemplateRenderer.kt` - Pebble template rendering wrapper
- `重构进度报告.md` - Detailed refactoring progress report (in Chinese)
- `EntityGenerator实现计划.md` - EntityGenerator implementation plan (in Chinese)

## Module Structure

```
codegen-plugin/
├── plugin/                           # Main plugin module
│   └── src/main/kotlin/com/only/codegen/
│       ├── CodegenPlugin.kt         # Plugin entry point
│       ├── CodegenExtension.kt      # Configuration
│       ├── GenEntityTask.kt         # Main task
│       ├── AbstractCodegenTask.kt   # Base task
│       ├── context/                 # Context interfaces
│       │   ├── BaseContext.kt
│       │   ├── EntityContext.kt
│       │   ├── MutableEntityContext.kt
│       │   └── builders/           # Context builders
│       ├── generators/              # File generators
│       │   ├── TemplateGenerator.kt
│       │   ├── EnumGenerator.kt
│       │   ├── EntityGenerator.kt
│       │   └── manager/            # Import management
│       ├── misc/                    # Utilities
│       │   ├── SqlSchemaUtils.kt
│       │   ├── NamingUtils.kt
│       │   └── Inflector.kt
│       ├── pebble/                  # Template rendering
│       └── template/                # Template model
└── settings.gradle.kts
```

## Development Notes

- The codebase uses extensive Kotlin properties with lazy initialization
- Most configuration values come from `CodegenExtension` and are cached in `BaseContext.baseMap`
- The plugin integrates with Hibernate/JPA annotations for entity generation
- Template conflict resolution supports: "skip", "warn", "overwrite"
- Files with `[cap4k-ddd-codegen-gradle-plugin:do-not-overwrite]` marker are never overwritten
