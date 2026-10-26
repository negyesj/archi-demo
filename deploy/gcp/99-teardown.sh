#!/usr/bin/env bash
# Minden archi-demo erőforrás törlése a projektből (Cloud Run, VM, hálózat, titkok, image-ek, szolgáltatásfiókok).
# Egyszerűbb, ha a teljes projektet törli:  gcloud projects delete archi-demo
source "$(dirname "$0")/lib.sh"
if [ "${1:-}" != "--yes" ]; then
  read -r -p "Minden archi-demo erőforrás VÉGLEGES törlése a(z) $PROJECT_ID projektből. Folytatja? (igen/nem) " A
  [ "$A" = "igen" ] || { echo "Megszakítva."; exit 1; }
fi
del() { "$@" --quiet >/dev/null 2>&1 && ok "${*#gcloud }" || true; }

step "Cloud Run"
for w in "${WORKER_POOLS[@]}"; do del gcloud run worker-pools delete "$w" --region "$REGION"; done
for s in "${FRONTENDS[@]}" gateway-ui gateway-public "${BACKEND_SERVICES[@]}" keycloak; do
  del gcloud run services delete "$s" --region "$REGION"
done
step "VM és hálózat"
del gcloud compute instances delete "$VM_NAME" --zone "$ZONE"
del gcloud compute addresses delete archi-infra-ip --region "$REGION"
del gcloud compute routers delete archi-router --region "$REGION"
del gcloud compute firewall-rules delete archi-allow-infra
del gcloud compute firewall-rules delete archi-allow-iap-ssh
echo "  (a Cloud Run által a $SUBNET_RUN alhálózatban foglalt IP-k felszabadulása 1–2 órát is igénybe vehet;"
echo "   ha az alhálózat törlése ezért nem sikerül, futtassa újra később)"
del gcloud compute networks subnets delete "$SUBNET_RUN" --region "$REGION"
del gcloud compute networks subnets delete "$SUBNET_INFRA" --region "$REGION"
del gcloud compute networks delete "$NETWORK"
step "Titkok, image-ek, szolgáltatásfiókok"
for s in $(gcloud secrets list --filter 'name~archi-' --format 'value(name)'); do del gcloud secrets delete "$s"; done
del gcloud artifacts repositories delete "$AR_REPO" --location "$REGION"
for sa in "$RUN_SA_EMAIL" "$INFRA_SA_EMAIL" "$BUILD_SA_EMAIL"; do del gcloud iam service-accounts delete "$sa"; done
ok "Kész."
