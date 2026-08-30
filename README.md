# EutherBeam

EutherBeam is a native Kotlin/Jetpack Compose Android remote for televisions on the local network. The first supported target is Samsung's encrypted 2014 H-series protocol, verified against a Samsung UE55H6475/UE55H6400-family TV.

## Current alpha

- Discovers Samsung TVs with SSDP/UPnP; no fixed IP address is required.
- Reads the TV identity from `/ms/1.0/`.
- Performs the complete SPC PIN exchange locally on Android.
- Stores the resulting session identity encrypted by Android Keystore.
- Sends encrypted Socket.IO remote keys over the LAN.
- Includes volume, mute, source, menu, and power controls.
- Uses no cloud service and does not log PINs or session keys.

Alpha 3 corrected Samsung's white-box command-key derivation and was physically verified with
`KEY_POWEROFF` on the target UE55H6400-family television. Upgrading from alpha 1 or alpha 2 requires
one new PIN pairing because those versions stored an unusable command key.

Alpha 4 adds a rounded directional pad with enter, return, info and exit controls; channel, guide,
and transport keys; a cyberpunk/Gruvbox visual system; and the serpent-and-TV launcher icon.

Alpha 5 integrates the physically tested NecFjärr external-control implementation as a separate NEC
Display tab. It discovers and verifies compatible NEC displays on TCP 7142, supports manual saved IP,
power and input control, and restores the last selected Samsung/NEC tab when the app opens again.

## Build

Requirements: JDK 17 and an Android SDK containing API 36.

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
ANDROID_HOME=/opt/android-sdk \
./gradlew testDebugUnitTest assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Protocol notes

Samsung H-series pairing uses HTTP port 8080 for the PIN exchange and Socket.IO on port 8000 for encrypted commands. Device discovery and metadata use SSDP/UPnP, port 7676, and `/ms/1.0/` on port 8001. Some firmware returns HTTP 404 from the optional companion `startService` endpoint while still exposing a working Socket.IO handshake; EutherBeam treats that call as best-effort.

The Kotlin implementation was checked byte-for-byte against the MIT-licensed `tdudek/samsung-remote-models-2014-and-newer` proof of concept and the Apache-2.0 `sermayoral/ha-samsungtv-encrypted` implementation. See `THIRD_PARTY_NOTICES.md`.

## Safety

The normal UI exposes consumer remote keys only. Factory and service-menu keys are intentionally excluded.
