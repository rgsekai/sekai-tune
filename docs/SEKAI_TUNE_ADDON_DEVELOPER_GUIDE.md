# Sekai Tune Addon Developer Specification

Sekai Tune supports custom audio provider addons via an open HTTP REST protocol. Addons allow users to stream music from custom sources, self-hosted libraries, or third-party platforms directly within Sekai Tune.

The Sekai Tune addon protocol is **100% compatible with BitChord addons** while offering extended capabilities such as enhanced schema resilience, dynamic multi-field artwork fallbacks, robust transliteration matching, and custom HTTP request header propagation.

---

## Architecture & How Addons Work

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              Sekai Tune                                 │
│                                                                         │
│  1. Registration & Health                                               │
│     GET /manifest.json ───────────────────────────────────────────────┐ │
│                                                                       │ │
│  2. Search & Track Matching (on song playback)                        │ │
│     GET /search?q={title}+{artist}&quality={quality} ─────────────────┤ │
│     └── TrackMatcher (Title cleaning, artist match, duration tolerance)│ │
│                                                                       │ │
│  3. Stream URL & Metadata Resolution                                  │ │
│     GET /stream/{id}?quality={quality}&atmos=auto ────────────────────┤ │
│     └── Direct stream URL, custom headers, container & codec flags    │ │
│                                                                       │ │
│  4. Playback (ExoPlayer)                                              │ │
│     Streams directly from resolved audio URL                          │ │
└───────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
                     ┌───────────────────────┐
                     │   Custom Addon API    │
                     │  (Node.js / Python /  │
                     │  Go / Cloudflare /    │
                     │     Serverless)       │
                     └───────────────────────┘
