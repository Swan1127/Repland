#!/usr/bin/env bash
# Give Linux CI a regular source directory for AGP/K2 lint analysis.
# repland/ remains the only tracked production source tree.
set -euo pipefail

root="$(cd "$(dirname "$0")" && pwd -P)"
source="$root/repland"
link_parent="$root/app/src/main/java/com/swan1127"
link="$link_parent/repland"

if [[ -e "$link" && ! -L "$link" ]]; then
    echo "Refusing to replace an existing non-symlink source path: $link" >&2
    exit 1
fi

bash "$root/setup-junction.sh"

if [[ ! -L "$link" || "$(readlink -f "$link_parent")" != "$link_parent" ]]; then
    echo "The CI source path must be a symlink under the canonical project directory: $link" >&2
    exit 1
fi
if [[ "$(readlink -f "$link")" != "$source" ]]; then
    echo "Refusing to replace a source link that does not point to $source" >&2
    exit 1
fi

# Unlink this verified link only. Never recursively delete an existing source tree.
unlink -- "$link"
mkdir -- "$link"
cp -R -- "$source/." "$link/"
diff -qr -- "$source" "$link"
echo "CI source copy matches the canonical source tree: $source"
