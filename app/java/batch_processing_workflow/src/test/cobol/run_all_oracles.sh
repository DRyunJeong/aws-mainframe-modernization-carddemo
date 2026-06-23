#!/usr/bin/env bash
# Regenerate every migrated program's golden master. Each program directory under
# src/test/cobol/<prog>/ with an oracle.conf is run via run_oracle.sh.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
for conf in "$HERE"/*/oracle.conf; do
    prog=$(basename "$(dirname "$conf")")
    echo "== $prog =="
    bash "$HERE/run_oracle.sh" "$prog"
done
echo "== all goldens regenerated =="
