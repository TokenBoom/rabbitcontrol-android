#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PRIVATE_KEY="${1:-$ROOT_DIR/core-signing-private.pem}"
PUBLIC_KEY="${2:-$ROOT_DIR/app/core-signing-public.pem}"

if [[ -f "$PRIVATE_KEY" ]]; then
  echo "Refusing to overwrite existing private key: $PRIVATE_KEY" >&2
  exit 1
fi

umask 077
openssl ecparam -name prime256v1 -genkey -noout -out "$PRIVATE_KEY"
chmod 600 "$PRIVATE_KEY"

openssl ec -in "$PRIVATE_KEY" -pubout -out "$PUBLIC_KEY" 2>/dev/null
chmod 644 "$PUBLIC_KEY"

PUB_B64="$(grep -v 'BEGIN PUBLIC KEY' "$PUBLIC_KEY" | grep -v 'END PUBLIC KEY' | tr -d '[:space:]')"

cat <<EOF
Generated ECDSA P-256 keypair:
  Private key (DO NOT COMMIT, gitignored): $PRIVATE_KEY
  Public key  (embedded into APK):         $PUBLIC_KEY

Public key (Base64 SPKI):
  $PUB_B64

Next steps:
  1. Add the entire contents of $PRIVATE_KEY to GitHub Repository Secrets as:
     CORE_SIGNING_PRIVATE_KEY_PEM
  2. Commit $PUBLIC_KEY so Gradle embeds it into BuildConfig.CORE_SIGNING_PUBLIC_KEY_B64.
  3. Back up $PRIVATE_KEY securely offline, then remove it from the working tree if desired.
EOF
