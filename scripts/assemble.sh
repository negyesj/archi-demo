#!/usr/bin/env bash
# A szolgáltatás-branchek összerakása a workspace/ mappába (git worktree-k), hogy a Docker Compose és a
# helyi build egyben lássa őket. A kód a brancheken külön marad; a workspace nem kerül verziókezelésbe.
#
#   ./scripts/assemble.sh            # minden branch az origin legfrissebb állapotára
#   ./scripts/assemble.sh --local    # helyi branchek (fetch nélkül)
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$(pwd)
LOCAL=${1:-}
[ "$LOCAL" = "--local" ] || git fetch --quiet origin '+refs/heads/*:refs/remotes/origin/*'
mkdir -p workspace
grep -v '^\s*#' scripts/branches.txt | grep -v '^\s*$' | while read -r BRANCH; do
  DIR="workspace/${BRANCH//\//-}"
  REF="origin/$BRANCH"; [ "$LOCAL" = "--local" ] && REF="$BRANCH"
  if [ -d "$DIR" ]; then
    git -C "$DIR" checkout --quiet --detach "$REF"
  else
    git worktree add --quiet --detach "$DIR" "$REF"
  fi
  echo "✓ $BRANCH → $DIR ($(git -C "$DIR" rev-parse --short HEAD))"
done
echo "Kész: $ROOT/workspace"
