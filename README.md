# OPIc Android App

Kotlin + Jetpack Compose Android app porting the PyQt5 OPIc Simulation desktop app.

## Setup

### 1. Android Studio
Open the `OPicAndroid/` folder in Android Studio (Hedgehog 2023.1.1+) or later.

### 2. Database
Export the existing `opic.db` content and place it as:
```
app/src/main/assets/opic_base.db
```
The DB must contain the `Questions`, `Users`, `User_Study_Progress`, `Test_Sessions`, `Test_Results` tables.
Audio paths in the DB are just link names (without extension) — audio files are downloaded separately.

To export from the existing Python app:
```python
# In the Python app, use: Settings → Export Excel
# Then re-import into SQLite with the correct schema
```

### 3. Audio Packs (Optional)
Audio files from `/Sound/` can be bundled into topic zip archives:
```
{topic_name}.zip → extracts to files/audio/{topic_name}/file.mp3
```
Upload to Firebase Storage and update the URLs in `AudioPackViewModel.seedDefaultPacks()`.

### 4. Build & Run
```bash
./gradlew assembleDebug
```
Or use Android Studio's Run button (▶).

## Architecture

```
MVVM + Repository Pattern
├── data/       Room DB + OkHttp downloader
├── domain/     Use cases (level calc, decay, test generation)
├── ui/         Compose screens + ViewModels
├── service/    WorkManager daily reminder
└── util/       Audio player/recorder, STT, script comparator
```

## Key Features
- **Study Mode**: Browse questions, play audio, record & STT compare
- **Test Mode**: Survey → Self-assessment → 12–15 question simulation
- **Review Mode**: Replay recordings, script comparison, edit STT
- **Audio Packs**: Per-topic zip download for offline audio
- **Daily Reminder**: WorkManager push notification at custom time

## Checklist (Phase 1 complete)
- [x] Project structure & build files
- [x] Room DB schema (mirrors Python opic.db exactly)
- [x] MVVM + Hilt DI wiring
- [x] Navigation graph (Home → Study → Test → Review → Settings)
- [x] All screens scaffolded

## Next Steps
1. Copy `opic.db` → `assets/opic_base.db`
2. Run on device/emulator
3. Upload audio zips to Firebase Storage
4. Update `AudioPackViewModel.seedDefaultPacks()` with real URLs
