#!/usr/bin/env bash
# 1. lépés: API-k, Artifact Registry, hálózat (VPC, alhálózatok, tűzfal, Cloud NAT), szolgáltatásfiókok.
# Többször is futtatható: ami már létezik, azt kihagyja.
source "$(dirname "$0")/lib.sh"

step "Projekt: $PROJECT_ID ($PROJECT_NUMBER), régió: $REGION"
if [ "$(gcloud billing projects describe "$PROJECT_ID" --format='value(billingEnabled)' 2>/dev/null)" = "False" ]; then
  echo "A projekthez nincs számlázási fiók rendelve. Console → Billing → Link a billing account." >&2; exit 1
fi

step "API-k engedélyezése (1–2 perc)"
gcloud services enable run.googleapis.com compute.googleapis.com artifactregistry.googleapis.com \
  cloudbuild.googleapis.com secretmanager.googleapis.com iam.googleapis.com logging.googleapis.com \
  monitoring.googleapis.com iap.googleapis.com
ok "API-k"

step "Artifact Registry: $AR_REPO"
exists gcloud artifacts repositories describe "$AR_REPO" --location "$REGION" || \
  gcloud artifacts repositories create "$AR_REPO" --location "$REGION" --repository-format docker \
    --description "archi-demo image-ek"
ok "$REGISTRY"

step "VPC hálózat és alhálózatok"
exists gcloud compute networks describe "$NETWORK" || \
  gcloud compute networks create "$NETWORK" --subnet-mode custom
exists gcloud compute networks subnets describe "$SUBNET_INFRA" --region "$REGION" || \
  gcloud compute networks subnets create "$SUBNET_INFRA" --network "$NETWORK" --region "$REGION" \
    --range "$SUBNET_INFRA_RANGE" --enable-private-ip-google-access
exists gcloud compute networks subnets describe "$SUBNET_RUN" --region "$REGION" || \
  gcloud compute networks subnets create "$SUBNET_RUN" --network "$NETWORK" --region "$REGION" \
    --range "$SUBNET_RUN_RANGE" --enable-private-ip-google-access
ok "$NETWORK: $SUBNET_INFRA ($SUBNET_INFRA_RANGE), $SUBNET_RUN ($SUBNET_RUN_RANGE)"

step "Tűzfal: a VPC-ből elérhető infrastruktúra portok, SSH csak IAP-n át"
exists gcloud compute firewall-rules describe archi-allow-infra || \
  gcloud compute firewall-rules create archi-allow-infra --network "$NETWORK" --direction INGRESS \
    --allow tcp:5432,tcp:27017,tcp:9092,tcp:6379 --source-ranges "$VPC_RANGE" --target-tags archi-infra
exists gcloud compute firewall-rules describe archi-allow-iap-ssh || \
  gcloud compute firewall-rules create archi-allow-iap-ssh --network "$NETWORK" --direction INGRESS \
    --allow tcp:22 --source-ranges 35.235.240.0/20 --target-tags archi-infra
ok "archi-allow-infra, archi-allow-iap-ssh"

step "Cloud NAT (a külső IP nélküli VM innen tölti le a Docker image-eket)"
exists gcloud compute routers describe archi-router --region "$REGION" || \
  gcloud compute routers create archi-router --network "$NETWORK" --region "$REGION"
exists gcloud compute routers nats describe archi-nat --router archi-router --region "$REGION" || \
  gcloud compute routers nats create archi-nat --router archi-router --region "$REGION" \
    --auto-allocate-nat-external-ips --nat-custom-subnet-ip-ranges "$SUBNET_INFRA"
ok "archi-nat"

step "Fix belső IP az infrastruktúra VM-nek: $INFRA_IP"
exists gcloud compute addresses describe archi-infra-ip --region "$REGION" || \
  gcloud compute addresses create archi-infra-ip --region "$REGION" --subnet "$SUBNET_INFRA" --addresses "$INFRA_IP"
ok "archi-infra-ip"

step "Szolgáltatásfiókok és szerepek"
bind() { # bind <sa email> <szerep>; az új fiók néhány másodperc után látszik, ezért újrapróbál
  for i in 1 2 3 4 5 6; do
    gcloud projects add-iam-policy-binding "$PROJECT_ID" --member "serviceAccount:$1" --role "$2" \
      --condition None >/dev/null 2>&1 && return 0
    sleep 10
  done
  echo "Nem sikerült: $2 → $1" >&2; return 1
}
for SA in "$RUN_SA:Cloud Run szolgáltatások" "$INFRA_SA:Infrastruktúra VM" "$BUILD_SA:Cloud Build"; do
  NAME=${SA%%:*}
  exists gcloud iam service-accounts describe "$NAME@$PROJECT_ID.iam.gserviceaccount.com" || \
    gcloud iam service-accounts create "$NAME" --display-name "archi-demo: ${SA#*:}"
done
for R in roles/secretmanager.secretAccessor roles/logging.logWriter roles/monitoring.metricWriter; do
  bind "$RUN_SA_EMAIL" "$R"; bind "$INFRA_SA_EMAIL" "$R"
done
for R in roles/artifactregistry.writer roles/logging.logWriter roles/storage.objectViewer; do
  bind "$BUILD_SA_EMAIL" "$R"
done
ok "$RUN_SA_EMAIL, $INFRA_SA_EMAIL, $BUILD_SA_EMAIL"

step "Kész. Következő: ./01-secrets.sh"
