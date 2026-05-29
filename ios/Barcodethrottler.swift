// BarcodeThrottler.swift
// Encapsulates barcode scan deduplication and time-based throttling logic.
//
// Extracted from QrCameraProSwift to satisfy the Single Responsibility Principle:
// the scanner session should not also own throttle state management.
//
// Usage:
//   let throttler = BarcodeThrottler(interval: 0.5)
//   if throttler.shouldEmit(barcode: "12345") { /* forward to JS */ }

import Foundation

/// Decides whether a newly detected barcode value should be forwarded to JavaScript.
///
/// A barcode is suppressed when:
///   1. The same value was emitted less than `interval` seconds ago, OR
///   2. The same value was the *most recent* emission AND the throttle window
///      has not yet expired (prevents rapid duplicate fire on the same code).
///
/// Once `interval` seconds have elapsed the same code will be emitted again,
/// allowing intentional re-scans after the user moves away and back.
final class BarcodeThrottler {

    // MARK: - Properties

    /// Minimum number of seconds that must pass before the same barcode value
    /// is forwarded to JavaScript a second time.
    let interval: TimeInterval

    /// The barcode string most recently allowed through the throttle gate.
    private var lastEmittedCode: String?

    /// The timestamp of the most recent allowed emission.
    private var lastEmitTime: Date?

    // MARK: - Initialiser

    /// Creates a new throttler.
    /// - Parameter interval: Throttle window in seconds. Defaults to 0.5 s.
    init(interval: TimeInterval = 0.5) {
        self.interval = interval
    }

    // MARK: - Public API

    /// Evaluates whether `barcode` should be forwarded to JavaScript.
    ///
    /// Returns `true` (and updates internal state) when the code is new **or**
    /// when the throttle window has expired since it was last emitted.
    /// Returns `false` when the same code fires again inside the throttle window.
    ///
    /// - Parameter barcode: The decoded barcode string to evaluate.
    /// - Returns: `true` if the caller should emit the event, `false` to suppress it.
    func shouldEmit(barcode: String) -> Bool {
        let now = Date()

        // Allow if this is a brand-new code (never seen, or different from last).
        let isNewCode = barcode != lastEmittedCode

        // Allow if the throttle window has expired, even for the same code.
        let throttleExpired: Bool = {
            guard let last = lastEmitTime else { return true }
            return now.timeIntervalSince(last) >= interval
        }()

        guard isNewCode || throttleExpired else {
            // Same code, inside throttle window — suppress.
            return false
        }

        // Gate passed — record state for the next evaluation.
        lastEmittedCode = barcode
        lastEmitTime = now
        return true
    }

    /// Resets all internal throttle state.
    /// Call this when the scanner stops so stale state does not carry over
    /// into the next scanning session.
    func reset() {
        lastEmittedCode = nil
        lastEmitTime = nil
    }
}