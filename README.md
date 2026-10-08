<div align="center">

  <img src="sekai_tune.svg" width="200" height="200" alt="Sekai Tune Logo">

  <h1 align="center">Sekai Tune</h1>

  <p align="center">
    <a href="https://github.com/rgsekai/sekai-tune/releases/latest"><img src="https://img.shields.io/github/v/release/rgsekai/sekai-tune?style=flat-square&color=3DDC84" alt="Latest Release"></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square" alt="License: GPL-3.0"></a>
    <a href="https://android.com"><img src="https://img.shields.io/badge/Platform-Android-green.svg?style=flat-square&logo=android&logoColor=white" alt="Platform: Android"></a>
    <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Language-Kotlin-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white" alt="Language: Kotlin"></a>
  </p>

  <p align="center">
    <strong>Sekai Tune is an open-source, privacy-focused YouTube Music client for Android built with Jetpack Compose and Material 3 design.</strong>
    <br />
    <em>Enjoy fast, ad-free streaming, live synced lyrics, customizable audio effects, and rich personalization without tracking or bloat.</em>
  </p>

</div>

---

> [!IMPORTANT]  
> **Geographic Availability:** If YouTube Music is not supported in your region, a VPN or proxy set to a supported country is required for initial catalog browsing and playback.

---

## 📸 Showcase

<div align="center">

  <img src="docs/assets/screen1.jpg" width="32%" alt="Sekai Tune Home Screen and Quick Picks">
  <img src="docs/assets/screen2.jpg" width="32%" alt="Sekai Tune Now Playing Player Screen">
  <img src="docs/assets/screen3.jpg" width="32%" alt="Sekai Tune Live Synced Lyrics with Translation">
  <img src="docs/assets/screen4.jpg" width="32%" alt="Sekai Tune Library and Playlists Management">
  <img src="docs/assets/screen5.jpg" width="32%" alt="Sekai Tune Listening Statistics and History">
  <img src="docs/assets/screen6.jpg" width="32%" alt="Sekai Tune Audio Equalizer and Effects">
  <img src="docs/assets/screen7.jpg" width="32%" alt="Sekai Tune Queue and Playback Controls">
  <img src="docs/assets/screen8.jpg" width="32%" alt="Sekai Tune Theme Palette and Dynamic Colors">
  <img src="docs/assets/screen9.jpg" width="32%" alt="Sekai Tune Appearance Settings and Extras">

</div>

---

## ✨ Features

<div align="center">

<table>
  <tr>
    <td width="50%" valign="top">
      <div align="left">
        <h3>🎵 Playback &amp; Library</h3>
        <ul>
          <li>Ad-free audio streaming with background listening and caching</li>
          <li>YouTube Music account sign-in for playlists, subscriptions, and likes</li>
          <li>Local audio file and offline downloads support</li>
          <li>Multi-account quick switching</li>
          <li>Fast, lightweight startup with minimal memory overhead</li>
        </ul>
      </div>
    </td>
    <td width="50%" valign="top">
      <div align="left">
        <h3>🎛️ Audio Controls</h3>
        <ul>
          <li>EBU R128 loudness normalization and volume boost</li>
          <li>Tempo, pitch, and playback speed adjustment</li>
          <li>Seamless crossfade between tracks</li>
          <li>System equalizer and spatial audio support</li>
        </ul>
      </div>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <div align="left">
        <h3>🎤 Lyrics &amp; Discovery</h3>
        <ul>
          <li>Live synced and word-by-word lyrics (BetterLyrics, LrcLib, KuGou, and more)</li>
          <li>AI-powered lyrics translation and romanization</li>
          <li>Built-in Shazam music recognition to identify surrounding audio</li>
          <li>Detailed listening stats and playback history</li>
        </ul>
      </div>
    </td>
    <td width="50%" valign="top">
      <div align="left">
        <h3>👥 Together &amp; Social</h3>
        <ul>
          <li><strong>Together Online:</strong> Host or join real-time synchronized listening rooms</li>
          <li><strong>Buddy List:</strong> Add friends from sessions and send invites with push notifications</li>
          <li>Spotify playlist and followed-artist import</li>
          <li>Animated Canvas video artwork (Spotify, Apple Music, TIDAL)</li>
        </ul>
      </div>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <div align="left">
        <h3>🎨 Interface &amp; Theming</h3>
        <ul>
          <li>Material 3 design powered by dynamic album-art colors</li>
          <li><strong>Liquid Glass:</strong> Customizable frosted glass surfaces, blur, and chromatic refraction</li>
          <li>Up to 9 distinct player styles and multiple background treatments</li>
          <li>Responsive layouts tailored for phones, foldables, and tablets</li>
        </ul>
      </div>
    </td>
    <td width="50%" valign="top">
      <div align="left">
        <h3>⚙️ Customization &amp; Widgets</h3>
        <ul>
          <li>Home-screen and lock-screen playback widgets</li>
          <li>Customizable gesture controls and animation tuning</li>
          <li>Local manual backup and restore (ZIP export of settings and library)</li>
          <li>Built-in in-app updater for seamless releases</li>
        </ul>
      </div>
    </td>
  </tr>
