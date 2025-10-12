# 5. 配置和模板

[← 上一章：KSP Processor 实现](04-ksp-processor.md) | [返回目录](README.md) | [下一章：执行流程 →](06-execution-flow.md)

---

## 5.1 CodegenExtension 扩展

```kotlin
// CodegenExtension.kt
abstract class CodegenExtension {
    // ... 现有配置 ...

    /**
     * 注解生成配置
     */
    abstract val annotation: AnnotationGenerationConfig
}

abstract class AnnotationGenerationConfig {
    /**
     * 是否启用注解生成
     */
    abstract val enabled: Property<Boolean>

    /**
     * 扫描的源代码根目录
     */
    abstract val sourceRoots: ListProperty<String>

    /**
     * 扫描的包路径
     */
    abstract val scanPackages: ListProperty<String>

    /**
     * 是否生成 Repository
     */
    abstract val generateRepository: Property<Boolean>

    /**
     * Repository 命名模板
     */
    abstract val repositoryNameTemplate: Property<String>

    /**
     * 是否生成 Service
     */
    abstract val generateService: Property<Boolean>

    /**
     * Service 命名模板
     */
    abstract val serviceNameTemplate: Property<String>

    /**
     * 是否生成 Controller
     */
    abstract val generateController: Property<Boolean>

    /**
     * 是否生成 Mapper
     */
    abstract val generateMapper: Property<Boolean>
}
```

## 5.2 使用示例

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "1.9.20-1.0.14"
    id("com.only.codegen") version "1.0.0"
}

dependencies {
    // 添加 KSP Processor
    ksp(project(":codegen-plugin:ksp-processor"))
    // 或从 Maven 发布的版本
    // ksp("com.only:codegen-ksp-processor:1.0.0")
}

codegen {
    basePackage.set("com.example")
    multiModule.set(true)

    // 数据库生成配置（现有）
    database {
        url.set("jdbc:mysql://localhost:3306/mydb")
        // ...
    }

    // 注解生成配置（新增）
    annotation {
        enabled.set(true)

        sourceRoots.set(listOf(
            "${projectDir}/domain/src/main/kotlin"
        ))

        scanPackages.set(listOf(
            "com.example.domain"
        ))

        generateRepository.set(true)
        repositoryNameTemplate.set("{{ Entity }}Repository")

        generateService.set(true)
        serviceNameTemplate.set("{{ Entity }}Service")

        generateController.set(false)
        generateMapper.set(false)
    }
}
```

## 5.3 Repository 模板

```kotlin
// plugin/src/main/resources/templates/repository.peb
package {{ package }}

import {{ EntityPackage }}.{{ Entity }}
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * {{ Entity }} Repository
 *
 * 聚合：{{ Aggregate }}
 */
@Repository
interface {{ Repository }} : JpaRepository<{{ Entity }}, {{ IdentityType }}> {

    /**
     * 根据 ID 查找 {{ Entity }}
     */
    fun findById(id: {{ IdentityType }}): {{ Entity }}?

    /**
     * 检查 {{ Entity }} 是否存在
     */
    fun existsById(id: {{ IdentityType }}): Boolean

    /**
     * 删除 {{ Entity }}
     */
    fun deleteById(id: {{ IdentityType }})
}
```

## 5.4 Service 模板

```kotlin
// plugin/src/main/resources/templates/service.peb
package {{ package }}

import {{ EntityPackage }}.{{ Entity }}
import {{ RepositoryImport }}
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * {{ Entity }} Application Service
 *
 * 聚合：{{ Aggregate }}
 */
@Service
@Transactional
class {{ Service }}(
    private val {{ repository }}: {{ Repository }}
) {

    /**
     * 创建 {{ Entity }}
     */
    fun create(entity: {{ Entity }}): {{ Entity }} {
        return {{ repository }}.save(entity)
    }

    /**
     * 根据 ID 查找 {{ Entity }}
     */
    fun findById(id: {{ IdentityType }}): {{ Entity }}? {
        return {{ repository }}.findById(id)
    }

    /**
     * 更新 {{ Entity }}
     */
    fun update(entity: {{ Entity }}): {{ Entity }} {
        return {{ repository }}.save(entity)
    }

    /**
     * 删除 {{ Entity }}
     */
    fun delete(id: {{ IdentityType }}) {
        {{ repository }}.deleteById(id)
    }

    /**
     * 查询所有 {{ Entity }}
     */
    fun findAll(): List<{{ Entity }}> {
        return {{ repository }}.findAll()
    }
}
```

---

[← 上一章：KSP Processor 实现](04-ksp-processor.md) | [返回目录](README.md) | [下一章：执行流程 →](06-execution-flow.md)
