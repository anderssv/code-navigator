package com.example.variants.moveclass.consumer

import com.example.variants.moveclass.original.Metrics
import com.example.variants.moveclass.original.metricsRegistry

class MetricsConsumer {
    private val metrics = Metrics()

    fun trackOrder(orderId: String) {
        metrics.record("orders", metricsRegistry.size + 1)
    }
}
