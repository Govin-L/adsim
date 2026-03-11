package com.adsim.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "adsim.simulation")
data class SimulationConfig(
    val maxConcurrency: Int = 20,
    val batchDelayMs: Long = 100,
    val maxRetries: Int = 3,
    val minSuccessRate: Double = 0.8,
    val agentBatchSize: Int = 20
)
