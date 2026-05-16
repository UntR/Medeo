# medeo

[简体中文](README.md)

medeo is a personal Android media discovery and playback app. It provides a Douban-style hot list, multi-source search, detail pages with source/episode switching, favorites, watch progress, and Media3/ExoPlayer playback.

The project is built for self-use sideload distribution. It is not intended for app-store release.

## Current Version

- App name: `medeo`
- Release version: `n0.1`
- Android application id: `com.czpn7.ying`
- Minimum SDK: 24
- Target/compile SDK: 36

The package id is intentionally kept as `com.czpn7.ying` for install/upgrade compatibility after the product rename.

## Features

- Douban-style ranked home feed from a public hot-list endpoint.
- Multi-source Apple CMS V10 search with per-source failure isolation.
- Detail page with poster, summary, source tabs, line tabs, episode list, favorite toggle, and continue watching.
- Media3/ExoPlayer playback with HLS support, cache, progress saving, source/line/episode switching, landscape immersive mode, playback speed controls, and long-press temporary 2x speed.
- Day/Night theme switch. Night uses a Tokyo Night-inspired palette.
- Settings for source toggles, real cache usage, cache clearing, and Wi-Fi-only autoplay.
- First-launch disclaimer dialog.

## Legal And Content Boundary

medeo does not include, store, upload, or distribute video content. Search results and playback URLs come from data sources enabled by the user.

Use this project only for technical learning, personal research, and lawful personal use. Make sure you have the necessary authorization before accessing any media content, and follow copyright laws and local regulations.

The app must not add download, export, save-to-gallery, or video-sharing features.

## Tech Stack

- Kotlin
- Jetpack Compose + Material 3
- Hilt
- Retrofit + OkHttp + Moshi
- Room
- DataStore
- Coil
- AndroidX Media3 / ExoPlayer
- Gradle 8.13 wrapper

## Project Layout

```text
app/
  src/main/java/com/czpn7/ying/
    data/          API DTOs, repositories, Room/DataStore models
    di/            Hilt modules
    player/        Media3 player screen and cache provider
    ui/            Compose screens, components, theme
    MainActivity.kt
docs/
  medeo-logo-preview.svg
gradle/
  wrapper/
```

## Development Setup

1. Install Android Studio with JDK 17 support.
2. Install Android SDK 36.
3. Open the project root in Android Studio.
4. Let Gradle sync with the checked-in wrapper.

Useful commands:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Release Build

Release signing reads either `keystore.properties` or environment variables.

Recommended local file:

```properties
storeFile=keystore/medeo-release.jks
storePassword=change-me
keyAlias=medeo
keyPassword=change-me
```

`keystore.properties` and real keystore files are ignored by Git. Use `keystore.properties.example` as a template.

Environment variable alternatives:

```bash
export MEDEO_RELEASE_STORE_FILE=keystore/medeo-release.jks
export MEDEO_RELEASE_STORE_PASSWORD=change-me
export MEDEO_RELEASE_KEY_ALIAS=medeo
export MEDEO_RELEASE_KEY_PASSWORD=change-me
```

Build:

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

Release APK output:

```text
app/build/outputs/apk/release/app-release.apk
```

## Safety Checks Before Sharing

Before pushing to GitHub, confirm these files are not tracked:

- `keystore.properties`
- `keystore/*.jks`
- `local.properties`
- `app/build/`
- any generated `.apk` or `.aab`

Before distributing an APK, run the basic verification path:

1. Launch the app and accept the disclaimer.
2. Confirm the home feed loads with posters.
3. Search for a title and open a detail page.
4. Play at least one episode.
5. Confirm favorites and continue watching work.
6. Confirm cache clearing works from Settings.
7. Confirm no video files are written to external storage.

## Acknowledgements

Thanks to the following open-source projects for product, UX, and media-app ecosystem inspiration:

- [LibreTV](https://github.com/LibreSpark/LibreTV)
- [OrionTV](https://github.com/orion-lib/OrionTV)
- [LunaTV](https://github.com/MoonTechLab/LunaTV)
- [Kazumi](https://github.com/Predidit/Kazumi)

medeo is an independent Android implementation. This acknowledgement does not imply that code from these projects is bundled in this repository.

## License

No open-source license has been selected yet. Until a license is added, all rights are reserved by default.
