TapPony's first release on GitHub. It's the same 1.2.0 that's on Google Play, built from the signed v1.2.0 tag through the reproducibility gate and signed with the TapPony release key. Google signs the Play build with its own key, so this APK won't install over a Play install. To switch, export your profiles, uninstall, install this one and import them. Secrets aren't in exports, so you'll enter those again.

Scan an NFC tag and the phone sends an HTTP request you designed to a server you chose. No account, no cloud, no telemetry.

## What TapPony does

- Reads the tag's own identity, not just NDEF: UID in canonical byte order, chip model, NXP originality signature and scan counter where the chip has them. Works with tags you didn't write, including MIFARE Classic badges on phones whose NFC chip reads them.
- Builds the request from your profile: method, URL, headers, JSON, form or raw body from templates like `{uid}` and `{payload}`, bearer, basic or API key auth, optional HMAC-SHA256 signing.
- Rules route each tag to one or more profiles. Batch mode, an offline queue, and the server's reply shown or spoken after each tap.
- Writes tags: links, text, the tag's own UID, and TapPony launch links that open the app and send even when it's closed.
- Quick Settings tile, launcher shortcuts per profile, a home screen widget and `tappony://scan` links from other apps.
- English, German, Spanish, French, Brazilian Portuguese, Russian and Simplified Chinese.

## New since 1.0.0

- Home screen widget. One tap opens the Scan tab with the profile you picked when you placed it, or with whichever profile is active.
- Copy buttons next to the UID, the server message and the reply on the scan result, and in History details.
- Duplicate a profile from its editor to start a new one from a working setup. The copy uses the same saved secrets.
- History search finds scans by UID (with or without separators), profile name or server message.

## Verify this build

Whole-file SHA-256 (is this download the published file):

```
228cb8e4bfc0a85090a00bd68155c45f504da04c2e29992f7eb31adf622da95d
```

Content hash (for rebuilders; excludes the signature, see docs/REPRODUCIBLE_BUILDS.md):

```
818a9ad7fde1cec887d23baa0842156ce6d5cc1525d4e63bc9cc4f01a8171482
```

The APK's detached signature is made with the NorseHorse release key
(A0CBC8F65AACE56F1C5B767753F9798E4919DE62) and attached to this release, along with the armored release key
(NorseHorse-release-key.asc) and TapPony-1.2.0-signing-keys.txt with the OpenPGP fingerprint and the APK signing
certificate's SHA-256. Check the fingerprint against keys.openpgp.org or keys.pgpony.app rather than trusting the
copy in the release alone. The README's Verify a release section has the commands.
