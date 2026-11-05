# TapPony (Android)

Scan an NFC tag and the phone sends an HTTP request you designed to a server you chose. The request can carry the tag's UID, chip details and NDEF content. No account, no cloud, no telemetry.

**Status:** 1.2.0, on [Google Play](https://play.google.com/store/apps/details?id=com.tappony.android) and [GitHub releases](https://github.com/norsehorse-dev/TapPonyAndroid/releases), with F-Droid to follow. Website and receiver docs: [tappony.app](https://tappony.app). iOS app: [TapPony](https://github.com/norsehorse-dev/TapPony).

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

## Verify a release

Every release is built reproducibly, so you can rebuild the APK from the
tagged source and confirm it matches the published binary, and you can
confirm the published APK is signed with the project's own key. You do not
have to trust the download.

Verify the signature and certificate. The release APK is signed with the
TapPony release key; its certificate SHA-256 fingerprint is:

```
4383f2bc43d863bc09c816c5295136d7197637ac320dcf8fbfb477703eb36444
```

To check the signer with [`AppVerifier`](https://github.com/soupslurpr/AppVerifier), paste this text as the verification info:

```
com.tappony.android
43:83:F2:BC:43:D8:63:BC:09:C8:16:C5:29:51:36:D7:19:76:37:AC:32:0D:CF:8F:BF:B4:77:70:3E:B3:64:44
```

This covers the GitHub build, and the F-Droid build once it is there. Google signs the Google Play build with a different key, so a Play install shows a different certificate and a GitHub APK can't update it in place.

Check a downloaded APK against it with the Android build tools:

```
apksigner verify --print-certs TapPony-<version>.apk
```

The printed "certificate SHA-256 digest" must equal the fingerprint above.

Every GitHub release carries four assets, and releases are immutable, so
the assets cannot be swapped after publishing:

- `TapPony-<version>.apk`, the APK
- `TapPony-<version>.apk.asc`, its detached OpenPGP signature
- `NorseHorse-release-key.asc`, the armored OpenPGP release key
- `TapPony-<version>-signing-keys.txt`, the OpenPGP release key
  fingerprint and the APK signing certificate SHA-256

```
gpg --import NorseHorse-release-key.asc
gpg --verify TapPony-<version>.apk.asc TapPony-<version>.apk
```

Before trusting the key that ships with a release, check its OpenPGP
fingerprint against a source GitHub does not control: keys.openpgp.org or
keys.pgpony.app. The release notes carry the whole-file SHA-256 for
downloaders and a content hash for rebuilders, each labeled with what it
means.

Verify the source. From 1.2.0 on, release tags and the commits on main are
signed with the norsehorse-dev SSH signing key:

```
ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIPhzdDdquHgaZq8dgEQzcncPBnSZvIVlusBg0TvOA8To
```

To check a tag or commit locally, list the key in an allowed signers file
and point git at it from inside a clone:

```
echo "norsehorse-dev@users.noreply.github.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIPhzdDdquHgaZq8dgEQzcncPBnSZvIVlusBg0TvOA8To" > allowed_signers
git config gpg.ssh.allowedSignersFile "$PWD/allowed_signers"
git verify-tag v<version>
git log --show-signature -1
```

The same key is registered as a signing key on the norsehorse-dev GitHub
account, so GitHub marks these commits and tags Verified; the copy here lets
you check them without relying on that badge.

Rebuild from source and compare. The release gate script clones a tag twice
into clean, isolated build roots, proves the two builds are identical, and
compares them against a candidate APK:

```
tools/verify_repro.sh rebuild v<version> TapPony-<version>.apk
```

It requires an Android SDK, JDK 17 or later and network access for the
clone and dependency downloads, and prints the canonical content hash. The
procedure is in `docs/REPRODUCIBLE_BUILDS.md`.

## License

Apache-2.0. See `LICENSE`.
