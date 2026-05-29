// QrCameraProView.swift
// UIView subclass that hosts the AVCaptureVideoPreviewLayer.
//
// Responsibilities (SRP):
//   • Observe `Notification.Name.qrCameraProSessionChanged` and attach/detach
//     the preview layer when the session starts or stops.
//   • Keep the preview layer sized to its own bounds on every layout pass.
//
// What this view does NOT do:
//   • It never touches the AVCaptureSession directly.
//   • It has no knowledge of barcode scanning or event emission.

import Foundation
import React
import AVFoundation

// MARK: - QrCameraProView

/// A UIView that renders the live camera feed from an `AVCaptureSession`.
///
/// The view listens for `Notification.Name.qrCameraProSessionChanged` and
/// automatically adds or removes the `AVCaptureVideoPreviewLayer` in response.
final class QrCameraProView: UIView {

    // MARK: - Private Properties

    /// The preview layer currently displayed, or `nil` when no session is active.
    private var previewLayer: AVCaptureVideoPreviewLayer?

    // MARK: - Initialiser

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
        subscribeToSessionChanges()

        // Attach the preview layer immediately if a session is already running
        // (e.g. the view is created after startScanning() was called).
        updatePreviewLayer(for: QrCameraProSwift.shared.getCaptureSession())
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported — instantiate QrCameraProView in code.")
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    // MARK: - Layout

    /// Keeps the preview layer flush with the view's bounds on every layout pass.
    override func layoutSubviews() {
        super.layoutSubviews()
        previewLayer?.frame = bounds
    }

    // MARK: - Private Session Observation

    /// Registers for the session-changed notification posted by `QrCameraProSwift`.
    private func subscribeToSessionChanges() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleSessionChanged(_:)),
            name: .qrCameraProSessionChanged,
            object: nil
        )
    }

    /// Handles the session-changed notification.
    /// Called on the main thread (QrCameraProSwift always posts on main).
    @objc private func handleSessionChanged(_ notification: Notification) {
        let session = notification.object as? AVCaptureSession
        updatePreviewLayer(for: session)
    }

    // MARK: - Private Preview Layer Management

    /// Replaces the current preview layer with one bound to `session`,
    /// or removes it entirely when `session` is `nil`.
    ///
    /// Safe to call with the same session multiple times — the old layer is
    /// removed before the new one is added (no duplicate sublayers).
    ///
    /// - Parameter session: The new `AVCaptureSession`, or `nil` to clear.
    private func updatePreviewLayer(for session: AVCaptureSession?) {
        // Remove any existing preview layer first.
        previewLayer?.removeFromSuperlayer()
        previewLayer = nil

        guard let session else { return }

        let newLayer = AVCaptureVideoPreviewLayer(session: session)
        newLayer.videoGravity = .resizeAspectFill
        newLayer.frame = bounds
        layer.addSublayer(newLayer)
        previewLayer = newLayer
    }
}