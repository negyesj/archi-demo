#!/usr/bin/env bash
# Füstteszt a futó Compose környezeten: platform admin token → tenant létrehozása → ACTIVE állapot →
# az első admin ideiglenes jelszava kiolvasható → a tenant nyilvános adata elérhető a gatewayen.
set -euo pipefail
KC=${KC:-http://localhost:8180}
API=${API:-http://localhost:8080}
CODE=${CODE:-smoke$(date +%s | tail -c 6)}

echo "Platform admin token..."
TOKEN=$(curl -sf -d grant_type=password -d client_id=platform-cli -d username=admin -d password=admin \
  "$KC/realms/platform/protocol/openid-connect/token" | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
H=(-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json")

echo "Katalógus..."
for i in $(seq 1 30); do
  N=$(curl -sf "${H[@]}" "$API/api/admin/catalog" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)))')
  [ "$N" -ge 2 ] && break; sleep 5
done
echo "  $N szolgáltatás a katalógusban"

echo "Tenant létrehozása: $CODE"
curl -sf "${H[@]}" -X POST "$API/api/admin/tenants" -d "{\"code\":\"$CODE\",\"name\":\"Füstteszt $CODE\",\"enabledServices\":[\"partner\",\"irat\"],\"adminUsername\":\"admin\",\"adminName\":\"Teszt Admin\",\"adminEmail\":\"admin@$CODE.local\"}" > /dev/null

for i in $(seq 1 60); do
  STATUS=$(curl -sf "${H[@]}" "$API/api/admin/tenants/$CODE" | python3 -c 'import sys,json;print(json.load(sys.stdin)["status"])')
  echo "  állapot: $STATUS"
  [ "$STATUS" = "ACTIVE" ] && break
  [ "$STATUS" = "FAILED" ] && { curl -s "${H[@]}" "$API/api/admin/tenants/$CODE"; exit 1; }
  sleep 5
done
[ "$STATUS" = "ACTIVE" ]

curl -sf "${H[@]}" -X POST "$API/api/admin/tenants/$CODE/bootstrap-password" > /dev/null && echo "Első admin jelszava kiolvasható"
sleep 5
curl -sf "$API/api/tenants/$CODE" && echo && echo "OK: a tenant elérhető: http://localhost:4200/t/$CODE"
