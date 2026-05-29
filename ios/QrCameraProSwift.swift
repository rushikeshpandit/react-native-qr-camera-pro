// QrCameraProSwift.swift
// Core AVFoundation implementation for the QR/Barcode scanner module.
//
// Responsibilities (SRP):
//   • Own and manage the AVCaptureSession lifecycle (setup, start, stop).
//   • Deliver decoded barcode metadata to the React Native event emitter.
//   • Control the device torch.
//
// What this class does NOT do (delegated elsewhere):
//   • Throttle/dedup logic → BarcodeThrottler
//   • Preview layer management → QrCameraProView
//   • JS bridge wiring → QrCameraPro (.mm)

import Foundation
import AVFoundation
import React

// MARK: - QrCameraProSwift

/// Singleton that owns the AVCaptureSession and drives barcode detection.
///
/// The class is `@objc` so it can be called from the Objective-C++ bridge
/// (QrCameraPro.mm) without a Swift–ObjC wrapper.
@objc(QrCameraProSwift)
public final class QrCameraProSwift: NSObject {

    // MARK: - Singleton

    /// Shared instance. Use this from QrCameraPro.mm — never create additional instances.
    @objc public static let shared = QrCameraProSwift()

    // MARK: - Private State

    /// The active capture session. `nil` when the scanner is stopped.
    private var captureSession: AVCaptureSession?

    /// Reference to the back camera device; needed for torch control.
    private var captureDevice: AVCaptureDevice?

    /// The metadata output attached to the capture session.
    private var metadataOutput: AVCaptureMetadataOutput?

    /// The video input attached to the capture session.
    private var videoInput: AVCaptureDeviceInput?

    /// Throttle/dedup helper — owns all timing and last-seen-code state.
    private let barcodeThrottler = BarcodeThrottler(interval: 0.5)

    /// Weak reference to the RCTEventEmitter so we can forward events to JS.
    /// Set by QrCameraPro.mm before startScanning is called.
    private weak var eventEmitter: RCTEventEmitter?

    /// Guards all `sendEvent` calls. Flipped by QrCameraPro.mm's
    /// `startObserving` / `stopObserving`, which are driven by the
    /// RCTEventEmitter listener-count transitions (0→1 and n→0).
    private var hasListeners = false

    /// Serial queue for all AVCaptureSession operations.
    /// Apple requires that `startRunning()` / `stopRunning()` are called off
    /// the main thread because they block until the hardware is ready.
    private let sessionQueue = DispatchQueue(
        label: "com.qrcamerapro.session",
        qos: .userInitiated
    )

    // MARK: - Initialiser

    /// Private initialiser — use `QrCameraProSwift.shared`.
    private override init() {
        super.init()
    }

    // MARK: - @objc Public API (called from QrCameraPro.mm)

    /// Stores a reference to the event emitter used to push events to JS.
    /// Must be called before `startScanning()`.
    /// - Parameter emitter: The `RCTEventEmitter` instance (i.e. the QrCameraPro TurboModule).
    @objc public func setEventEmitter(_ emitter: RCTEventEmitter?) {
        eventEmitter = emitter
    }

    /// Informs the singleton whether a JS listener is currently registered.
    /// Called by QrCameraPro.mm's `startObserving` / `stopObserving` hooks.
    /// - Parameter value: `true` when at least one JS listener is active.
    @objc public func setHasListeners(_ value: Bool) {
        hasListeners = value
    }

    /// Returns the current capture session so the preview view can attach a layer.
    /// Returns `nil` when the scanner is stopped.
    @objc public func getCaptureSession() -> AVCaptureSession? {
        return captureSession
    }

    /// Sets up the AVCaptureSession (if needed) and starts running.
    /// Safe to call multiple times — a running session will not be re-initialised.
    /// Dispatched onto `sessionQueue` to keep AVFoundation off the main thread.
    @objc public func startScanning() {
        sessionQueue.async { [weak self] in
            self?.setupAndStartSession()
        }
    }

    /// Stops the capture session and releases all camera resources.
    /// After this call `getCaptureSession()` returns `nil`.
    @objc public func stopScanning() {
        sessionQueue.async { [weak self] in
            self?.tearDownSession()
        }
    }

    /// Turns the device torch on or off.
    /// Silently ignored when the device has no torch (e.g. simulator).
    /// - Parameter enabled: `true` to turn the torch on, `false` to turn it off.
    @objc public func toggleTorch(_ enabled: Bool) {
        sessionQueue.async { [weak self] in
            self?.applyTorchMode(enabled)
        }
    }

    // MARK: - Private Session Management

    /// Initialises the capture session and all of its inputs/outputs, then starts it.
    ///
    /// Idempotent: if the session already exists (but is not running — e.g. after
    /// an interruption) the setup phase is skipped and only `startRunning()` is called.
    ///
    /// Must be called on `sessionQueue`.
    private func setupAndStartSession() {
        // If the session is already running there is nothing to do.
        if captureSession?.isRunning == true { return }

        // Only build the session graph when we don't have one yet.
        if captureSession == nil {
            guard buildSessionGraph() else { return }
        }

        // startRunning() blocks until hardware is ready — must not be called on main.
        captureSession?.startRunning()

        // Notify the preview view on the main thread so it can add the preview layer.
        postSessionChangedNotification(session: captureSession)
    }

