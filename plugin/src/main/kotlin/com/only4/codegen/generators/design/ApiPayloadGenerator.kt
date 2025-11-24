package com.only4.codegen.generators.design

import com.only4.codegen.context.design.DesignContext
import com.only4.codegen.context.design.models.ApiPayloadDesign
import com.only4.codegen.context.design.models.PayloadField
import com.only4.codegen.imports.ApiPayloadImportManager
import com.only4.codegen.ksp.models.FieldMetadata
import com.only4.codegen.misc.refPackage
import com.only4.codegen.template.TemplateNode

/**
 * 生成适配层 portal/api/payload 下的请求负载（单体/列表/分页）
 * 模板来源：resources/templates/api_payload_*.kt.peb
 */
class ApiPayloadGenerator : DesignTemplateGenerator {

    override val tag: String = "api_payload"
    override val order: Int = 10

    context(ctx: DesignContext)
    override fun shouldGenerate(design: Any): Boolean {
        if (design !is ApiPayloadDesign) return false
        // 避免重复生成（按名称唯一）
        if (ctx.typeMapping.containsKey(generatorName(design))) return false
        return true
    }

    context(ctx: DesignContext)
    override fun buildContext(design: Any): Map<String, Any?> {
        require(design is ApiPayloadDesign) { "Design must be ApiPayloadDesign" }

        val result = ctx.baseMap.toMutableMap()

        // 推断 payload 类型
        val payloadType = ApiPayloadImportManager.inferPayloadType(design.name)
        val importManager = ApiPayloadImportManager(payloadType)
        importManager.addBaseImports()

        with(ctx) {
            // 输出到 adapter 模块
            result.putContext(tag, "modulePath", ctx.adapterPath)
            result.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: "adapter.portal.api.payload"))
            result.putContext(tag, "package", refPackage(design.`package`))

            result.putContext(tag, "Payload", generatorName(design))
            result.putContext(tag, "Comment", design.desc)

            // 字段解析与类型推断
            val fieldContext = resolveFields(design)
            result.putContext(tag, "requestFields", fieldContext.requestFieldsForTemplate)
            result.putContext(tag, "responseFields", fieldContext.responseFieldsForTemplate)
            importManager.add(*fieldContext.imports.toTypedArray())

            // imports
            result.putContext(tag, "imports", importManager.toImportLines())
        }

        return result
    }

    context(ctx: DesignContext)
    override fun generatorFullName(design: Any): String {
        require(design is ApiPayloadDesign)
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(".adapter.portal.api.payload")
        val `package` = refPackage(design.`package`)
        return "$basePackage$templatePackage$`package`${refPackage(generatorName(design))}"
    }

    context(ctx: DesignContext)
    override fun generatorName(design: Any): String {
        require(design is ApiPayloadDesign)
        return design.className()
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            // 单对象（非 List/Page）
            TemplateNode().apply {
                type = "file"
                tag = this@ApiPayloadGenerator.tag
                pattern = "^(?!.*(List|list|Page|page)).*$"
                name = "{{ Payload }}.kt"
                format = "resource"
                data = "templates/api_payload_single.kt.peb"
                conflict = "skip"
            },
            // 列表
            TemplateNode().apply {
                type = "file"
                tag = this@ApiPayloadGenerator.tag
                pattern = "^.*(List|list).*$"
                name = "{{ Payload }}.kt"
                format = "resource"
                data = "templates/api_payload_list.kt.peb"
                conflict = "skip"
            },
            // 分页
            TemplateNode().apply {
                type = "file"
                tag = this@ApiPayloadGenerator.tag
                pattern = "^.*(Page|page).*$"
                name = "{{ Payload }}.kt"
                format = "resource"
                data = "templates/api_payload_page.kt.peb"
                conflict = "skip"
            }
        )
    }

    context(ctx: DesignContext)
    override fun onGenerated(design: Any) {
        if (design is ApiPayloadDesign) {
            val full = generatorFullName(design)
            ctx.typeMapping[generatorName(design)] = full
        }
    }

    private data class ResolvedFields(
        val requestFieldsForTemplate: List<Map<String, String>>,
        val responseFieldsForTemplate: List<Map<String, String>>,
        val imports: Set<String>,
    )

    context(ctx: DesignContext)
    private fun resolveFields(design: ApiPayloadDesign): ResolvedFields {
        val imports = mutableSetOf<String>()

        fun inferType(field: PayloadField): String {
            field.type?.takeIf { it.isNotBlank() }?.let { return it }

            // 聚合内查找字段类型：聚合根 -> 值对象 -> 实体
            design.aggregates.forEach { aggName ->
                val agg = ctx.aggregateMap[aggName] ?: return@forEach
                lookupFieldType(agg.aggregateRoot.fields, field.name)?.let { return it }
                agg.valueObjects.forEach { vo ->
                    lookupFieldType(vo.fields, field.name)?.let { return it }
                }
                agg.entities.forEach { entity ->
                    lookupFieldType(entity.fields, field.name)?.let { return it }
                }
            }
            return "kotlin.String"
        }

        fun renderType(rawType: String, nullable: Boolean): String {
            val cleaned = rawType.trim()
            val shortName = cleaned.substringAfterLast(".")
            val needsImport = cleaned.contains(".") && !cleaned.startsWith("kotlin.")
                    && cleaned != shortName && !cleaned.startsWith("java.lang.")
            val mappedType = ctx.typeMapping[shortName]
            if (cleaned == shortName && mappedType != null) {
                imports.add(mappedType)
            } else if (needsImport) {
                imports.add(cleaned)
            }
            return if (nullable) "$shortName?" else shortName
        }

        fun toTemplateFields(fields: List<PayloadField>): List<Map<String, String>> =
            fields.map { f ->
                val rawType = inferType(f)
                val typeForCode = renderType(rawType, f.nullable)
                val defaultValue = f.defaultValue?.takeIf { it.isNotBlank() }
                buildMap {
                    put("name", f.name)
                    put("type", typeForCode)
                    defaultValue?.let { put("defaultValue", it) }
                }
            }

        val request = toTemplateFields(design.requestFields)
        val response = toTemplateFields(design.responseFields)

        return ResolvedFields(
            requestFieldsForTemplate = request,
            responseFieldsForTemplate = response,
            imports = imports,
        )
    }

    private fun lookupFieldType(fields: List<FieldMetadata>, name: String): String? {
        val match = fields.firstOrNull { it.name.equals(name, ignoreCase = false) } ?: return null
        return match.type
    }
}
