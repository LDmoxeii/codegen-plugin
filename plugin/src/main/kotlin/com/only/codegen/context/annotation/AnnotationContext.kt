package com.only.codegen.context.annotation

import com.only.codegen.context.BaseContext

/**
 * 基于注解的代码生成上下文（只读接口）
 *
 * 与 EntityContext 完全独立，但共享 BaseContext 的基础属性
 */
interface AnnotationContext : BaseContext {

    /**
     * 类信息映射
     * key: 类的全限定名（FQN）
     * value: ClassInfo（包含包名、类名、注解、字段等信息）
     */
    val classMap: Map<String, ClassInfo>

    /**
     * 注解信息映射
     * key: 注解的简单名称（如 "Aggregate", "Entity"）
     * value: 包含该注解的所有类的信息列表
     */
    val annotationMap: Map<String, List<AnnotationInfo>>

    /**
     * 聚合信息映射
     * key: 聚合名称（如 "User", "Order"）
     * value: AggregateInfo（包含聚合根、实体、值对象等）
     */
    val aggregateMap: Map<String, AggregateInfo>

    /**
     * 源代码根目录（用于 KSP 扫描）
     */
    val sourceRoots: List<String>

    /**
     * 扫描的包路径（可选过滤）
     */
    val scanPackages: List<String>
}

/**
 * 可变的注解上下文（用于构建阶段）
 *
 * Builder 模式：各个 AnnotationContextBuilder 会修改此上下文
 */
interface MutableAnnotationContext : AnnotationContext {
    override val classMap: MutableMap<String, ClassInfo>
    override val annotationMap: MutableMap<String, MutableList<AnnotationInfo>>
    override val aggregateMap: MutableMap<String, AggregateInfo>
}

/**
 * 类信息（从 KSP 元数据构建）
 */
data class ClassInfo(
    val packageName: String,
    val simpleName: String,
    val fullName: String,
    val filePath: String,
    val annotations: List<AnnotationInfo>,
    val fields: List<FieldInfo>,
    val superClass: String?,
    val interfaces: List<String>,
    val isAggregateRoot: Boolean = false,
    val isEntity: Boolean = false,
    val isValueObject: Boolean = false,
)

/**
 * 注解信息
 */
data class AnnotationInfo(
    val name: String,                        // 注解名称（如 "Aggregate"）
    val fullName: String,                    // 完整注解名
    val attributes: Map<String, Any?>,       // 注解属性
    val targetClass: String,                  // 注解所在的类
)

/**
 * 字段信息
 */
data class FieldInfo(
    val name: String,
    val type: String,
    val annotations: List<AnnotationInfo>,
    val isId: Boolean = false,
    val isNullable: Boolean = false,
    val defaultValue: String? = null,
)

/**
 * 聚合信息
 */
data class AggregateInfo(
    val name: String,                        // 聚合名称
    val aggregateRoot: ClassInfo,            // 聚合根实体
    val entities: List<ClassInfo>,           // 聚合内的实体
    val valueObjects: List<ClassInfo>,       // 聚合内的值对象
    val identityType: String,                // 聚合根的 ID 类型
    val modulePath: String,                   // 所属模块路径
)
