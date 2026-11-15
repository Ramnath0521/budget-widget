# Contributing

Thanks for helping. Budget Widget is a small, privacy-first app, so most contributions are bank
SMS formats, bug fixes and polish. Read [docs/SCOPE.md](docs/SCOPE.md) before proposing features:
v1 scope is fixed.

## Setup

- JDK 21, an Android SDK (platform 37), git.
- `git clone --recurse-submodules ...`, or run `git submodule update --init` after cloning.
- `local.properties` with `sdk.dir=...`, or `ANDROID_HOME`.

```sh
./gradlew spotlessApply                                 # format
./gradlew spotlessCheck lint test assembleRelease       # everything CI runs (plus the scripts below)
scripts/check-permissions.sh app/build/outputs/apk/release/app-release-unsigned.apk
scripts/check-release-apk.sh app/build/outputs/apk/release/app-release-unsigned.apk   # not debuggable, no network code
scripts/check-module-boundaries.sh
scripts/check-no-logging.sh                             # no Log/println outside the reviewed allowlist
```

## Dependency verification

Gradle checks the SHA-256 of every dependency and plugin it downloads against
`gradle/verification-metadata.xml`, so a tampered artifact on a mirror fails the build. After
adding or updating a dependency, a plugin or the Android Gradle Plugin, refresh it with the same
task set CI and the release run, then review the diff (new entries should only be the artifacts
you meant to add, from the expected group):

```sh
./gradlew --write-verification-metadata sha256 \
  spotlessCheck lint test assembleDebug assembleRelease :app:bundleRelease :baselineprofile:assemble
git diff gradle/verification-metadata.xml
```

Never edit hashes by hand, and never add a `trust` rule to silence a failure. AAPT2 is a
per-OS artifact, so a first build on macOS or Windows may need the same refresh; commit the extra
entries. IDE source and javadoc downloads are trusted by name only (they never reach the APK).

## Ground rules

- **No network.** No `INTERNET` permission, and no analytics, crash reporting, ads, Firebase or network libraries. CI fails on any permission not listed in `config/permissions-allowlist.txt`.
- **Money is `Long` paise.** Never `Double`/`Float`.
- **Only `:ingest` touches the parser** (`com.pennywiseai.*`). Everything else uses our own `SmsParser` / `ParsedSms` types.
- **Never edit `third_party/`.** Parser fixes go in our own rule layer in `:ingest` and, when they belong upstream, to the PennyWise project; see [docs/PARSER_IMPROVEMENT.md](docs/PARSER_IMPROVEMENT.md) and [docs/PARSER_UPGRADE.md](docs/PARSER_UPGRADE.md).
- **Store only financial SMS.**
- Warnings are errors: Kotlin, lint and ktlint.

## Workflow

- Branch from `main`: `m<N>-<slug>` for milestones, `fix/<slug>` or `feat/<slug>` otherwise.
- [Conventional Commits](https://www.conventionalcommits.org/): `feat(widget): ...`, `fix(ingest): ...`, `test: ...`, `build: ...`, `ci: ...`, `docs: ...`. One logical change per commit, and the message says what changed and why.
- Logic gets unit tests before UI depends on it.
- Decisions the spec doesn't cover go in [docs/DECISIONS.md](docs/DECISIONS.md).

## Reporting a bank SMS the app doesn't understand

Use the **Bank SMS format** issue template. Maintainers turn each report into a golden test case in
`ingest/src/test/resources/golden/` (format in its README). **Anonymize first:** replace names, account and card
digits, UPI IDs, reference numbers and balances with fake values of the same shape (for example,
keep `XX1234` as `XX` plus 4 digits). Keep the sender ID (such as `AD-HDFCBK`) and the wording
exactly as they are.

## License

By contributing, you agree that your contributions are licensed under the
[AGPL-3.0](LICENSE), the same as the project.
