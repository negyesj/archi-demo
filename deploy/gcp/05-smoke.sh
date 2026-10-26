#!/usr/bin/env bash
# 6. lépés: füstteszt a felhőben – platform admin token, tenant létrehozása, provisioning (PostgreSQL, MongoDB,
# Keycloak realm, első admin), ACTIVE állapot, a tenant elérhető a gatewayen.
#   ./05-smoke.sh            # véletlen tenant kód
#   CODE=demo ./05-smoke.sh  # megadott tenant kód (kisbetű, szám)
source "$(dirname "$0")/lib.sh"

step "Füstteszt: $(url gateway-ui)"
KC=$(url keycloak) API=$(url gateway-ui) APP_URL=$(url frontend-app) TIMEOUT=${TIMEOUT:-900} \
ADMIN_USER=admin ADMIN_PASSWORD=$(gcloud secrets versions access latest --secret archi-platform-admin-password) \
  "$REPO_ROOT/scripts/smoke-test.sh"

step "Belépés a felületekre"
echo "  Tenant admin felület:  $(url frontend-admin)   (felhasználó: admin)"
echo "    jelszó:              gcloud secrets versions access latest --secret archi-platform-admin-password"
echo "  Felhasználói felület:  $(url frontend-app)/t/<tenant kód>"
echo "    az első tenant admin ideiglenes jelszavát a tenant admin felületen lehet kiolvasni"
echo "  Publikus API:          $(url gateway-public)/v1/..."
