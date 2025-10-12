plugins {
    kotlin("jvm") version "2.1.0"
    `maven-publish`
}

group = "com.only"
version = "1.0.0"

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

// Publishing configuration
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.only"
            artifactId = "ksp-processor"
            version = "1.0.0"
        }
    }
}
