package com.example.adaptivepayment

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.adaptivepayment.network.NetworkMonitor
import kotlinx.coroutines.launch

class NetworkTestActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnOpenScanner: Button
    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network_test)

        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        btnOpenScanner = findViewById(R.id.btnOpenScanner)
        networkMonitor = NetworkMonitor(this)

        btnOpenScanner.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        runNetworkTest()
    }

    private fun runNetworkTest() {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            btnOpenScanner.visibility = View.GONE
            tvStatus.text = getString(R.string.status_checking)

            val metrics = networkMonitor.assessNetwork()

            progressBar.visibility = View.GONE
            btnOpenScanner.visibility = View.VISIBLE

            if (metrics.requestSuccessful) {
                tvStatus.text = "${getString(R.string.status_stable)}\nLatency: ${metrics.latencyMs}ms"
            } else {
                tvStatus.text = getString(R.string.status_unstable)
            }
        }
    }
}
