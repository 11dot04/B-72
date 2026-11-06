# Making a release

A release is a tag on a commit, plus a page on GitHub with the APK attached. The workflow in `.github/workflows/release.yml` does the building and the page; you pick the version and push the tag.

## 1. Get the commit ready

1. Change `versionName` and bump `versionCode` in `app/build.gradle.kts`. `versionCode` must go up with every release or Android refuses the update (the first native build uses 100).
2. Add a section to `CHANGELOG.md` headed with the version, for example `## 2.0.1`. Its text becomes the release notes.
3. Make sure the secrets `TMDB_KEY` and `SPOTIFY_CLIENT_ID` exist (Repository, Settings, Secrets and variables, Actions). They are optional, but a release without them has no film lookups or Spotify.
4. Run the normal "Build APK" workflow on the commit and install the debug build on your phone first.

## 2. Tag and push

```
git add -A
git commit -m "Release 2.0.1"
git push
git tag v2.0.1
git push origin v2.0.1
```

Pushing the tag starts the Release workflow (Actions tab). When it turns green, the Releases page (right-hand side of the repository's front page) has a new release called `v2.0.1` with `B-72-v2.0.1.apk` attached.

A tag with a hyphen, such as `v2.1.0-beta1`, is marked as a pre-release.

## 3. Without the workflow

1. Download the `b72-release-apk` artifact from a "Build APK" run.
2. On GitHub: Releases, Draft a new release, choose or create the tag, paste the notes, drag the APK in, Publish release.

## 4. If something goes wrong

- Delete the tag and try again: `git tag -d v2.0.1 && git push origin :refs/tags/v2.0.1`, delete the release on GitHub, fix, tag again.
- The workflow fails at "Fetch fonts": a URL in google/fonts moved. Open the folder in a browser and fix the file name in both workflow files.

## Signing

Release and debug builds share one key (`keystore/b72.jks`), so people who tried a debug build can update to a release and keep their data. The key and its password are in the repository, which is fine for sideloading but means anyone can sign an APK that Android will accept as an update to yours. If the app ever goes to a store, or you want updates to be trustworthy, make a private key, store it as a repository secret, and read it in the `release` block of `app/build.gradle.kts`. Switching keys means people must uninstall first, so decide before many people have it.

## Checklist

- [ ] `versionCode` is higher than the last release
- [ ] `CHANGELOG.md` has a section for this version
- [ ] Installed and opened on a phone, on top of the previous release
- [ ] Notes, canvases and artifacts from the previous release are still there
