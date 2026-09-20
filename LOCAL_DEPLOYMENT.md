# Local Android deployment

This checkout is a managed family build fixed to `https://8.163.2.191` and the
private Family room configured in `ManagedFamilyConfig`. Registration, QR
login, homeserver selection, room creation, room-directory navigation, and
opening other rooms are hidden or guarded. Existing sessions are restored in
place; a session from another homeserver is retained and shown on the repair
screen instead of being logged out or reset.

The managed first-time flow requires both a verified session and enabled
recovery before Family content can open. Disabled recovery routes to the
existing setup flow; incomplete recovery routes to recovery-key entry. Identity
reset is hidden and guarded in this build. After those checks complete, the app
waits for sync, accepts an invitation only for the configured Family room, and
opens that room. A ready joined session opens the same room directly on restart.
Back navigation returns to a small repair screen with Settings, where
verification, recovery, notification, and developer diagnostics remain
available to the administrator.

The build does not automate cryptographic identity creation or recovery. Those
remain one-time supervised Element X flows: verify the new device, configure or
restore secure backup, and retain the recovery material outside the APK. No
password, access token, administrator token, or recovery secret is embedded.

The deployment was verified from upstream Element X Android v26.09.1
(`18bb4632f2`) with the published Matrix Rust SDK Android binding
`org.matrix.rustcomponents:sdk-android:26.08.25`. It does not require a local
Rust SDK build.

## Prerequisites

- A physical Android device visible to `adb devices -l`. Set its serial in
  `ANDROID_SERIAL` before running `device`, `install`, `launch`, or `deploy`.
  Those recipes reject emulators.
- JDK 21 installed at
  `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
- Android SDK Platform 37 and a working `just` installation.

The Gradle recipes use one worker, macOS background scheduling at nice level
15, and a 3 GiB heap to reduce contention on the development Mac. Build logs
and device evidence are written under the ignored `build/codex-logs/`
directory.

## Commands

```sh
export ANDROID_SERIAL='<physical-device-serial>'
just device   # Verify the selected physical device.
just build    # Build the arm64 gplayDebug APK.
just install  # Build and install in place, preserving existing app data.
just launch   # Cold-start the installed debug application.
just deploy   # Build, install, and launch.
just lint     # Run ktlint checks.
just test     # Run the login feature unit tests.
just ci       # Run lint, tests, and the APK build.
```

The verified arm64 artifact is
`app/build/intermediates/apk/gplay/debug/app-gplay-arm64-v8a-debug.apk`. It installs
as `io.element.android.x.debug`, keeping its data and identity separate from
the release package `io.element.android.x`. The install recipe uses
`adb install -r -t` and never uninstalls the existing application. The `-t`
flag is required because this debug APK is marked `testOnly`.

This fork build reports version `26.09.2-family.2`. Both verified APK manifests
use version code `202609030`, so the arm64 APK upgrades the previously installed
`202609022` build while preserving its application data.

Analytics and crash-reporting providers are removed from the managed family
build graph. Local diagnostic logs and the explicit administrator bug-report
flow remain available, but the app does not silently send analytics or crash
events to Element production services.

The gplay build is appropriate for the target OnePlus device because Google
Play services are present. The existing session previously completed Matrix
sync and MAS login through `https://8.163.2.191/auth/` on Android 11 (API 30).

## Physical verification

The family.2 arm64-only cached build completed successfully in 4 minutes 58
seconds with one Gradle worker and the background resource policy. The full
multi-output packaging attempt had exposed an AGP 9.3.1 `ApkFlinger` signing
deadlock, so the reproducible build recipe uses AGP's target-ABI properties to
package only the physical device's `arm64-v8a` output. No clean or test rerun is
needed for that packaging retry.

The final ignored artifact staging directory is
`build/codex-artifacts/family2/`. It contains binary-truth combined
`output-metadata.json` plus these verified APKs:

- `app-gplay-arm64-v8a-debug.apk`: 161,799,942 bytes, SHA-256
  `55db951e2561d37b7079d8fac0fa0e9d817c95c51e1889636cfbfcadfca514b9`.
