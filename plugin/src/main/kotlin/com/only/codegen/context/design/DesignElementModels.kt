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
 * 命令设计
 */
data class CommandDesign(
    override val type: String = "cmd",
    override val name: String,              // 命令类名 (如 CreateCategoryCmd)
    override val fullName: String,          // 完整相对路径 (如 category.CreateCategoryCmd)
    override val packagePath: String,       // 相对包路径 (如 category)
    override val aggregate: String?,        // 主聚合名
    override val aggregates: List<String>,  // 所有关联聚合
    override val desc: String,              // 描述
    override val primaryAggregateMetadata: AggregateMetadata?,      // 主聚合元信息
    override val aggregateMetadataList: List<AggregateMetadata>,    // 所有聚合元信息
    val requestName: String,       // Request 类名
    val responseName: String       // Response 类名
) : BaseDesign

/**
 * 查询设计
 */
data class QueryDesign(
    override val type: String = "qry",
    override val name: String,              // 查询类名 (如 GetCategoryTreeQry)
    override val fullName: String,          // 完整相对路径
    override val packagePath: String,       // 相对包路径
    override val aggregate: String?,        // 主聚合名
    override val aggregates: List<String>,  // 所有关联聚合
    override val desc: String,              // 描述
    override val primaryAggregateMetadata: AggregateMetadata?,      // 主聚合元信息
    override val aggregateMetadataList: List<AggregateMetadata>,    // 所有聚合元信息
    val requestName: String,       // Request 类名
    val responseName: String       // Response 类名
) : BaseDesign

/**
 * Saga 设计
 */
data class SagaDesign(
    override val type: String = "saga",
    override val name: String,
    override val fullName: String,
    override val packagePath: String,
    override val aggregate: String?,
    override val aggregates: List<String>,
    override val desc: String,
    override val primaryAggregateMetadata: AggregateMetadata?,
    override val aggregateMetadataList: List<AggregateMetadata>,
    val requestName: String,
    val responseName: String
) : BaseDesign

/**
 * 客户端(防腐层)设计
 */
data class ClientDesign(
    override val type: String = "cli",
    override val name: String,
    override val fullName: String,
    override val packagePath: String,
    override val aggregate: String?,
    override val aggregates: List<String>,
    override val desc: String,
    override val primaryAggregateMetadata: AggregateMetadata?,
    override val aggregateMetadataList: List<AggregateMetadata>,
    val requestName: String,
    val responseName: String
) : BaseDesign

/**
 * 集成事件设计
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
    val mqTopic: String?,          // MQ 主题
    val mqConsumer: String?,       // MQ 消费者
    val internal: Boolean = true   // 是否内部事件
) : BaseDesign

/**
 * 领域事件设计
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
    val entity: String,            // 关联实体
    val persist: Boolean = false   // 是否持久化
) : BaseDesign

/**
 * 领域服务设计
 */
data class DomainServiceDesign(
    override val type: String = "svc",
    override val name: String,
    override val fullName: String,
    override val packagePath: String,
    override val aggregate: String?,
    override val aggregates: List<String>,
    override val desc: String,
    override val primaryAggregateMetadata: AggregateMetadata?,
    override val aggregateMetadataList: List<AggregateMetadata>
) : BaseDesign
