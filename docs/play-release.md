# Google Play release

fastlane runs under Homebrew's ruby@3.4 (fastlane 2.x doesn't install on Ruby 4):

    export PATH="/opt/homebrew/opt/ruby@3.4/bin:$PATH"
    bundle install

## One-time setup

1. **Service account for fastlane supply.** In Google Cloud, enable the Google Play Android Developer API,
   create a service account and download its JSON key outside the repo (for example
   `~/Apps/keystores/play-service-account.json`). In Play Console, Users and permissions, invite the
   service account's email with release and store listing permissions for TapPony. Then put
   `PLAY_JSON_KEY=/path/to/play-service-account.json` in `.env` at the repo root (gitignored).
2. **Create the app** in Play Console: TapPony, app, free. Google requires the first bundle of a new app to
   be uploaded by hand, so build it (`bundle exec fastlane bundle`) and upload
   `app/build/outputs/bundle/release/app-release.aab` to the closed testing track in the browser once.
3. **App signing.** TapPony uses Google-managed app signing; the TapPony keystore is the upload key. Play
   installs are signed with Google's key, so `site/.well-known/assetlinks.json` lists both Play's app signing
   SHA-256 (App integrity page) and the release keystore's fingerprint used by F-Droid and GitHub builds.
4. **Policy forms:** privacy policy `https://tappony.app/privacy`; ads: none; app access: no login needed;
   content rating questionnaire (utility, no user content shared); target audience 18+; data safety:
   no data collected or shared by the developer (the app sends requests only to servers the user configures,
   at the user's direction, and has no analytics or ads).

## Each release

    bundle exec fastlane screenshots
    bundle exec fastlane metadata
    bundle exec fastlane closed

`screenshots` runs on an emulator or unlocked phone (Android 13+) and renders at 1080x1920. `metadata`
uploads the listing text, icon, feature graphic and screenshots, attached to the current versionCode's
release on the closed track. `closed` builds and uploads to closed testing ("alpha").

New personal accounts must keep a closed test running with at least 12 opted-in testers for 14 days before
Play offers production. After that, `bundle exec fastlane promote` moves the closed release to production.
Every release also needs `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` (F-Droid reads
the same files).
