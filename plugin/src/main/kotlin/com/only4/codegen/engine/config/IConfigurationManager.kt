package com.only4.codegen.engine.config

import com.only4.codegen.DatabaseConfig
import com.only4.codegen.GenerationConfig

/**
 * 配置管理接口：统一获取生成相关配置。
 */
interface IConfigurationManager {
    fun getGenerationConfig(): GenerationConfig
    fun getDatabaseConfig(): DatabaseConfig
    fun getTemplateConfig(): TemplateConfig
    fun reloadConfig()
}

