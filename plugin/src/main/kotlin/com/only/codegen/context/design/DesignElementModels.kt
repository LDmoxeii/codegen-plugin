package com.only.codegen.context.design

/**
 * 设计元素基础模型 (JSON 原始数据)
 */
data class DesignElement(
    val type: String,              // 设计类型: cmd/qry/saga/cli/ie/de/svc
    val name: String,              // 相对类名 (如 category.CreateCategory)
    val aggregate: String?,        // 可选聚合名 (单个,向后兼容)
    val aggregates: List<String>?,  // 多聚合支持
    val desc: String,              // 描述
    val metadata: Map<String, Any?> = emptyMap()  // 扩展元数据
)

/**
 * 统一设计接口 (所有设计类的基类)
 */
interface BaseDesign {
    val type: String               // cmd/qry/saga/cli/ie/de/svc
    val name: String               // 设计类名 (如 CreateCategoryCmd)
    val fullName: String           // 完整相对路径 (如 category.CreateCategoryCmd)
    val packagePath: String        // 相对包路径 (如 category)
    val aggregate: String?         // 主聚合 (第一个聚合,可能为 null)
    val aggregates: List<String>   // 所有关联聚合
    val desc: String               // 描述
    val primaryAggregateMetadata: AggregateMetadata?      // 主聚合元信息
    val aggregateMetadataList: List<AggregateMetadata>    // 所有聚合元信息
}

/**
 * KSP 聚合元数据
 */
data class AggregateMetadata(
    val name: String,                    // 聚合名称
    val fullName: String,                // 全限定类名
    val packageName: String,             // 包名
    val aggregateRoot: EntityMetadata,   // 聚合根实体
    val entities: List<EntityMetadata>,  // 包含的实体列表
    val idType: String?                  // ID 类型
)

/**
 * KSP 实体元数据
 */
data class EntityMetadata(
    val name: String,           // 实体名称
    val fullName: String,       // 全限定类名
    val packageName: String,    // 包名
    val isAggregateRoot: Boolean,
    val idType: String?,        // ID 类型
    val fields: List<FieldMetadata> = emptyList()
)

/**
 * 字段元数据
 */
data class FieldMetadata(
    val name: String,
    val type: String,
    val nullable: Boolean
)

/**
 * 通用设计类 (适用于 cmd/qry/saga/cli/svc)
 *
 * 这些设计类型只有 BaseDesign 的基础字段,无专属字段
 * 简单的字符串拼接 (如 requestName, responseName) 在模板中完成
 */
data class CommonDesign(
    override val type: String,
    override val name: String,              // 设计类名 (如 CreateCategoryCmd)
    override val fullName: String,          // 完整相对路径 (如 category.CreateCategoryCmd)
    override val packagePath: String,       // 相对包路径 (如 category)
    override val aggregate: String?,        // 主聚合名
    override val aggregates: List<String>,  // 所有关联聚合
    override val desc: String,              // 描述
    override val primaryAggregateMetadata: AggregateMetadata?,      // 主聚合元信息
    override val aggregateMetadataList: List<AggregateMetadata>     // 所有聚合元信息
) : BaseDesign

/**
 * 集成事件设计 (有专属字段)
 */
data class IntegrationEventDesign(
    override val type: String = "ie",
    override val name: String,
    override val fullName: String,
    override val packagePath: String,
    override val aggregate: String?,
    override val aggregates: List<String>,
    override val desc: String,
    override val primaryAggregateMetadata: AggregateMetadata?,
    override val aggregateMetadataList: List<AggregateMetadata>,
    val mqTopic: String?,          // MQ 主题 (用户配置)
    val mqConsumer: String?,       // MQ 消费者 (用户配置)
    val internal: Boolean = true   // 是否内部事件 (业务逻辑)
) : BaseDesign

/**
 * 领域事件设计 (有专属字段)
 */
data class DomainEventDesign(
    override val type: String = "de",
    override val name: String,
    override val fullName: String,
    override val packagePath: String,
    override val aggregate: String,        // 必须关联聚合
    override val aggregates: List<String>,
    override val desc: String,
    override val primaryAggregateMetadata: AggregateMetadata?,
    override val aggregateMetadataList: List<AggregateMetadata>,
    val entity: String,            // 关联实体 (用户配置)
    val persist: Boolean = false   // 是否持久化 (业务逻辑)
) : BaseDesign
