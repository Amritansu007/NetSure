package com.example.adaptivepayment.logic

import com.example.adaptivepayment.network.NetworkMetrics
import com.example.adaptivepayment.network.NetworkStatus

class DecisionEngine {

    companion object {
        private const val LATENCY_THRESHOLD_MS = 1500L // 1.5 seconds max acceptable latency
    }

    fun evaluate(metrics: NetworkMetrics): NetworkStatus {
        // 1. If system says no internet -> Unstable
        if (!metrics.hasInternetConnection) {
            return NetworkStatus.Unstable
        }

        // 2. If the real ping failed -> Unstable
        if (!metrics.requestSuccessful) {
            return NetworkStatus.Unstable
        }

        // 3. If latency is too high -> Unstable
        if (metrics.latencyMs > LATENCY_THRESHOLD_MS) {
            return NetworkStatus.Unstable
        }

        // 4. Optionally: If on 2G (check omitted for simplicity, latency usually catches this) -> Unstable

        return NetworkStatus.Stable
    }
}
