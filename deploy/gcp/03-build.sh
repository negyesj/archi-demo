#!/usr/bin/env bash
# 4. lépés: a branchek összerakása (scripts/assemble.sh) és az összes image buildje Cloud Builddel
# az Artifact Registrybe. Időtartam: kb. 20–30 perc.
#   ./03-build.sh                       # minden image
#   ./03-build.sh frontend-admin iam    # csak a megnevezett image-ek (néhány perc)
# Image nevek: gateway tenant-admin provisioner iam audit webhook partner irat outbox-relay
#              reporting-projector frontend-app frontend-admin keycloak
source "$(dirname "$0")/lib.sh"
cd "$REPO_ROOT"

step "Branchek összerakása a workspace/ mappába"
./scripts/assemble.sh

step "Keycloak build-kontextus (platform realm Cloud Run helyőrzőkkel)"
rm -rf workspace/_keycloak && mkdir -p workspace/_keycloak
cp deploy/gcp/keycloak/Dockerfile workspace/_keycloak/
sed -e 's#"http://localhost:4300/\*"#"${ADMIN_UI_REDIRECT}"#' \
    -e 's#"value": "admin"#"value": "${PLATFORM_ADMIN_PASSWORD}"#' \
    infra/keycloak/platform-realm.json > workspace/_keycloak/platform-realm.json
if ! grep -q 'ADMIN_UI_REDIRECT' workspace/_keycloak/platform-realm.json ||
   ! grep -q 'PLATFORM_ADMIN_PASSWORD' workspace/_keycloak/platform-realm.json; then
  echo "A realm fájl helyőrzőinek beírása nem sikerült" >&2; exit 1
fi

# Csak a forráskód kerüljön feltöltésre (git metaadat, node_modules, build kimenet nélkül)
cat > workspace/.gcloudignore <<'IGN'
.gcloudignore
.git
**/.git
**/node_modules/
**/target/
**/dist/
**/.angular/
IGN

step "Cloud Build: $REGISTRY (tag: $IMAGE_TAG${*:+, csak: $*})"
gcloud builds submit workspace --config deploy/gcp/cloudbuild.yaml \
  --substitutions "_REGISTRY=$REGISTRY,_TAG=$IMAGE_TAG,_ONLY=$*" \
  --service-account "projects/$PROJECT_ID/serviceAccounts/$BUILD_SA_EMAIL"

step "Image-ek:"
gcloud artifacts docker images list "$REGISTRY" --include-tags --filter "tags:$IMAGE_TAG" --format 'value(package)' | sort -u
echo "Következő: ./04-deploy.sh${*:+ $*}"
