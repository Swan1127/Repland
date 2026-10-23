#!/usr/bin/env bash
# Repland: rebuild the symlink that keeps a single copy of the sources.
# Run from the package root: ./setup-junction.sh
set -euo pipefail

root="$(cd "$(dirname "$0")" && pwd)"
target="$root/repland"
link_parent="$root/app/src/main/java/com/swan1127"
link="$link_parent/repland"

if [ ! -d "$target" ]; then
    echo "repland/ not found next to this script. Run it from the package root." >&2
    exit 1
fi
if [ -e "$link" ] || [ -L "$link" ]; then
    echo "The symlink already exists: $link"
    exit 0
fi

mkdir -p "$link_parent"
ln -s ../../../../../../repland "$link"
echo "Symlink created: $link -> $target"
