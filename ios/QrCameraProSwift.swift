import Foundation
import AVFoundation
import React

@objc(QrCameraProSwift)
public class QrCameraProSwift: NSObject, AVCaptureMetadataOutputObjectsDelegate {
    @objc public static let shared = QrCameraProSwift()

    private var captureSession: AVCaptureSession?
    private var captureDevice: AVCaptureDevice?
    private var metadataOutput: AVCaptureMetadataOutput?
    private var videoInput: AVCaptureDeviceInput?

    private var lastScannedCode: String?
    private var lastScanTime: Date?
    private let scanThrottleInterval: TimeInterval = 0.5

    private var eventEmitter: RCTEventEmitter?

    // FIX 2: Guard all sendEvent() calls with this flag.
    // Set to true by QrCameraPro.startObserving() (first JS listener added),
    // false by QrCameraPro.stopObserving() (last JS listener removed).
    // This closes the timing window where the camera starts but JS hasn't
    // registered listeners yet via useEffect.
    private var hasListeners = false

    // FIX 3: Apple explicitly states AVCaptureSession.startRunning() must NOT
    // be called on the main thread — it blocks until the session is ready.
    // Use a dedicated serial queue for all session operations.
    private let sessionQueue = DispatchQueue(label: "com.qrcamerapro.session", qos: .userInitiated)

    private override init() {
        super.init()
    }

    @objc public func setEventEmitter(_ emitter: RCTEventEmitter?) {
        self.eventEmitter = emitter
    }

    // Called by QrCameraPro.startObserving() / stopObserving()
    @objc public func setHasListeners(_ value: Bool) {
        hasListeners = value
    }

    @objc public func getCaptureSession() -> AVCaptureSession? {
        return captureSession
    }

    @objc public func startScanning() {
        // FIX 3: Dispatch camera setup — including startRunning() — to the
        // session queue, not the main thread.
        sessionQueue.async {
            self.setupCamera()
        }
    }

    @objc public func stopScanning() {
        sessionQueue.async {
            self.stopCamera()
        }
    }

    @objc public func toggleTorch(_ enabled: Bool) {
        // Torch control is a quick lock/config, fine on session queue.
        sessionQueue.async {
            self.controlTorch(enabled)
        }
    }

    private func setupCamera() {
        if captureSession?.isRunning == true { return }

        if captureSession == nil {
            captureSession = AVCaptureSession()

            captureDevice = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back)
            guard let captureDevice = captureDevice else {
                sendError("Failed to get capture device")
                return
            }

            do {
                videoInput = try AVCaptureDeviceInput(device: captureDevice)
                if captureSession!.canAddInput(videoInput!) {
                    captureSession!.addInput(videoInput!)
                }

                metadataOutput = AVCaptureMetadataOutput()
                if captureSession!.canAddOutput(metadataOutput!) {
                    captureSession!.addOutput(metadataOutput!)
                    // Deliver metadata callbacks on main queue — fine since we
                    // only read metadata objects and forward to JS from here.
                    metadataOutput?.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
                    metadataOutput?.metadataObjectTypes = [
                        .qr, .ean8, .ean13, .pdf417, .aztec,
                        .code128, .code39, .code93, .dataMatrix,
                        .interleaved2of5, .itf14, .upce
                    ]
                }
            } catch {
                sendError("Error setting up camera: \(error.localizedDescription)")
                return
            }
        }

        // Safe to call here — we're already on sessionQueue (background).
        captureSession?.startRunning()

        // Post the notification on main so UIKit observers (QrCameraProView)
        // can safely touch layer hierarchy.
        let session = captureSession
        DispatchQueue.main.async {
            NotificationCenter.default.post(
                name: NSNotification.Name("QrCameraProSessionChanged"),
                object: session
            )
        }
    }

    private func stopCamera() {
        if captureSession?.isRunning == true {
            captureSession?.stopRunning()
        }
        captureSession = nil
        videoInput = nil
        metadataOutput = nil
        captureDevice = nil

        DispatchQueue.main.async {
            NotificationCenter.default.post(
                name: NSNotification.Name("QrCameraProSessionChanged"),
                object: nil
            )
        }
    }

    private func controlTorch(_ enabled: Bool) {
        guard let device = captureDevice else { return }
        if device.hasTorch {
            do {
                try device.lockForConfiguration()
                device.torchMode = enabled ? .on : .off
                device.unlockForConfiguration()
            } catch {
                sendError("Torch could not be used: \(error.localizedDescription)")
            }
        }
    }

    private func sendError(_ message: String) {
        // FIX 2: Guard with hasListeners so we don't emit into the void.
        guard hasListeners else { return }
        eventEmitter?.sendEvent(withName: "onCameraError", body: ["message": message])
    }

    public func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let barcodeData = object.stringValue else { return }

        let currentTime = Date()
        if let lastScan = lastScanTime,
           currentTime.timeIntervalSince(lastScan) < scanThrottleInterval { return }
        if barcodeData == lastScannedCode { return }

        lastScannedCode = barcodeData
        lastScanTime = currentTime

        // FIX 2: Only emit if a JS listener is actually registered.
        guard hasListeners else { return }

        let barcodeType = mapBarcodeType(object.type)
        eventEmitter?.sendEvent(
            withName: "onBarcodeScanned",
            body: ["data": barcodeData, "type": barcodeType]
        )
    }

    private func mapBarcodeType(_ type: AVMetadataObject.ObjectType) -> String {
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