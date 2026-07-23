#!/usr/bin/env sh
# Builds a macOS .pkg installer for junto.
# Called by goreleaser's after hook on darwin builds only.
# Usage: scripts/build-pkg.sh <version>
set -e

VERSION="${1:-dev}"

# Only runs on macOS — gate so the hook is harmless on Linux CI runners.
if [ "$(uname -s)" != "Darwin" ]; then
  echo "build-pkg.sh: skipping (not macOS)"
  exit 0
fi

command -v pkgbuild >/dev/null 2>&1 || { echo "build-pkg.sh: pkgbuild not found, skipping"; exit 0; }

# Locate the macOS binary goreleaser built. With universal_binaries it
# lands in dist/junto_darwin_all/junto; prefer any darwin path, then fall
# back to any junto executable in dist/.
BINARY=$(find dist -type f -name junto -path "*darwin*" | head -1)
[ -z "$BINARY" ] && BINARY=$(find dist -type f -name junto | head -1)
if [ -z "$BINARY" ]; then
  echo "build-pkg.sh: junto binary not found in dist/, skipping"
  exit 0
fi

STAGING=$(mktemp -d)
PKG_SCRIPTS=$(mktemp -d)
trap 'rm -rf "$STAGING" "$PKG_SCRIPTS"' EXIT

# Payload: just the binary at /usr/local/bin/junto
mkdir -p "$STAGING/usr/local/bin"
cp "$BINARY" "$STAGING/usr/local/bin/junto"
chmod 755 "$STAGING/usr/local/bin/junto"

# postinstall: install mpv via Homebrew if available, else print instructions.
# macOS always runs postinstall as root, but Homebrew refuses to run as
# root — installing mpv from here as-is silently does nothing (brew
# exits nonzero, and the old "2>/dev/null || true" swallowed it without
# a trace). Homebrew has to run as the actual logged-in user instead.
cat > "$PKG_SCRIPTS/postinstall" << 'EOF'
#!/bin/sh
CONSOLE_USER=$(stat -f "%Su" /dev/console 2>/dev/null || true)
if [ -n "$CONSOLE_USER" ] && [ "$CONSOLE_USER" != "root" ] \
   && sudo -u "$CONSOLE_USER" -i command -v brew >/dev/null 2>&1; then
  if ! sudo -u "$CONSOLE_USER" -i brew install mpv; then
    echo ""
    echo "junto installed to /usr/local/bin/junto"
    echo "automatic mpv install failed — install it yourself: brew install mpv"
  fi
else
  echo ""
  echo "junto installed to /usr/local/bin/junto"
  echo "mpv is required. Install it: brew install mpv"
  echo "or download from https://mpv.io/installation/"
fi
junto doctor 2>/dev/null || true
EOF
chmod 755 "$PKG_SCRIPTS/postinstall"

OUTPUT="dist/junto_${VERSION}_macos.pkg"
pkgbuild \
  --root "$STAGING" \
  --scripts "$PKG_SCRIPTS" \
  --identifier "watch.junto.junto" \
  --version "$VERSION" \
  --install-location "/" \
  "$OUTPUT"

# Publish a checksum alongside the .pkg: goreleaser's own checksums.txt
# only covers the artifacts it built itself, and this one is produced by
# a separate step afterward (see release.yml) — without this, someone
# downloading the .pkg directly has no way to verify it.
if command -v sha256sum >/dev/null 2>&1; then
  (cd dist && sha256sum "$(basename "$OUTPUT")") > "${OUTPUT}.sha256"
else
  (cd dist && shasum -a 256 "$(basename "$OUTPUT")") > "${OUTPUT}.sha256"
fi

echo "build-pkg.sh: created $OUTPUT (and ${OUTPUT}.sha256)"
