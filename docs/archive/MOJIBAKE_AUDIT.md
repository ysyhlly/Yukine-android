# Mojibake Audit

## Scope

Scanned Kotlin, Java, and XML sources under:

- `app/src/main/java`
- `app/src/main/res`
- `app/src/test/java`
- `app/src/androidTest/java`

The scan treats source files as UTF-8. PowerShell output on this workstation can
render valid UTF-8 Chinese as mojibake, so UTF-8 file reads are the authority.

## Initial Findings

| File | Line | Mojibake | Intended text | Status |
|---|---:|---|---|---|
| `app/src/main/java/app/yukine/ui/SettingsScreen.kt` | 240 | `杩斿洖` | `返回` | Fixed |
| `app/src/test/java/app/yukine/MainActivityArchitectureContractTest.java` | 205 | `姝屽崟瀵煎叆澶辫触` | `歌单导入失败` | Replaced with generated legacy text |
| `app/src/test/java/app/yukine/MainActivityArchitectureContractTest.java` | 206 | `鏃犳硶鍔犺浇璐︽埛姝屽崟` | `无法加载账户歌单` | Replaced with generated legacy text |

## Fixes

- `SettingsScreen.kt` now matches the normal Chinese `返回` label directly.
- `MainActivityArchitectureContractTest.java` keeps its regression intent by
  deriving the legacy mojibake text from the correct Chinese source string.
- `.editorconfig` now pins source editing to UTF-8.
- `:app:checkMojibake` scans source files during `check` and `preBuild`.
- `HomeDashboardStateFactory.kt` moved repeated home dashboard labels into
  `AppLanguage.java`, reducing bare user-facing Chinese in Kotlin.
- `HomeDashboardScreen.kt` now consumes labels from `HomeDashboardUiState`
  instead of embedding homepage, Now Playing, recent activity, weekly recap,
  recommendation, and streaming guide copy directly in Compose.
- `DashboardRepository.kt` now uses the same `AppLanguage.java` keys for
  local and remote homepage fallback copy.
- `NowBar.kt`, `NowBarStateFactory.kt`, `NowPlayingScreen.kt`,
  `NowPlayingStateFactory.kt`, and `now/NowPlayingDestination.kt` now route
  playback page labels, empty-state copy, lyrics fallback, copy Toasts, and
  volume Toasts through `AppLanguage.java`.
- `EchoNavHostState.kt`, `EchoNavGraph.kt`, and `MainActivity.java` now pass the
  current language mode into the native playback destination, so the playback
  page follows explicit language selection instead of only the system locale.
- Download management and streaming-login playlist dialogs now reuse
  `AppLanguage.java` keys for both settings entry copy and runtime dialog/status
  messages.
- `OnboardingScreen.kt`, `EchoApp.kt`, and `MainActivity.java` now pass the
  current language mode into onboarding and reuse `AppLanguage.java` for
  onboarding steps, status pills, finish gating, and missing-setup messages.
- `LibraryGrouping.kt`, `LibraryGroupsRenderController.kt`, and
  `MainActivity.java` now pass the current language mode into library grouping
  screens. Unknown album/artist/track values, group titles, group subtitles,
  favorites entry copy, empty states, and group detail actions are backed by
  `AppLanguage.java`.
- `HomeDashboardViewModel.kt` now receives the active language mode for playback
  updates, so the hero continuation sentence and "Now playing"/"Continue
  playing" detail copy follow the user's language setting.
- `TrackListRenderController.kt` now uses `AppLanguage.java` for recommendation
  track metrics instead of embedding the Chinese `曲目` label.
- `QueueScreen.kt`, `QueueRenderController.kt`, and `queue/QueueViewModel.kt`
  now route the queue drag-to-reorder accessibility label through
  `AppLanguage.java`.
- `StreamingPlaylistController.kt` now uses the existing account-playlist
  loading key instead of embedding Chinese status text after streaming login.
- `LibraryGroupsScreen.kt`, `LibraryGroupsRenderController.kt`, and
  `LibraryPlaylistsRenderController.kt` now pass the play-button accessibility
  label through `LibraryGroupActions` and `AppLanguage.java`.
- `EchoPageScaffold.kt` no longer embeds a Chinese fallback for the back button;
  callers still pass localized `backLabel` where the active language mode is
  known.
- `PlaylistListScreen.kt` now takes rename/delete accessibility labels from
  `PlaylistRowActions`; `CollectionsRenderController.kt` supplies localized
  values from `AppLanguage.java`.
