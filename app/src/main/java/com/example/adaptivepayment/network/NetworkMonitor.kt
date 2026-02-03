package com.example.adaptivepayment.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.measureTimeMillis

class NetworkMonitor(private val context: Context) {

    private val connectivityManager = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Performs a comprehensive network stability check.
     * 1. Checks system connectivity status (Is connected?)
     * 2. Checks transport type (WiFi vs Cellular)
     * 3. Performs a real HTTP "ping" to measure latency and packet success.
     */
    suspend fun assessNetwork(): NetworkMetrics = withContext(Dispatchers.IO) {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        val hasInternet = capabilities != null && 
                (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                 capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (!hasInternet) {
            return@withContext NetworkMetrics(
                hasInternetConnection = false,
                isWifi = false,
                latencyMs = -1,
                requestSuccessful = false
            )
        }

        // Perform Ping Test
        // Using Google DNS check (generate_204) or a reliable public IP 
        // Real-world: Should be your API health endpoint.
        // We use Google's 'generate_204' as it's extremely lightweight.
        var latency = 0L
        var success = false
        
        try {
            latency = measureTimeMillis {
                val url = URL("https://clients3.google.com/generate_204")
                val connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 3000 // 3s timeout
                connection.readTimeout = 3000
                connection.useCaches = false
                connection.requestMethod = "HEAD"
                
                val responseCode = connection.responseCode
                success = responseCode == 204
                connection.disconnect()
            }
        } catch (e: Exception) {
            success = false
            latency = -1
        }

        return@withContext NetworkMetrics(
            hasInternetConnection = true, // We know system *thinks* it has internet
            isWifi = isWifi,
            latencyMs = if (success) latency else -1,
            requestSuccessful = success
        )
    }
}
