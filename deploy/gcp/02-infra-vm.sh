#!/usr/bin/env bash
# 3. lépés: az infrastruktúra VM (PostgreSQL 16, MongoDB 7, Kafka KRaft, Redis 7) létrehozása és megvárása.
source "$(dirname "$0")/lib.sh"

if exists gcloud compute instances describe "$VM_NAME" --zone "$ZONE"; then
  step "A VM már létezik: metaadat frissítése és az indítószkript újrafuttatása (újraindítás)"
  gcloud compute instances add-metadata "$VM_NAME" --zone "$ZONE" \
    --metadata-from-file "startup-script=$GCP_DIR/vm-startup.sh,infra-compose=$GCP_DIR/infra-compose.yml"
  gcloud compute instances reset "$VM_NAME" --zone "$ZONE"
else
  step "VM létrehozása: $VM_NAME ($VM_TYPE, $ZONE, belső IP $INFRA_IP, külső IP nélkül)"
  gcloud compute instances create "$VM_NAME" --zone "$ZONE" --machine-type "$VM_TYPE" \
    --subnet "$SUBNET_INFRA" --private-network-ip "$INFRA_IP" --no-address --tags archi-infra \
    --image-family debian-12 --image-project debian-cloud \
    --boot-disk-size 50GB --boot-disk-type pd-balanced \
    --service-account "$INFRA_SA_EMAIL" --scopes cloud-platform --shielded-secure-boot \
    --metadata-from-file "startup-script=$GCP_DIR/vm-startup.sh,infra-compose=$GCP_DIR/infra-compose.yml"
fi
START=$(date -u +%Y-%m-%dT%H:%M:%SZ)

step "Várakozás, amíg az infrastruktúra elindul (első alkalommal 3–6 perc)"
for i in $(seq 1 90); do
  LINE=$(gcloud compute instances get-serial-port-output "$VM_NAME" --zone "$ZONE" 2>/dev/null |
    grep -o 'ARCHI-INFRA-READY [0-9TZ:-]*' | tail -1 || true)
  if [ -n "$LINE" ] && [[ "${LINE#ARCHI-INFRA-READY }" > "$START" || "${LINE#ARCHI-INFRA-READY }" == "$START" ]]; then
    ok "infrastruktúra fut ($LINE)"; break
  fi
  [ "$i" = 90 ] && { warn "Nem indult el 15 perc alatt. Napló: gcloud compute instances get-serial-port-output $VM_NAME --zone $ZONE | grep -i archi"; exit 1; }
  sleep 10
done

echo
echo "Belépés a VM-re (IAP):  gcloud compute ssh $VM_NAME --zone $ZONE --tunnel-through-iap"
echo "Konténerek:             sudo docker compose -f /opt/archi/compose.yml ps"
echo "Következő: ./03-build.sh"
