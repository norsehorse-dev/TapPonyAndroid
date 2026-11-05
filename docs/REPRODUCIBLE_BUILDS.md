# Reproducible builds and GitHub releases (TapPony Android)

TapPony uses the same release gate as PGPony Android. The rule behind it:

**No APK gets uploaded until it is proven content-identical to a fresh
clean-clone build of the tag it claims to be.**

PGPony learned this the hard way (its F-Droid builds of 4.0.1 and 4.1.1
failed because the signed APK was not built from the tagged commit). TapPony
starts with the gate in place.

## The moving parts

1. `tools/verify_repro.sh` is the gate:

   ```
   tools/verify_repro.sh rebuild v1.2.0 TapPony-1.2.0.apk
   tools/verify_repro.sh compare a.apk b.apk
   tools/verify_repro.sh content-hash some.apk
   ```

   `rebuild` clones the tag twice into isolated build roots (separate
   `GRADLE_USER_HOME`s, `--no-daemon`), builds `:app:assembleRelease` in
   each, proves the two builds are content-identical, then proves the
   candidate APK matches them. "Content" means every byte of every ZIP
   entry, ignoring only the signature files under `META-INF/`. The APK
   Signing Block lives outside the ZIP entries, so it is never compared.
   That is the comparison F-Droid's signature-copy verification makes, so a
   pass here predicts a pass there.

   A clean clone has no `keystore.properties`, so its release build comes
   out unsigned (`app-release-unsigned.apk`), the same as F-Droid's build.

2. `.github/workflows/reproducible.yml` runs the same gate on Linux on every
   `v*` tag push, and on demand for any tag. It rebuilds the tag twice per
   JDK (17 and 21), fails unless the build is deterministic, compares against
   the `TapPony-*.apk` release asset when one exists, and uploads the
   unsigned APK as a run artifact. If the Mac and CI ever disagree, the CI
   artifact is the canonical one, since it is the one built in an
   F-Droid-like environment.

## What keeps the build reproducible

- AGP 8.13.2, Gradle 8.14.3, Kotlin 2.1.0, KSP 2.1.0-1.0.29, all pinned
  in-tree. No dynamic dependency versions.
- `vcsInfo.include = false` on the release build type and
  `dependenciesInfo.includeInApk = false`, so no commit hash or dependency
  blob lands in the APK.
- Minify is off, so there is no R8 map-id in the dex.
- APK entry timestamps are normalized by AGP.
- Never extract an APK to a macOS filesystem to compare or hash it: AGP's
  shortened resource names can differ only by case and collapse there. The
  script reads the ZIP directly.

## Signing

The GitHub (and later F-Droid) APK is signed with the TapPony release
keystore, v2 only (minSdk 26). That keystore is also the Play upload key;
Google signs Play installs with its own key, so a Play install shows a
different certificate and can't be updated in place by a GitHub APK.

Release certificate SHA-256:

```
4383f2bc43d863bc09c816c5295136d7197637ac320dcf8fbfb477703eb36444
```

The same value is in `site/.well-known/assetlinks.json` on tappony.app and in
the README. Every release checks the signed APK against it before publishing.

Never re-sign with apksigner from build-tools 35 or newer. It rewrites the ZIP
alignment fields, so the file stops matching a Gradle build byte for byte and
F-Droid's apksigcopier verification fails even with identical contents. Sign
by building inside the gate's clean clone with the keystore present (step 3
below). Reading a certificate with `apksigner verify` is fine.

## Immutable releases

Release immutability is on for this repository. A published release can't have
assets added, replaced or removed, and its tag can't move, so everything is
attached to a draft first and only then published. Anything wrong after
publishing means a new patch version.

Turn it on once, and check it:

```
gh api -X PUT repos/norsehorse-dev/TapPonyAndroid/immutable-releases
gh api repos/norsehorse-dev/TapPonyAndroid/immutable-releases
```

## Release procedure

Assumes the version is bumped and committed, the build is device-tested and
the release notes are in `docs/releases/RELEASE_NOTES_X.Y.Z.md` without the
hashes yet. `<work>` is the work directory the gate prints.

Step 1, sign the unpushed commits, push, then tag and push the tag (the tag
push starts the CI gate):

```
git rebase --autostash --force-rebase --gpg-sign origin/main
git log --show-signature origin/main..HEAD
git push origin main
git tag -s vX.Y.Z -m "TapPony X.Y.Z"
git verify-tag vX.Y.Z
git rev-parse HEAD vX.Y.Z^{commit}
git push origin vX.Y.Z
```

The two hashes `git rev-parse` prints must be the same.

Step 2, run the local gate bare. This proves determinism and produces the
canonical clean build:

```
tools/verify_repro.sh rebuild vX.Y.Z
```

