plugins {
    kotlin("jvm") version "2.1.0"
    `maven-publish`
}

group = "com.only4"
version = "0.1.5-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // KSP API - 使用与 Kotlin 2.1.0 兼容的版本
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

// Kotlin compilation configuration
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
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
            credentials {
                username = providers.gradleProperty("aliyun.maven.username").get()
                password = providers.gradleProperty("aliyun.maven.password").get()
            }
        }
    }
}
