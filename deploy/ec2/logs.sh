#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
PROJECT_DIR=$(cd -- "$SCRIPT_DIR/../.." && pwd)
DEPLOY_DIR="$PROJECT_DIR/deploy/ec2"
CONFIG_FILE="$DEPLOY_DIR/deploy.config"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

[ -f "$CONFIG_FILE" ] || fail "Falta $CONFIG_FILE."
source <(sed 's/\r$//' "$CONFIG_FILE")

: "${EC2_HOST:?Define EC2_HOST en deploy/ec2/deploy.config}"
: "${EC2_KEY_PATH:?Define EC2_KEY_PATH en deploy/ec2/deploy.config}"
EC2_USER=${EC2_USER:-ubuntu}
EC2_PORT=${EC2_PORT:-22}

EC2_KEY_PATH="${EC2_KEY_PATH//\\//}"
if [ ! -f "$EC2_KEY_PATH" ]; then
  if command -v wslpath >/dev/null 2>&1 && [[ "$EC2_KEY_PATH" =~ ^[a-zA-Z]: ]]; then
    EC2_KEY_PATH=$(wslpath -u "$EC2_KEY_PATH" 2>/dev/null || echo "$EC2_KEY_PATH")
  elif [[ "$EC2_KEY_PATH" =~ ^/c/(.*) ]] && [ -d "/mnt/c" ]; then
    EC2_KEY_PATH="/mnt/c/${BASH_REMATCH[1]}"
  elif [[ "$EC2_KEY_PATH" =~ ^/mnt/c/(.*) ]] && [ -d "/c" ]; then
    EC2_KEY_PATH="/c/${BASH_REMATCH[1]}"
  fi
fi

[ -f "$EC2_KEY_PATH" ] || fail "No se encontro la clave: $EC2_KEY_PATH"

EFFECTIVE_KEY_PATH="$EC2_KEY_PATH"
TEMP_KEY=""
if [[ "$EC2_KEY_PATH" =~ ^/mnt/ ]] || ! chmod 600 "$EC2_KEY_PATH" 2>/dev/null; then
  TEMP_KEY=$(mktemp "${TMPDIR:-/tmp}/ec2_key.XXXXXX.pem")
  cp "$EC2_KEY_PATH" "$TEMP_KEY"
  chmod 600 "$TEMP_KEY"
  EFFECTIVE_KEY_PATH="$TEMP_KEY"
fi

cleanup() {
  [ -n "${TEMP_KEY:-}" ] && rm -f "$TEMP_KEY"
}
trap cleanup EXIT

echo "=========================================================="
echo " Consultando logs de tributario-back en la instancia EC2..."
echo "=========================================================="
ssh -i "$EFFECTIVE_KEY_PATH" -p "$EC2_PORT" -o StrictHostKeyChecking=accept-new "$EC2_USER@$EC2_HOST" \
  "sudo docker logs --tail=100 tributario-back"

