# Releasing

Releases are manual. Push the version tag, then trigger the **release**
workflow from the GitHub Actions tab; it runs the tests and then
[goreleaser](https://goreleaser.com) builds the binaries (macOS
universal, Linux amd64/arm64), publishes the GitHub release, and pushes
the Homebrew cask to the tap. Nothing is released automatically —
ordinary pushes and even tag pushes publish nothing on their own.

## One-time setup (before the first release)

1. **Create the tap repository**: a public GitHub repo named
   `swayam-mishra/homebrew-tap` (empty is fine; goreleaser writes
   `Casks/junto.rb` into it). Users then install with
   `brew install --cask swayam-mishra/tap/junto`.

2. **Create a tap token**: a GitHub fine-grained personal access token
   with **Contents: read & write** on `swayam-mishra/homebrew-tap` only.

3. **Add the secret**: in the `junto` repo settings →
   Secrets and variables → Actions → new repository secret named
   `TAP_GITHUB_TOKEN` with that token as the value.

> **Formula-to-cask migration (done as of 2026-07-07):** the tap used to
> hold a Homebrew *formula* (`Formula/junto.rb`) — goreleaser's `brews`
> config is deprecated (see
> [goreleaser.com/deprecations/#brews](https://goreleaser.com/deprecations/#brews)),
> so `.goreleaser.yaml` generates a *cask* instead (`Casks/junto.rb`,
> `brew install --cask ...`). `Formula/junto.rb` has been deleted from
> `homebrew-tap` and a `tap_migrations.json` mapping `"junto": "junto"`
> added, so an existing formula user's `brew upgrade` migrates them to
> the cask automatically. Anyone still stuck on the old formula (`brew
> upgrade` says "already installed" at an old version) can force it:
> `brew uninstall swayam-mishra/tap/junto && brew install --cask
> swayam-mishra/tap/junto`.

## macOS notarization (future TODO)

junto's binaries are **not** Apple-notarized. macOS Gatekeeper therefore
blocks the first launch of a quarantined binary ("Apple could not verify
'junto' is free of malware"). We work around it by stripping the
`com.apple.quarantine` flag: `scripts/install.sh` does it for the
direct-download path, and the Homebrew cask does it via a post-install
`xattr` hook in `.goreleaser.yaml`. That covers the `curl | sh` and
Homebrew paths; a `.pkg` or manual tarball download still shows the prompt
once (documented `xattr` step in the README).

The proper fix is to sign + notarize the release so no flag-stripping is
needed on any path. When junto has real users, do this:

1. Join the **Apple Developer Program** ($99/yr) and create a **Developer
   ID Application** certificate.
2. Add CI secrets: the certificate (`.p12` + password) and an **App Store
   Connect API key** (issuer ID, key ID, `.p8`).
3. Configure goreleaser's [`notarize`](https://goreleaser.com/customization/notarize/)
   block to sign the macOS binaries and submit them to Apple's notary
   service, then remove the `xattr` quarantine-strip hook from the cask
   (no longer needed once notarized).

Once notarized, the Gatekeeper prompt disappears for every install method.

## Cutting a release

```sh
# 1. Make sure CHANGELOG.md has a section for the version.
# 2. Tag and push:
git tag v1.0.0
git push origin main v1.0.0
```

3. On GitHub: **Actions → release → Run workflow** (on `main`, with
   HEAD at the tagged commit). The workflow refuses to run if HEAD
   isn't tagged.

Verify afterwards:

```sh
brew install --cask swayam-mishra/tap/junto
junto version
```

## Dry run (optional, requires goreleaser installed locally)

```sh
goreleaser release --snapshot --clean   # builds into dist/, publishes nothing
```
