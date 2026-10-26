#!/usr/bin/env bash
# Füstteszt a futó Compose környezeten: platform admin token → tenant létrehozása → ACTIVE állapot →
# az első admin ideiglenes jelszava kiolvasható → a tenant nyilvános adata elérhető a gatewayen.
# Indulás után a szolgáltatások lassan (egy kisebb gépen percek alatt) állnak fel, ezért mindenhol vár/újrapróbál.
set -uo pipefail
KC=${KC:-http://localhost:8180}
API=${API:-http://localhost:8080}
CODE=${CODE:-smoke$(date +%s | tail -c 6)}
TIMEOUT=${TIMEOUT:-600}

json() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)" 2>/dev/null; }

wait_until() { # wait_until <leírás> <parancs>
  local desc="$1"; shift
  local start=$(date +%s)
  until "$@"; do
    if [ $(( $(date +%s) - start )) -gt "$TIMEOUT" ]; then echo "IDŐTÚLLÉPÉS: $desc"; exit 1; fi
    sleep 5
  done
  echo "✓ $desc"
}

get_token() {
  TOKEN=$(curl -sf -d grant_type=password -d client_id=platform-cli -d username=admin -d password=admin \
    "$KC/realms/platform/protocol/openid-connect/token" | json 'd["access_token"]')
  [ -n "$TOKEN" ]
}
wait_until "platform admin token (Keycloak)" get_token
H=(-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json")

catalog_ready() {
  get_token || return 1
  H=(-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json")
  local n
  n=$(curl -sf "${H[@]}" "$API/api/admin/catalog" | json 'len(d)')
  [ -n "$n" ] && [ "$n" -ge 4 ]
}
wait_until "szolgáltatás-katalógus (tenant-admin + manifestek)" catalog_ready

echo "Tenant létrehozása: $CODE"
curl -sf "${H[@]}" -X POST "$API/api/admin/tenants" -d "{\"code\":\"$CODE\",\"name\":\"Füstteszt $CODE\",\"enabledServices\":[\"partner\",\"irat\"],\"adminUsername\":\"admin\",\"adminName\":\"Teszt Admin\",\"adminEmail\":\"admin@$CODE.local\"}" > /dev/null \
  || { echo "Tenant létrehozás sikertelen"; curl -s "${H[@]}" -X POST "$API/api/admin/tenants" -d "{}"; exit 1; }

tenant_active() {
  get_token || return 1
  H=(-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json")
  STATUS=$(curl -sf "${H[@]}" "$API/api/admin/tenants/$CODE" | json 'd["status"]')
  echo "  állapot: ${STATUS:-?}"
  if [ "$STATUS" = "FAILED" ]; then curl -s "${H[@]}" "$API/api/admin/tenants/$CODE"; echo; exit 1; fi
  [ "$STATUS" = "ACTIVE" ]
}
wait_until "tenant aktív (provisioner: PostgreSQL, MongoDB, Keycloak realm, első admin)" tenant_active

curl -sf "${H[@]}" -X POST "$API/api/admin/tenants/$CODE/bootstrap-password" | json '"✓ első admin: " + d["username"] + " (ideiglenes jelszó kiolvasható)"'

public_info() { curl -sf "$API/api/tenants/$CODE" > /dev/null; }
wait_until "tenant elérhető a gatewayen (/api/tenants/$CODE)" public_info
echo "OK: http://localhost:4200/t/$CODE"
