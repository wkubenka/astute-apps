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

The manifest URL is set in `app/build.gradle.kts` via `BuildConfig.MANIFEST_URL`. Update it to point to your hosted `manifest.json`.

## Manifest Schema

The app is driven by a single JSON manifest:

```json
{
  "apps": [
    {
      "id": "com.example.myapp",
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
