package com.example.variants.moveclass.original

val metricsRegistry = mutableMapOf<String, Int>()

class Metrics {
    fun record(name: String, value: Int) {
        metricsRegistry[name] = value
    }

    fun get(name: String): Int = metricsRegistry[name] ?: 0
}
