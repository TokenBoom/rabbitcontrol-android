#!/usr/bin/env bash
set -euo pipefail

CORE_VERSION=""
TAG_NAME=""
CORE_GIT_SHA="unknown"
ABI_VERSION="1"
MIN_APP_VERSION="0.2.0"
SO_PATH=""
ABI="arm64-v8a"
RELEASE_NOTES_FILE=""
OUT_DIR=""
PRIVATE_KEY_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --core-version) CORE_VERSION="$2"; shift 2 ;;
    --tag-name) TAG_NAME="$2"; shift 2 ;;
    --core-git-sha) CORE_GIT_SHA="$2"; shift 2 ;;
    --abi-version) ABI_VERSION="$2"; shift 2 ;;
    --min-app-version) MIN_APP_VERSION="$2"; shift 2 ;;
    --so-path) SO_PATH="$2"; shift 2 ;;
    --abi) ABI="$2"; shift 2 ;;
    --release-notes-file) RELEASE_NOTES_FILE="$2"; shift 2 ;;
    --out-dir) OUT_DIR="$2"; shift 2 ;;
    --private-key-file) PRIVATE_KEY_FILE="$2"; shift 2 ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ -z "$CORE_VERSION" || -z "$SO_PATH" || -z "$OUT_DIR" || -z "$PRIVATE_KEY_FILE" ]]; then
  echo "Missing required arguments (--core-version, --so-path, --out-dir, --private-key-file)" >&2
  exit 1
fi

CORE_VERSION="${CORE_VERSION#v}"
CORE_VERSION="${CORE_VERSION#V}"
if [[ -z "$TAG_NAME" ]]; then
  TAG_NAME="core-v${CORE_VERSION}"
fi

if [[ ! -f "$SO_PATH" ]]; then
  echo "Shared library not found: $SO_PATH" >&2
  exit 1
fi
if [[ ! -f "$PRIVATE_KEY_FILE" ]]; then
  echo "Private key not found: $PRIVATE_KEY_FILE" >&2
  exit 1
fi

mkdir -p "$OUT_DIR"

ASSET_NAME="librabbitcontrol_android-${ABI}.so"
DEST_SO="$OUT_DIR/$ASSET_NAME"
cp "$SO_PATH" "$DEST_SO"

PUBLISHED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

python3 - "$CORE_VERSION" "$TAG_NAME" "$CORE_GIT_SHA" "$ABI_VERSION" "$MIN_APP_VERSION" \
  "$PUBLISHED_AT" "$ABI" "$ASSET_NAME" "$DEST_SO" "$RELEASE_NOTES_FILE" "$OUT_DIR/core-manifest.json" <<'PY'
import hashlib
import json
import os
import sys

(
    core_version,
    tag_name,
    core_git_sha,
    abi_version,
    min_app_version,
    published_at,
    abi,
    asset_name,
    dest_so,
    notes_file,
    out_manifest,
) = sys.argv[1:]

with open(dest_so, "rb") as f:
    data = f.read()

sha256_hex = hashlib.sha256(data).hexdigest()
size_bytes = len(data)

notes = ""
if notes_file and os.path.isfile(notes_file):
    with open(notes_file, "r", encoding="utf-8") as nf:
        notes = nf.read().strip()

manifest = {
    "schemaVersion": 1,
    "coreVersion": core_version,
    "tagName": tag_name,
    "coreGitSha": core_git_sha,
    "abiVersion": int(abi_version),
    "minAppVersion": min_app_version,
    "publishedAt": published_at,
    "releaseNotes": notes,
    "abis": {
        abi: {
            "fileName": asset_name,
            "sha256": sha256_hex,
            "sizeBytes": size_bytes,
        }
    },
}

with open(out_manifest, "w", encoding="utf-8") as mf:
    json.dump(manifest, mf, ensure_ascii=False, indent=2)
    mf.write("\n")
PY

TMP_SIG="$(mktemp)"
TMP_PUB="$(mktemp)"
trap 'rm -f "$TMP_SIG" "$TMP_PUB"' EXIT

openssl dgst -sha256 -sign "$PRIVATE_KEY_FILE" -out "$TMP_SIG" "$OUT_DIR/core-manifest.json"
openssl base64 -A -in "$TMP_SIG" -out "$OUT_DIR/core-manifest.json.sig"

# Self-verify signature before uploading
openssl ec -in "$PRIVATE_KEY_FILE" -pubout -out "$TMP_PUB" 2>/dev/null
openssl dgst -sha256 -verify "$TMP_PUB" -signature "$TMP_SIG" "$OUT_DIR/core-manifest.json"

(
  cd "$OUT_DIR"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$ASSET_NAME" core-manifest.json core-manifest.json.sig > SHA256SUMS.txt
  else
    shasum -a 256 "$ASSET_NAME" core-manifest.json core-manifest.json.sig > SHA256SUMS.txt
  fi
)

echo "Built and signed core release artifacts in $OUT_DIR"
