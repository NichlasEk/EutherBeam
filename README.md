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

Alpha 6 adds a local Android TV room controller using Remote Service v2 rather than ADB. It discovers
compatible players on TCP 6467, performs six-character PIN pairing over mutual TLS, keeps the private
RSA identity non-exportable in Android Keystore, and maintains the encrypted TCP 6466 command session
including protobuf keepalives. The Android TV tab provides navigation, Home, Back, power, media and
volume controls. A saved room link associates the player with either the Samsung TV or NEC display,
and room scenes can start or sleep the player together with the selected display. The Android TV tab
also participates in the existing last-open-tab restoration.

Alpha 7 remembers the last discovered Samsung TV even while its SSDP and IP services are asleep. A
standby card stores or automatically resolves the TV MAC address, sends repeated Wake-on-LAN magic
packets to global and subnet broadcasts on UDP ports 7 and 9, attempts Samsung IP power as a fast
path, and automatically rediscovers the TV for up to 45 seconds. This makes an offline TV remain
visible and actionable instead of disappearing from the app.

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

Android TV Remote Service v2 uses mutual TLS and length-delimited protobuf messages: port 6467 for
PIN pairing and port 6466 for the persistent remote channel. Pairing and control stay on the LAN and
do not require developer options, USB debugging or ADB on the player.

The Kotlin implementation was checked byte-for-byte against the MIT-licensed `tdudek/samsung-remote-models-2014-and-newer` proof of concept and the Apache-2.0 `sermayoral/ha-samsungtv-encrypted` implementation. See `THIRD_PARTY_NOTICES.md`.

## Safety

The normal UI exposes consumer remote keys only. Factory and service-menu keys are intentionally excluded.