    /// Constructs the AVCaptureSession, wires inputs and outputs.
    /// - Returns: `true` if setup succeeded, `false` on any error (error is emitted to JS).
    private func buildSessionGraph() -> Bool {
        let session = AVCaptureSession()

        // --- Input ---
        guard let device = AVCaptureDevice.default(
            .builtInWideAngleCamera,
            for: .video,
            position: .back
        ) else {
            sendError("Failed to access the back camera.")
            return false
        }

        do {
            let input = try AVCaptureDeviceInput(device: device)
            guard session.canAddInput(input) else {
                sendError("Cannot add camera input to the capture session.")
                return false
            }
            session.addInput(input)
            captureDevice = device
            videoInput = input
        } catch {
            sendError("Camera input error: \(error.localizedDescription)")
            return false
        }

        // --- Output ---
        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else {
            sendError("Cannot add metadata output to the capture session.")
            return false
        }
        session.addOutput(output)

        // Metadata callbacks are lightweight (just reading a string) so main queue is fine.
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = SupportedBarcodeTypes.all

        metadataOutput = output
        captureSession = session
        return true
    }

    /// Stops the running session and releases all AVFoundation objects.
    ///
    /// Resets the barcode throttler so stale state does not bleed into the
    /// next scanning session.
    ///
    /// Must be called on `sessionQueue`.
    private func tearDownSession() {
        captureSession?.stopRunning()
        captureSession = nil
        videoInput = nil
        metadataOutput = nil
        captureDevice = nil
        barcodeThrottler.reset()

        postSessionChangedNotification(session: nil)
    }

    // MARK: - Private Torch Control

    /// Applies the requested torch mode to the capture device.
    ///
    /// Locks the device for configuration, changes the torch mode, then unlocks.
    /// Errors are forwarded to JS via `onCameraError`.
    ///
    /// Must be called on `sessionQueue`.
    /// - Parameter enabled: `true` for torch on, `false` for torch off.
    private func applyTorchMode(_ enabled: Bool) {
        guard let device = captureDevice, device.hasTorch else { return }

        do {
            try device.lockForConfiguration()
            device.torchMode = enabled ? .on : .off
            device.unlockForConfiguration()
        } catch {
            sendError("Torch error: \(error.localizedDescription)")
        }
    }

    // MARK: - Private Event Helpers

    /// Emits an `onCameraError` event to the JS side.
    ///
    /// Guarded by `hasListeners` so no spurious warnings are logged when
    /// JS has not yet registered a listener (common during early startup).
    /// - Parameter message: A human-readable description of the error.
    private func sendError(_ message: String) {
        guard hasListeners else { return }
        eventEmitter?.sendEvent(withName: "onCameraError", body: ["message": message])
    }

    /// Posts `QrCameraProSessionChanged` on the **main thread** so that
    /// `QrCameraProView` can safely update its preview layer (UIKit requirement).
    ///
    /// Extracted to satisfy DRY — called from both `setupAndStartSession` and
    /// `tearDownSession`.
    /// - Parameter session: The new session, or `nil` when the scanner stopped.
    private func postSessionChangedNotification(session: AVCaptureSession?) {
        DispatchQueue.main.async {
            NotificationCenter.default.post(
                name: .qrCameraProSessionChanged,
                object: session
            )
        }
    }
}

// MARK: - AVCaptureMetadataOutputObjectsDelegate

extension QrCameraProSwift: AVCaptureMetadataOutputObjectsDelegate {

    /// Invoked on the main queue whenever the camera detects metadata objects.
    ///
    /// Filters out non-barcode objects, applies throttle/dedup via `BarcodeThrottler`,
    /// then emits `onBarcodeScanned` to JS with the decoded string and type.
    public func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        // Take only the first barcode-readable object in the frame.
        guard
            let readable = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
            let barcodeValue = readable.stringValue
        else { return }

        // Apply throttle / dedup — suppress if the same code fired too recently.
        guard barcodeThrottler.shouldEmit(barcode: barcodeValue) else { return }

        // Only forward to JS when a listener is registered.
        guard hasListeners else { return }

        let barcodeType = BarcodeTypeMapper.string(for: readable.type)
        eventEmitter?.sendEvent(
            withName: "onBarcodeScanned",
            body: ["data": barcodeValue, "type": barcodeType]
        )
    }
}

// MARK: - Supporting Namespaces

/// All AVFoundation barcode/QR types the scanner will recognise.
/// Centralised here so they are easy to extend without touching session logic (OCP).
private enum SupportedBarcodeTypes {
    static let all: [AVMetadataObject.ObjectType] = [
        .qr, .ean8, .ean13, .pdf417, .aztec,
        .code128, .code39, .code93, .dataMatrix,
        .interleaved2of5, .itf14, .upce
    ]
}

/// Maps AVFoundation `ObjectType` values to the string constants the JS side expects.
/// Isolated here so the mapping can be tested and extended independently (SRP / OCP).
private enum BarcodeTypeMapper {

    /// Returns the JS-facing barcode type string for a given AVFoundation object type.
    /// Falls back to the raw value string for any unrecognised type.
    static func string(for type: AVMetadataObject.ObjectType) -> String {
        switch type {
        case .qr:               return "QR_CODE"
        case .ean8:             return "EAN_8"
        case .ean13:            return "EAN_13"
        case .pdf417:           return "PDF_417"
        case .aztec:            return "AZTEC"
        case .code128:          return "CODE_128"
        case .code39:           return "CODE_39"
        case .code93:           return "CODE_93"
        case .dataMatrix:       return "DATA_MATRIX"
        case .interleaved2of5:  return "ITF"
        case .itf14:            return "ITF_14"
        case .upce:             return "UPC_E"
        default:                return type.rawValue
        }
    }
}

// MARK: - Notification Name

extension Notification.Name {
    /// Posted (on the main thread) whenever the active AVCaptureSession changes.
    /// The `object` of the notification is the new `AVCaptureSession`, or `nil`
    /// when the scanner has stopped.
    static let qrCameraProSessionChanged = Notification.Name("QrCameraProSessionChanged")
}