```

### Addon Lifecycle in Sekai Tune

1. **Discovery & Validation:** When a user enters an Addon URL in **Settings > Sources**, Sekai Tune sends a `GET /manifest.json` request. The manifest is parsed and validated.
2. **Prioritization:** Multiple addons and internal sources (e.g. YouTube Music) are ordered in a priority list. If enabled, addons can take precedence over default sources.
3. **Candidate Search (`/search`):** When playing a track, Sekai Tune requests candidates from the addon by title and artist.
4. **Intelligent Match Verification (`TrackMatcher`):** Sekai Tune compares the returned candidate metadata against the target track:
   - Strips noise tokens (e.g. `(Official Video)`, `[MV]`, `Remastered`).
   - Ensures version consistency (if the original is not a remix or live acoustic, it rejects remix/live versions).
   - Verifies artist token intersection and Romaji/Kana transliterations.
   - Enforces the user-configured duration tolerance (default ±10 seconds).
5. **Stream Resolution (`/stream/{id}`):** Sekai Tune requests audio stream details (bitrate, sample rate, bit depth, codec, stream URL, and any required HTTP headers).
6. **Graceful Fallback:** If an addon reports `404 Not Found` or empty results, Sekai Tune classifies this as a `NO_MATCH` and immediately falls back to the next source in the chain without interrupting playback or showing error banners.

---

## 1. Addon Manifest (`GET /manifest.json`)

The manifest identifies your addon, defines supported features, and specifies user-configurable settings.

### Endpoint
```http
GET /manifest.json
```

### Response Example

```json
{
  "id": "my-music-provider",
  "name": "My Lossless Provider",
  "version": "1.0.0",
  "description": "High-fidelity lossless streaming source for Sekai Tune",
  "resources": ["search", "stream"],
  "allowDownloads": 1,
  "checkValidLossless": 1,
  "settings": [
    {
      "key": "quality",
      "type": "select",
      "label": "Streaming Quality",
      "default": "lossless",
      "options": [
        { "value": "lossless", "label": "FLAC / Lossless (Hi-Res)" },
        { "value": "high", "label": "High Quality (320kbps AAC/MP3)" },
        { "value": "standard", "label": "Standard Quality (128kbps)" }
      ]
    }
  ]
}
```

### Manifest Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | `String` | **Yes** | Unique identifier for your addon (e.g. `com.example.provider`). |
| `name` | `String` | **Yes** | Human-readable name displayed in Sekai Tune settings. |
| `version` | `String` | No | Addon version string (e.g. `1.0.0`). |
| `description` | `String` | No | Short summary of the provider. |
| `resources` | `Array<String>`| No | Supported actions: `["search", "stream"]`. |
| `allowDownloads`| `Int` or `Boolean` | No | `1` / `true` to permit downloading tracks through this addon (default: `1`). |
| `checkValidLossless` | `Int` or `Boolean` | No | `1` / `true` if lossless tracks should be validated by Sekai Tune (default: `0`). |
| `settings` | `Array<Setting>` | No | Configurable settings exposed in the Sekai Tune UI. |

### Settings Object Format

| Field | Type | Description |
|---|---|---|
| `key` | `String` | Setting key passed as a query parameter in `/search` and `/stream/{id}` (e.g. `"quality"`). |
| `type` | `String` | Must be `"select"` for dropdown menus. |
| `label` | `String` | UI display label. |
| `default` | `String` | Default value if not configured by the user. |
| `options` | `Array<Option>` | List of selectable choices with `value` and `label`. |

---

## 2. Search Endpoint (`GET /search`)

Called by Sekai Tune to search for track candidates corresponding to the current song.

### Endpoint
```http
GET /search?q={query}&quality={quality}
```

### Query Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `q` | `String` | **Yes** | Search query string (typically `{title} {artist}`). |
| `quality` | `String` | No | Selected quality preference from manifest settings. |
| `[custom]` | `String` | No | Any other user-configured setting defined in `manifest.json`. |

### Response Schema

Sekai Tune accepts **two response formats**:

#### Option A: Container Object (Recommended / BitChord compatible)
```json
{
  "tracks": [
    {
      "id": "trk_987654321",
      "title": "Brave Shine",
      "artist": "Aimer",
      "album": "DAWN",
      "duration": 234,
      "artworkURL": "https://img.provider.com/artwork/987654321.jpg",
      "format": "flac",
      "audioQuality": "lossless"
    }
  ]
}
```
*(Note: Sekai Tune also accepts containers named `results`, `data`, or `items`)*.

#### Option B: Direct JSON Array
```json
[
  {
    "id": "trk_987654321",
    "title": "Brave Shine",
    "artist": "Aimer",
    "duration": 234,
    "artworkURL": "https://img.provider.com/artwork/987654321.jpg"
  }
]
```

### Track Object Properties

| Field | Type | Required | Notes & Resilience in Sekai Tune |
|---|---|---|---|
| `id` | `String` | **Yes** | Unique identifier passed to `/stream/{id}`. |
| `title` | `String` | **Yes** | Song title. |
| `artist` | `String` or `List` | **Yes** | `artist` string (e.g. `"Aimer"` or `"Artist A, Artist B"`) or `artists` array. |
| `album` | `String` or `Object` | No | Album title string or object `{ "title": "..." }`. |
| `duration` | `Int` or `Double` | **Yes** | Duration in seconds (`duration` or `durationSec`). If missing or `0`, duration matching may fail. |
| `artworkURL` | `String` or `Object` | No | Supports `artworkURL`, `albumArtworkURL`, `thumbnail`, `cover`, `image`, `picture`, snake_case variants, or image objects with `url`. |
| `streamURL` | `String` | No | Optional direct stream URL if resolution is already done at search time. |
| `audioQuality` | `String` | No | e.g. `"lossless"`, `"high"`, `"standard"`. |
| `format` | `String` | No | Codec or container hint (e.g. `"flac"`, `"aac"`, `"mp3"`). |

---

## 3. Stream Endpoint (`GET /stream/{id}`)

Called to obtain the playback URL and audio parameters for a selected track.

### Endpoint
```http
GET /stream/{id}?quality={quality}&atmos=auto
```

### Query Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `id` | `Path` | **Yes** | Track ID returned from `/search`. |
| `quality` | `Query` | No | Desired quality (`lossless`, `high`, `standard`, etc.). |
| `atmos` | `Query` | No | Spatial audio / Dolby Atmos mode (`auto`, `off`). |

### Response Schema

```json
{
  "url": "https://cdn.provider.com/audio/987654321.flac",
  "codec": "flac",
  "container": "flac",
  "bitrate": 1411200,
  "sampleRate": 44100,
  "bitDepth": 16,
  "headers": {
    "Authorization": "Bearer sample_token",
    "Referer": "https://provider.com"
  }
}
```

### Stream Object Properties

| Field | Type | Required | Description |
|---|---|---|---|
| `url` | `String` | **Yes** | Direct HTTPS URL to audio stream. Supports progressive audio, HLS (`.m3u8`), and DASH (`.mpd`). |
| `codec` | `String` | No | Audio codec: `flac`, `alac`, `aac`, `opus`, `mp3`, `vorbis`. |
| `container` | `String` | No | Audio container: `flac`, `m4a`, `mp4`, `webm`, `ogg`, `mp3`, `hls`, `dash`. |
| `bitrate` | `Int` or `Long` | No | Bitrate in **bps** (e.g. `320000`) or **kbps** (e.g. `320`). Sekai Tune automatically normalizes kbps to bps. |
| `sampleRate` | `Int` | No | Sample rate in **Hz** (e.g. `44100`, `96000`, `192000`). |
| `bitDepth` | `Int` | No | Bit depth for lossless audio (e.g. `16` or `24`). |
| `headers` | `Map<String, String>` | No | Custom HTTP request headers sent by ExoPlayer when requesting audio segments. |
| `audioMode` | `String` | No | Special audio mode: `stereo`, `spatial`, or `atmos`. |

---

## Candidate Matching & Quality Ranking

Understanding how Sekai Tune selects and validates candidates helps you optimize search responses.

### 1. `TrackMatcher` Algorithm
1. **Packaging Words Removal:** Words such as `[MV]`, `Official Audio`, `Lyric Video`, `HD`, `4K`, `Remastered` are stripped before comparison.
2. **Version Marker Guard:** If the requested track is NOT a remix, Sekai Tune rejects candidates containing `"remix"`, `"live"`, `"acoustic"`, or `"cover"`.
3. **Artist Verification:** Whole-word token intersection is verified. Supports comma, slash, and `feat.` splitting, as well as Japanese Kana/Romaji conversion.
4. **Duration Tolerance Window:** Candidate duration must match target duration within the user's tolerance (configured between 1s and 20s; default is ±10 seconds).

> **Developer Tip:** Always return accurate `duration` (in seconds) in your `/search` results to avoid being filtered out by `TrackMatcher`.

### 2. Quality Ranking
Sekai Tune prioritizes streams in the following hierarchy:
1. **Hi-Res Lossless:** 24-bit FLAC / ALAC with sample rate $\ge 48\,\text{kHz}$.
2. **Lossless CD:** 16-bit / 44.1 kHz FLAC / ALAC.
3. **High Quality:** AAC / Opus $\ge 256\,\text{kbps}$ or MP3 $320\,\text{kbps}$.
4. **Standard:** 128 kbps to 192 kbps.

---

## Error Handling & Status Codes

Sekai Tune uses smart response classification to avoid breaking user playback:

| Status Code / Scenario | Sekai Tune Behavior |
|---|---|
| `404 Not Found` | Treated as **`NO_MATCH`**. Silently falls back to the next source without throwing errors. |
| Empty `tracks: []` | Treated as **`NO_MATCH`**. Falls back immediately. |
| `400` / `403` with body `"not found"` or `"unavailable"` | Treated as **`NO_MATCH`**. Graceful fallback. |
| `429 Too Many Requests` | Addon is rate-limited. Sekai Tune respects the `Retry-After` header and backs off exponentially. |
| `401 Unauthorized` | Critical error: indicates invalid credentials or expired token. |
| `5xx Server Error` | Addon failure. Sekai Tune skips to the next source and logs a warning. |

---

## Reference Implementations

### Node.js / Express Example

```javascript
import express from 'express';

