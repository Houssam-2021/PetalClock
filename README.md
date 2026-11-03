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

## Experimental build

`./gradlew assembleExperimental` builds **Petal Clock Experimental** (`com.blurify.clockwall.reference.experimental`),
a separate app that installs next to the normal one. It adds a glass fingerprint lens on the lock screen and a
chromatic shock wave from it when you unlock (Android 13+). Its launcher screen can turn the effect off and move or
resize the lens. To go back, set the normal Petal Clock wallpaper again or uninstall the experimental app.
The normal `assembleDebug` / `assembleRelease` builds are unchanged and never include the effect.
