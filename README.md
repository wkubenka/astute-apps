# Astute Repo

Personal Android app repository and installer. A centralized hub for discovering, downloading, installing, and updating personal-use Android apps without relying on the Google Play Store.

## How It Works

1. Fetches a hosted JSON manifest listing all available apps
2. Compares manifest versions against locally installed versions
3. Shows install status (Not Installed, Up to Date, Update Available)
4. Downloads APKs and triggers the system installer on tap

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Repository |
| Networking | Retrofit 3 + OkHttp 5 |
| Local Storage | Room |
| Image Loading | Coil 3 |
| DI | Hilt |
| Serialization | kotlinx.serialization |

## Building

Requires JDK 17 and the Android SDK (API 36).

```bash
./gradlew assembleDebug
```

The debug APK is output to `app/build/outputs/apk/debug/app-debug.apk`.

## Configuration

The app manifest lives at `manifest.json` in the root of this repo. The manifest URL in `app/build.gradle.kts` (`BuildConfig.MANIFEST_URL`) points to its raw GitHub URL.

## Manifest Schema

The app is driven by a single JSON manifest:

```json
{
  "apps": [
    {
      "id": "com.astute.myapp",
      "name": "My App",
      "version_code": 1,
      "version_name": "1.0.0",
      "description": "Short description of the app.",
      "apk_url": "https://example.com/myapp-1.0.0.apk",
      "icon_url": "https://example.com/myapp-icon.png",
      "changelog": "Initial release.",
      "min_sdk": 26
    }
  ]
}
```

## Adding Apps

Only first-party apps under the `com.astute.*` namespace are included. No code changes are needed — just update `manifest.json` at the root of this repo and make the APK available for download.

### Steps

1. **Build a release APK** and host it at a stable URL (e.g. a GitHub release asset).
2. **Host an app icon** (PNG, ideally the `xxxhdpi` launcher icon) at a public URL.
3. **Add an entry** to the `apps` array in `manifest.json`:

```json
{
  "id": "com.astute.myapp",
  "name": "My App",
  "version_code": 1,
  "version_name": "1.0.0",
  "description": "Short description of the app.",
  "apk_url": "https://github.com/wkubenka/myapp/releases/download/v1.0.0/myapp-1.0.0.apk",
  "icon_url": "https://raw.githubusercontent.com/wkubenka/myapp/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
  "changelog": "Initial release.",
  "min_sdk": 26
}
```

4. **Commit and push** — the app will appear on the next pull-to-refresh.

### Field Reference

| Field | Required | Description |
|-------|----------|-------------|
| `id` | Yes | Android package name under `com.astute.*`. Must be unique across the manifest. |
| `name` | Yes | Display name shown in the app list. |
| `version_code` | Yes | Integer version code. Used to compare against the installed version. |
| `version_name` | Yes | Human-readable version string (e.g. `1.0.0`). |
| `description` | Yes | Short description shown below the app name. |
| `apk_url` | Yes | Direct download URL for the APK file. |
| `icon_url` | Yes | URL to the app icon (PNG). |
| `changelog` | No | What changed in this version. |
| `min_sdk` | No | Minimum Android SDK level required to run the app. |

### Updating an Existing App

To publish a new version, update the entry's `version_code`, `version_name`, `apk_url`, and optionally `changelog`. Astute Repo compares `version_code` against the installed version to show an "Update Available" badge.

## Project Structure

```
app/src/main/java/com/william/astuterepo/
├── AstuteRepoApp.kt              Hilt application
├── MainActivity.kt               Entry point
├── di/AppModule.kt                Hilt module (Retrofit, Room, OkHttp)
├── data/
│   ├── model/                     AppEntry, ManifestResponse
│   ├── local/                     Room DAO + Database
│   ├── remote/                    Retrofit API interface
│   └── repository/                AppRepository (network + cache)
└── ui/
    ├── applist/                   App list screen + ViewModel
    └── theme/                     Material 3 theme
```

## License

Private — personal use only.
