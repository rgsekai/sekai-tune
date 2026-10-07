# Sekai Tune Privacy Notice

_Last updated: October 7, 2026_

This notice covers the Sekai Tune Android app in this repository. It explains what the app stores on your device, what it can send to external services when you use specific features, and what Android permissions it requests.

It is based on the current source code and build configuration. It does not replace the privacy terms of Google (including Firebase, Google Sign-In and YouTube Music), Spotify, TIDAL, Apple, lyrics providers, GitHub, Render, or any other third-party service you use through the app.

## Privacy Summary

- Most core app data (library, history, settings, caches) is stored locally on your device.
- Sekai Tune does not sell your data, does not show ads, and does not use advertising SDKs.
- Sekai Tune does not include third-party analytics, install counters, or automatic crash-reporting services. Crash reports are shown on your device and leave it only if you choose to share them.
- Some features need an online account or service: YouTube Music, Google Sign-In, Together Online, Buddy List, and Spotify. If you do not use a feature, its data is not sent.
- When data leaves your device, it is because you used a feature that requires it, or because of a small number of background requests listed below (playback pre-loading, update checks, player configuration).
- Together Online, Buddy List and push notifications use **Google Firebase** (Authentication, Cloud Firestore and Cloud Messaging). Firebase stores only your display name, profile picture, and the Together and Buddy List data described below. Your app settings, API keys, account sessions and tokens are never uploaded to Firebase. Older versions of the app could upload an encrypted copy of these; the current version deletes that copy from your account the first time you sign in.
- Android backup and device-transfer may copy part of the app's local data unless excluded by the app's backup rules.
- The current Android manifest does not request location, contacts, camera, calendar, SMS, or call log permissions.

## Data the App May Store on Your Device

| Category | Examples | Why it is stored |
| :-- | :-- | :-- |
| Library and playback data | Songs, artists, albums, playlists, like state, download state, play time, audio format metadata | Library management, playback, downloads, statistics |
| Search and lyrics data | Search queries, cached lyrics, romanization results | Search history and lyrics features |
| Listening history | Playback events with song ID, timestamp, and play time | Listening stats and history features |
| App settings | Language, country, UI and audio settings, proxy settings, cache settings, history pause toggles, Together settings | Personalization and feature configuration |
| YouTube / YouTube Music session (optional) | Account name, email, channel handle, visitor data, data sync ID, cookie, PO token values | Signed-in YouTube Music features |
| Google account session (optional) | Firebase sign-in state, your Google account name, email and profile picture URL as provided by Google | Together Online and Buddy List |
| Spotify session (optional) | Spotify session cookie and related tokens, cached Spotify playlist and followed-artist metadata | Spotify library features |
| Buddy and Together data (optional) | Display name, Together client ID, last join link, cached buddy names, push-notification token | Together Online and Buddy List |
| Matching caches | Cached stream-resolution results, remembered playback client, catalog-match results for uploaded songs | Faster playback, downloads and uploaded-song playback |
| Downloaded and cached files | Streaming cache, in-app downloads, partial download files, app-managed files | Offline use and performance |
| Saved-to-device audio | Audio files you export with Save to Device, saved to `Music/Sekai Tune/` through Android MediaStore | Your own offline music files |
| Update data | Cached release information, the version you dismissed, a downloaded update APK (temporary) | In-app update prompt |
| Crash reports | Device model, Android version, stack trace, shown in the in-app crash screen | Debugging, only shared if you share it |

## Data the App May Send Off Your Device

Sekai Tune contacts external services only for the features below. The exact payload depends on the feature and your settings.