</table>

</div>

---

## 📥 Download

Grab the latest stable APK from the [GitHub Releases](https://github.com/rgsekai/sekai-tune/releases/latest) page:

👉 **[Download Sekai Tune (Latest Release)](https://github.com/rgsekai/sekai-tune/releases/latest)**

When installing the APK, Android may display a Google Play Protect prompt stating *"App scan recommended"* for apps installed outside the Play Store; tapping **"Scan app"** is completely normal.

> [!WARNING]  
> **Note:** The official repository releases above are the only trusted download source. We are not responsible for any modified or unofficial packages distributed elsewhere.

---

## 🛠️ Building from Source

### Prerequisites
- **JDK 21** (e.g. Eclipse Temurin or OpenJDK 21)
- **Android SDK** with build tools (compileSdk 37, minSdk 26)
- **Android Studio Ladybug / Meerkat** or newer (optional, for IDE development)

### Build Commands
Clone the repository and compile via Gradle:

```bash
# Clone the repository
git clone https://github.com/rgsekai/sekai-tune.git
cd sekai-tune

# Build universal release APK (GMS variant)
./gradlew assembleGmsMobileUniversalRelease

# Build ARM64 release APK (GMS variant)
./gradlew assembleGmsMobileArm64Release

# Build FOSS universal release APK (Google-free variant)
./gradlew assembleFossMobileUniversalRelease
```

> [!NOTE]  
> Firebase features (Together Online and Buddy List) require `google-services.json` in the `app/` directory. Push notifications and session invitations rely on the optional relay configuration (`RELAY_SERVICE_URL` and `RELAY_SERVICE_API_KEY` in `local.properties` or environment variables).

---

## 🔒 Privacy

Sekai Tune is built with privacy as a foundational principle:

- **Local Storage by Default:** Your music library, playback history, local cache, settings, and session credentials stay strictly on your device.
- **No Trackers:** No third-party advertising, usage tracking, analytics, or automated crash-reporting SDKs are included.
- **Firebase Scope:** Google Firebase is used strictly for online real-time features (Together Online, display name, profile picture, and Buddy List). App settings, API keys, and account tokens are never uploaded to Firebase.

For full details on data handling and external services, read our [Privacy Notice](PRIVACY.md).

---

## 💖 Support the Project

Sekai Tune is free, open-source software developed in personal time. If you enjoy the app and want to support ongoing maintenance and server costs:

- ☕ **Buy Me a Coffee:** [buymeacoffee.com/rgsekai](https://buymeacoffee.com/rgsekai)
- 💳 **UPI (India):** `rgsekai@upi` (SekaiTune)

Your support is deeply appreciated!

---

## 📜 Open-Source Acknowledgments

Sekai Tune is built on the shoulders of the open-source community:

* **[ArchiveTune](https://github.com/ArchiveTune/ArchiveTune)** & **[InnerTune](https://github.com/z-huang/InnerTune)** — The foundational upstream music player architecture.
* **[SimpMusic](https://github.com/maxrave-dev/SimpMusic)** — Lyrics API provider implementation.
* **[BetterLyrics](https://github.com/BetterLyrics/BetterLyrics)** — Word-by-word synced lyrics, unison, and artwork integration.
* **[Material Color Utilities](https://github.com/material-foundation/material-color-utilities)** — Dynamic theming extraction.
* **[Read You](https://github.com/Ashinch/ReadYou)** and **[Seal](https://github.com/JunkFood02/Seal)** — UI component design inspiration.
* **Translators, beta testers, and contributors** who continue to test, translate, and improve Sekai Tune.

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0** (GPL-3.0). See the [LICENSE](LICENSE) file for complete license terms.

---

## ⚖️ Legal Disclaimer

Sekai Tune is an independent, third-party client.
- It is **not** affiliated with, endorsed by, or sponsored by Google LLC or YouTube.
- It does not bypass YouTube's technical copy protections.
- Users are encouraged to support artists by purchasing music and subscriptions through official channels.