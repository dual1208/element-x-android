# Local Android deployment

This checkout defaults new sign-ins to `https://8.163.2.191`. The account
provider field remains editable, and `matrix.org` is still offered by account
provider autocomplete.

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

The Gradle recipes use at most two workers and a 3 GiB heap so they are safe to
run on the development Mac. Build logs and device evidence are written under
the ignored `build/codex-logs/` directory.

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

The tested artifact is
`app/build/outputs/apk/gplay/debug/app-gplay-arm64-v8a-debug.apk`. It installs
as `io.element.android.x.debug`, keeping its data and identity separate from
the release package `io.element.android.x`. The install recipe uses
`adb install -r -d` and never uninstalls the existing application.

The gplay build is appropriate for the tested OnePlus device because Google
Play services are present. Foreground Matrix sync and login through the MAS
issuer at `https://8.163.2.191/auth/` have been verified on Android 11 (API
30).

## Push notification limitation

This checkout includes Firebase and UnifiedPush code, but its
`PUSHER_APP_ID_RELEASE`, `PUSHER_APP_ID_DEBUG`, and `PUSHER_APP_ID_NIGHTLY`
values are unset. A fork-specific Firebase project, FCM credentials, and
Matrix push gateway have not been configured or verified. Foreground sync
works while the application is open; reliable background notifications should
not be expected until those services are configured. See
[`docs/notifications.md`](docs/notifications.md) for the push gateway and
background execution model.