- `app-gplay-universal-debug.apk`: 410,023,078 bytes, SHA-256
  `738b2138ad524ca212074bad0df50bb18453cd1bce8d553fd89f7ee9f574b030`.

Both APKs report package `io.element.android.x.debug`, version
`26.09.2-family.2`, manifest version code `202609030`, minimum SDK 24, a valid
ZIP and 16 KiB page alignment, and a valid APK Signature Scheme v2 signature.
Their signing-certificate SHA-256 is
`b0b051dc565c812fe17f6f3e945b4d79047123ab0da61286769eb2949197130e`,
matching the previously installed build. The arm64 APK contains only
`arm64-v8a` native libraries.

AGP's arm64-only `output-metadata.json` reported split code `202609032`, while
both `aapt dump badging` and the binary Android manifest report `202609030`.
The combined deployment metadata therefore records the verified binary value
`202609030`; the unmodified Gradle metadata is retained at
`build/codex-logs/output-metadata-family2-arm64-gradle.json` for diagnosis.

The family.2 arm64 APK was installed in place on the physical OnePlus 6,
Android 11 (API 30), with `adb install -r -t`. The installed package reports
version `26.09.2-family.2` and version code `202609030`. A cold launch completed
successfully and left `io.element.android.x.MainActivity` in the foreground.
No UI tap, screenshot, account-data inspection, recovery action, logout,
message, or call was performed during family.2 verification.

### Previous family.1 recovery-gate evidence

`assembleGplayDebug` completed successfully with the one-worker background
policy. The arm64 APK is 161,799,458 bytes and has SHA-256
`bdc6d53b41a1a4cbc1c4b5b6033e9c0863d33bdd49edf51ee36d3d0e9d961097`.
Its signing-certificate SHA-256 is
`b0b051dc565c812fe17f6f3e945b4d79047123ab0da61286769eb2949197130e`,
which matches the previously installed build.

The APK was installed on the physical arm64 Android 11 device with
`adb install -r`; package data was retained, and a cold launch resumed the
existing account in `io.element.android.x.MainActivity`. The installed package
reported version `26.09.2-family.1` and version code `202609022`. The managed
crypto gate presented the existing supervised **Get recovery key** setup before
opening Family. During verification, one automated coordinate tap
(`500,1060`) was attempted. Semantic bounds were not recorded before the tap;
scaled screenshot evidence places that point near or just above the
**Generate your recovery key** control, so whether its event was invoked is
unknown. After 30 seconds the screen still showed the same control, no recovery
key was visible, and no local key file had been written. If invoked, the
control calls `enableRecovery`, so the unchanged client UI alone does not prove
that server-side recovery state was untouched; recheck the account recovery
metadata before any later setup attempt. No identity-reset or
change-recovery-key action was invoked, and the application was not uninstalled
or cleared.

A subsequent read-only server check found the `api30` backup still at version
1 with ETag 2 and two sessions, and its cross-signing stream IDs still at
2/3/4. Both task accounts had zero `m.secret_storage.default_key` rows and zero
`m.secret_storage.key.*` rows. No persisted secret-storage or backup change was
detected. Because pre-attempt secret-storage fingerprints were not recorded,
this evidence cannot exclude transient client-side work.

Ignored verification evidence is under `build/codex-logs/`, including the
family.2 targeted test, packaging, staged-APK verification, raw metadata, and
the earlier family.1 install, launch, signing, and non-secret recovery-gate
records.

## Push notification limitation

This managed build disables the bundled upstream Element Firebase provider and
uses fork-specific public pusher app IDs. A fork-specific Firebase project,
FCM credentials, and Matrix push gateway have not been configured or verified.
Specifically, this deployment still needs a Firebase Android app and
`google-services.json`, an FCM HTTP v1 service account credential on its push
gateway, and a deployed push gateway URL before Firebase can be re-enabled.
The existing source otherwise falls back to Element's `vector-alpha` Firebase
resources and `matrix.org` push gateway; those are deliberately excluded from
this family build. Foreground sync
works while the application is open; reliable background notifications should
not be expected until those services are configured. See
[`docs/notifications.md`](docs/notifications.md) for the push gateway and
background execution model.
