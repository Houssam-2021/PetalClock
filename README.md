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