const app = express();
const PORT = 3000;

// 1. Manifest
app.get('/manifest.json', (req, res) => {
  res.json({
    id: "sample-addon",
    name: "Sample Express Addon",
    version: "1.0.0",
    description: "Minimal reference addon for Sekai Tune",
    resources: ["search", "stream"],
    allowDownloads: 1,
    checkValidLossless: 1,
    settings: [
      {
        key: "quality",
        type: "select",
        label: "Audio Quality",
        default: "lossless",
        options: [
          { value: "lossless", label: "FLAC Lossless" },
          { value: "high", label: "High (320kbps)" }
        ]
      }
    ]
  });
});

// 2. Search
app.get('/search', async (req, res) => {
  const query = req.query.q;
  if (!query) {
    return res.status(400).json({ error: "Missing query 'q'" });
  }

  // Perform search in your library/service
  // Returning candidates:
  res.json({
    tracks: [
      {
        id: "track_101",
        title: "Sample Song",
        artist: "Sample Artist",
        album: "Sample Album",
        duration: 215,
        artworkURL: "https://example.com/art.jpg",
        format: "flac",
        audioQuality: "lossless"
      }
    ]
  });
});

// 3. Stream
app.get('/stream/:id', async (req, res) => {
  const { id } = req.params;
  const quality = req.query.quality || "lossless";

  if (id !== "track_101") {
    return res.status(404).json({ error: "Track not found" });
  }

  res.json({
    url: "https://example.com/audio/sample_101.flac",
    codec: "flac",
    container: "flac",
    bitrate: 1411200,
    sampleRate: 44100,
    bitDepth: 16
  });
});

