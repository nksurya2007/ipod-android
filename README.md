# iPod for Android

Android version of the iPod-style music player. Kotlin, no third-party libraries. It has the click wheel, menus, Cover Flow, Brick Game, 10 themes and 8 wheel styles, and plays the music stored on your phone.

Made by **SN STUDIO** 💙

## Features

- **Click wheel**: rotate to scroll, center to select, MENU to go back, play/pause, previous, next
- **Library**: Playlists, Artists, Albums, Songs, Shuffle Songs, Now Playing (artwork, progress, volume / seek)
- **Line list or Cover Flow** per category (Settings → Cover Flow: press center on a row to switch)
- **Sort**: A-Z, Z-A, Recently Added (Settings → Sort)
- **Brick Game** (Extras)
- **10 themes**: Classic, Dark, Green, Rose, Ocean, Sunset, Purple, Gold, U2 Red, Matrix
- **8 wheel styles**: White, Black, Silver, Red, Gold, Blue, Pink, Green
- Clock (centered) and battery icon in the title bar
- Click sound on/off, saved settings, About with SN STUDIO

## Requirements

- Android 5.0 (API 21) or later
- Music files on the phone
- Permission to read audio (asked on first launch)

## Build the APK on GitHub (no Android Studio needed)

1. Create a new repo and push this folder as the repo root.
2. Open the **Actions** tab and run **Build APK** (it also runs on every push).
3. When it turns green, download the `iPodAndroid-apk` artifact and unzip it to get `app-release.apk`.

## Install

1. Copy the APK to your phone.
2. Open it and allow **Install unknown apps** for your file manager or browser when asked.
3. Install. Later builds install over the old one and keep your settings.

## Project layout

```
.
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── .github/workflows/android.yml     # builds the APK
└── app/src/main
    ├── AndroidManifest.xml
    ├── res/drawable/ic_launcher.xml
    └── java/com/surya/ipodapp
        ├── MainActivity.kt      # menus, Now Playing, settings, navigation
        ├── ClickWheel.kt        # touch wheel
        ├── CoverFlowView.kt     # 3D Cover Flow
        ├── BrickGameView.kt     # Breakout
        ├── PlayerEngine.kt      # queue + MediaPlayer
        ├── Data.kt              # MediaStore library + artwork loader
        ├── Views.kt             # progress bar, play icon, battery icon
        └── Theme.kt             # themes and wheel styles
```

## Notes

- Playback runs inside the app. Music keeps playing when you switch apps, but stops if Android closes the app or you swipe it away.
- Playlists only show what Android exposes through its media database, so some phones will show none.
- Artists and Albums are grouped by the tags in your files.
