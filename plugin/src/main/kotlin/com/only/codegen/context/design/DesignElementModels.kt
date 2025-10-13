package com.only.codegen.context.design

/**
 * 设计元素基础模型
 */
data class DesignElement(
    val type: String,              // 设计类型: cmd/qry/saga/cli/ie/de/svc
    val name: String,              // 相对类名 (如 category.CreateCategory)
    val aggregate: String?,        // 可选聚合名
    val desc: String,              // 描述
    val metadata: Map<String, Any?> = emptyMap()  // 扩展元数据
)

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
    val name: String,              // 命令类名 (如 CreateCategoryCmd)
    val fullName: String,          // 完整相对路径 (如 category.CreateCategoryCmd)
    val packagePath: String,       // 相对包路径 (如 category)
    val aggregate: String?,        // 可选聚合名
    val desc: String,              // 描述
    val requestName: String,       // Request 类名
    val responseName: String,      // Response 类名
    val aggregateMetadata: AggregateMetadata? = null  // 关联的聚合元数据
)

/**
 * 查询设计
 */
data class QueryDesign(
    val name: String,              // 查询类名 (如 GetCategoryTreeQry)
    val fullName: String,          // 完整相对路径
    val packagePath: String,       // 相对包路径
    val aggregate: String?,        // 可选聚合名
    val desc: String,              // 描述
    val requestName: String,       // Request 类名
    val responseName: String,      // Response 类名
    val crossAggregate: Boolean = false,  // 是否跨聚合查询
    val includes: List<String> = emptyList()  // 涉及的聚合列表
)

/**
 * Saga 设计
 */
data class SagaDesign(
    val name: String,
    val fullName: String,
    val packagePath: String,
    val aggregate: String?,
    val desc: String,
    val requestName: String,
    val responseName: String
)

/**
 * 客户端(防腐层)设计
 */
data class ClientDesign(
    val name: String,
    val fullName: String,
    val packagePath: String,
    val aggregate: String?,
    val desc: String,
    val requestName: String,
    val responseName: String
)

/**
 * 集成事件设计
 */
data class IntegrationEventDesign(
    val name: String,
    val fullName: String,
    val packagePath: String,
    val aggregate: String?,
    val desc: String,
    val mqTopic: String?,          // MQ 主题
    val mqConsumer: String?,       // MQ 消费者
    val internal: Boolean = true   // 是否内部事件
)

/**
 * 领域事件设计
 */
data class DomainEventDesign(
    val name: String,
    val fullName: String,
    val packagePath: String,
    val aggregate: String,         // 必须关联聚合
    val entity: String,            // 关联实体
    val desc: String,
    val persist: Boolean = false,  // 是否持久化
    val aggregateMetadata: AggregateMetadata? = null
)

/**
 * 领域服务设计
 */
data class DomainServiceDesign(
    val name: String,
    val fullName: String,
    val packagePath: String,
    val aggregate: String?,
    val desc: String
)
