#!/usr/bin/env bash
# 5. lépés: Keycloak, backend szolgáltatások, gatewayek (Cloud Run service), workerek (Cloud Run worker pool)
# és a két frontend telepítése. Többször futtatható: új revíziót hoz létre a legfrissebb image-ekből.
#   ./04-deploy.sh            # minden komponens
#   ./04-deploy.sh iam irat   # csak a megnevezett komponensek
source "$(dirname "$0")/lib.sh"
ONLY=" $* "
want() { [ "$ONLY" = "  " ] || [[ "$ONLY" == *" $1 "* ]]; }

KC_URL=$(url keycloak)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# --- Környezeti változók (YAML fájlokba írva, így a vesszőt tartalmazó értékek is biztonságosak) ---
yaml_env() { # yaml_env <fájl> KULCS=érték...
  local f="$1"; shift
  : > "$f"
  for kv in "$@"; do printf '%s: "%s"\n' "${kv%%=*}" "${kv#*=}" >> "$f"; done
}
BACKEND_ENV=(
  "SPRING_PROFILES_ACTIVE=gcp"
  "PLATFORM_GCP_PROJECTID=$PROJECT_ID"
  "KAFKA_BOOTSTRAP=$INFRA_IP:9092"
  "REDIS_HOST=$INFRA_IP"
  "PLATFORM_DB_URL=jdbc:postgresql://$INFRA_IP:5432/platform"
  "PLATFORM_DB_USER=platform_app"
  "TENANT_DB_HOST=$INFRA_IP"
  "TENANT_DB_PORT=5432"
  "MONGO_HOST=$INFRA_IP"
  "MONGO_PORT=27017"
  "KEYCLOAK_PUBLIC_URL=$KC_URL"
  "KEYCLOAK_INTERNAL_URL=$KC_URL"
  "KEYCLOAK_ADMIN_USER=admin"
  "IAM_BASE_URL=$(url iam)"
  "PARTNER_URI=$(url partner)"
  "TRACING_SAMPLING=0"
)
BACKEND_SECRETS="PLATFORM_DB_PASSWORD=archi-platform-db-password:latest,PLATFORM_SECRET_KEY=archi-platform-secret-key:latest,KEYCLOAK_ADMIN_PASSWORD=archi-kc-admin-password:latest,SPRING_DATA_REDIS_PASSWORD=archi-redis-password:latest"
ROUTES=(
  "TENANT_ADMIN_URI=$(url tenant-admin)" "IAM_URI=$(url iam)" "AUDIT_URI=$(url audit)"
  "IRAT_URI=$(url irat)" "WEBHOOK_URI=$(url webhook)"  # PARTNER_URI a BACKEND_ENV-ben
)
VPC_FLAGS=(--network "$NETWORK" --subnet "$SUBNET_RUN" --vpc-egress private-ranges-only)

# --- Telepítő függvények ---
deploy_service() { # deploy_service <név> <image> <env fájl> [további gcloud kapcsolók...]
  local name="$1" image="$2" envf="$3"; shift 3
  gcloud run deploy "$name" --image "$(img "$image")" --region "$REGION" --service-account "$RUN_SA_EMAIL" \
    --env-vars-file "$envf" --port 8080 $PUBLIC_FLAG --quiet "$@"
}
deploy_backend() { # Kafka-fogyasztó Spring Boot szolgáltatás: mindig fut 1 példány, CPU nincs visszafogva
  local name="$1" image="$2" envf="$3"; shift 3
  deploy_service "$name" "$image" "$envf" "${VPC_FLAGS[@]}" --set-secrets "$BACKEND_SECRETS" \
    --cpu 1 --memory 1Gi --min-instances 1 --max-instances 1 --no-cpu-throttling --cpu-boost --execution-environment gen2 "$@"
}
deploy_worker() { # deploy_worker <név> <env fájl> [további secret hozzárendelések]
  local name="$1" envf="$2" extra="${3:-}"
  gcloud run worker-pools deploy "$name" --image "$(img "$name")" --region "$REGION" \
    --service-account "$RUN_SA_EMAIL" "${VPC_FLAGS[@]}" --instances 1 --cpu 1 --memory 1Gi \
    --env-vars-file "$envf" --set-secrets "$BACKEND_SECRETS${extra:+,$extra}" --quiet
}

# Párhuzamos futtatás: minden komponens külön naplóba; a végén összesítünk.
PIDS=(); NAMES=()
bg() { local n="$1"; shift; "$@" > "$TMP/$n.log" 2>&1 & PIDS+=($!); NAMES+=("$n"); echo "  … $n"; }
wait_all() {
  local fail=0
  for i in "${!PIDS[@]}"; do
    if wait "${PIDS[$i]}"; then ok "${NAMES[$i]}"
    else warn "HIBA: ${NAMES[$i]}"; tail -25 "$TMP/${NAMES[$i]}.log"; fail=1; fi
  done
  PIDS=(); NAMES=()
  [ "$fail" = 0 ] || { echo "Javítás után futtassa újra (akár csak a hibás komponensre): ./04-deploy.sh <név>" >&2; exit 1; }
}

