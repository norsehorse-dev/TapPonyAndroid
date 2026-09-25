# TapPony (Android)

Scan an NFC tag and the phone sends an HTTP request you designed to a server you chose. The request can carry the tag's UID, chip details and NDEF content. No account, no cloud, no telemetry.

**Status:** 1.0.0 in development, Phase A (reader mode, request engine, Scan screen, profiles).

## Layout

- `app/` Compose app: reader-mode NFC, Scan / Profiles / Settings, OkHttp sender, Keystore-wrapped secrets
- `core/` pure Kotlin/JVM core, the twin of the iOS `TapPonyKit`: template engine, host policy, UID and NDEF handling, request assembly, profile codec, presets
- `fixtures/` shared conformance vectors, identical in the iOS repo and run by both test suites
- `PROFILE_SCHEMA.md` the cross-platform contract for profiles and templates
- `tools/fixtures/` the Python reference implementation that generates `fixtures/`

## Build

Requires the Android SDK with API 36 and JDK 17 or later.

```
./gradlew :core:test :app:assembleDebug
```

A signing config is read from `keystore.properties` (git-ignored) if present; see `keystore.properties.sample`.

## Privacy

The only network traffic is the requests you configure, to the hosts you type. Plain HTTP only ever goes to local network addresses, and only when a profile opts in. Secrets are encrypted with a key that stays in the Android Keystore and never appear in profiles or exports.

## License

Apache-2.0. See `LICENSE`.
