package com.only.codegen.ksp

import com.google.gson.Gson
import com.only.codegen.ksp.models.AggregateMetadata
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * AnnotationProcessor 单元测试
 *
 * 注意: 这些是基础的元数据模型测试
 * 完整的 KSP 处理器集成测试需要在实际项目中运行
 */
class AnnotationProcessorTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `should create AggregateMetadata correctly`() {
        val metadata = AggregateMetadata(
            aggregateName = "User",
            className = "User",
            qualifiedName = "com.example.domain.User",
            packageName = "com.example.domain",
            isAggregateRoot = true,
            isEntity = true,
            isValueObject = false,
            identityType = "kotlin.Long",
            fields = emptyList()
        )

        assertEquals("User", metadata.aggregateName)
        assertEquals("User", metadata.className)
        assertEquals("com.example.domain.User", metadata.qualifiedName)
        assertEquals("com.example.domain", metadata.packageName)
        assertTrue(metadata.isAggregateRoot)
        assertTrue(metadata.isEntity)
        assertEquals("kotlin.Long", metadata.identityType)
    }

    @Test
    fun `should serialize and deserialize AggregateMetadata to JSON`() {
        val metadata = AggregateMetadata(
            aggregateName = "Order",
            className = "Order",
            qualifiedName = "com.example.domain.Order",
            packageName = "com.example.domain",
            isAggregateRoot = true,
            isEntity = true,
            isValueObject = false,
            identityType = "kotlin.Long",
            fields = emptyList()
        )

        val gson = Gson()
        val json = gson.toJson(metadata)

        assertNotNull(json)
        assertTrue(json.contains("\"aggregateName\":\"Order\""))
        assertTrue(json.contains("\"isAggregateRoot\":true"))

        // 反序列化
        val deserialized = gson.fromJson(json, AggregateMetadata::class.java)
        assertEquals(metadata.aggregateName, deserialized.aggregateName)
        assertEquals(metadata.className, deserialized.className)
        assertEquals(metadata.isAggregateRoot, deserialized.isAggregateRoot)
    }

    @Test
    fun `should serialize array of AggregateMetadata to JSON`() {
        val metadataList = listOf(
            AggregateMetadata(
                aggregateName = "User",
                className = "User",
                qualifiedName = "com.example.domain.User",
                packageName = "com.example.domain",
                isAggregateRoot = true,
                isEntity = true,
                isValueObject = false,
                identityType = "kotlin.Long",
                fields = emptyList()
            ),
            AggregateMetadata(
                aggregateName = "Order",
                className = "Order",
                qualifiedName = "com.example.domain.Order",
                packageName = "com.example.domain",
                isAggregateRoot = true,
                isEntity = true,
                isValueObject = false,
                identityType = "kotlin.Long",
                fields = emptyList()
            )
        )

        val gson = Gson()
        val json = gson.toJson(metadataList)

        assertNotNull(json)

        // 反序列化
        val deserialized = gson.fromJson(json, Array<AggregateMetadata>::class.java)
        assertEquals(2, deserialized.size)
        assertEquals("User", deserialized[0].aggregateName)
        assertEquals("Order", deserialized[1].aggregateName)
    }

    @Test
    fun `should create AnnotationProcessorProvider`() {
        val provider = AnnotationProcessorProvider()
        assertNotNull(provider)
    }
}
