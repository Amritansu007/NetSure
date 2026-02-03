package com.example.adaptivepayment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.adaptivepayment.logic.DecisionEngine
import com.example.adaptivepayment.network.NetworkMonitor
import com.example.adaptivepayment.network.NetworkStatus
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var statusHeader: TextView
    private lateinit var statusIndicator: View
    private lateinit var containerStandard: LinearLayout
    private lateinit var containerFallback: LinearLayout

    private lateinit var networkMonitor: NetworkMonitor
    private val decisionEngine = DecisionEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Views
        statusHeader = findViewById(R.id.tvStatusHeader)
        statusIndicator = findViewById(R.id.viewStatusIndicator)
        containerStandard = findViewById(R.id.containerStandard)
        containerFallback = findViewById(R.id.containerFallback)

        // Initialize Logic
        networkMonitor = NetworkMonitor(this)

        // Start Assessment
        assessConnection()
        
        // Setup Button Listeners
        findViewById<Button>(R.id.btnPayStandard).setOnClickListener {
            Toast.makeText(this, "Processing Secure Online Payment...", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<Button>(R.id.btnPayFallback).setOnClickListener {
            Toast.makeText(this, "Initiating Offline Payment (SMS/USSD)...", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnPayQr).setOnClickListener {
            Toast.makeText(this, "Generating Offline QR Code...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun assessConnection() {
        // UI: Checking State
        updateStatusUI(NetworkStatus.Checking)

        lifecycleScope.launch {
            // Logic: Perform Network Test
            val metrics = networkMonitor.assessNetwork()
            
            // Logic: Decide
            val decision = decisionEngine.evaluate(metrics)

            // UI: Update based on decision
            updateStatusUI(decision)
        }
    }

    private fun updateStatusUI(status: NetworkStatus) {
        when (status) {
            is NetworkStatus.Checking -> {
                statusHeader.text = getString(R.string.status_checking)
                statusIndicator.backgroundTintList = ColorStateList.valueOf(getColor(R.color.status_checking))
                containerStandard.visibility = View.GONE
                containerFallback.visibility = View.GONE
            }
            is NetworkStatus.Stable -> {
                statusHeader.text = getString(R.string.status_stable)
                statusIndicator.backgroundTintList = ColorStateList.valueOf(getColor(R.color.status_stable))
                containerStandard.visibility = View.VISIBLE
                containerFallback.visibility = View.GONE
            }
            is NetworkStatus.Unstable -> {
                statusHeader.text = getString(R.string.status_unstable)
                statusIndicator.backgroundTintList = ColorStateList.valueOf(getColor(R.color.status_unstable))
                containerStandard.visibility = View.GONE
                containerFallback.visibility = View.VISIBLE
            }
        }
    }
}
