#!/usr/bin/env bash
# Az összes Java modul buildje Dockerek nélkül (helyi Maven): előbb a közös könyvtárak, utána a szolgáltatások.
set -euo pipefail
cd "$(dirname "$0")/../workspace"
mvn -B -ntp -q -f platform-libs/pom.xml install -DskipTests
for d in service-* worker-*; do
  echo "== $d"
  mvn -B -ntp -q -f "$d/pom.xml" package -DskipTests
done
