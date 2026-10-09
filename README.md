# Harish Video Player

[![CI](https://github.com/HarishKumarKG/video_player_android/actions/workflows/ci.yml/badge.svg)](https://github.com/HarishKumarKG/video_player_android/actions/workflows/ci.yml)

A clean, fast video player for Android, built with Kotlin, Jetpack Compose and Media3 ExoPlayer.
It's the sister app of [Harish Music Player](https://github.com/HarishKumarKG/music_player_android) and shares its look: the H logo, light and dark themes, and the profile menu.

## Features

### Library

- **Folder browser**: start at Internal storage (or pick an SD card) and tap through folders like a file manager. Only folders that contain videos appear, so you never open an empty one.
- Path bar at the top (Internal storage › DCIM › Camera). Tap any step to jump back; the Back button goes up one level.
- Each video shows a thumbnail, duration, quality (720p / 1080p / 4K) and size.
- **Sort**: date modified, name A–Z / Z–A, longest, largest. Folders follow the date and name sorts.
- **Search** by file or folder name. Case and accents don't matter, and words can be in any order.
- **Recently played**: every video you stopped halfway, newest first, with how much is left. Finished videos drop off by themselves. There's a strip on the first screen and a full list in the menu, where you can remove one video or clear all.
- A red "watched so far" bar on each thumbnail.
- Instant start: the list is cached, then refreshed quietly in the background. There's also a refresh button.
- Video info: resolution, size, type, date and location.

### Player

- Big play button, ⏪ 10s / ⏩ 10s, previous / next video and a seek bar.
- **Gestures**:
  - Double-tap left or right to jump 10s. Keep tapping to add more.
  - Swipe up or down on the left side for brightness, on the right side for volume.
  - Swipe sideways to scrub through the video.
- **Lock** the screen so pocket touches do nothing.
- **Aspect ratio**: Fit, Stretch or Zoom.
- **Rotate** button. Wide videos open in landscape automatically.
- **Playback speed** from 0.25x to 2x.
- **Subtitles**: pick embedded tracks (e.g. MKV languages) or load your own `.srt`, `.vtt` or `.ass` file.
- **Audio track** picker for videos with several languages.
- **Resume**: every video continues where you stopped, with a "Start over" button. Up to 2,000 videos are remembered.

### Background and picture-in-picture

- **Picture-in-picture**: press Home and the video keeps playing in a small floating window with ⏪ ⏯ ⏩ buttons.
- **Background audio** (optional): keep listening with the screen off. The video track is switched off to save battery.
- Media notification with thumbnail, controls and a draggable seek bar. Headset and Bluetooth buttons work too.

### Settings

Everything below is on by default. Turn off whatever you don't want from **Menu → Settings**:

| Setting | Default | What it does |
| --- | --- | --- |
| Resume where I left off | On | Each video starts from the spot you stopped at |
| Show recently played | On | The half-watched strip on the first screen |
| Auto picture-in-picture | On | Home button shrinks the playing video into a floating window |
| Background audio | Off | Sound keeps playing when you leave the app |

### Look

- Light, dark and system themes, plus a quick sun / moon toggle.
- Animated splash with the H logo and a red play button.
- Burger menu with a profile card and a photo slideshow.

## Tech stack

| Area | Library |
| --- | --- |
| Language | Kotlin 2.4 (AGP 9 built-in Kotlin) |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Video | Media3 ExoPlayer, PlayerView, MediaSessionService |
| DI | Hilt (KSP) |
| Async | Kotlin Coroutines and Flow |
| Build | Gradle 9.8, AGP 9.4, compileSdk / targetSdk 37, minSdk 30 |

## Architecture

The app uses clean architecture with four layers:

```
app/src/main/java/com/harish/videoplayer
├── domain/     Pure Kotlin rules with no Android code (easy to unit test)
│   ├── model/      Video, sorting, formatting, player settings
│   ├── library/    Folder browsing, search index
│   ├── resume/     When to remember a position
│   └── subtitle/   Subtitle file types
├── data/       Where data comes from and is saved
│   ├── video/      MediaStore + JSON cache
│   ├── resume/     Saved positions (resume.json)
│   └── settings/   Theme and player switches
├── playback/   The one shared ExoPlayer, queue, tracks, media session service
└── ui/         Compose screens
    ├── home/       Folder browser, search, rows
    ├── recent/     Recently played list
    ├── settings/   Settings switches
    ├── player/     Player screen, controls, gestures, PiP, sheets
    ├── navigation/ Nav host and drawer
    └── theme/, brand/, splash/, themepicker/, permission/, common/
```

**How playback flows, step by step:**

1. You tap a video in a list. `HomeViewModel.play(list, index)` hands the whole list to `PlaybackController`.
2. `PlaybackController` loads the list into the single ExoPlayer, jumps to the saved position, and connects to `PlaybackService`. Connecting is what shows the notification.
3. The player screen draws the picture with `PlayerView` and puts Compose controls on top.
4. Every few seconds, and on pause, the position is saved through `ResumeRepository`.

## Getting started

```bash
git clone git@github.com:HarishKumarKG/video_player_android.git
```

Open the folder in Android Studio, let Gradle sync, then press **Run** on a phone running Android 11 or newer.

Run the unit tests:

```bash
./gradlew testDebugUnitTest
```

## Continuous integration and releases

- **CI** (`.github/workflows/ci.yml`): every push and pull request to `main` runs the tests and builds a debug APK. You can download the APK from the run page.
- **Release** (`.github/workflows/release.yml`): push a tag such as `v1.0.0` to get a signed APK and AAB attached to a GitHub Release.

The release workflow needs four repository secrets:

| Secret | What it is |
| --- | --- |
| `KEYSTORE_BASE64` | Your `.jks` file, base64-encoded (`base64 -i release.jks \| pbcopy` on a Mac) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

## Permissions

| Permission | Why |
| --- | --- |
| `READ_MEDIA_VIDEO` (Android 13+) / `READ_EXTERNAL_STORAGE` (12 and below) | List and play your videos |
| `POST_NOTIFICATIONS` | Media notification during background audio |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` | Keep background audio alive with the screen off |

## Data stored on the phone

All of it is in the app's private storage, and nothing leaves the phone:

- `videos_cache.json`: the last video list, for an instant start.
- `resume.json`: where you stopped each video (up to the 2,000 most recent).
- Shared preferences: theme and the Settings switches.

## Roadmap

- Remember chosen subtitle files per video
- Playlists and favourites
- Equalizer and audio boost
- Network streams (URL / SMB)

## Author

**Harish**, mobile app developer (Android, iOS, Flutter, KMP, .NET MAUI). [github.com/HarishKumarKG](https://github.com/HarishKumarKG)
