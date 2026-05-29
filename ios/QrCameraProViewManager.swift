// QrCameraProViewManager.swift
// RCTViewManager that vends QrCameraProView instances to React Native.
//
// Separated from QrCameraProView.swift to respect the Single Responsibility
// Principle: the manager's only job is to create views for the React tree.
// All rendering logic lives in QrCameraProView.

import Foundation
import React

// MARK: - QrCameraProViewManager

/// React Native view manager for the `QrCameraProView` native component.
///
/// Registered as `QrCameraProViewManager` so the Obj-C shim in
/// `QrCameraProViewManager.m` can reference it via `RCT_EXTERN_MODULE`.
@objc(QrCameraProViewManager)
final class QrCameraProViewManager: RCTViewManager {

    // MARK: - RCTViewManager Overrides

    /// Declares that view creation must happen on the main queue,
    /// which is required for all UIKit view instantiation.
    override static func requiresMainQueueSetup() -> Bool {
        return true
    }

    /// Creates and returns a new `QrCameraProView` for React Native to mount.
    override func view() -> UIView! {
        return QrCameraProView()
    }
}