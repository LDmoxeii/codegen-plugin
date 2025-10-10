package com.only.codegen.pebble

import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.ClasspathLoader
import io.pebbletemplates.pebble.loader.StringLoader

object PebbleInitializer {

    @Volatile
    private var initialized = false

    private lateinit var config: PebbleConfig
    private lateinit var engine: PebbleEngine

    @Synchronized
    fun initPebble(config: PebbleConfig = PebbleConfig.DEFAULT) {
        if (initialized) return

        this.config = config

        val stringLoader = StringLoader()

        val classpathLoader = ClasspathLoader().apply {
            prefix = config.classpathPrefix
            suffix = ".peb"
        }

        val loader = CompositeLoader(classpathLoader, stringLoader)

        engine = PebbleEngine.Builder()
            .loader(loader)
            .strictVariables(config.strictMode)
            .cacheActive(config.cacheEnabled)
            .autoEscaping(config.autoEscaping)
            .newLineTrimming(config.newLineTrimming)
            .build()

        initialized = true
    }

    @Synchronized
    fun isInitialized() = initialized

    /**
     * 获取全局 PebbleEngine
     */
    fun getEngine(): PebbleEngine {
        check(initialized) { "PebbleInitializer not initialized" }
        return engine
    }

    /**
     * 获取当前配置
     */
    fun getConfig(): PebbleConfig {
        check(initialized) { "PebbleInitializer not initialized" }
        return config
    }

    /**
     * 重置引擎(主要用于测试)
     */
    @Synchronized
    fun reset() {
        initialized = false
    }
}
