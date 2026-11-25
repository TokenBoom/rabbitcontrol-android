#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$ROOT_DIR/rabbitcontrol-server"
ENV_FILE="$SERVER_DIR/.env.local"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE. Create it with DATABASE_URL before running this script." >&2
  exit 1
fi

set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a
unset REDIS_URL

SSH_HOST="${RC_DB_SSH_HOST:-huangpj@100.76.106.45}"
DB_LOCAL_PORT="${RC_DB_LOCAL_PORT:-15432}"
LAN_HOST="${RC_LAN_HOST:-192.168.8.9}"

if nc -z 127.0.0.1 21118 >/dev/null 2>&1; then
  echo "Port 21118 is already listening. Stop the existing server before starting another." >&2
  exit 1
fi

if [[ ! -x "$SERVER_DIR/backend/target/debug/rc-server" ]]; then
  echo "Building rc-server..."
  (cd "$SERVER_DIR/backend" && cargo build --locked)
fi

if [[ ! -f "$SERVER_DIR/web/dist/index.html" ]]; then
  echo "Building web frontend..."
  (cd "$SERVER_DIR/web" && npm ci && npm run build)
fi

TUNNEL_PID=""

cleanup() {
  if [[ -n "$TUNNEL_PID" ]] && kill -0 "$TUNNEL_PID" 2>/dev/null; then
    kill "$TUNNEL_PID" 2>/dev/null || true
    wait "$TUNNEL_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

if ! nc -z 127.0.0.1 "$DB_LOCAL_PORT" >/dev/null 2>&1; then
  if [[ "$SSH_HOST" == *@100.* || "$SSH_HOST" == 100.* ]] && command -v tailscale >/dev/null 2>&1; then
    if ! tailscale status >/dev/null 2>&1; then
      echo "Tailscale is not running. Start/connect Tailscale first so SSH can reach $SSH_HOST." >&2
      exit 1
    fi
  fi

  echo "Opening SSH database tunnel to $SSH_HOST (127.0.0.1:$DB_LOCAL_PORT -> 127.0.0.1:5432)..."
  ssh -N \
    -o BatchMode=yes \
    -o ConnectTimeout=5 \
    -o ExitOnForwardFailure=yes \
    -o ServerAliveInterval=30 \
    -o ServerAliveCountMax=3 \
    -L "127.0.0.1:${DB_LOCAL_PORT}:127.0.0.1:5432" \
    "$SSH_HOST" &
  TUNNEL_PID=$!

  tunnel_ready=0
  for _ in $(seq 1 75); do
    if ! kill -0 "$TUNNEL_PID" 2>/dev/null; then
      echo "SSH database tunnel exited unexpectedly." >&2
      exit 1
    fi
    if nc -z 127.0.0.1 "$DB_LOCAL_PORT" >/dev/null 2>&1; then
      tunnel_ready=1
      break
    fi
    sleep 0.2
  done

  if [[ "$tunnel_ready" -ne 1 ]]; then
    echo "Timed out waiting for database tunnel on 127.0.0.1:$DB_LOCAL_PORT." >&2
    exit 1
  fi
fi

echo "Starting RabbitControl server in foreground:"
echo "  Local: https://127.0.0.1:21118"
echo "  LAN:   https://${LAN_HOST}:21118"
echo "Press Ctrl+C to stop."

cd "$SERVER_DIR"
"$SERVER_DIR/backend/target/debug/rc-server" \
  --http 0.0.0.0:21118 \
  --signal 0.0.0.0:21116 \
  --relay 0.0.0.0:21117 \
  --public-host "$LAN_HOST" \
  --tls-san "$LAN_HOST" \
  --web-dir "$SERVER_DIR/web/dist"
