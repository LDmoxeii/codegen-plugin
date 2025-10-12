# 6. 执行流程

[← 上一章：配置和模板](05-configuration-templates.md) | [返回目录](README.md) | [下一章：总结和展望 →](07-summary.md)

---

## 6.1 典型使用场景

```bash
# 场景 1：先生成 Domain 层，再生成 Adapter/Application 层
./gradlew genEntity        # 生成 Entity, Enum, Schema 等
./gradlew genAnnotation    # 基于生成的实体，生成 Repository, Service 等

# 场景 2：一键生成所有代码
./gradlew genAll           # 依赖 genEntity 和 genAnnotation
```

## 6.2 Task 依赖配置

```kotlin
// CodegenPlugin.kt
class CodegenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // ... 现有配置 ...

        // 注册新任务
        val genAnnotationTask = project.tasks.register(
            "genAnnotation",
            GenAnnotationTask::class.java
        ) {
            group = "codegen"
            description = "Generate code based on KSP annotations"

            // 依赖 genEntity，确保实体已生成
            dependsOn("genEntity")
        }

        // 创建一键生成任务
        project.tasks.register("genAll") {
            group = "codegen"
            description = "Generate all code (entities + annotation-based)"

            dependsOn("genEntity", "genAnnotation")
        }

        // 检查是否启用 KSP
        project.afterEvaluate {
            val extension = project.extensions.getByType(CodegenExtension::class.java)
            if (extension.annotation.enabled.get()) {
                // 确保 KSP 插件已应用
                project.plugins.apply("com.google.devtools.ksp")

                logger.lifecycle("KSP annotation processing enabled")
            }
        }
    }
}
```

## 6.3 测试 KSP Processor

### 6.3.1 测试示例

```kotlin
package com.only.codegen.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.junit.jupiter.api.Test
import java.io.File
import com.google.common.truth.Truth.assertThat

class AnnotationProcessorTest {

    @Test
    fun `should process Aggregate annotation`() {
        val kotlinSource = SourceFile.kotlin(
            "User.kt", """
            package com.example.domain

            import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
            import jakarta.persistence.Entity
            import jakarta.persistence.Id

            @Aggregate(aggregate = "User", root = true)
            @Entity
            class User(
                @Id
                var id: Long = 0L,
                var name: String? = null
            )
        """
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(kotlinSource)
            symbolProcessorProviders = listOf(AnnotationProcessorProvider())
            inheritClassPath = true
        }

        val result = compilation.compile()

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

        // 验证生成的 JSON 文件
        val metadataDir = File(compilation.kspSourcesDir, "metadata")
        val aggregatesFile = File(metadataDir, "aggregates.json")

        assertThat(aggregatesFile.exists()).isTrue()

        val content = aggregatesFile.readText()
        assertThat(content).contains("\"aggregateName\": \"User\"")
        assertThat(content).contains("\"isAggregateRoot\": true")
    }
}
```

## 6.4 KSP 元数据示例

### 6.4.1 aggregates.json

```json
[
  {
    "aggregateName": "User",
    "className": "User",
    "qualifiedName": "com.example.domain.aggregates.user.User",
    "packageName": "com.example.domain.aggregates.user",
    "isAggregateRoot": true,
    "isEntity": true,
    "isValueObject": false,
    "identityType": "Long",
    "fields": [
      {
        "name": "id",
        "type": "kotlin.Long",
        "isId": true,
        "isNullable": false,
        "annotations": ["Id", "GeneratedValue", "Column"]
      },
      {
        "name": "name",
        "type": "kotlin.String",
        "isId": false,
        "isNullable": true,
        "annotations": ["Column"]
      }
    ]
  }
]
```

---

[← 上一章：配置和模板](05-configuration-templates.md) | [返回目录](README.md) | [下一章：总结和展望 →](07-summary.md)
