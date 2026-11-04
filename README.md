# PetalClock

A blue petal live wallpaper for Android with a big, soft, floating clock. It wakes from thin outlines every time you turn on the screen, and the petals blur on the home screen. Inspired by the HyperOS clock animation.

**Download:** https://petalclock.houseli.site

Direct APK: https://github.com/Houssam-2021/PetalClock/releases/latest/download/petalclock.apk

- Android 12 (API 31) or newer, no root needed
- No ads, no account, no data collection

## Build

Open this folder in Android Studio (JDK 17+, Android SDK 36) or run:

    ./gradlew assembleDebug

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Notes

- Samsung's system Always On Display is not replaced; the wallpaper only draws while its surface is visible.
- The website lives in `docs/` and is deployed as a Cloudflare Worker with static assets (`npx wrangler deploy`).

## Fingerprint effect (Android 13+)

A liquid glass lens sits under the fingerprint icon on the lock screen; on unlock it swells into a water drop and a
chromatic wave ripples out. It is drawn as one AGSL shader over the finished frame (`FingerprintFx.kt`), can be moved,
resized or switched off in the app, and `CHROMATIC_FX` in `app/build.gradle` turns it off entirely.

## Releasing

Push a tag such as `v1.2` (or use *Run workflow* on the *Release APK* action). The workflow signs the APK with the
release key from the repository secrets, checks it matches the key that signed v1.0, and publishes `petalclock.apk`
as the latest release, which the website's download button serves.
