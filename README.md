# AList Android

A native Android app for [AList](https://github.com/AlistGo/alist) — runs the AList server locally and provides a built-in WebView for the web UI.

## Features

- **Auto-start AList** on app launch
- **Foreground service** keeps AList running in background
- **WebView** with full support: file upload/download, JavaScript, cookies, video playback, dark mode
- **Boot receiver** — auto-restart on device reboot
- **Material 3 dark theme** — clean, minimal UI
- **ARM64 native** — built for modern Android devices

## Download

Download the latest APK from [Releases](../../releases).

## Build from Source

### Prerequisites

- JDK 17
- Android SDK (API 26+)
- Gradle 8.5+ (wrapper included)

### Setup

1. Clone the repo
2. Copy `local.properties.template` to `local.properties` and set `sdk.dir`
3. Place the AList binary at `app/src/main/assets/alist/alist`
   - Download from https://github.com/AlistGo/alist/releases (use `alist-android-arm64.tar.gz` → extract the `alist` binary)
4. Build:
   ```bash
   ./gradlew assembleDebug   # Debug build
   ./gradlew assembleRelease # Release build (needs signing config)
   ```

### First Build Note

The Gradle wrapper jar (`gradle-wrapper.jar`) must exist at `gradle/wrapper/gradle-wrapper.jar`. If missing, generate it:
```bash
gradle wrapper --gradle-version 8.5
```
Or copy it from any existing Gradle project / Android Studio project.

## GitHub Actions — Auto Release

The project includes a GitHub Actions workflow (`.github/workflows/build-release.yml`) that:

1. Triggers on push to `main` or manual dispatch
2. Builds the Release APK with signing
3. Creates a GitHub Release with the APK attached

### Required Secrets

Set these in your repo → Settings → Secrets and variables → Actions:

| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded `.jks` keystore file (`base64 -w0 your.jks`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias name |
| `KEY_PASSWORD` | Key password |

### Generate Signing Keystore

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias alist
```

Then encode:
```bash
base64 -w0 release.jks
```

Paste the output as `KEYSTORE_BASE64` secret.

## Architecture

```
io.alist.app
├── alist/
│   ├── AListManager.kt          # Binary extraction, download, process lifecycle
│   ├── AListForegroundService.kt # Foreground service with notification
│   └── BootReceiver.kt          # Auto-start on boot
├── ui/
│   ├── MainActivity.kt          # Main screen with status + controls
│   └── WebViewActivity.kt       # Full-featured WebView for AList Web UI
└── res/
    ├── layout/                   # XML layouts (ViewBinding)
    ├── values/                   # Colors, strings, themes (Material 3 dark)
    └── assets/alist/             # AList binary goes here (not in repo)
```

## License

This project is not affiliated with the AList project. AList is licensed under AGPL-3.0.
