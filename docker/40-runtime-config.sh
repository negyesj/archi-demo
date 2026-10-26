#!/bin/sh
# Futásidejű konfiguráció a környezeti változókból (ugyanaz az image minden környezetben).
set -e
cat > /usr/share/nginx/html/config.json <<JSON
{ "keycloakUrl": "${KEYCLOAK_PUBLIC_URL:-http://localhost:8180}", "appName": "${APP_NAME:-Tenant admin}" }
JSON
