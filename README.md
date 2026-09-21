# Paper Radar

An Android app that turns newly published research papers into a scrollable news feed.

Keeping up with a field usually means running the same searches over and over. Paper Radar inverts that: you choose journals and topics once, and new work arrives in a feed you can skim, save, and search.

## Features

- **Feed** of recent papers from selected journals and arXiv categories
- **Library** for saving papers to read later
- **Search** across sources
- **Background sync** so the feed is already filled when the app opens
- Offline reading for anything already synced

## Architecture

Unidirectional data flow with a repository boundary, so no screen ever touches the network directly.

```
ui/ (Compose screens)  ->  ViewModel  ->  PaperRepository
                                              |
                          NetworkPaperRepository -> RemotePaperSource -> ArxivSource
                          FakePaperRepository    (previews and tests)
                                              |
                                         PaperStore (Room cache)
```

- `PaperRepository` is an interface. `FakePaperRepository` lets every screen render in a Compose preview and in tests with no network call.
- `SyncWorker` (WorkManager) refreshes the feed in the background.
- Each screen owns a ViewModel built through an explicit factory, so state survives configuration changes.

## Testing

- Screenshot tests with **Roborazzi** on **Robolectric**, to catch unintended UI changes
- Instrumented tests on device and emulator

## Stack

Kotlin | Jetpack Compose | Navigation Compose | Room | WorkManager | Retrofit + Moshi | OkHttp | Coroutines | DataStore | Coil

## Build

```bash
./gradlew assembleDebug
```

Release signing reads `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` from the environment. No keystore, API key, or `local.properties` is committed to this repository.
