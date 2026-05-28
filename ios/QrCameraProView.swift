import Foundation
import React
import AVFoundation

@objc(QrCameraProViewManager)
class QrCameraProViewManager: RCTViewManager {
    override static func requiresMainQueueSetup() -> Bool {
        return true
    }

    override func view() -> UIView! {
        return QrCameraProView()
    }
}

class QrCameraProView: UIView {
    var previewLayer: AVCaptureVideoPreviewLayer?
    var captureSession: AVCaptureSession? {
        didSet {
            updatePreviewLayer()
        }
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
        
        // Listen for session changes
        NotificationCenter.default.addObserver(self, selector: #selector(sessionChanged(_:)), name: NSNotification.Name("QrCameraProSessionChanged"), object: nil)
        
        // Initial session if already started
        self.captureSession = QrCameraProSwift.shared.getCaptureSession()
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        previewLayer?.frame = bounds
    }

    @objc func sessionChanged(_ notification: Notification) {
        DispatchQueue.main.async {
            self.captureSession = notification.object as? AVCaptureSession
        }
    }
    
    private func updatePreviewLayer() {
        if let currentLayer = previewLayer {
            currentLayer.removeFromSuperlayer()
            previewLayer = nil
        }
        
        if let session = captureSession {
            let layer = AVCaptureVideoPreviewLayer(session: session)
            layer.videoGravity = .resizeAspectFill
            layer.frame = bounds
            self.layer.addSublayer(layer)
            self.previewLayer = layer
        }
    }
}
