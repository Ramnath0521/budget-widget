# Reproducible build

From 1.0.3 the release APK can be rebuilt from source and compared with the one on GitHub. Only
the signature differs. This is what F-Droid and IzzyOnDroid check, and it lets anyone confirm that
the published APK is exactly this source with nothing added (docs/DECISIONS.md ADR-0050).

## What makes it reproducible

- The build has no timestamps, random values or paths in its output: AGP writes fixed zip
  timestamps, R8 and dex are deterministic, and the baseline profile is checked in, not generated
  during the build.
- No git commit file in the APK (`vcsInfo.include = false`): the source archive has no `.git`.
- No dependency list encrypted with Google's key (`dependenciesInfo` off).
- Gradle verifies every downloaded dependency against `gradle/verification-metadata.xml`, and
  the wrapper checks the Gradle distribution's SHA-256.

## Rebuild a release and compare

Needs JDK 21 (Temurin, as CI uses), the Android SDK (platform 37, build-tools 36.0.0) and git.

```sh
tag=v1.0.3
gh release download "$tag" -R SwashBuckler104/budget-widget \
  -p budget-widget.apk -p "budget-widget-$tag-source.tar.gz"

# The source archive holds the parser-core submodule too (AGPL source offer).
tar -xzf "budget-widget-$tag-source.tar.gz"
cd "budget-widget-$tag-source"
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleRelease --no-build-cache --no-configuration-cache

python3 -I scripts/compare-apks.py ../budget-widget.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

`compare-apks.py` compares every zip entry by SHA-256 and ignores only signature data (the v2/v3
signing block, and v1 signature files if any). Expected output: `N entries identical`, exit 0.
F-Droid's `apksigcopier` gives the same answer by copying the signature onto the unsigned build.

Also check where the APK came from:

```sh
gh attestation verify budget-widget.apk -R SwashBuckler104/budget-widget
```

This checks the SLSA provenance that the release workflow signs: this repository's
`release.yml`, at the tagged commit, built this exact file.

## Checked

- v1.0.2 (built before this was set up): a local build from its sources matched the published APK
  in all 1,123 entries except `META-INF/version-control-info.textproto`, the git commit file that
  1.0.3 drops. Built with `--no-build-cache --rerun-tasks`, at a different path, from files
  without `.git`.
- The 1.0.3 build from a copy of the tracked files at another path is byte-identical to the build
  in the repository.

## When it breaks

Look at the entries `compare-apks.py` lists. The usual causes: a different JDK or build-tools
version, a generated file with a timestamp or absolute path, or a plugin that adds build
metadata. Fix the cause in the build (never by editing the APK), add the case here, and rebuild.
