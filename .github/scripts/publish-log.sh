#!/usr/bin/env bash
# CI napló publikálása a ci-logs branchre (a fejlesztés közbeni visszajelzéshez).
# Használat: publish-log.sh <logfile> <status>
set -u
LOG="$(realpath "$1")"; STATUS="$2"
SAFE="${GITHUB_REF_NAME//\//_}"
WORK=$(mktemp -d)
REMOTE="https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"
for i in 1 2 3 4 5; do
  rm -rf "$WORK/cil"
  if git clone -q --depth 1 --branch ci-logs "$REMOTE" "$WORK/cil" 2>/dev/null; then :; else
    mkdir -p "$WORK/cil" && (cd "$WORK/cil" && git init -q -b ci-logs && git remote add origin "$REMOTE")
  fi
  cd "$WORK/cil"
  git config user.name "ci-bot"; git config user.email "ci-bot@users.noreply.github.com"
  { echo "branch: ${GITHUB_REF_NAME}"; echo "commit: ${GITHUB_SHA}"; echo "status: ${STATUS}"; echo "run: ${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"; echo "----"; tail -n 2500 "$LOG"; } > "${SAFE}.log"
  echo "${STATUS} ${GITHUB_SHA}" > "${SAFE}.status"
  git add -A && git commit -q -m "ci: ${GITHUB_REF_NAME} ${STATUS} ${GITHUB_SHA:0:7}" || exit 0
  if git push -q origin ci-logs 2>/dev/null; then exit 0; fi
  sleep $((i * 3))
done
echo "Nem sikerült a napló publikálása" >&2
