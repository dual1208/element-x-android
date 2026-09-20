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

Gradle writes the arm64 artifact to
`app/build/outputs/apk/gplay/debug/app-gplay-arm64-v8a-debug.apk`. It installs
as `io.element.android.x.debug`, keeping its data and identity separate from
the release package `io.element.android.x`. The install recipe uses
`adb install -r -d` and never uninstalls the existing application.

This fork build reports version `26.09.2-family.1`. The arm64 APK version code
is `202609022`, so it upgrades the previously installed `202609012` build while
preserving its application data.

Analytics and crash-reporting providers are removed from the managed family
build graph. Local diagnostic logs and the explicit administrator bug-report
flow remain available, but the app does not silently send analytics or crash
events to Element production services.

The gplay build is appropriate for the target OnePlus device because Google
Play services are present. The existing session previously completed Matrix
sync and MAS login through `https://8.163.2.191/auth/` on Android 11 (API 30);
the managed-family upgrade still requires physical install and launch
verification.

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
