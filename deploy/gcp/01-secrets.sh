#!/usr/bin/env bash
# 2. lépés: véletlen jelszavak és kulcsok a Secret Managerben. Meglévő titkot nem ír felül.
source "$(dirname "$0")/lib.sh"

create_secret() { # create_secret <név> <érték>
  if exists gcloud secrets describe "$1"; then ok "$1 (már létezik)"; return; fi
  printf '%s' "$2" | gcloud secrets create "$1" --data-file=- \
    --replication-policy user-managed --locations "$REGION" >/dev/null
  ok "$1"
}
secret_value() { gcloud secrets versions access latest --secret "$1"; }

step "Titkok létrehozása"
create_secret archi-platform-secret-key     "$(openssl rand -base64 32)"   # AES-256 kulcs a platform titoktárához
create_secret archi-pg-admin-password       "$(openssl rand -hex 16)"      # postgres szuperuser (provisioner)
create_secret archi-platform-db-password    "$(openssl rand -hex 16)"      # platform_app
create_secret archi-keycloak-db-password    "$(openssl rand -hex 16)"      # keycloak adatbázis
create_secret archi-mongo-root-password     "$(openssl rand -hex 16)"
create_secret archi-redis-password          "$(openssl rand -hex 16)"
create_secret archi-kc-admin-password       "$(openssl rand -hex 16)"      # Keycloak master admin
create_secret archi-platform-admin-password "$(openssl rand -hex 12)"      # platform realm "admin" (tenant admin UI)
create_secret archi-mongo-admin-uri \
  "mongodb://root:$(secret_value archi-mongo-root-password)@${INFRA_IP}:27017/admin"

step "Kész. A platform admin jelszava (tenant admin felület, felhasználó: admin):"
echo "  gcloud secrets versions access latest --secret archi-platform-admin-password"
echo "Következő: ./02-infra-vm.sh"