- `LocalStreamingAuthStore.kt` and `streaming/StreamingGateway.kt` now emit
  canonical English status strings for local-login and gateway-fallback states;
  `StreamingSearchScreen.kt` maps those known statuses back to localized
  `AppLanguage.java` labels at the UI boundary.
- `LocalNeteaseStreamingClient.kt` now emits canonical English error strings
  for empty liked playlists, missing NetEase account IDs, and required NetEase
  sign-in; `StreamingSearchScreen.kt` maps those known provider errors back to
  localized `AppLanguage.java` labels at the UI boundary.

## Remaining Text Debt

Known mojibake fragments currently scan clean in Kotlin/Java/XML sources. The
remaining work is text consolidation rather than encoding repair:

| Area | Current state | Next action |
|---|---|---|
| Onboarding | `OnboardingScreen.kt` has no remaining raw Chinese UI literals; onboarding copy and host status messages come from `AppLanguage.java`. | Keep new onboarding copy in `AppLanguage.java` and extend `AppLanguageOnboardingTest.kt` when adding steps. |
| Download management | Main settings entry, queue dialog, status Toasts, and current track/artwork download labels now come from `AppLanguage.java`. | Continue with provider-specific authenticated download copy when that feature lands. |
| Library grouping defaults | `LibraryGrouping.kt` no longer owns Chinese group titles/count labels; group render paths use `AppLanguage.java` and preserve Chinese defaults for legacy callers. | Keep new library grouping copy in `AppLanguage.java`; add focused tests when adding new group modes. |
| Home playback continuation | `HomeDashboardViewModel.kt` no longer owns Chinese continuation copy; playback events pass `settingsStore.languageMode()` from `MainActivity.java`. | Keep new homepage playback copy in `AppLanguage.java`; update `HomeDashboardViewModelTest.kt` when the continuation card changes. |
| Queue accessibility | Queue drag-to-reorder text now comes from `QueueScreenLabels` and `AppLanguage.java` in both ViewModel and legacy render-controller paths. | Keep future queue action labels in `QueueScreenLabels`; extend `QueueViewModelTest.kt` for language-sensitive labels. |
| Shared page scaffold | `EchoPageTitle` uses caller-provided `backLabel` for localized pages and has an English fallback only for legacy callers. | Keep new pages passing localized `backLabel` from their render state rather than relying on the fallback. |
| Legacy playlist list | `PlaylistListScreen.kt` no longer embeds Chinese rename/delete labels; the active collections path passes localized labels through row actions. | Prefer `CollectionsScreen.kt` for current playlist UI and keep any future legacy reuse passing localized labels. |
| Streaming gateway/provider status | Local login, gateway fallback, and local NetEase account/playlist errors now use canonical English in the streaming data layer and localized labels in `StreamingSearchScreen.kt`. | Keep new provider/gateway status messages as canonical strings or structured states, then map them at the UI boundary. |
| Package/technical identifiers | `app.yukine` remains package/application id. | User-facing brand copy is being normalized to `YUKINE`; technical `Echo*` class/theme names and existing User-Agent/header strings are tracked separately to avoid breaking code/API contracts. |

## Brand Naming

The project brand is `YUKINE`. Current user-facing fixes include:

- Homepage title now comes from `AppLanguage` key `app.name` and renders `YUKINE`.
- Widget empty title, notification fallback title, playback notification channel
  description, onboarding brand copy, artwork fallback, default generated
  playlist names, M3U fallback title, mock streaming display copy, and download
  directory fallback now use `YUKINE`.
- Android `app_name` and visible theme preset copy now use `YUKINE`.
- Download and streaming-login playlist dialogs use `AppLanguage.java` keys
  instead of hard-coded Chinese strings in `MainActivity.java`.
- Onboarding brand copy uses `AppLanguage` key `app.name`, so the first-run
  flow follows the same `YUKINE` identity as the rest of the app.
- Library grouping now maps standard unknown metadata such as unknown album,
  artist, and track labels through the active language mode, avoiding Chinese
  fallback text in English mode.

## Verification

Commands run successfully:

