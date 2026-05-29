package com.qrcamerapro

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.Closeable

/**
 * CameraX [ImageAnalysis.Analyzer] that feeds camera frames into ML Kit's barcode
 * scanner and forwards the first detected barcode to [onBarcodeDetected].
 *
 * Implements [Closeable] so the underlying ML Kit [BarcodeScanning] client can be
 * properly released when the analyzer is no longer needed. Failure to close leaks
 * native ML Kit resources.
 *
 * **Thread-safety**: [analyze] is invoked on the camera executor thread. The
 * [onBarcodeDetected] callback will also be invoked on that thread — callers are
 * responsible for any required thread-hopping.
 *
 * @param onBarcodeDetected Callback invoked with the first [Barcode] found in a frame.
 *                          Only called when at least one barcode is detected.
 */
internal class BarcodeAnalyzer(
    private val onBarcodeDetected: (Barcode) -> Unit,
) : ImageAnalysis.Analyzer, Closeable {

    /** ML Kit barcode scanner client. Must be [close]d when no longer in use. */
    private val scanner = BarcodeScanning.getClient()

    /**
     * Processes one camera frame.
     *
     * Wraps the [ImageProxy] in an [InputImage] (preserving rotation), runs it
     * through ML Kit, and calls [onBarcodeDetected] with the first result.
     * The [ImageProxy] is always closed in the completion listener regardless of
     * success or failure, which is required by CameraX to unblock the pipeline.
     *
     * @param imageProxy The camera frame provided by CameraX.
     */
    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            // No image data — release the proxy and move on.
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                // Only forward the first barcode; multiple simultaneous codes are rare
                // and processing only one keeps the callback contract simple.
                barcodes.firstOrNull()?.let(onBarcodeDetected)
            }
            .addOnFailureListener {
                // Analysis failures are non-fatal — the next frame will be tried.
                // Errors are intentionally not forwarded to JS here; persistent
                // failures will surface through the camera error path instead.
            }
            .addOnCompleteListener {
                // Always close the proxy — CameraX blocks the pipeline until this.
                imageProxy.close()
            }
    }

    /**
     * Releases the underlying ML Kit scanner client.
     *
     * Must be called when the analyzer is removed from the [ImageAnalysis] use case
     * (i.e. when [QrCameraProModule.releaseCamera] runs) to free native resources.
     */
    override fun close() {
        scanner.close()
    }
}