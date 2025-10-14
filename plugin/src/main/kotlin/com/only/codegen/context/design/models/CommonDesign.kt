package com.only.codegen.context.design.models

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
    override val primaryAggregateMetadata: AggregateInfo?,      // 主聚合元信息
    override val aggregateMetadataList: List<AggregateInfo>,     // 所有聚合元信息
) : BaseDesign