Note `<work>` and the content hash.

Step 3, build the signed release APK inside the verified clone. The clone has
no local.properties, so pass ANDROID_HOME:

```
cp keystore.properties <work>/srcA/
cd <work>/srcA
env ANDROID_HOME="$HOME/Library/Android/sdk" GRADLE_USER_HOME=<work>/gradleA ./gradlew --no-daemon :app:assembleRelease
cp app/build/outputs/apk/release/app-release.apk /tmp/TapPony-X.Y.Z.apk
```

`keystore.properties` points at the keystore with a path. If it is relative,
make sure it still resolves from `<work>/srcA`, or the signed build fails.

Step 4, gate the exact file that will be uploaded:

```
cd ~/Apps/TapPonyAndroid
tools/verify_repro.sh compare /tmp/TapPony-X.Y.Z.apk <work>/buildA.apk
```

Must print IDENTICAL, with the same content hash as step 2.

Step 5, confirm both CI legs passed (Actions, reproducible-check).

Step 6, sign and collect the hashes:

```
gpg --detach-sign --armor --local-user 0x53F9798E4919DE62 --output /tmp/TapPony-X.Y.Z.apk.asc /tmp/TapPony-X.Y.Z.apk
gpg --armor --export 0x53F9798E4919DE62 > /tmp/NorseHorse-release-key.asc
gpg --with-colons --fingerprint 0x53F9798E4919DE62 | awk -F: '/^fpr/{print "OpenPGP release key fingerprint: " $10; exit}' > /tmp/TapPony-X.Y.Z-signing-keys.txt
"$HOME/Library/Android/sdk/build-tools/$(ls "$HOME/Library/Android/sdk/build-tools" | sort -V | tail -1)/apksigner" verify --print-certs /tmp/TapPony-X.Y.Z.apk | awk '/certificate SHA-256 digest/{print "APK signing certificate SHA-256: " $NF; exit}' >> /tmp/TapPony-X.Y.Z-signing-keys.txt
cat /tmp/TapPony-X.Y.Z-signing-keys.txt
shasum -a 256 /tmp/TapPony-X.Y.Z.apk
```

The certificate line must equal the value under Signing above. If it doesn't,
stop. Put the whole-file SHA-256 and the content hash into the release notes'
Verify this build section now: the notes are part of the immutable release.

Step 7, publish. Every release carries four assets: the APK, its detached
signature, the armored OpenPGP release key, and the signing-keys text file.

```
gh release create vX.Y.Z --draft --verify-tag --title "TapPony X.Y.Z" --notes-file docs/releases/RELEASE_NOTES_X.Y.Z.md /tmp/TapPony-X.Y.Z.apk /tmp/TapPony-X.Y.Z.apk.asc /tmp/NorseHorse-release-key.asc /tmp/TapPony-X.Y.Z-signing-keys.txt
gh release view vX.Y.Z
gh release edit vX.Y.Z --draft=false
gh api repos/norsehorse-dev/TapPonyAndroid/releases/tags/vX.Y.Z --jq '{immutable, draft, assets: [.assets[].name]}'
```

Check the draft lists all four assets and the notes carry both hashes before
publishing; that is the point of no return.

Step 8, re-run the workflow on the tag (Actions, reproducible-check, Run
workflow, or `gh workflow run reproducible-check -f tag=vX.Y.Z`) so the
release-asset comparison runs end to end against the published file. Then
delete `<work>`.

## Release candidates

RCs follow PGPony's public RC workflow (PGPonyAndroid
`docs/RC_BUILD_SIGN_DEPLOY.md`): a signed `vX.Y.Z-rcN` tag on the RC commit,
the same gate, and a GitHub pre-release with the same four assets, named
`TapPony-X.Y.Z-RCN.apk`. Pre-releases are immutable too.

## F-Droid

When TapPony goes to F-Droid, the recipe must only pick up release tags from
the start, since an RC keeps its release's versionCode:

```
UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$
```

Pin the release certificate above in `AllowedAPKSigningKeys` and point
`Binaries` at `https://github.com/norsehorse-dev/TapPonyAndroid/releases/download/v%v/TapPony-%v.apk`.

## When the gate fails

- Two clean builds on the same machine differ: real nondeterminism. Suspect
  floating dependency resolution first, then AGP, KSP or Room changes.
- Clean builds match each other but not the candidate: the candidate was built
  from different source or a dirty tree. Rebuild it inside the clean clone.
- Mac gate passes but CI differs from the Mac: environment drift. Ship the CI
  artifact's content, and pin the divergent input in-tree.
- `compare` says the contents are identical but F-Droid reports a digest
  mismatch: the ZIP layout differs, so the APK was signed by a tool that
  rewrote the archive. See Signing.
