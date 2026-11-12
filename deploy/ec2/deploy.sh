#!/usr/bin/env bash
set -Eeuo pipefail

# Directorios de referencia
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
PROJECT_DIR=$(cd -- "$SCRIPT_DIR/../.." && pwd)
DEPLOY_DIR="$PROJECT_DIR/deploy/ec2"
CONFIG_FILE="$DEPLOY_DIR/deploy.config"
APP_ENV_FILE="$DEPLOY_DIR/.env"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

[ -f "$CONFIG_FILE" ] || fail "Falta $CONFIG_FILE. Copia deploy.config.example como deploy.config y configura tus credenciales de EC2."
[ -f "$APP_ENV_FILE" ] || fail "Falta $APP_ENV_FILE. Copia .env.example como .env dentro de deploy/ec2/ y reemplaza los valores REEMPLAZAR."

# Cargar configuracion local de conexion (limpiando posibles saltos de linea CRLF de Windows)
# shellcheck disable=SC1090
source <(sed 's/\r$//' "$CONFIG_FILE")

: "${EC2_HOST:?Define EC2_HOST en deploy/ec2/deploy.config}"
: "${EC2_KEY_PATH:?Define EC2_KEY_PATH en deploy/ec2/deploy.config}"
EC2_USER=${EC2_USER:-ubuntu}
EC2_PORT=${EC2_PORT:-22}
REMOTE_DIR=${REMOTE_DIR:-/home/$EC2_USER/tributario}

# Normalizar ruta de clave privada (soporta Windows C:/, WSL /mnt/c y Git Bash /c)
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

[ -f "$EC2_KEY_PATH" ] || fail "No se encontro la clave privada (.pem): $EC2_KEY_PATH"
command -v ssh >/dev/null 2>&1 || fail "No se encontro ssh. Ejecuta el script desde Git Bash o WSL."
command -v scp >/dev/null 2>&1 || fail "No se encontro scp. Ejecuta el script desde Git Bash o WSL."
command -v tar >/dev/null 2>&1 || fail "No se encontro tar. Ejecuta el script desde Git Bash o WSL."

if grep -q 'REEMPLAZAR' "$APP_ENV_FILE"; then
  fail "Aun existen valores con 'REEMPLAZAR' en deploy/ec2/.env. Configuralos antes de desplegar."
fi

# En WSL o discos NTFS montados en Linux, chmod no puede cambiar permisos en /mnt/c
# Por eso copiamos la clave a /tmp (ext4/tmpfs) donde chmod 600 si es respetado por SSH
EFFECTIVE_KEY_PATH="$EC2_KEY_PATH"
TEMP_KEY=""
if [[ "$EC2_KEY_PATH" =~ ^/mnt/ ]] || ! chmod 600 "$EC2_KEY_PATH" 2>/dev/null; then
  TEMP_KEY=$(mktemp "${TMPDIR:-/tmp}/ec2_key.XXXXXX.pem")
  cp "$EC2_KEY_PATH" "$TEMP_KEY"
  chmod 600 "$TEMP_KEY"
  EFFECTIVE_KEY_PATH="$TEMP_KEY"
fi

SSH_OPTIONS=(
  -i "$EFFECTIVE_KEY_PATH"
  -p "$EC2_PORT"
  -o StrictHostKeyChecking=accept-new
  -o ConnectTimeout=15
)
SCP_OPTIONS=(
  -i "$EFFECTIVE_KEY_PATH"
  -P "$EC2_PORT"
  -o StrictHostKeyChecking=accept-new
  -o ConnectTimeout=15
)
REMOTE="$EC2_USER@$EC2_HOST"
RELEASE_ID="$(date -u +%Y%m%d%H%M%S)-$$"
REMOTE_ARCHIVE="/tmp/tributario-$RELEASE_ID.tar.gz"
REMOTE_ENV="/tmp/tributario-$RELEASE_ID.env"
ARCHIVE=$(mktemp "${TMPDIR:-/tmp}/tributario-deploy.XXXXXX.tar.gz")

cleanup() {
  rm -f "$ARCHIVE"
  [ -n "${TEMP_KEY:-}" ] && rm -f "$TEMP_KEY"
}
trap cleanup EXIT

echo "=========================================================="
echo " Conectando con la instancia EC2 ($REMOTE)..."
echo "=========================================================="
ssh "${SSH_OPTIONS[@]}" "$REMOTE" "true"

echo "Empaquetando Sistema Tributario (excluyendo temporales y frontmobile)..."
tar -czf "$ARCHIVE" \
  --exclude='.git' \
  --exclude='frontmobile' \
  --exclude='node_modules' \
  --exclude='*/node_modules' \
  --exclude='target' \
  --exclude='back/target' \
  --exclude='frontweb/dist' \
  --exclude='dist' \
  --exclude='.env' \
  --exclude='.env.*' \
  --exclude='deploy/ec2/.env' \
  --exclude='deploy/ec2/deploy.config' \
  --exclude='back/storage/predios/*' \
  --exclude='.claude' \
  --exclude='.vscode' \
  --exclude='.idea' \
  -C "$PROJECT_DIR" .

