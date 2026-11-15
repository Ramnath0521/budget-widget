# Security and privacy audit

Date: 2026-11-15. Commit audited: `9d9e358` (main, v1.0.1 / versionCode 3). APK audited:
`app/build/outputs/apk/release/app-release-unsigned.apk` built locally from that commit (3.7 MB, R8 on).

Claim under test: the app only reads bank SMS on the phone, parses and categorises them, fully
offline, with nothing another app or a bad actor could reuse to get data out.

**Verdict: the claim holds.** No high-severity finding. Three medium items (OTP bodies kept in the unparsed list, screen capture,
unencrypted backup files) and a few low ones, listed at the end.

## Results

| # | Area | Result |
|---|---|---|
| 1 | Merged manifest permissions | PASS |
| 2 | Dependencies | PASS |
| 3 | Exported components and intents | PASS |
| 4 | Data at rest | PASS with notes (F2, F3) |
| 5 | Leaks (logs, lock screen, recents) | PASS with one gap (F1) |
| 6 | Code, scripts, CI, keys | PASS with notes (F4, F5) |
| 7 | Submodule integrity | PASS |
| 8 | MobSF | NOT RUN (no local image; see below) |

## 1. Merged manifest (release APK)

`aapt2 dump permissions` on the release APK:

```
android.permission.RECEIVE_SMS
android.permission.READ_SMS
android.permission.POST_NOTIFICATIONS
android.permission.WAKE_LOCK
android.permission.FOREGROUND_SERVICE
<pkg>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION   (declared + used, signature-level, androidx.core)
```

- No `INTERNET`, no `ACCESS_NETWORK_STATE`, no `RECEIVE_BOOT_COMPLETED`, no `SEND_SMS`/`WRITE_SMS`,
  no storage, contacts, location or `QUERY_ALL_PACKAGES`.
- WorkManager's `RECEIVE_BOOT_COMPLETED` and `ACCESS_NETWORK_STATE` are removed in
  `app/src/main/AndroidManifest.xml` (ADR-0013). WorkManager's `RescheduleReceiver` still lists a
  `BOOT_COMPLETED` filter, but it is not exported and without the permission it never fires.
- `scripts/check-permissions.sh` (allowlist, run in CI and in the release workflow) passes on both
  debug and release APKs.
- `allowBackup=false`, not debuggable, no `usesCleartextTraffic`, no `networkSecurityConfig`.

## 2. Dependencies

`:app:dependencies --configuration releaseRuntimeClasspath`: 193 unique artifacts.

- All are `androidx.*`, `org.jetbrains.*` (Kotlin, coroutines, kotlinx-datetime, serialization),
  `com.google.dagger` (Hilt), plus: `okio` (DataStore file I/O, not OkHttp),
  `sh.calvin.reorderable`, `jsr305`, `jspecify`, `javax.inject`, `jakarta.inject`,
  `guava:listenablefuture` (empty marker artifact).
- Grep for okhttp, retrofit, ktor, volley, firebase, gms, play-services, sentry, crashlytics,
  analytics, ads, cronet, grpc, coil, glide: **zero hits**.
- Licences: everything is Apache-2.0 (jsr305 is BSD-3). Both are one-way compatible with AGPL-3.0.
  parser-core is AGPL-3.0, same as the app.
