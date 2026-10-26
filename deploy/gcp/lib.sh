# shellcheck shell=bash
# Közös függvények a deploy/gcp szkriptekhez (source-olva használjuk, önállóan nem fut).
set -euo pipefail
GCP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$GCP_DIR/../.." && pwd)"
# shellcheck source=config.env
source "$GCP_DIR/config.env"

if ! PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)' 2>/dev/null) \
   || [ -z "$PROJECT_NUMBER" ]; then
  {
    echo "A(z) '$PROJECT_ID' projekt nem érhető el a(z) '$(gcloud config get-value account 2>/dev/null)' fiókkal."
    echo "Gyakori ok: a projekt AZONOSÍTÓJA eltér a nevétől (pl. archi-demo-123456). A valódi azonosító:"
    gcloud projects list --format='table(projectId,name,projectNumber)' 2>/dev/null || true
    echo "Megoldás: PROJECT_ID=<azonosító> ./$(basename "$0")   vagy írja át a config.env PROJECT_ID sorát."
    echo "Ha a fiók rossz: gcloud auth login"
  } >&2
  exit 1
fi
gcloud config set project "$PROJECT_ID" >/dev/null 2>&1
REGISTRY="${REGION}-docker.pkg.dev/${PROJECT_ID}/${AR_REPO}"
RUN_SA_EMAIL="${RUN_SA}@${PROJECT_ID}.iam.gserviceaccount.com"
INFRA_SA_EMAIL="${INFRA_SA}@${PROJECT_ID}.iam.gserviceaccount.com"
BUILD_SA_EMAIL="${BUILD_SA}@${PROJECT_ID}.iam.gserviceaccount.com"

# Cloud Run determinisztikus URL-je: https://SZOLGÁLTATÁS-PROJEKTSZÁM.RÉGIÓ.run.app
url() { echo "https://$1-${PROJECT_NUMBER}.${REGION}.run.app"; }
img() { echo "${REGISTRY}/$1:${IMAGE_TAG}"; }

step() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
ok()   { printf '\033[32m✓\033[0m %s\n' "$*"; }
warn() { printf '\033[33m! %s\033[0m\n' "$*"; }
# exists <gcloud describe parancs...>: igaz, ha az erőforrás már létezik
exists() { "$@" >/dev/null 2>&1; }

# A futó komponensek listája (név → image)
BACKEND_SERVICES=(tenant-admin iam audit webhook partner irat)
WORKER_POOLS=(provisioner outbox-relay reporting-projector)
FRONTENDS=(frontend-app frontend-admin)