# 1) Keycloak – a többi szolgáltatás ennek a címét használja kibocsátóként (iss)
if want keycloak; then
  step "Keycloak ($KC_URL)"
  yaml_env "$TMP/keycloak.yaml" \
    "KC_DB_URL=jdbc:postgresql://$INFRA_IP:5432/keycloak" "KC_DB_USERNAME=keycloak" \
    "KC_HOSTNAME=$KC_URL" "KC_PROXY_HEADERS=xforwarded" "KC_HTTP_ENABLED=true" \
    "KC_BOOTSTRAP_ADMIN_USERNAME=admin" "ADMIN_UI_REDIRECT=$(url frontend-admin)/*"
  deploy_service keycloak keycloak "$TMP/keycloak.yaml" "${VPC_FLAGS[@]}" \
    --set-secrets "KC_DB_PASSWORD=archi-keycloak-db-password:latest,KC_BOOTSTRAP_ADMIN_PASSWORD=archi-kc-admin-password:latest,PLATFORM_ADMIN_PASSWORD=archi-platform-admin-password:latest" \
    --cpu 2 --memory 2Gi --min-instances 1 --max-instances 1 --cpu-boost --execution-environment gen2
  ok "keycloak"
fi

# 2) Backend szolgáltatások párhuzamosan
step "Backend szolgáltatások (párhuzamosan, 3–6 perc)"
yaml_env "$TMP/backend.yaml" "${BACKEND_ENV[@]}"
yaml_env "$TMP/webhook.yaml" "${BACKEND_ENV[@]}" "WEBHOOK_ALLOW_HTTP=false"
for s in "${BACKEND_SERVICES[@]}"; do
  want "$s" || continue
  if [ "$s" = webhook ]; then bg "$s" deploy_backend "$s" "$s" "$TMP/webhook.yaml"
  else bg "$s" deploy_backend "$s" "$s" "$TMP/backend.yaml"; fi
done

# 3) Workerek (Cloud Run worker pool: nincs HTTP port, folyamatosan fut)
yaml_env "$TMP/provisioner.yaml" "${BACKEND_ENV[@]}" \
  "PG_ADMIN_URL=jdbc:postgresql://$INFRA_IP:5432/postgres" "PG_ADMIN_USER=postgres" \
  "APP_REDIRECT_URIS=$(url frontend-app)/*"
for w in "${WORKER_POOLS[@]}"; do
  want "$w" || continue
  if [ "$w" = provisioner ]; then
    bg "$w" deploy_worker "$w" "$TMP/provisioner.yaml" \
      "PG_ADMIN_PASSWORD=archi-pg-admin-password:latest,MONGO_ADMIN_URI=archi-mongo-admin-uri:latest"
  else bg "$w" deploy_worker "$w" "$TMP/backend.yaml"; fi
done
wait_all

# 4) Gatewayek: felületi (a frontendek mögött) és publikus integrációs API
step "Gatewayek"
yaml_env "$TMP/gateway-ui.yaml" "${BACKEND_ENV[@]}" "${ROUTES[@]}" "GATEWAY_MODE=ui"
yaml_env "$TMP/gateway-public.yaml" "${BACKEND_ENV[@]}" "${ROUTES[@]}" "GATEWAY_MODE=public" \
  "RATE_LIMIT_RPS=20" "RATE_LIMIT_BURST=40"
want gateway-ui && bg gateway-ui deploy_backend gateway-ui gateway "$TMP/gateway-ui.yaml"
want gateway-public && bg gateway-public deploy_backend gateway-public gateway "$TMP/gateway-public.yaml"
wait_all

# 5) Frontendek (nginx; az /api/ hívásokat a felületi gatewayre továbbítja)
step "Frontendek"
for f in "${FRONTENDS[@]}"; do
  want "$f" || continue
  yaml_env "$TMP/$f.yaml" "API_UPSTREAM=$(url gateway-ui)" "KEYCLOAK_PUBLIC_URL=$KC_URL"
  bg "$f" deploy_service "$f" "$f" "$TMP/$f.yaml" --cpu 1 --memory 256Mi --min-instances 0 --max-instances 2
done
wait_all

step "Kész. Címek:"
printf '  %-28s %s\n' "Tenant admin felület:" "$(url frontend-admin)" \
  "Felhasználói felület:" "$(url frontend-app)/t/<tenant kód>" \
  "Publikus integrációs API:" "$(url gateway-public)/v1/..." \
  "Keycloak:" "$KC_URL"
echo "Következő: ./05-smoke.sh"