echo "Subiendo paquete de la version $RELEASE_ID a EC2..."
scp "${SCP_OPTIONS[@]}" "$ARCHIVE" "$REMOTE:$REMOTE_ARCHIVE"
scp "${SCP_OPTIONS[@]}" "$APP_ENV_FILE" "$REMOTE:$REMOTE_ENV"

echo "Desplegando en EC2 mediante Docker Compose..."
ssh "${SSH_OPTIONS[@]}" "$REMOTE" bash -s -- \
  "$REMOTE_DIR" "$RELEASE_ID" "$REMOTE_ARCHIVE" "$REMOTE_ENV" "$EC2_HOST" <<'REMOTE_SCRIPT'
set -Eeuo pipefail

remote_dir=$1
release_id=$2
remote_archive=$3
remote_env=$4
ec2_host=$5
release_dir="$remote_dir/releases/$release_id"

cleanup_remote() {
  rm -f "$remote_archive" "$remote_env"
}
trap cleanup_remote EXIT

# 1. Configurar SWAP de 2GB si la maquina no tiene suficiente memoria (AWS Academy)
swap_size=$(free -m | awk '/^Swap:/ {print $2}')
if [ -z "$swap_size" ] || [ "$swap_size" -eq 0 ]; then
  echo "Optimizando memoria: activando 2GB de Swap para compilacion..."
  sudo fallocate -l 2G /swapfile || sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
fi

# 2. Verificar e instalar Docker y Docker Compose v2 si no estan instalados
if ! command -v docker >/dev/null 2>&1 || ! sudo docker compose version >/dev/null 2>&1; then
  echo "Instalando Docker y Docker Compose..."
  sudo apt-get update
  sudo env DEBIAN_FRONTEND=noninteractive apt-get install -y docker.io docker-compose-v2
fi

sudo systemctl enable --now docker

# 3. Preparar directorio de release y descomprimir
mkdir -p "$release_dir/deploy/ec2"
tar -xzf "$remote_archive" -C "$release_dir"
install -m 600 "$remote_env" "$release_dir/deploy/ec2/.env"
cd "$release_dir"

# Asegurar que CORS permita la IP actual de la EC2
if ! grep -q "^CORS_ORIGINS=" deploy/ec2/.env; then
  echo "CORS_ORIGINS=http://$ec2_host,http://localhost" >> deploy/ec2/.env
fi

COMPOSE_CMD="sudo docker compose -p tributario --env-file deploy/ec2/.env -f compose.ec2.yaml"

echo "Limpiando posibles contenedores anteriores con el mismo nombre..."
sudo docker rm -f tributario-frontweb tributario-back tributario-db 2>/dev/null || true

echo "Validando configuracion de Docker Compose..."
$COMPOSE_CMD config --quiet

echo "Construyendo imagenes (Spring Boot Java 21 + React 19 / Nginx)..."
$COMPOSE_CMD build

echo "Iniciando contenedores (PostgreSQL, Back y Frontweb)..."
$COMPOSE_CMD up -d --remove-orphans

echo "Esperando que el servicio Backend y la base de datos inicien correctamente..."
back_container=$($COMPOSE_CMD ps -q back)
[ -n "$back_container" ] || { echo "ERROR: No se creo el contenedor del backend." >&2; exit 1; }

status=starting
for i in $(seq 1 60); do
  status=$(sudo docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$back_container" 2>/dev/null || echo "starting")
  [ "$status" = "healthy" ] && break
  if [ "$status" = "unhealthy" ] || [ "$status" = "exited" ] || [ "$status" = "dead" ]; then
    break
  fi
  echo "   Estado actual del backend: $status (intento $i/60)..."
  sleep 5
done

if [ "$status" != "healthy" ]; then
  echo "ERROR: El Backend no alcanzo el estado saludable (estado: $status)." >&2
  $COMPOSE_CMD logs --tail=120 back db >&2
  exit 1
fi

# 4. Actualizar symlink al release actual
ln -sfn "$release_dir" "$remote_dir/current"

# 5. Limpieza de imagenes antiguas y releases viejas para liberar disco y recursos
echo "Limpiando imagenes antiguas de Docker no utilizadas..."
sudo docker image prune -f >/dev/null 2>&1 || true

echo "Conservando solo las ultimas 3 releases para optimizar espacio..."
ls -dt "$remote_dir/releases"/* 2>/dev/null | tail -n +4 | xargs -r rm -rf 2>/dev/null || true

echo "=========================================================="
echo " Estado de los contenedores:"
echo "=========================================================="
$COMPOSE_CMD ps

echo "Despliegue completado exitosamente."
REMOTE_SCRIPT

echo
echo "=========================================================="
echo " ¡El Sistema Tributario se desplegó correctamente en EC2!"
echo "=========================================================="
echo " Accede a la plataforma web en:"
echo " http://$EC2_HOST"
echo
echo " Endpoint de salud de la API:"
echo " http://$EC2_HOST/api/health"
echo "=========================================================="

