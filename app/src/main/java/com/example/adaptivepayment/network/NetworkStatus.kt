package com.example.adaptivepayment.network

sealed class NetworkStatus {
    object Checking : NetworkStatus()
    object Stable : NetworkStatus()
    object Unstable : NetworkStatus()
}

data class NetworkMetrics(
    val hasInternetConnection: Boolean,
    val isWifi: Boolean,
    val latencyMs: Long,
    val requestSuccessful: Boolean
)
