# Harish — Video Player for Android

A modern, offline video player (with casting to your TV) for Android built with **Kotlin**, **Jetpack Compose** and **Media3 ExoPlayer**.
It plays the videos already on your phone, with a folder browser, resume-where-you-left-off,
recently played, gestures, subtitles, picture-in-picture, background audio and polished light & dark themes.

![CI](https://github.com/HarishKumarKG/video_player_android/actions/workflows/ci.yml/badge.svg)
![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.09.00-4285F4?logo=jetpackcompose&logoColor=white)
![Media3](https://img.shields.io/badge/Media3-1.9.0-E63946)
![minSdk](https://img.shields.io/badge/minSdk-30-blue)
![targetSdk](https://img.shields.io/badge/targetSdk-37-blue)

---

## Features

### Library
- **Folder browser** – file-manager style navigation through internal storage and SD card,
  showing only folders that contain videos
- **Path bar** – *Internal storage › DCIM › Camera*; tap any step to jump back, Back goes up one level
- **Video rows** – thumbnail, duration, quality (720p / 1080p / 4K), size and a red *watched so far* bar
- **Sort** – *date modified*, *name (A–Z / Z–A)*, *longest* or *largest*
- **Search** – live results as you type, across file and folder names
  (case- and accent-insensitive, words in any order)
- **Recently played** – your last 10 half-watched videos, newest first, with time left;
  finished videos drop off automatically. Strip on the first screen + full list in the menu
  (remove one or clear all)
- **Video info** – resolution, size, type, date and location
- **Rename** – ⋮ → *Rename* changes the real file name on the phone (extension kept; Android asks once for files made by other apps). Resume spots carry over
- **Rename folders** – ⋮ on any folder → *Rename folder*. Needs *All files access*, switched on once (the app opens that setting for you)
- **Fast start-up** – the video list is cached and shown instantly, then refreshed in the background
- Pull new downloads in any time with the **refresh** button

### Playback
- **Resumes where you left off** – every video continues from its last position
  (up to 2,000 remembered), with a *Start over* button
- **Play queue** that follows the folder you played from – ⏮ / ⏭ move through it
- **Controls** – ⏪10s ⏯ ⏩10s, seek bar, playback speed (0.25x – 2x)
- **Gestures**
  - Double-tap left / right to skip 10s (keep tapping to add more)
  - Swipe up / down: left side = brightness, right side = volume — keep swiping past 100% for a **boost up to 200%** (like VLC)
  - Swipe sideways to scrub through the video
- **Lock** – pocket-proof screen lock
- **Aspect ratio** – Fit · Stretch · Zoom
- **Rotation** – follows how you hold the phone; the rotate button locks landscape or portrait (tap again for auto-rotate)
- **Lock rotation** – one tap keeps the video the way you're holding it (great on a bus or in bed); tap again to follow the phone
- **Subtitles** – embedded tracks (e.g. MKV) or load your own `.srt` / `.vtt` / `.ass` file
- **Audio tracks** – switch language for multi-audio videos; each track shows its format and channels (e.g. *Tamil · Dolby Digital · 5.1*)
- **Remembers your audio choice** – each video reopens with the track you picked, and new videos start in your last-picked language (reset in Settings)
- **Clear voices for 5.1 / 7.1** – surround movies are mixed down to stereo with the dialogue channel at full strength, so voices don't go quiet on phone speakers or earphones (switch in Settings)
- **Plays almost any audio** – FFmpeg decoders handle Dolby Digital (AC-3 / E-AC-3), DTS, TrueHD, FLAC, Opus and more, even where the phone has no decoder of its own

### Cast to TV
- **Cast button** in the player finds every TV on your Wi-Fi: **Chromecast / Google TV / Android TV** and **DLNA smart TVs** (Samsung, LG, Sony, Panasonic…)
- **Original quality** – the TV streams the untouched file from the phone, so **4K, 8K, HDR, HEVC, AV1, Dolby audio** play exactly as recorded (whatever the TV can decode). Nothing is re-encoded
- Starts on the TV **where you were** on the phone; **Stop casting** brings it back to the phone at the TV's spot
- **Phone as remote** – play/pause, ±10 s, seek bar, previous/next in the folder, TV volume
- Keeps playing with the **phone locked** (notification with *Stop casting*); the position is saved to *Recently played*
- Built-in mini web server with **range requests** (fast seeking), random private links per video

### Picture-in-picture, background & notification
- **Picture-in-picture** – press Home and the video keeps playing in a floating window with ⏪ ⏯ ⏩
- **Background audio** (on by default) – lock the phone and the sound keeps playing, with play / pause / skip and a seek bar on the lock screen; the video track is switched off to save battery and comes back when you unlock
- Media notification with **thumbnail, ⏮ ⏯ ⏭** and a draggable progress bar
- **MediaSession**: lock-screen controls, Bluetooth / headset buttons, Android media panel
- **Audio focus** – pauses for calls and other apps, resumes after short interruptions
- **Pauses when headphones are unplugged** or Bluetooth disconnects

### Settings
| Setting | Default | What it does |
|---|---|---|
| Resume where I left off | On | Each video starts from the spot you stopped at |
| Show recently played | On | Half-watched strip on the first screen |
| Auto picture-in-picture | On | Home shrinks the playing video into a floating window |
| Clear voices (5.1 / 7.1) | On | Dialogue stays loud when surround audio plays on 2 speakers |
| Background audio | On | Sound keeps playing when you lock the phone or leave the app |

### Look & feel
- **Light, Dark and System** themes, with a quick ☀️/🌙 toggle in the header
- Animated **splash screen** and animated **"H" logo** with a red play button
- Side menu with a profile card (rotating ring, photo slideshow)
- Edge-to-edge layout, immersive full-screen player

---

## Tech stack

| Area | Library / tool |
|---|---|
| Language | Kotlin 2.4.20 |
| UI | Jetpack Compose (BOM 2026.09.00), Material 3 |
| Navigation | Navigation Compose 2.10.2 |
| Dependency injection | Hilt 2.60.1 (KSP 2.3.11), androidx.hilt 1.4.0 |
| Async | Kotlin Coroutines & Flow (`StateFlow`, `SharedFlow`) |
| Lifecycle | AndroidX Lifecycle 2.11.0 (`collectAsStateWithLifecycle`) |
| Media | Media3 1.9.0 – `ExoPlayer`, `PlayerView`, `MediaSessionService`; Jellyfin `media3-ffmpeg-decoder` for audio |
| Storage | `MediaStore` (videos), JSON files in app storage (cache, resume positions), `SharedPreferences` (theme, settings) |
| Build | Gradle 9.8.0, Android Gradle Plugin 9.4.0 (built-in Kotlin), version catalog |
| Tests | JUnit 4 |

**SDK:** `minSdk 30` (Android 11) · `targetSdk / compileSdk 37` · Java 17

---

## Architecture

Layered **clean architecture** with unidirectional data flow:
`ui` → `domain` ← `data`, and a separate `playback` layer for the player and session service.

```
com.harish.videoplayer
├── VideoPlayerApp.kt            Application (Hilt)
├── MainActivity.kt              Theme, splash, navigation host
├── domain/                      Pure Kotlin – no Android UI
│   ├── model/                   Video, VideoSortOrder, formatting, PlayerSettings, ThemeMode
│   ├── library/                 Folder browser tree, breadcrumbs, video search
│   ├── resume/                  Resume rules, recently played
│   └── subtitle/                Subtitle file types
├── data/
│   ├── video/                   VideoRepository (MediaStore + JSON cache)
│   ├── resume/                  ResumeRepository (saved positions)
│   └── settings/                ThemeRepository, PlayerSettingsRepository
├── playback/
│   ├── PlayerHolder.kt          The single shared ExoPlayer
│   ├── PlaybackController.kt    Single entry point: queue, resume, tracks, subtitles, background mode
│   ├── PlaybackService.kt       MediaSessionService: notification, lock screen, Bluetooth
│   └── VideoThumbnailBitmapLoader.kt  Video frames for the notification
├── cast/
│   ├── CastManager.kt           Search, cast, remote control, follows the TV every second
│   ├── CastServer.kt            Tiny HTTP server: streams the original file (with Range support)
│   ├── CastService.kt           Foreground service: keeps casting alive with the screen off
│   ├── dlna/                    SSDP discovery + UPnP AVTransport / RenderingControl (smart TVs)
│   └── googlecast/              Chromecast / Google TV via Google's Default Media Receiver
└── ui/
    ├── home/                    Folder browser, header, search, rows, ViewModel
    ├── recent/                  Recently played list
    ├── settings/                Settings screen
    ├── player/                  Player screen, controls, gestures, PiP, sheets, system UI
    ├── navigation/              Destinations, nav host, side menu
    ├── themepicker/             Theme screen
    ├── permission/              Media/notification permission screen
    ├── common/                  Thumbnail loading & caching, modifiers
    └── brand/ splash/ theme/    Logo, colours, typography, backgrounds
```

**How playback flows**

```
UI ──PlaybackController──▶ ExoPlayer (PlayerHolder) ──▶ PlayerView + Compose controls
              │                     │
              │                     └──▶ PlaybackService / MediaSession (notification, lock screen, Bluetooth)
              └──▶ ResumeRepository (position saved every few seconds and on pause)
```

---

## Getting started

### Requirements
- **Android Studio** (latest stable) with **Android SDK Platform 37**
- **JDK 17+** (Android Studio's bundled JDK works)
- A device or emulator running **Android 11 (API 30) or newer** with some videos on it

### Build & run
```bash
git clone https://github.com/HarishKumarKG/video_player_android.git
cd video_player_android
./gradlew assembleDebug          # or press ▶ Run in Android Studio
```

Install on a connected device:
```bash
./gradlew installDebug
```

### Run the unit tests
```bash
./gradlew testDebugUnitTest
```
Tests cover sorting, search, the folder browser and breadcrumbs, resume rules, recently played and formatting.

---

## Continuous integration

GitHub Actions workflows in `.github/workflows/`:

| Workflow | Runs when | What it does |
|---|---|---|
| `ci.yml` | Push or pull request to `main` | Runs unit tests, builds a debug APK (downloadable from the run's *Artifacts*) |
| `release.yml` | A `v*` tag is pushed / a GitHub Release is published | Runs tests, builds a **signed** APK + AAB and attaches them to the release |

Release signing uses four repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Permissions

| Permission | Why |
|---|---|
| `READ_MEDIA_VIDEO` (Android 13+) / `READ_EXTERNAL_STORAGE` (Android 11–12) | Read the videos on the device |
| `POST_NOTIFICATIONS` | Show the playback notification (Android 13+) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playing in the background |
| `WAKE_LOCK` | Keep background audio / casting going with the screen off |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Casting: the TV downloads the video from the phone over your Wi-Fi |
| `CHANGE_WIFI_MULTICAST_STATE` | Casting: find DLNA TVs (SSDP) |
| `ACCESS_LOCAL_NETWORK` (Android 17+) | Casting: talk to devices on your network (asked the first time you tap Cast) |
| `MANAGE_EXTERNAL_STORAGE` ("All files access") | Rename folders. Asked only when you first rename a folder |

The app works fully offline. The network is only used for casting, and only inside your own Wi-Fi — nothing is sent to the internet.

---

## Data stored on the device

All files live in the app's private storage and are removed when the app is uninstalled.

| File | Contents |
|---|---|
| `videos_cache.json` | Last scanned video list (instant start-up) |
| `resume.json` | Where you stopped each video (up to 2,000) |
| `settings` (SharedPreferences) | Selected theme |
| `player_settings` (SharedPreferences) | Resume, recently played, picture-in-picture, background audio |
| `audio_choices` (SharedPreferences) | Audio track picked per video + preferred language |

---

## Tips

**Background audio stops on some phones?**
Some manufacturers (Xiaomi, Oppo, Vivo, OnePlus, Samsung…) add extra battery savers.
Allow the app to run in the background (e.g. *Battery → No restrictions / Unrestricted*) –
see [dontkillmyapp.com](https://dontkillmyapp.com) for step-by-step guides.

**Subtitles:** keep your `.srt` file next to the video, then use *Subtitles → Load subtitle file…* in the player.

**Closing the floating window** (✕) always pauses the video, even with background audio on.

---

## Roadmap ideas
- Remember chosen subtitle files per video
- Playlists and favourites
- Equalizer and audio boost
- Network streams (URL / SMB)

---

## Author

**Harish** — Mobile App Developer (Android · iOS · Flutter · KMP · .NET MAUI)
GitHub: [@HarishKumarKG](https://github.com/HarishKumarKG)

## License

No license has been chosen yet, so all rights are reserved by default.
Add a `LICENSE` file (for example MIT or Apache 2.0) if you want others to use the code.
