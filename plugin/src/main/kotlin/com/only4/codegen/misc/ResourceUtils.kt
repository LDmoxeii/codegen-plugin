package com.only4.codegen.misc

import java.nio.charset.StandardCharsets

fun loadFromClasspath(path: String): String? =
    object {}.javaClass.classLoader.getResourceAsStream(path)?.use { ins ->
        ins.bufferedReader(StandardCharsets.UTF_8).readText()
    }
