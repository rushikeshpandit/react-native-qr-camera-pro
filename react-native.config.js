module.exports = {
  dependencies: {
    'react-native-qr-camera-pro': {
      platforms: {
        ios: {
          componentName: 'QrCameraProView', // The name used in requireNativeComponent
          viewManager: './ios/QrCameraProViewManager', // Path to the Swift file containing the Manager
          // podspecPath: './QrCameraPro.podspec', // This might be optional if in root
        },
        android: {
          // Potentially configure Android if needed, though often handled by `react-native-builder-bob` or Gradle.
        },
      },
    },
  },
};
