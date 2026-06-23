#!/usr/bin/env bash
# =============================================================================
# run_oracle.sh <prog> [case...] - generate the GOLDEN MASTER for a batch
# program by running the *original* app/cbl/<PROG>.cbl under GnuCOBOL.
#
# This is the source of truth for the Java migration (MIGRATION_STRATEGY.md):
# the oracle is the running COBOL, never a code interpretation.
#
# Per-program specifics live in  src/test/cobol/<prog>/oracle.conf  which sets:
#   CBL            absolute path to the original program source
#   EXTRA_CBL      (optional) extra sources to link, e.g. a called subprogram
#   DEFAULT_PARM   (optional) default JCL PARM
#   wire_inputs  <work> <dsdir>    export env for input/output file wiring
#   capture_outputs <work> <goldendir>   copy/mask outputs into the golden dir
# and a harness  src/test/cobol/<prog>/HARNESS.cbl  that loads inputs, CALLs the
# program (unmodified), and dumps any modified KSDS.
#
# Requires: GnuCOBOL (cobc) with a working INDEXED handler, python3.
# =============================================================================
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)                 # src/test/cobol
PROJ=$(cd "$HERE/../../.." && pwd)                  # batch_processing_workflow
REPO=$(cd "$PROJ/../../.." && pwd)                  # repo root
DATA="$PROJ/src/test/resources/datasets"
GOLDEN="$PROJ/src/test/resources/golden"
STD="${COBSTD:-ibm}"

prog="${1:-}"; [ -z "$prog" ] && { echo "usage: run_oracle.sh <prog> [case...]"; exit 2; }
shift || true
conf="$HERE/$prog/oracle.conf"
[ -f "$conf" ] || { echo "missing $conf"; exit 2; }

HARNESS="$HERE/$prog/HARNESS.cbl"
EXTRA_CBL=""
DEFAULT_PARM="2022071800"
source "$conf"     # sets CBL (+ optional HARNESS/EXTRA_CBL/DEFAULT_PARM) and defines wire_inputs/capture_outputs

BUILD="$HERE/$prog/build"; mkdir -p "$BUILD"; export BUILD
if [ "${RUN_DIRECT:-}" = 1 ]; then
    # The original program is the runnable main (loaded/dumped by helper programs the
    # conf compiles via compile_extra). Used where a single combined harness hits a
    # GnuCOBOL many-files codegen issue (e.g. CBTRN02C).
    echo ">> [$prog] compiling original $(basename "$CBL") as standalone (cobc --std=$STD, UNMODIFIED)"
    cobc -std="$STD" -fixed -x "$CBL" $EXTRA_CBL ${INCDIRS:-} -I "$REPO/app/cpy" -o "$BUILD/harness"
else
    echo ">> [$prog] compiling original $(basename "$CBL") + harness (cobc --std=$STD, UNMODIFIED)"
    cobc -std="$STD" -fixed -c "$CBL" ${INCDIRS:-} -I "$REPO/app/cpy" -o "$BUILD/prog.o"
    objs="$BUILD/prog.o"
    for x in $EXTRA_CBL; do
        cobc -std="$STD" -fixed -c "$x" -I "$REPO/app/cpy" -o "$BUILD/$(basename "$x").o"
        objs="$objs $BUILD/$(basename "$x").o"
    done
    cobc -std="$STD" -free -x "$HARNESS" $objs -o "$BUILD/harness"
fi
# optional: compile per-program helper loaders/dumpers
if declare -f compile_extra >/dev/null; then compile_extra "$BUILD"; fi

# mask_ts <in> <out> <recwidth> <start> <end> : blank a per-record byte range (non-deterministic timestamps)
mask_ts() {
python3 - "$@" <<'PY'
import sys
data = open(sys.argv[1], 'rb').read()
rec, a, b = int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5])
out = bytearray()
for i in range(0, len(data), rec):
    r = bytearray(data[i:i+rec])
    if len(r) == rec:
        r[a:b] = b'#' * (b - a)
    out += r
open(sys.argv[2], 'wb').write(out)
PY
}

run_one() {
    local name="$1" dsdir="$2"
    local parm="$DEFAULT_PARM"
    [ -f "$dsdir/parm.txt" ] && parm=$(tr -d ' \n\r' < "$dsdir/parm.txt")
    local work; work=$(mktemp -d)
    export PARMDATE="$parm"
    wire_inputs "$work" "$dsdir"
    echo ">> [$prog/$name] running oracle (PARM=$parm)"
    local rc=0
    # Keep SYSOUT (DISPLAY) separate from stderr: libcob runtime notes (e.g. implicit
    # CLOSE warnings) are not program output and must not pollute the golden.
    "$BUILD/harness" > "$work/stdout.txt" 2>"$work/stderr.txt" || rc=$?
    # tolerate business RETURN-CODEs (e.g. CBTRN02C sets RC=4 when rejects exist);
    # only a signal (rc>128) is a genuine crash.
    if [ "$rc" -gt 128 ]; then
        echo "!! oracle crashed (rc=$rc) for $prog/$name"; sed 's/^/   /' "$work/stderr.txt"; rm -rf "$work"; return 1
    fi
    mkdir -p "$GOLDEN/$prog/$name"
    capture_outputs "$work" "$GOLDEN/$prog/$name"
    echo "   -> golden/$prog/$name"
    rm -rf "$work"
}

names=("$@")
if [ ${#names[@]} -eq 0 ]; then
    names=()
    [ -d "$DATA/$prog/sample" ] && names+=(sample)
    [ -d "$DATA/$prog/synthetic" ] && for d in "$DATA/$prog/synthetic"/*/; do [ -d "$d" ] && names+=("$(basename "$d")"); done
fi
for n in "${names[@]}"; do
    if [ "$n" = sample ]; then run_one sample "$DATA/$prog/sample"; else run_one "$n" "$DATA/$prog/synthetic/$n"; fi
done
echo ">> [$prog] done. goldens in $GOLDEN/$prog/"