app.listen(PORT, () => {
  console.log(`Sekai Tune Addon server running at http://localhost:${PORT}`);
});
```

---

### Python / FastAPI Example

```python
from fastapi import FastAPI, HTTPException, Query
from typing import Optional, List
from pydantic import BaseModel

app = FastAPI(title="Sekai Tune Python Addon")

class TrackItem(BaseModel):
    id: str
    title: str
    artist: str
    album: Optional[str] = None
    duration: int
    artworkURL: Optional[str] = None
    format: Optional[str] = "flac"
    audioQuality: Optional[str] = "lossless"

class SearchResponse(BaseModel):
    tracks: List[TrackItem]

class StreamResponse(BaseModel):
    url: str
    codec: str = "flac"
    container: str = "flac"
    bitrate: int = 1411200
    sampleRate: int = 44100
    bitDepth: int = 16

@app.get("/manifest.json")
def get_manifest():
    return {
        "id": "python-addon",
        "name": "Python FastAPI Addon",
        "version": "1.0.0",
        "resources": ["search", "stream"],
        "allowDownloads": 1,
        "checkValidLossless": 1,
        "settings": [
            {
                "key": "quality",
                "type": "select",
                "label": "Audio Quality",
                "default": "lossless",
                "options": [
                    {"value": "lossless", "label": "Lossless FLAC"},
                    {"value": "high", "label": "High Quality AAC"}
                ]
            }
        ]
    }

@app.get("/search", response_model=SearchResponse)
def search_tracks(q: str = Query(...), quality: Optional[str] = "lossless"):
    # Replace with real database or upstream API search
    return SearchResponse(
        tracks=[
            TrackItem(
                id="trk_fastapi_01",
                title="Example Track",
                artist="Example Artist",
                duration=180,
                artworkURL="https://example.com/cover.jpg",
                format="flac",
                audioQuality=quality
            )
        ]
    )

@app.get("/stream/{track_id}", response_model=StreamResponse)
def get_stream(track_id: str, quality: Optional[str] = "lossless"):
    if track_id != "trk_fastapi_01":
        raise HTTPException(status_code=404, detail="Track not found")
        
    return StreamResponse(
        url="https://example.com/media/trk_fastapi_01.flac",
        codec="flac",
        container="flac",
        bitrate=1411200,
        sampleRate: 44100,
        bitDepth: 16
    )
```

---

## Testing & Adding to Sekai Tune

1. **Test with `curl`:**
   ```bash
   # Check Manifest
   curl -i http://localhost:3000/manifest.json

   # Test Search
   curl -i "http://localhost:3000/search?q=Aimer+Brave+Shine"

   # Test Stream Resolution
   curl -i "http://localhost:3000/stream/track_101?quality=lossless"
   ```

2. **Add to Sekai Tune:**
   - Open Sekai Tune on your Android device or emulator.
   - Go to **Settings > Sources**.
   - Tap **Add Source / Addon**.
   - Enter your addon URL (e.g. `http://10.0.2.2:3000` for Android Emulator localhost, or your remote HTTPS URL).
   - Once verified, toggle it on and drag it to adjust source priority.

