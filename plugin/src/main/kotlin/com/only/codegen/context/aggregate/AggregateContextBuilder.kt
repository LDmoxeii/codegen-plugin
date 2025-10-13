package com.only.codegen.context.aggregate

import com.only.codegen.context.ContextBuilder

/**
 * 注解上下文构建器接口（独立于 EntityContextBuilder）
 *
 * 用于构建 AnnotationContext 的各个部分
 * 每个 Builder 负责填充 MutableAnnotationContext 的特定部分
 *
 * @see AnnotationContext
 * @see MutableAnnotationContext
 */
interface AggregateContextBuilder : ContextBuilder<MutableAnnotationContext>
