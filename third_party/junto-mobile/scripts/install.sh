#!/usr/bin/env sh
# junto installer — fetches the binary for your platform and installs mpv.
# Usage: curl -fsSL https://junto.watch/install | sh
set -e

REPO="swayam-mishra/junto"
BIN="junto"
INSTALL_DIR="/usr/local/bin"

die() { echo "error: $*" >&2; exit 1; }
say() { echo "  $*"; }

# run_privileged runs its arguments as root if possible (already root, or
# sudo is available), otherwise prints what the caller should run
# themselves and returns failure — a plain "sudo foo" would just hang or
# fail with a confusing message on a system with no sudo at all (some
# minimal distros/containers).
run_privileged() {
  if [ "$(id -u)" = "0" ]; then
    "$@"
  elif command -v sudo >/dev/null 2>&1; then
    sudo "$@"
  else
    say "no sudo available — run this yourself: $*"
    return 1
  fi
}

# Everything lives inside main, called only on the very last line. A
# `curl | sh` pipeline executes as bytes arrive; if the download is cut
# short mid-script, an interpreter can already have started running
# whatever partial commands it had received. Wrapping the whole script in
# one function means the shell must finish parsing the entire file
# (through the matching closing brace) before it can execute any of it —
# a truncated download hits an unexpected EOF and aborts instead of
# running a partial, potentially different command.
main() {
  # Detect OS and arch
  OS=$(uname -s)
  ARCH=$(uname -m)
  case "$OS" in
    Darwin) OS="darwin" ;;
    Linux)  OS="linux" ;;
    *) die "unsupported OS: $OS (download manually from https://github.com/$REPO/releases)" ;;
  esac
  case "$ARCH" in
    x86_64)         ARCH="amd64" ;;
    aarch64|arm64)  ARCH="arm64" ;;
    *) die "unsupported arch: $ARCH (download manually from https://github.com/$REPO/releases)" ;;
  esac

  echo "junto installer"
  echo ""

  # macOS + Homebrew: one command gets junto AND mpv
  if [ "$OS" = "darwin" ] && command -v brew >/dev/null 2>&1; then
    say "Homebrew detected — using brew (this also installs mpv):"
    say "  brew install --cask swayam-mishra/tap/junto"
    echo ""
    brew install --cask swayam-mishra/tap/junto
    echo ""
    say "done. Run: junto doctor"
    exit 0
  fi

  # Fetch latest release tag from GitHub API. Captured unfiltered (no -f)
  # so a non-2xx response body — notably a rate-limit error — can still
  # be inspected below instead of being discarded, which used to surface
  # as a generic "check your internet connection" regardless of cause.
  say "fetching latest release..."
  API_RESPONSE=$(curl -sSL "https://api.github.com/repos/$REPO/releases/latest") \
    || die "could not reach GitHub — check your internet connection"
  case "$API_RESPONSE" in
    *"API rate limit exceeded"*)
      die "GitHub API rate limit exceeded for unauthenticated requests — wait a while and retry, or download manually from https://github.com/$REPO/releases/latest" ;;
  esac
  LATEST=$(printf '%s' "$API_RESPONSE" | grep '"tag_name"' | head -1 | sed 's/.*"tag_name": *"\([^"]*\)".*/\1/')
  [ -n "$LATEST" ] || die "could not determine latest version from GitHub's response — try again, or download manually from https://github.com/$REPO/releases/latest"
  say "latest: $LATEST"

  # goreleaser names archives after the version with no "v" prefix, but the
  # GitHub release tag (and its download URL) keeps the "v" — build the
  # tarball name from the stripped version, everything else from $LATEST.
  VERSION="${LATEST#v}"
  if [ "$OS" = "darwin" ]; then
    TARBALL="${BIN}_${VERSION}_darwin_all.tar.gz"
  else
    TARBALL="${BIN}_${VERSION}_linux_${ARCH}.tar.gz"
  fi
  BASE_URL="https://github.com/$REPO/releases/download/$LATEST"

  # Download to a temp directory
  TMP=$(mktemp -d)
  trap 'rm -rf "$TMP"' EXIT

  say "downloading $TARBALL..."
  curl -fsSL "$BASE_URL/$TARBALL" -o "$TMP/$TARBALL" \
    || die "download failed — try manually: $BASE_URL/$TARBALL"

  # Verify SHA-256
  say "verifying checksum..."
  curl -fsSL "$BASE_URL/checksums.txt" -o "$TMP/checksums.txt" \
    || die "could not fetch checksums"
  cd "$TMP"
  if command -v sha256sum >/dev/null 2>&1; then
    grep "$TARBALL" checksums.txt | sha256sum -c - \
      || die "checksum mismatch — download may be corrupt, please retry"
  elif command -v shasum >/dev/null 2>&1; then
    grep "$TARBALL" checksums.txt | shasum -a 256 -c - \
      || die "checksum mismatch — download may be corrupt, please retry"
  else
    die "no sha256sum or shasum found — refusing to install an unverified binary"
  fi

  tar -xzf "$TARBALL" -C "$TMP"

  # Install binary
  if [ -w "$INSTALL_DIR" ]; then
    install -m 755 "$TMP/$BIN" "$INSTALL_DIR/"
    say "installed to $INSTALL_DIR/$BIN"
  elif command -v sudo >/dev/null 2>&1; then
    # $INSTALL_DIR itself may not exist yet (e.g. a fresh Apple Silicon
    # Mac has no /usr/local/bin by default) — the writability check above
    # already returns false for a missing directory, so this branch must
    # create it before installing into it.
    sudo mkdir -p "$INSTALL_DIR"
    sudo install -m 755 "$TMP/$BIN" "$INSTALL_DIR/"
    say "installed to $INSTALL_DIR/$BIN (via sudo)"
  else
    # Fall back to ~/.local/bin
    INSTALL_DIR="$HOME/.local/bin"
    mkdir -p "$INSTALL_DIR"
    install -m 755 "$TMP/$BIN" "$INSTALL_DIR/"
    say "installed to $INSTALL_DIR/$BIN"
    say "note: make sure $INSTALL_DIR is in your PATH"
  fi
  cd /

  # macOS quarantines anything downloaded from the network, which makes
  # Gatekeeper block the binary on first launch ("Apple could not verify
  # ... is free of malware") even though the checksum above already
  # proves it's the untampered release binary. Best-effort: try without
  # privilege first (covers the common writable-INSTALL_DIR and
  # ~/.local/bin fallback cases above), then with sudo (covers the
  # sudo-installed case) — if both fail, silently leave it for the
  # README's documented `xattr -d com.apple.quarantine` workaround rather
  # than failing an otherwise-successful install over a cosmetic step.
  if [ "$OS" = "darwin" ] && command -v xattr >/dev/null 2>&1; then
    xattr -d com.apple.quarantine "$INSTALL_DIR/$BIN" 2>/dev/null \
      || sudo xattr -d com.apple.quarantine "$INSTALL_DIR/$BIN" 2>/dev/null \
      || true
  fi

  # Install mpv
  echo ""
  say "installing mpv..."
  if [ "$OS" = "darwin" ]; then
    if command -v brew >/dev/null 2>&1; then
      brew install mpv
    else
      say "mpv not found. Install it from https://mpv.io/installation/ then re-run: junto doctor"
    fi
  elif command -v apt-get >/dev/null 2>&1; then
    run_privileged apt-get install -y mpv \
      || say "install mpv yourself: https://mpv.io/installation/"
  elif command -v dnf >/dev/null 2>&1; then
    run_privileged dnf install -y mpv \
      || say "install mpv yourself: https://mpv.io/installation/"
  elif command -v pacman >/dev/null 2>&1; then
    run_privileged pacman -S --noconfirm mpv \
      || say "install mpv yourself: https://mpv.io/installation/"
  elif command -v zypper >/dev/null 2>&1; then
    run_privileged zypper install -y mpv \
      || say "install mpv yourself: https://mpv.io/installation/"
  else
    say "mpv not found and no supported package manager detected."
    say "Install mpv from https://mpv.io/installation/ then re-run: junto doctor"
  fi

  echo ""
  echo "junto $LATEST installed."
  echo "Run \`junto doctor\` to verify everything is working."
}

main "$@"
