package com.only4.codegen.generators.design

import com.only4.codegen.context.design.DesignContext
import com.only4.codegen.context.design.models.CommonDesign
import com.only4.codegen.manager.ApiPayloadImportManager
import com.only4.codegen.misc.refPackage
import com.only4.codegen.misc.toUpperCamelCase
import com.only4.codegen.template.TemplateNode

/**
 * 生成适配层 portal/api/payload 下的请求负载（单体/列表/分页）
 * 模板来源：resources/templates/api_payload_*.kt.peb
 */
class ApiPayloadGenerator : DesignTemplateGenerator {

    override val tag: String = "api_payload"
    override val order: Int = 25

    context(ctx: DesignContext)
    override fun shouldGenerate(design: Any): Boolean {
        if (design !is CommonDesign) return false
        // 避免重复生成（按名称唯一）
        if (ctx.typeMapping.containsKey(generatorName(design))) return false
        return true
    }

    context(ctx: DesignContext)
    override fun buildContext(design: Any): Map<String, Any?> {
        require(design is CommonDesign) { "Design must be CommonDesign" }

        val result = ctx.baseMap.toMutableMap()

        // 推断 payload 类型
        val payloadType = ApiPayloadImportManager.inferPayloadType(design.name)
        val importManager = ApiPayloadImportManager(payloadType)
        importManager.addBaseImports()

        with(ctx) {
            // 输出到 adapter 模块
            result.putContext(tag, "modulePath", ctx.adapterPath)
            result.putContext(tag, "templatePackage", templatePackage[tag] ?: refPackage(".adapter.portal.api.payload"))
            result.putContext(tag, "package", refPackage(design.`package`))

            result.putContext(tag, "Payload", generatorName(design))
            result.putContext(tag, "Comment", design.desc)

            // imports
            result.putContext(tag, "imports", importManager.toImportLines())
        }

        return result
    }

    context(ctx: DesignContext)
    override fun generatorFullName(design: Any): String {
        require(design is CommonDesign)
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(".adapter.portal.api.payload")
        val `package` = refPackage(design.`package`)
        return "$basePackage$templatePackage$`package`${refPackage(generatorName(design))}"
    }

    context(ctx: DesignContext)
    override fun generatorName(design: Any): String {
        require(design is CommonDesign)
        // 不强制后缀，按设计名转 UpperCamelCase
        return toUpperCamelCase(design.name)!!
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
        if (design is CommonDesign) {
            val full = generatorFullName(design)
            ctx.typeMapping[generatorName(design)] = full
        }
    }
}

