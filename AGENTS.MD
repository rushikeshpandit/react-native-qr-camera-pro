I wanted to build a npm module for react native app with name "react-native-qr-camera-pro" with main target audience is react native bar app but I will be happy to support expo as well.

It should use exact same dependencies as mentioned follows

"devDependencies": {
    "@commitlint/config-conventional": "^19.8.1",
    "@eslint/compat": "^1.3.2",
    "@eslint/eslintrc": "^3.3.1",
    "@eslint/js": "^9.35.0",
    "@react-native/babel-preset": "0.83.0",
    "@react-native/eslint-config": "0.83.0",
    "@release-it/conventional-changelog": "^10.0.1",
    "@types/jest": "^29.5.14",
    "@types/react": "^19.2.0",
    "commitlint": "^19.8.1",
    "del-cli": "^6.0.0",
    "eslint": "^9.35.0",
    "eslint-config-prettier": "^10.1.8",
    "eslint-plugin-prettier": "^5.5.4",
    "jest": "^29.7.0",
    "lefthook": "^2.0.3",
    "prettier": "^2.8.8",
    "react": "19.2.0",
    "react-native": "0.83.0",
    "react-native-builder-bob": "^0.40.13",
    "release-it": "^19.0.4",
    "turbo": "^2.5.6",
    "typescript": "^5.9.2"
  },

It should scan the qr code and returns the appropriate output.

I wanted to write it using native kotlin and swift/swiftui.

I dont want to use any extra dependency and want to implement all code using native codebase as much as possible.

I want you to build this module, use typescript, KISS and SOLID principles.

use proper naming conventions, follow strict coding standards, write comments before every method.

It should have 
1. Scan QR
2. Barcode support
3. Torch control
4. Scan overlay UI


Product Thinking (Before Architecture)
You’re not just building a module—you’re building something that should be:
Lightweight (no external deps)
Production-grade (camera lifecycle, permissions, threading handled)
Typed & ergonomic (clean TS API)
Bridge-efficient (minimal JS ↔ native overhead)

Native Layer

iOS (Swift)
Use: AVCaptureSession
Detect QR: AVMetadataObject.ObjectType.qr
Render camera preview: AVCaptureVideoPreviewLayer

Android (Kotlin)
Use: CameraX
QR detection: ImageAnalysis + ML Kit Barcode Scanner
Lifecycle aware camera binding

Bridge Strategy
Use TurboModule + Fabric (New Architecture)
Why:
Faster event communication
Future-proof (RN 0.76+)
Less bridge overhead

Native Module Responsibilities
Shared Responsibilities
Camera lifecycle
Permission handling
Frame processing
Dedup scanning (avoid duplicate scans)
Event emission


Key Technical Challenges (Solve These Well)
1. Camera Permission
iOS: NSCameraUsageDescription
Android: runtime permissions
2. Performance
Avoid scanning every frame
Use throttling (e.g., 300–500ms)
3. Duplicate Scan Prevention
Cache last scanned value
Ignore repeats for X ms
4. Lifecycle
Pause on background
Resume on foreground
5. Threading
iOS: DispatchQueue for metadata
Android: Executor for ImageAnalysis


Enable New Architecture
TurboModules
Fabric component

TypeScript Interface
Define strict types
Export public API
Optional Imperative API
1. startScanning()
2. stopScanning()
3. toggleTorch()

iOS Implementation
Setup AVCaptureSession
Add metadata output
Emit event via bridge

Android Implementation
Setup CameraX Preview + Analyzer
Android → QR detection requires ML Kit
ML Kit = Google dependency (recommended)
Integrate ML Kit Barcode scanning
Emit events

Create Native View Component
iOS: UIView
Android: ViewGroup

Bridge to React Native
Fabric component binding
Event emitter

Build React Wrapper
<QRScannerView />
Props → native props

Example App
Scan QR → show result

Coding Standards
1. Strict TypeScript (strict: true)
2. SwiftLint-style formatting
3. Kotlin clean architecture
4. JSDoc before every function
5. No magic values (constants only)
6. Separation of concerns




I want proper readme.md file with proper documentation, a short description which I can fill into npmjs



use following keywords in package.json

{
  "keywords": [
    "react-native",
    "react-native-qr-scanner",
    "qr-scanner",
    "qr-code",
    "barcode-scanner",
    "react-native-camera",
    "qr-reader",
    "camera-x",
    "mlkit",
    "avfoundation",
    "react-native-fabric",
    "react-native-turbo-module",
    "react-native-new-architecture"
  ]
}

also create a doc folder under which there is separate .md file for architecture and performance.

Make sure that there should not be any em-dashes or any other emoji's in any of the .md file

I will be using yarn for this, I want example app with well written code and output. Use custom hooks wherever possible also

This module will be open source, MIT licensed and PR's are welcome.
Github repo link : https://github.com/rushikeshpandit/react-native-qr-camera-pro.git

I think you can add following in readme.md
# Features

-   **Fast** -- Native implementation (Swift + Kotlin + C/C++)
-   **Cross‑platform** -- Works on both Android and iOS
-   **New Architecture Ready** -- Supports Fabric & TurboModules
-   **Zero Dependencies**
-   **MIT Licensed**

It should include installation steps, how to use module, API reference, use case and
# Contributing

PRs are welcome!

1.  Fork the repo
2.  Create a feature branch
3.  Commit changes
4.  Open a pull request

------------------------------------------------------------------------

# License

MIT © Rushikesh Pandit

Built with ❤️ for the React Native community.




--------------


Give me a ready to download files along with their paths and should be working as specified above. I want all the code to be written, not just prototype. This code is getting reviewed by codex and my seniors and if this does not work, then I might be in trouble.