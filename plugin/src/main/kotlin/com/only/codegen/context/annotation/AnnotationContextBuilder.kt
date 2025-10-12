package com.only.codegen.context.annotation

/**
 * 注解上下文构建器接口（独立于 EntityContextBuilder）
 *
 * 用于构建 AnnotationContext 的各个部分
 * 每个 Builder 负责填充 MutableAnnotationContext 的特定部分
 *
 * @see AnnotationContext
 * @see MutableAnnotationContext
 */
interface AnnotationContextBuilder {

    /**
     * 执行顺序
     * 数值越小越先执行
     *
     * 推荐值：
     * - 10: 基础数据收集（KspMetadataContextBuilder）
     * - 20: 聚合信息构建（AggregateInfoBuilder）
     * - 30: 类型映射构建（IdentityTypeBuilder）
     */
    val order: Int

    /**
     * 构建上下文
     *
     * @param context 可变的注解上下文，可以直接修改其中的 Map
     */
    fun build(context: MutableAnnotationContext)
}