| Service or feature | Data that may be sent | When it happens |
| :-- | :-- | :-- |
| YouTube / YouTube Music (Google) | Search terms, playback and stream requests, library and playlist requests, and, if signed in, session values such as visitor data, sync ID, cookie or token values. Your IP address is visible to Google. | When you browse, stream, download, sync or sign in. Playback pre-loading for upcoming tracks and a short warm-up at app start also contact YouTube. |
| Stream-resolution fallback services | Video IDs and player-script details needed to decode a stream, sent to third-party extractor services (for example `api.pipepipe.dev`) | Only as a last-resort fallback when the standard playback path fails |
| Player configuration (GitHub) | A request to download player-script configuration files. No personal data beyond your IP address. | Periodically, to keep playback working |
| Google Sign-In and Firebase Authentication | Your Google account identity (name, email, profile picture URL) through Google's sign-in flow. Anonymous sign-in (no account details) is used for guests in Together Online. | When you sign in, or host or join an online Together session |
| Cloud Firestore (Google Firebase) | See "Firebase features" below | When you use Together Online or Buddy List |
| Firebase Cloud Messaging (Google) | A push token identifying your app install, used to deliver notifications | When you are signed in and Buddy List notifications are enabled |
| Notification relay (self-hosted on Render) | The recipient's push token, the sender's display name and the session ID, used to deliver a Buddy List or session-invite notification. The relay holds the credentials needed to send via Firebase. It logs only timestamps and response codes, and stores no message contents. Render, the hosting provider, may keep standard server logs such as IP address. | When you send a buddy request or a session invite |
| Spotify | Session cookie and tokens, playlist and artist requests, actions you take (add to playlist, follow artist), Canvas lookups | When Spotify is connected and you use Spotify features |
| Canvas and artwork providers (BetterLyrics, Apple Music, TIDAL, Spotify Canvas) | Song title, artist name, album identifiers or URLs used to look up animated artwork | When animated artwork (Canvas) is enabled and a track is playing |
| Lyrics providers (LrcLib, KuGou, SimpMusic, Paxsenix and others) | Song title, artist, album and duration needed to find lyrics | When lyrics are requested |
| Uploaded songs (YouTube Music) | The audio files you choose to upload go to YouTube Music. For playback and downloads, the song title, artist and duration are used to search the public YouTube Music catalog for a matching version. | When you upload, play, download or save an uploaded song |
| Music recognition (Shazam) | A frequency-hash signature created on your device from the microphone input and sent to Shazam's service (`amp.shazam.com`). Raw audio is never sent or saved. | Only when you start music recognition |
| GitHub releases | Update-check requests, and the update APK download if you accept an update. Your IP address is visible to GitHub. | Periodically in the background (about every six hours) and when you open update prompts |
| Together LAN mode | Playback state and queue data sent directly to other devices on your local network. No cloud server is involved. | When you host or join a LAN session |
| Donation links | Nothing from the app. Tapping a donation option opens Buy Me a Coffee or your UPI app, which have their own privacy terms. The app does not process payments. | When you tap a donation option |

## Firebase Features (Together Online and Buddy List)

These features store data in Google Firebase, in a project owned by the Sekai Tune maintainer.

**Together Online**
- Each session stores: the session code, playback state, queue metadata, and a participant list with each person's ID, display name, profile picture URL (if available), and host or connection status.
- Guests can join anonymously. Anonymous participants have no account details and no profile picture.
- If you host a session, the IDs of buddies you invite are stored on the session.
- Other participants in the same session can see your display name and profile picture.

**Buddy List**
- Buddy requests are stored until accepted, rejected or cancelled. A request holds both people's IDs, display names and profile picture URLs. Accepted buddies are stored as an ID, display name and profile picture URL on both users' lists.
- Buddies are added only through people you meet in the same session. Buddies cannot see your email address.
- Removing a buddy deletes the entry on both sides.
- Each signed-in device stores a push token, its device model and the time it was updated (under your account) so notifications can reach it. Tokens are not automatically cleaned up when you stop using a device.

**Your profile**
- When you sign in with Google, a profile record is created containing your account ID, display name, profile picture URL, and the time it was last updated. You can choose a custom display name; it is not overwritten by your Google name on later sign-ins.
- Your Google email address is held by Firebase Authentication. It is not written to your profile record, buddy lists or Together sessions.

