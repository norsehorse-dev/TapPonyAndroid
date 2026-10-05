# TapPony (Android)

Scan an NFC tag and the phone sends an HTTP request you designed to a server you chose. The request can carry the tag's UID, chip details and NDEF content. No account, no cloud, no telemetry.

**Status:** 1.0.0 release candidate. Builds will be posted on [tappony.app/rc](https://tappony.app/rc/) and GitHub releases, with F-Droid to follow. Website and receiver docs: [tappony.app](https://tappony.app). iOS app: [TapPony](https://github.com/norsehorse-dev/TapPony).

## Features

- Reads the tag's own identity, not just NDEF: UID in canonical byte order, chip model, NXP originality signature and scan counter where the chip has them. Works with tags you didn't write, including MIFARE Classic badges on phones whose NFC chip reads them.
- A full request builder: method, URL, headers, JSON, form or raw body built from templates like `{uid}` and `{payload}`, bearer, basic or API key auth, optional HMAC-SHA256 signing.
- Rules that route each tag to one or more profiles, batch mode, an offline queue, and the server's reply shown or spoken after each tap.
- Tag writing: links, text, the tag's own UID, and TapPony launch links that open the app and send even when it's closed.
- A Quick Settings tile, launcher shortcuts per profile and `tappony://scan` links from other apps.
- English, German, Spanish, French, Brazilian Portuguese, Russian and Simplified Chinese.

## Layout

- `app/` Compose app: reader-mode NFC reading and tag writing, Scan / Profiles / Tags / History / Settings, App Links for launch links, OkHttp sender, Keystore-wrapped secrets, Room history and offline queue
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
