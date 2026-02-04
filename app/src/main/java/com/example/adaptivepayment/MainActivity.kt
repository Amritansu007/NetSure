package com.example.adaptivepayment

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.adaptivepayment.ussd.UssdHelper
import com.example.adaptivepayment.util.extractUpiIdFromQr
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: androidx.camera.view.PreviewView

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var barcodeScanner: BarcodeScanner? = null
    private var scanningStopped = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else showPermissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.previewView)
        initBarcodeScanner()
        checkCameraPermission()
    }

    private fun initBarcodeScanner() {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ->
                startCamera()
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindPreviewAndAnalysis(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindPreviewAndAnalysis(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, UpiQrAnalyzer(
                    barcodeScanner = requireNotNull(barcodeScanner),
                    onUpiIdFound = { upiId -> onUpiIdDetected(upiId) },
                    isScanningStopped = { scanningStopped }
                ))
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
        } catch (e: Exception) {
            Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onUpiIdDetected(upiId: String) {
        if (scanningStopped) return
        scanningStopped = true
        copyToClipboard(upiId)
        runOnUiThread {
            Toast.makeText(this, "UPI ID copied: $upiId", Toast.LENGTH_LONG).show()
            // USSD helper: show dialog to open *99# and guide user to paste UPI ID when prompted
            UssdHelper.promptAndStartUssd(this, upiId, amount = null)
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", text))
    }

    private fun showPermissionDenied() {
        Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == UssdHelper.REQUEST_CALL_PHONE) {
            UssdHelper.handlePermissionResult(this, requestCode, permissions, grantResults)
        }
    }

    override fun onResume() {
        super.onResume()
        // Cancel USSD flow notification when user returns to app
        UssdHelper.cancelNotification(this)
        // TODO: Integrate with NetworkMonitor or SMS receiver — checkPaymentStatus() to confirm payment
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        barcodeScanner?.close()
    }
}

/**
 * CameraX ImageAnalysis.Analyzer that runs ML Kit barcode detection and extracts UPI ID from QR content.
 */
private class UpiQrAnalyzer(
    private val barcodeScanner: BarcodeScanner,
    private val onUpiIdFound: (String) -> Unit,
    private val isScanningStopped: () -> Boolean
) : ImageAnalysis.Analyzer {

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (isScanningStopped()) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )
        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                if (isScanningStopped()) return@addOnSuccessListener
                for (barcode in barcodes) {
                    barcode.rawValue?.let { raw ->
                        extractUpiIdFromQr(raw)?.let { upiId ->
                            onUpiIdFound(upiId)
                            return@addOnSuccessListener
                        }
                    }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
