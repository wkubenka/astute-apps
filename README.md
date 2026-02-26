# Astute Repo

Personal Android app repository and installer. A centralized hub for discovering, downloading, installing, and updating personal-use Android apps without relying on the Google Play Store.

## How It Works

1. Fetches a lightweight JSON manifest listing repos to include
2. For each repo, fetches the latest GitHub Release to get the current version, APK, and changelog
3. Compares available versions against locally installed versions
4. Shows install status (Not Installed, Up to Date, Update Available)
5. Downloads APKs and triggers the system installer on tap

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

## Adding Your App

The manifest is a lightweight registry — it only contains static metadata about each app. Version info, APK downloads, and changelogs are pulled automatically from your GitHub Releases.

### Prerequisites

Your app repo must:

1. **Use GitHub Releases** to publish versions. Tag each release with a semver tag (e.g. `v1.0.0`).
2. **Attach an APK** as a release asset (the `.apk` file).
3. **Host an app icon** (PNG) at a stable URL (e.g. the `xxxhdpi` launcher icon committed to the repo).

### Steps

1. **Add an entry** to the `apps` array in `manifest.json`:

```json
{
  "id": "com.astute.myapp",
  "name": "My App",
  "description": "Short description of the app.",
  "icon_url": "https://raw.githubusercontent.com/wkubenka/myapp/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
  "github": "wkubenka/myapp",
  "min_sdk": 26
}
```

2. **Commit and push** — the app will appear on the next pull-to-refresh.

That's it. When you publish a new GitHub Release with an APK asset, Astute Repo picks it up automatically — no manifest changes needed.

### Manifest Field Reference

| Field | Required | Description |
|-------|----------|-------------|
| `id` | Yes | Android package name (e.g. `com.astute.myapp`). Must be unique. |
| `name` | Yes | Display name shown in the app list. |
| `description` | Yes | Short description shown below the app name. |
| `icon_url` | Yes | URL to the app icon (PNG). |
| `github` | Yes | GitHub `owner/repo` path (e.g. `wkubenka/myapp`). Used to fetch releases. |
| `min_sdk` | No | Minimum Android SDK level required. Devices below this are warned. |

### What Gets Pulled from GitHub Releases

For each app, Astute Repo calls `GET https://api.github.com/repos/{owner}/{repo}/releases/latest` and extracts:

| Data | Source |
|------|--------|
| Version | `tag_name` (strips leading `v`) |
| APK download URL | First `.apk` asset's `browser_download_url` |
| Changelog | `body` (release notes) |

### Publishing Updates

Just create a new GitHub Release:

1. Tag it with a semver tag (e.g. `v1.1.0`)
2. Attach the APK as a release asset
3. Write release notes in the body (shown as the changelog)

Astute Repo compares the release tag against the installed `versionName` to show an "Update Available" badge. No manifest changes required.

### Manifest Schema

```json
{
  "apps": [
    {
      "id": "com.astute.myapp",
      "name": "My App",
      "description": "Short description of the app.",
      "icon_url": "https://raw.githubusercontent.com/wkubenka/myapp/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
      "github": "wkubenka/myapp",
      "min_sdk": 26
    }
  ]
}
```

## Project Structure

```
app/src/main/java/com/astute/repo/
├── AstuteRepoApp.kt              Hilt application
├── MainActivity.kt               Entry point
├── di/AppModule.kt                Hilt module (Retrofit, Room, OkHttp)
├── data/
│   ├── model/                     AppEntry, ManifestEntry, GitHubRelease
│   ├── local/                     Room DAO + Database
│   ├── remote/                    ManifestApi + GitHubApi
│   ├── repository/                AppRepository (manifest + releases + cache)
│   └── network/                   ConnectivityObserver
├── domain/                        VersionChecker, InstallManager
└── ui/
    ├── applist/                   App list screen + ViewModel
    ├── detail/                    App detail bottom sheet
    ├── components/                Shared UI components
    └── theme/                     Material 3 theme
```

## License

Private — personal use only.