**Settings and keys stay on your device**
- Your app settings, API keys, YouTube Music and Spotify sessions, and proxy credentials are not uploaded to Firebase. Signing in on a new device does not restore them; you set them up again on that device.

**Retention**
- Data in Firebase stays until it is removed by app actions (leaving or ending a session, removing a buddy, cancelling a request) or until you ask for it to be deleted. To request deletion of your Firebase-stored data, open an issue in the project repository.

## Android Permissions

| Permission | Why the app requests it |
| :-- | :-- |
| `INTERNET` | Connect to YouTube, Firebase, lyrics, Canvas, Spotify, update and other online features |
| `ACCESS_NETWORK_STATE` | Detect connectivity and adapt network behavior |
| `POST_NOTIFICATIONS` | Playback, download, update and Buddy List notifications |
| `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` (Android 12 and below) | Read local audio files |
| `RECORD_AUDIO` | Music recognition |
| `BLUETOOTH_CONNECT` | Bluetooth audio devices and playback controls |
| `RECEIVE_BOOT_COMPLETED` | Restore playback-related behavior after restart |
| `WAKE_LOCK` | Keep playback and download work running |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_DATA_SYNC` | Background playback, downloads and related work |
| `REQUEST_INSTALL_PACKAGES` | Install an update you accept in the in-app update prompt. Android also asks for your approval before the app can install anything. |

Home-screen widgets, launcher shortcuts and the Quick Settings tile do not need extra permissions. They can start playback without opening the app. If you deny a permission, the related feature may stop working.

## Backups, Device Transfer, and Local Retention

Sekai Tune enables Android backup support. The backup and data-transfer rules exclude some cache and download paths, including the ExoPlayer cache, the download directory, and `exoplayer_internal.db`. Other app data, including the local database and preferences, may still be included in Android cloud backup or device transfer depending on your Android settings.

The app also offers a manual backup feature that creates a ZIP archive of app settings and database files. This is a user-triggered export. Keep the file private, because the archive may include session values and tokens stored in settings.

Local data remains on your device until you clear app data, uninstall the app, remove it through an app action, or Android backup restores it elsewhere. Files saved to `Music/Sekai Tune/` are normal files in your own storage and are not removed when you uninstall the app.

## User Controls and Choices

- Use many core features without signing in to anything.
- Choose whether to sign in to YouTube Music, Google (for Together Online and Buddy List) or Spotify.
- Turn off Canvas, lyrics providers, and the Spotify playlist and artist features from within the app.
- Join Together Online sessions anonymously.
- Grant or deny Android runtime permissions (notifications, media access, microphone).
- Configure or disable proxy-related settings.
- Pause search history and listening history.
- Create a local backup export.
- Sign out of Google, YouTube Music or Spotify, remove a buddy, or leave a session at any time.
- Clear app data or uninstall the app to remove local data.
- For connected external services, you may also need to revoke access or rotate tokens with those providers.

## Security Notes and Limitations

- The current Android manifest allows cleartext traffic. This is needed for the local streaming proxy, Cast and local-network features, so some connections may use HTTP if a feature or configured endpoint uses it.
- The manifest enables Android audio playback capture, used for music recognition and the audio visualizer. Under Android platform rules, compatible system features or authorized apps may be able to capture app audio.
- Local app data (database, preferences, caches) is not encrypted by the app. This includes API keys, YouTube Music session values, Spotify tokens and proxy credentials, which stay on your device and are never uploaded to Firebase.
- Buddy List accept, reject and remove actions are performed by your device writing to Firebase, protected by security rules that tie each action to your account ID.
- The notification relay runs on a free hosting tier and may take time to start after being idle.
- Third-party services (Google, Spotify, Render, lyrics and Canvas providers) have their own logging, retention and security practices.
- If future code changes add new integrations or data flows, this notice should be updated.

## Changes to This Notice

This file is reviewed whenever Sekai Tune changes its permissions, storage model, external integrations, backup behavior, or network architecture. The date at the top shows the last update.

## Project Contact

For questions, corrections or data-deletion requests, use the project repository.

- Repository: https://github.com/rgsekai/sekai-tune
- Issues: https://github.com/rgsekai/sekai-tune/issues

## Technical Appendix

This appendix maps the main statements above to implementation surfaces in the codebase.

| Topic | What the code shows | Main files |
| :-- | :-- | :-- |
| Permissions and backup | Manifest declares network, media, microphone, Bluetooth, notification, boot, wake-lock, foreground-service and install-package permissions; enables backup, cleartext traffic and audio playback capture. XML rules exclude selected caches from backup and device transfer. | `AndroidManifest.xml`, `res/xml/data_extraction_rules.xml`, `res/xml/backup_rules.xml` |
| Local database | Room schema holds songs, artists, albums, playlists, search history, lyrics, audio format metadata, playback events, and a matched-catalog ID for uploaded songs. | `app/schemas/.../InternalDatabase/` |
| Settings and tokens stored locally | DataStore keys for UI, proxy, history toggles, Together values, YouTube session values, account fields, and update-dismissal state. | `PreferenceKeys.kt` |
| YouTube signed-in state | Innertube layer exposes visitor data, sync ID, cookie, PO token values and proxy state. | `YouTube.kt` |
| Stream resolution | Direct YouTube clients are tried first; third-party extractor fallback and remote player configuration are used only if needed. | `ResolveAudioStreamUseCase.kt`, `YTPlayerUtils.kt`, `RemotePlayerConfigStore.kt` |
| Together Online | Firestore-backed host and guest classes read and write session documents, participants and invited IDs. LAN mode uses a local Ktor server. | `FirestoreTogetherHost.kt`, `FirestoreTogetherGuest.kt`, `MusicTogetherScreen.kt` |
| Buddy List and push | Repository handles buddy requests, buddy lists, invites and push-token registration; messaging service receives notifications. | `BuddyRepository.kt`, `BuddyFcmTokenManager.kt`, `BuddyMessagingService.kt` |
| Local-only settings | App settings, API keys, session values and tokens are kept in local storage only. No settings or secrets are written to Firestore. | Local DataStore preferences |
| Spotify | Spotify session, playlist and artist features; playback always comes from YouTube Music. | `spotifycore` module (`Spotify.kt`, `SpotifyAuth.kt`) |
| Canvas providers | Lookups to BetterLyrics, Apple Music, TIDAL and Spotify using song and artist details. | `canvas` module, `TidalCanvasProvider.kt`, `SekaiTuneCanvas.kt` |
| Uploaded songs | Resumable upload to YouTube Music and catalog matching by title, artist and duration. | `ResolveUploadedCatalogMatchUseCase.kt` |
| Downloads | In-app downloads and Save to Device, with paused-download state kept on the device. | `AudioDownloadWorker.kt`, `PausedDeviceDownloadStore.kt` |
| Updates | Release check against the project's GitHub releases, a periodic worker, and an in-app installer. | `Updater.kt`, `UpdateCheckWorker.kt`, `AppUpdateInstaller.kt` |
| Manual backup export | User-chosen ZIP of settings and database files. | `BackupRestoreViewModel.kt` |
| Dependency posture | Compose, Room, Hilt, Ktor, Media3, Coil, Timber, Firebase (Authentication, Firestore, Messaging) and related libraries. No advertising, analytics or crash-reporting SDKs are declared. | `app/build.gradle.kts`, `gradle/libs.versions.toml` |

## Open Documentation Boundaries

The following areas should be documented carefully if the project wants stronger privacy claims.

- What Render itself logs for the notification relay, and for how long (the relay's own code logs only timestamps and response codes).
- Whether every endpoint used by optional features is always HTTPS in real use, since the manifest allows cleartext traffic.
- Whether local app storage is encrypted at rest on all supported devices.
- Retention practices of Google Firebase, Spotify, canvas, lyrics and extractor providers.
- Whether stale push tokens and abandoned Together sessions should be cleaned up automatically.
