package com.qrcamerapro

/**
 * Decides whether a newly detected barcode value should be forwarded to JavaScript.
 *
 * Encapsulates all throttle and deduplication state so that [QrCameraProModule]
 * is not responsible for tracking timing or last-seen codes (SRP).
 *
 * A barcode emission is **suppressed** when:
 *  1. The same value was most recently emitted AND the [intervalMs] window has
 *     not yet expired, OR
 *  2. A different value arrives within [intervalMs] of the last emission.
 *
 * Once [intervalMs] milliseconds elapse, the same code is allowed through again,
 * enabling intentional re-scans after the user moves the device away and back.
 *
 * **Thread-safety**: This class is not thread-safe. All calls must arrive on the
 * same thread (the ML Kit success listener runs on the calling thread, which is
 * the camera executor — always the same thread per analyzer instance).
 *
 * @param intervalMs Minimum milliseconds between successive emissions. Defaults to 500 ms.
 */
internal class BarcodeThrottler(private val intervalMs: Long = 500L) {

    /** The barcode string most recently allowed through the throttle gate, or null. */
    private var lastEmittedCode: String? = null

    /** System clock timestamp (ms) of the most recent allowed emission. */
    private var lastEmitTime: Long = 0L

    /**
     * Evaluates whether [barcode] should be forwarded to JavaScript.
     *
     * Returns `true` (and updates internal state) when:
     *  - The barcode is different from the last emitted code, OR
     *  - The same code arrives after the throttle window has expired.
     *
     * Returns `false` to suppress the event when the same code fires again
     * inside the throttle window.
     *
     * @param barcode The decoded barcode string to evaluate.
     * @return `true` if the caller should emit the event; `false` to suppress it.
     */
    fun shouldEmit(barcode: String): Boolean {
        val now = System.currentTimeMillis()
        val isNewCode = barcode != lastEmittedCode
        val throttleExpired = (now - lastEmitTime) >= intervalMs

        if (!isNewCode && !throttleExpired) {
            // Same code inside the throttle window — suppress.
            return false
        }

        // Gate passed — record state for the next evaluation.
        lastEmittedCode = barcode
        lastEmitTime = now
        return true
    }

    /**
     * Resets all throttle state.
     *
     * Must be called when the scanner stops so stale state does not carry over
     * into the next scanning session.
     */
    fun reset() {
        lastEmittedCode = null
        lastEmitTime = 0L
    }
}