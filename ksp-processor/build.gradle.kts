import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.20"
    `maven-publish`
}

group = "com.only4"
version = "0.2.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // KSP API - 使用稳定可用的 API 版本（与 Kotlin 2.2 编译兼容）
    implementation("com.google.devtools.ksp:symbol-processing-api:2.1.0-1.0.29")

    // JSON processing
    implementation("com.google.code.gson:gson:2.10.1")

    // Kotlin standard library
    implementation(kotlin("stdlib"))

    // Testing dependencies
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Kotlin compilation configuration (Kotlin 2.2+ compilerOptions DSL)
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// 添加源码 jar 任务
val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

// Publishing configuration
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(sourcesJar)
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
        }
    }
    repositories {
        maven {
            name = "AliYunMaven"
            url = uri("https://packages.aliyun.com/67053c6149e9309ce56b9e9e/maven/code-gen")
            val aliyunUser = providers.gradleProperty("aliyun.maven.username").orNull
            val aliyunPass = providers.gradleProperty("aliyun.maven.password").orNull
            if (!aliyunUser.isNullOrBlank() && !aliyunPass.isNullOrBlank()) {
                credentials {
                    username = aliyunUser
                    password = aliyunPass
                }
            }
        }
    }
}