```powershell
./gradlew.bat --no-daemon :app:checkMojibake --console=plain
./gradlew.bat --no-daemon :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
./gradlew.bat --no-daemon :app:testDebugUnitTest --tests app.yukine.AppLanguageOnboardingTest --console=plain
./gradlew.bat --no-daemon :app:testDebugUnitTest --tests app.yukine.LibraryGroupingTest --tests app.yukine.LibraryGroupsRenderControllerTest --console=plain
./gradlew.bat --no-daemon :app:testDebugUnitTest --tests app.yukine.HomeDashboardViewModelTest --tests app.yukine.TrackListRenderControllerTest --console=plain
./gradlew.bat --no-daemon :app:testDebugUnitTest --tests app.yukine.queue.QueueViewModelTest --console=plain
./gradlew.bat --no-daemon :app:testDebugUnitTest --tests app.yukine.LibraryGroupsRenderControllerTest --tests app.yukine.LibraryPlaylistsRenderControllerTest --console=plain
./gradlew.bat --no-daemon :app:testDebugUnitTest --tests app.yukine.StreamingSearchScreenStatusTest --console=plain
./gradlew.bat --no-daemon :app:testDebugUnitTest --tests app.yukine.StreamingSearchScreenStatusTest --tests app.yukine.streaming.RemoteStreamingGatewayTest --console=plain
./gradlew.bat --no-daemon :app:testDebugUnitTest --console=plain
./gradlew.bat --no-daemon :app:assembleDebug --console=plain
./gradlew.bat --no-daemon :app:compileDebugKotlin :app:compileDebugJavaWithJavac :app:testDebugUnitTest :app:assembleDebug --console=plain
```

Focused UTF-8 scan for known mojibake fragments returns no source hits outside
the intentional detector configuration in `app/build.gradle`.

Focused scan for user-visible `Echo`/`ECHO` string literals now leaves only
technical identifiers such as User-Agent values, HTTP headers, and log tags.

Focused scan for repeated homepage Chinese copy in
`HomeDashboardScreen.kt`, `HomeDashboardStateFactory.kt`, and
`DashboardRepository.kt` now returns only comments; user-facing strings are
provided through `HomeDashboardUiState` and `AppLanguage.java`.

Focused UTF-8 scan for raw Chinese in `OnboardingScreen.kt` returns zero hits.
`AppLanguageOnboardingTest.kt` covers key onboarding brand/action labels and the
localized missing-setup separator.

Focused UTF-8 scan for raw Chinese in `LibraryGroupsRenderController.kt` returns
zero hits. `LibraryGroupingTest.kt` and `LibraryGroupsRenderControllerTest.kt`
cover localized unknown metadata, track/album counts, group empty states,
favorites group copy, and group detail actions.

Focused UTF-8 scan for raw Chinese in `HomeDashboardViewModel.kt` and
`TrackListRenderController.kt` returns zero hits. `HomeDashboardViewModelTest.kt`
and `TrackListRenderControllerTest.kt` cover the English playback continuation
copy and recommendation track metric label.

Focused UTF-8 scan for raw Chinese in `StreamingPlaylistController.kt`,
`QueueScreen.kt`, `QueueRenderController.kt`, and `queue/QueueViewModel.kt`
returns zero hits. `QueueViewModelTest.kt` covers Chinese and English
drag-to-reorder labels.

Focused UTF-8 scan for raw Chinese in `LibraryGroupsScreen.kt`,
`LibraryGroupsRenderController.kt`, `LibraryPlaylistsRenderController.kt`, and
`EchoPageScaffold.kt` returns zero hits. `LibraryGroupsRenderControllerTest.kt`
covers the localized play-button label.

Focused UTF-8 scan for raw Chinese in `PlaylistListScreen.kt` returns zero hits;
the current collections render path provides rename/delete labels via
`PlaylistRowActions`.

Focused UTF-8 scan for raw Chinese in `LocalStreamingAuthStore.kt` returns zero
hits; focused scan for `streaming/StreamingGateway.kt` now leaves only comments
mentioning NetEase recommendation concepts. `StreamingSearchScreenStatusTest.kt`
covers localized UI mapping for local-login and gateway fallback statuses.

Focused UTF-8 scan for raw Chinese in `LocalNeteaseStreamingClient.kt` now leaves
only comments and provider-matching heuristics; user-visible NetEase error copy
is routed through `AppLanguage.java`. `StreamingSearchScreenStatusTest.kt`
covers localized UI mapping for those canonical NetEase provider errors.

`:app:check` currently reaches `:app:testReleaseUnitTest` and then fails 29
release-only Robolectric/Compose destination tests with
`Unable to resolve activity ... androidx.activity.ComponentActivity`. The same
debug unit-test suite and debug APK build pass; this appears to be the existing
release unit-test environment for Compose destination tests rather than a
mojibake/localization regression.

Manual device walkthrough is still pending: the SDK `adb.exe` exists at
`C:\Users\31283\AppData\Local\Android\Sdk\platform-tools\adb.exe`, but
`adb devices` currently reports no connected device or emulator.
