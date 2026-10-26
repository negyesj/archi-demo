#!/usr/bin/env bash
# Költségcsökkentés két kipróbálás között. Az adatok (VM lemez, Secret Manager, image-ek) megmaradnak.
#   ./98-pause.sh pause    # workerek törlése, szolgáltatások min. 0 példány, VM leállítása
#   ./98-pause.sh resume   # VM indítása, majd ./04-deploy.sh (mindent visszaállít)
source "$(dirname "$0")/lib.sh"
case "${1:-}" in
  pause)
    step "Worker poolok törlése (a ./04-deploy.sh újra létrehozza őket)"
    for w in "${WORKER_POOLS[@]}"; do
      gcloud run worker-pools delete "$w" --region "$REGION" --quiet 2>/dev/null && ok "$w" || true
    done
    step "Cloud Run szolgáltatások: minimum 0 példány (forgalom nélkül leállnak)"
    for s in keycloak gateway-ui gateway-public "${BACKEND_SERVICES[@]}"; do
      gcloud run services update "$s" --region "$REGION" --min-instances 0 --quiet >/dev/null && ok "$s"
    done
    step "Infrastruktúra VM leállítása"
    gcloud compute instances stop "$VM_NAME" --zone "$ZONE"
    ok "Szüneteltetve. Folytatás: ./98-pause.sh resume"
    ;;
  resume)
    step "Infrastruktúra VM indítása"
    gcloud compute instances start "$VM_NAME" --zone "$ZONE"
    echo "Várakozás az infrastruktúrára (2 perc)…"; sleep 120
    "$GCP_DIR/04-deploy.sh"
    ;;
  *) echo "Használat: $0 pause|resume" >&2; exit 2 ;;
esac
