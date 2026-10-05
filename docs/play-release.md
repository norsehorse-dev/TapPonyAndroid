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
3. **App signing.** When Play asks how to sign, upload the existing TapPony key (the "export and upload a
   key from a Java keystore" option) so Play, F-Droid and the direct APK all share one signature and
   `assetlinks.json` keeps working. If Play generates its own key instead, add Play's app signing SHA-256
   (App integrity page) as a second fingerprint in `site/.well-known/assetlinks.json` and redeploy the site.
4. **Policy forms:** privacy policy `https://tappony.app/privacy`; ads: none; app access: no login needed;
   content rating questionnaire (utility, no user content shared); target audience 18+; data safety:
   no data collected or shared by the developer (the app sends requests only to servers the user configures,
   at the user's direction, and has no analytics or ads).

## Each release

    bundle exec fastlane screenshots   # phone or emulator on Android 13+, writes images per language
    bundle exec fastlane metadata      # listing text, icon, feature graphic, screenshots
    bundle exec fastlane closed        # build and upload to closed testing ("alpha")

New personal accounts must keep a closed test running with at least 12 opted-in testers for 14 days before
Play offers production. After that, `bundle exec fastlane promote` moves the closed release to production.
Every release also needs `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` (F-Droid reads
the same files).
