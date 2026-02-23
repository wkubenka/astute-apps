# Astute Repo

**Personal App Repository & Installer**

*Project Overview & Implementation Plan — Version 1.0 — February 2026*

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [System Architecture](#system-architecture)
3. [Technology Stack](#technology-stack)
4. [Feature Specification](#feature-specification)
5. [Implementation Plan](#implementation-plan)
6. [Project Structure](#project-structure)
7. [Hosting Strategy](#hosting-strategy)
8. [Security Considerations](#security-considerations)
9. [Estimated Timeline](#estimated-timeline)
10. [Success Criteria](#success-criteria)

---

## Executive Summary

Astute Repo is a personal Android application that serves as a private app repository and installer. It provides a centralized hub for discovering, downloading, installing, and updating a growing suite of personal-use Android applications, all without relying on the Google Play Store or any third-party distribution platform.

The app fetches a hosted JSON manifest describing all available applications, compares it against locally installed versions, and presents a clean interface for managing installations and updates. It is designed to be lightweight, maintainable, and extensible as the number of personal apps grows over time.

### Key Goals

- Provide a single entry point for installing and updating all personal Android apps
- Eliminate manual APK sideloading by automating download and install workflows
- Surface version mismatches so updates are never missed
- Establish a reusable project architecture and toolchain for all future Android apps
- Keep infrastructure costs at zero or near-zero using free-tier hosting

---

## System Architecture

### High-Level Overview

The system consists of three components that work together: a static file host serving the manifest and APK binaries, the Astute Repo client application running on Android, and the Android PackageManager which handles the actual installation.

| Component | Role | Technology |
|-----------|------|------------|
| **Static Host** | Serves manifest.json and APK files | GitHub Releases, S3, or any static host |
| **Astute Repo Client** | Fetches manifest, manages downloads, triggers installs | Kotlin, Jetpack Compose, Retrofit, Room |
| **Android OS** | Handles APK verification and installation | PackageManager, FileProvider, Intent system |

### Manifest Schema

The repository is driven by a single JSON manifest file hosted alongside the APK binaries. The manifest defines every available app and its current release metadata.

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Android application ID (e.g., `com.william.moneymanager`) |
| `name` | String | Human-readable app name |
| `version_code` | Integer | Incremental version code for comparison |
| `version_name` | String | Display version string (e.g., `1.2.0`) |
| `description` | String | Short description of the app's purpose |
| `apk_url` | String | Direct download URL for the APK file |
| `icon_url` | String | URL to the app's icon image |
| `changelog` | String? | Optional release notes for the current version |
| `min_sdk` | Integer? | Optional minimum Android SDK version required |

### Data Flow

1. **Fetch:** Astute Repo downloads manifest.json from the configured host URL on launch and on pull-to-refresh.
2. **Compare:** For each app in the manifest, query the local PackageManager to determine if the app is installed and its current version code.
3. **Display:** Render the app list with status badges: Not Installed, Up to Date, or Update Available.
4. **Action:** On user tap, download the APK to local storage and fire an ACTION_INSTALL_PACKAGE intent via FileProvider.
5. **Verify:** After returning from the installer, re-query PackageManager to confirm the install succeeded and refresh the UI.

---

## Technology Stack

| Layer | Choice | Rationale |
|-------|--------|-----------|
| **Language** | Kotlin | Modern Android standard, null safety, coroutines |
| **UI Framework** | Jetpack Compose | Declarative, less boilerplate, reusable across all apps |
| **Architecture** | MVVM + Repository Pattern | Clean separation, testable, recommended by Google |
| **Networking** | Retrofit + OkHttp | Industry standard, interceptors for logging/caching |
| **Local Storage** | Room Database | Cache manifest data for offline display |
| **Image Loading** | Coil | Kotlin-first, Compose-native, lightweight |
| **DI** | Hilt | Standard Android DI, reduces manual wiring |
| **Download Mgmt** | Android DownloadManager | System-level, handles retries and notifications |
| **Backend / Hosting** | GitHub Releases or S3 | Free, reliable, easy to automate via CI |

---

## Feature Specification

### Core Features (MVP)

#### App Catalog

- Scrollable list of all available apps with icon, name, version, and short description
- Status badges: Not Installed, Installed (Up to Date), Update Available
- Pull-to-refresh to re-fetch the manifest
- Search and filter by name or category (if categories are added to the manifest)

#### Install & Update Flow

- Tap to download APK with a progress indicator
- Automatic launch of the system installer via ACTION_INSTALL_PACKAGE intent
- Graceful handling of the "Install from unknown sources" permission, with a guided prompt to the settings page
- Post-install verification and UI refresh

#### Version Management

- Compare manifest `version_code` against PackageManager's installed `versionCode`
- Highlight apps with available updates with a visual badge or separate "Updates" tab
- Display changelogs when available

#### Offline Support

- Cache the last-fetched manifest in Room so the app list renders even without connectivity
- Show a banner or snackbar when the manifest is stale or the device is offline

### Future Enhancements

- Automatic background update checks via WorkManager with notification alerts
- One-tap "Update All" batch action
- App categories and grouping in the UI
- APK signature verification to ensure binaries haven't been tampered with
- Download history and rollback to previous versions
- Dark mode and Material You dynamic theming
- Self-update capability for Astute Repo itself

---

## Implementation Plan

The project is broken into five phases. Each phase results in a working increment that can be tested on-device.

### Phase 1: Project Scaffolding & Manifest (Days 1–2)

| Task | Details |
|------|---------|
| **Project Setup** | Create new Android project with Kotlin, Compose, Hilt, Retrofit, Room, Coil dependencies |
| **Define Manifest** | Design and finalize the JSON schema; create a sample manifest.json with 2–3 placeholder apps |
| **Host Manifest** | Push manifest.json and sample APKs to a GitHub repo's releases or an S3 bucket |
| **API Layer** | Create Retrofit interface for fetching manifest; write a Repository class with Room caching |

*Deliverable: App boots and fetches the manifest, logging the parsed data.*

### Phase 2: App List UI (Days 3–5)

| Task | Details |
|------|---------|
| **App List Screen** | LazyColumn displaying each app's icon, name, version, description, and status badge |
| **Version Checking** | Query PackageManager for installed apps; compare version codes; assign status labels |
| **Pull to Refresh** | Implement SwipeRefresh to re-fetch the manifest and recompute statuses |
| **App Detail Sheet** | Bottom sheet or detail screen showing full description, changelog, and action button |

*Deliverable: Functional UI showing app list with correct install statuses.*

### Phase 3: Download & Install Engine (Days 6–9)

| Task | Details |
|------|---------|
| **APK Download** | Use DownloadManager to fetch APKs with progress tracking exposed to the UI |
| **FileProvider Setup** | Configure FileProvider in AndroidManifest.xml and file_paths.xml for sharing APKs with the installer |
| **Install Intent** | Fire ACTION_INSTALL_PACKAGE intent with the FileProvider URI; handle the result callback |
| **Permission Flow** | Detect if "Install Unknown Apps" is not granted; show explanation dialog and navigate to settings |
| **Post-Install Refresh** | Listen for PACKAGE_ADDED broadcast or re-query PackageManager on resume to update the UI |

*Deliverable: Full install flow working end-to-end from tapping "Install" to the app appearing on the device.*

### Phase 4: Polish & Offline Support (Days 10–12)

| Task | Details |
|------|---------|
| **Offline Mode** | Serve cached manifest from Room when network is unavailable; show staleness banner |
| **Error Handling** | Graceful error states for network failures, download failures, and install cancellations |
| **Search** | Add a search bar to filter the app list by name |
| **UI Polish** | Loading skeletons, animations, Material 3 theming, adaptive icon support |

*Deliverable: Production-quality UI with resilient error handling.*

### Phase 5: Automation & Distribution (Days 13–14)

| Task | Details |
|------|---------|
| **CI Pipeline** | GitHub Actions workflow: build APK, bump version, update manifest.json, create release |
| **Manifest Script** | Script to auto-generate manifest.json from the release artifacts of all app repos |
| **Self-Distribution** | Include Astute Repo itself in the manifest for self-updating capability |

*Deliverable: Fully automated publish pipeline; push code → manifest updates automatically.*

---

## Project Structure

The recommended package layout follows standard Android conventions with MVVM architecture:

| Package / File | Purpose |
|----------------|---------|
| `data/remote/ManifestApi.kt` | Retrofit interface for fetching the manifest |
| `data/local/AppDao.kt` | Room DAO for cached app entries |
| `data/local/AppDatabase.kt` | Room database definition |
| `data/repository/AppRepository.kt` | Single source of truth; coordinates network + cache |
| `data/model/AppEntry.kt` | Data class matching the manifest schema |
| `domain/InstallManager.kt` | Download, FileProvider, and install intent logic |
| `domain/VersionChecker.kt` | PackageManager queries and comparison logic |
| `ui/applist/AppListScreen.kt` | Main screen Composable with LazyColumn |
| `ui/applist/AppListViewModel.kt` | ViewModel driving the app list state |
| `ui/detail/AppDetailSheet.kt` | Bottom sheet with full info and install button |
| `ui/theme/Theme.kt` | Material 3 theme and color definitions |
| `di/AppModule.kt` | Hilt module providing Retrofit, Room, etc. |

---

## Hosting Strategy

Two recommended options, both free for personal use:

### Option A: GitHub Releases (Recommended)

- Store manifest.json in a dedicated repository (e.g., `app-repo`)
- Upload APKs as release assets on each app's repository
- The manifest's `apk_url` points to the GitHub release download URL
- Free, version-controlled, and automatable via GitHub Actions
- No server to maintain; GitHub's CDN handles bandwidth

### Option B: AWS S3 Static Hosting

- Create an S3 bucket with public read access or pre-signed URLs
- Upload manifest.json and APKs to organized prefixes (e.g., `/apps/`, `/icons/`)
- Optionally front with CloudFront for caching and a custom domain
- Free tier covers 5 GB storage and 15 GB transfer per month, more than enough for personal use

---

## Security Considerations

Since this is a personal-use application, the threat model is limited, but a few safeguards are worth implementing from the start:

- **HTTPS Everywhere:** All manifest and APK URLs must use HTTPS to prevent man-in-the-middle tampering.
- **APK Signing:** Sign all APKs with a personal keystore. Android will reject updates to an app if the signing key changes, providing built-in tamper detection.
- **Manifest Integrity:** Optionally add a checksum field (SHA-256) to each manifest entry. Astute Repo can verify the downloaded APK's hash before triggering the install.
- **No Root Required:** The app uses only standard Android APIs and does not require root access. All installs go through the system's package installer with user confirmation.
- **Unknown Sources Scope:** Since Android 8.0, the "Install Unknown Apps" permission is per-app. Only Astute Repo needs this permission, not the entire device.

---

## Estimated Timeline

Based on approximately 2–3 hours of development per day:

| Phase | Duration | Cumulative |
|-------|----------|------------|
| Phase 1: Scaffolding & Manifest | 2 days | Day 2 |
| Phase 2: App List UI | 3 days | Day 5 |
| Phase 3: Download & Install Engine | 4 days | Day 9 |
| Phase 4: Polish & Offline Support | 3 days | Day 12 |
| Phase 5: Automation & Distribution | 2 days | Day 14 |

**Total estimated time:** Approximately 14 working days (2–3 weeks at part-time pace). A focused weekend sprint could compress Phases 1–3 into 3–4 days for a working MVP.

---

## Success Criteria

The project is considered complete when the following criteria are met:

1. Astute Repo can fetch a remote manifest and display all listed apps with correct install statuses
2. A user can install any listed app by tapping a single button (plus the system install confirmation)
3. A user can update any app when a newer version is available in the manifest
4. The app functions gracefully offline by displaying cached data
5. A new app can be added to the repository by updating the manifest and uploading an APK, with no changes to Astute Repo's code
6. The publish pipeline is automated: pushing a new release triggers manifest updates