- Release dex string scan: no `java.net.URL/Socket/HttpURLConnection`, no `javax.net`, no
  `android.webkit`, no `DexClassLoader/PathClassLoader/InMemoryDexClassLoader`. Only
  `ConnectivityManager` appears (WorkManager's constraint tracker, inert without the permission).
  URLs in the dex are doc links in library error strings, two regexes from upstream bank parsers,
  and the two GitHub links shown on the About screen.

## 3. Exported components

| Component | Exported | Guard | Risk |
|---|---|---|---|
| `MainActivity` | yes (launcher) | none needed | Accepts one deep link, compared by equality to the Analysis URI; it only switches tab. No extras are read. |
| `QuickSortActivity` | no | - | - |
| `SmsReceiver` | yes | `android.permission.BROADCAST_SMS` (system only) | Other apps cannot send it fake SMS. |
| `QuickSortWidgetReceiver`, `InsightWidgetReceiver` | yes | `APPWIDGET_UPDATE` only | Standard for widgets. Only triggers a re-render. |
| `NotificationActionReceiver`, `DayTickReceiver` | no | - | Reached only by our own PendingIntents and system broadcasts. |
| Glance `GlanceRemoteViewsService`, `RemoteViewsCompatService` | yes | `BIND_REMOTEVIEWS` (system) | Library standard. |
| WorkManager `SystemJobService` | yes | `BIND_JOB_SERVICE` (system) | Library standard. |
| `DiagnosticsReceiver`, `ProfileInstallReceiver` | yes | `android.permission.DUMP` (shell/system) | Library standard. |
| `InitializationProvider` | no | - | No `FileProvider`, no other content provider. |

- No app can trigger an SMS read or pull data: there is no exported provider, no bound service of
  ours, and no activity that returns data.
- Every `PendingIntent` (5 call sites in `:notifications`) uses `FLAG_IMMUTABLE`. Notification
  commands carry only a transaction id and a category id, re-validated on receipt
  (`NotificationCommand.kt:45`).
- No `FileProvider`. Export and backup use the Storage Access Framework
  (`CreateDocument`/`OpenDocument` in `SettingsScreen.kt:100-106`), so the app never holds a
  storage permission and the user picks the destination.
- Outgoing intents: only `ACTION_VIEW` to the two fixed GitHub URLs on the About screen.

## 4. Data at rest

- Room database and DataStore live in app-private internal storage
  (`/data/data/<pkg>/databases`, `/files/datastore`). Protected by the Android sandbox and
  file-based encryption. Plain SQLite (no SQLCipher).
- Cloud backup and device transfer are excluded for every domain (`data_extraction_rules.xml`,
  `backup_rules.xml`) on top of `allowBackup=false`.
- CSV export guards against spreadsheet formula injection (`DataTransfer.kt:230`).
- Restore parses a versioned JSON file, rejects newer versions, and does not log parse errors
  (they could quote the file).
- **F2**: backup JSON and CSV are plaintext wherever the user saves them (already disclosed in
  PRIVACY.md).
- **F3**: stored SMS bodies are plaintext in the private DB. See "DB encryption" below.

**Is DB encryption worth it?** No, not as SQLCipher. It adds a ~4 MB native library per ABI
(the APK is 3.7 MB today), a larger audit surface, and a key that sits in the Keystore of the same
unlocked device. It only helps against root or forensic access to an unlocked phone, where the
SMS inbox itself is equally readable. Better value: encrypt the *exported* backup (F2), and offer
to drop raw SMS bodies after N days (F3).

## 5. Leaks

- Logging: one `Log.w` in the whole app (`core/data/.../inbox/InboxEvents.kt:28`), which logs a
  listener class name and an exception. No SMS body or amount is logged anywhere, debug or release.
  No `println`, no `printStackTrace`.
- Lock-screen notifications: every transaction and summary notification is
  `VISIBILITY_PRIVATE` with a `setPublicVersion` that has only a generic title
  (`TransactionNotificationFactory.kt:51-52, 84-85, 102-106`). With the phone set to "hide
  sensitive content", no amount, merchant or account shows. With the system set to "show all
  content" Android shows the full notification; that is the user's system choice.
- Widgets: both declare `widgetCategory="home_screen"` only, so they cannot be placed on the lock
  screen through the standard API.
- Clipboard: "copy" on an unparsed SMS sets `EXTRA_IS_SENSITIVE` (`UnparsedScreen.kt:234`).
- **F1**: no `FLAG_SECURE` or `setRecentsScreenshotEnabled(false)`. Amounts are visible in the
  recents thumbnail, in screenshots, and to screen recording or casting.

## 6. Code, scripts, CI, keys

- No dynamic code loading, no `Class.forName`, no `System.load*`, no `Runtime.exec`, no WebView in
  our sources (grep over all non-test Kotlin outside `third_party/`).
- Native libraries in the APK: `libandroidx.graphics.path.so` (Compose) and
  `libdatastore_shared_counter.so` (DataStore). Both are AndroidX, 5-11 KB each. No other blobs;
  `assets/` holds only the baseline profile.
- Tracked binaries: only `gradle/wrapper/gradle-wrapper.jar`, with `distributionSha256Sum` pinned.
- No keystore, `.jks`, `.pem`, `.p12`, `.env` or `google-services.json` is tracked or was ever
  added in git history. History scan for private keys, tokens and inline passwords: no hits.
  `.gitignore` covers `*.jks`, `*.keystore`, `keystore.properties`, `local.properties`.
- Signing: only from environment variables (`app/build.gradle.kts:18-30`), supplied by GitHub
  secrets in `release.yml`. The keystore is decoded to `RUNNER_TEMP` and removed in an
  `if: always()` step.
- Workflows: all third-party actions are pinned to full commit SHAs. `ci.yml` has
  `contents: read` and runs on `pull_request` (not `pull_request_target`), so fork PRs get no
  secrets. `release.yml` runs only on `v*` tag push, with `contents: write` scoped to the release
  job. The Gradle cache is read-only in the release job. CI uploads the debug/unsigned APK as a
  workflow artifact, nothing else.
- `scripts/`: five local check scripts (permissions, module boundaries, Hilt, R8 reflection) and
  `capped.sh`. None touch the network or secrets.
- **F4**: no Gradle dependency verification file (`gradle/verification-metadata.xml`), so a
  tampered artifact on a Maven mirror would not be detected at build time.
- **F5**: the release has a SHA-256 file but no build provenance attestation, and the build is
  not yet confirmed reproducible.

## 7. Submodule integrity

- `.gitmodules` URL: `https://github.com/sarim2000/pennywiseai-tracker` (upstream PennyWise).
- Pinned commit `0623c2b94c2d61c2a30a9068c5525bde83f6a46c`. GitHub's API returns the same commit
  with tree `671643eec03de891a0a95959f68e09561aa20b33`; the local `HEAD^{tree}` is identical.
  Upstream `main` contains it (`behind_by=0`, main is 149 commits ahead).
- Working tree clean (`git status --porcelain` empty), `git fsck` clean.
- `parser-core/build.gradle.kts` compiles the upstream source directories directly, with no patch
  step. Its only runtime dependency is `kotlinx-datetime`.
- Note for the plan (not a security issue): upstream has a branch `feat/transfer-loan-direction`
  and 149 newer commits. Worth reading before designing the loan/transfer work.

## 8. MobSF

Not run. There is no MobSF image on this machine and pulling it (about 1.5 GB) was not worth the
budget given the manual results above. To run it later, offline after the pull:

```sh
docker run --rm -p 127.0.0.1:8000:8000 --network none opensecurity/mobile-security-framework-mobsf
```

(`--network none` blocks the UI port too; use a user-defined internal network if you need the UI.)
Expected noise: "app can read SMS", exported widget receivers, and the unsigned-APK warning.

## Trust statement

> Budget Widget cannot send your data anywhere. The app does not have Android's internet
> permission, so the operating system itself blocks every network connection: no cloud, no
> analytics, no ads, no crash reports. It asks for two things only: reading SMS (to find bank
> transaction messages) and showing notifications. Personal messages and one-time passwords are
> never stored. Everything stays in the app's private storage and is excluded from Google backup.
> The code is open source (AGPL-3.0), every release is checked automatically against a fixed
> permission list, and you can verify the permissions yourself in
> Settings > Apps > Budget Widget > Permissions.

## Fixes for the implementer (ranked)

| ID | Severity | Fix |
|---|---|---|
| F9 | Medium | Found in device testing (TEST_REPORT.md item 6): for **known** bank senders every unparsed body is stored, including OTPs, promos and reminders (`IngestPipeline.kt:99` applies `FinancialSmsHeuristic` to unknown senders only). Never store a body that matches the OTP/secret pattern, and keep a known-sender `NO_MATCH` body only if it looks like a transaction. Add a migration that deletes existing rows that fail the same test. |
| F1 | Medium | Screen privacy. Default: `setRecentsScreenshotEnabled(false)` on API 33+ for `MainActivity` and `QuickSortActivity` (hides the recents thumbnail, keeps screenshots working). Add a Settings toggle "Block screenshots" that sets `FLAG_SECURE` on both activities (default off; it also blocks the user's own screenshots). |
| F2 | Medium | Password-protected backup. Optional passphrase on JSON backup: PBKDF2-HMAC-SHA256 (>= 600k iterations) to AES-256-GCM using `javax.crypto` only (no new dependency). Versioned header, restore detects encrypted files and asks for the passphrase. CSV stays plaintext with a warning line in the dialog. |
| F3 | Low | SMS body retention. Setting "Keep original SMS text: forever / 90 days / 30 days"; a periodic worker nulls `smsBody` on older, already-sorted transactions. Do not add SQLCipher (see section 4). |
| F4 | Low | Add `gradle/verification-metadata.xml` (`./gradlew --write-verification-metadata sha256 help`, then the CI task set so all configurations are covered). Document the refresh step in CONTRIBUTING.md. |
| F5 | Low | Release provenance: add `actions/attest-build-provenance` (needs `id-token: write`, `attestations: write` on the release job) and work toward a reproducible build (required by F-Droid anyway; see PLAN.md section D). |
| F6 | Info | Checked on 2026-11-15: the phone runs the **signed release** v1.0.1 from GitHub (certificate SHA-256 `e99e72ef...b0e7`, not debuggable), so `run-as` cannot read its data. Keep it that way: never install a debug-signed build on a phone with real data (see PLAN.md "Updating the phone"). |
| F7 | Info | `InboxEvents.kt:28`: log only the exception class, not the full throwable, so a future listener cannot leak data through an exception message. |
| F8 | Info | Add a CI check that the release dex contains no `Ljava/net/`, `Ljavax/net/`, `Landroid/webkit/` or `Ldalvik/system/*ClassLoader` references from our own packages (extends `check-permissions.sh` from "cannot" to "does not try"). |
