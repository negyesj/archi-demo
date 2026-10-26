#!/bin/bash
# Az archi-infra VM indítószkriptje (minden bootkor lefut, ismételhető).
# Telepíti a Dockert, a Secret Managerből megírja a .env fájlt és elindítja az infra-compose.yml-t.
set -euo pipefail
MD=http://metadata.google.internal/computeMetadata/v1
md() { curl -sf -H Metadata-Flavor:Google "$MD/$1"; }
PROJECT=$(md project/project-id)

if ! command -v docker >/dev/null || ! docker compose version >/dev/null 2>&1; then
  echo "archi: Docker telepítése"
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker

mkdir -p /opt/archi/pg-init && cd /opt/archi
md instance/attributes/infra-compose > compose.yml

TOKEN=$(md instance/service-accounts/default/token | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
secret() {
  curl -sf -H "Authorization: Bearer $TOKEN" \
    "https://secretmanager.googleapis.com/v1/projects/$PROJECT/secrets/$1/versions/latest:access" |
    python3 -c 'import sys,json,base64;print(base64.b64decode(json.load(sys.stdin)["payload"]["data"]).decode())'
}
INFRA_IP=$(md instance/network-interfaces/0/ip)
PG_ADMIN_PASSWORD=$(secret archi-pg-admin-password)
PLATFORM_DB_PASSWORD=$(secret archi-platform-db-password)
KEYCLOAK_DB_PASSWORD=$(secret archi-keycloak-db-password)
MONGO_ROOT_PASSWORD=$(secret archi-mongo-root-password)
REDIS_PASSWORD=$(secret archi-redis-password)
umask 077
cat > .env <<ENV
INFRA_IP=$INFRA_IP
PG_ADMIN_PASSWORD=$PG_ADMIN_PASSWORD
PLATFORM_DB_PASSWORD=$PLATFORM_DB_PASSWORD
KEYCLOAK_DB_PASSWORD=$KEYCLOAK_DB_PASSWORD
MONGO_ROOT_PASSWORD=$MONGO_ROOT_PASSWORD
REDIS_PASSWORD=$REDIS_PASSWORD
ENV

# A PostgreSQL első indulásakor fut: platform és Keycloak adatbázis (a tenant adatbázisokat a provisioner hozza létre).
umask 022
cat > pg-init/01-init.sh <<'SQL'
#!/bin/sh
set -e
psql -v ON_ERROR_STOP=1 -U postgres \
  -v platform_pw="$PLATFORM_DB_PASSWORD" -v keycloak_pw="$KEYCLOAK_DB_PASSWORD" <<'EOSQL'
create role platform_app with login password :'platform_pw';
create database platform owner platform_app;
create role keycloak with login password :'keycloak_pw';
create database keycloak owner keycloak;
EOSQL
SQL

docker compose up -d --wait --wait-timeout 600
docker compose ps
echo "ARCHI-INFRA-READY $(date -u +%Y-%m-%dT%H:%M:%SZ)"
