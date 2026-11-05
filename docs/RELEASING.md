# Releasing Arcify

Release APKs are built and signed by GitHub Actions (`.github/workflows/release.yml`) when a tag
like `v0.2.0` is pushed. They appear on the repository's **Releases** page with a SHA-256 file.

## One-time setup: the release key
The release key proves that every update comes from you. Android only installs an update over an
existing install if it is signed with the **same key**, so keep the keystore and its passwords
safe (for example in a password manager) and back the file up. **Never commit it**; `*.jks`,
`*.keystore` and `keystore.properties` are git-ignored.

1. Create the keystore (you'll be asked for a password and your name):
   ```bash
   keytool -genkeypair -v -keystore arcify-release.jks -alias arcify -keyalg RSA -keysize 4096 -validity 10000
   ```
   Keep it outside the repo, e.g. `C:\Users\<you>\keys\arcify-release.jks`.
2. Add four repository secrets (GitHub → Settings → Secrets and variables → Actions), or with `gh`:
   ```bash
   base64 -w0 arcify-release.jks | gh secret set ARCIFY_KEYSTORE_BASE64
   gh secret set ARCIFY_KEYSTORE_PASSWORD
   gh secret set ARCIFY_KEY_ALIAS --body arcify
   gh secret set ARCIFY_KEY_PASSWORD
   ```
   (`gh secret set` without `--body` asks for the value, so it doesn't end up in your shell history.
   On Windows PowerShell, use `[Convert]::ToBase64String([IO.File]::ReadAllBytes("arcify-release.jks")) | gh secret set ARCIFY_KEYSTORE_BASE64`.)

## Making a release
1. Make sure `main` is green in CI.
2. Tag and push:
   ```bash
   git tag v0.2.0
   git push origin v0.2.0
   ```
   The version name comes from the tag; the version code is derived from it (`1.2.3` → `10203`),
   so each tag must be higher than the last. A tag with a suffix (`v0.3.0-beta.1`) is published as
   a pre-release.
3. The workflow runs the unit tests, builds the shrunk (R8) release APK, verifies its signature
   and publishes `Arcify-<version>.apk` plus its `.sha256`.

## Building a signed release locally
Create `keystore.properties` in the repo root (git-ignored):
```properties
storeFile=C:/Users/<you>/keys/arcify-release.jks
storePassword=...
keyAlias=arcify
keyPassword=...
```
Then run `./gradlew :app:assembleRelease -Parcify.versionName=0.2.0`. Without a key the release
APK is unsigned. For a quick local smoke test only, `-Parcify.debugSignRelease=true` signs it with
the debug key; never publish such a build, and uninstall it afterwards, because a properly signed
release can't be installed over it.

## Installing
- **From Releases:** download `Arcify-<version>.apk` on the phone and open it; Android asks to
  allow installs from that app (browser or file manager) once.
- **Release and debug side by side:** the release app is `app.arcify`, the debug build is
  `app.arcify.debug`. Each needs its own Accessibility, Usage access and other permissions. Turn
  off the blocker of the one you aren't using so the two don't both block.
