#!/usr/bin/env python3
"""Generate CBIMPORT datasets.

The 'sample' input (expdata.dat) is the export file produced by the CBEXPORT oracle
(the chain in the plan) and is written there by cbexport's run_oracle capture — so
run `data.py cbexport` + `run_oracle.sh cbexport` BEFORE this. Here we derive a small
'unknown' case from it to exercise routing + the WHEN OTHER error path: a 'C' record,
the same record with its type byte flipped to 'Z' (unknown), and an 'A' record. Records
are raw 500-byte (record-sequential), since they carry binary COMP fields."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
DS = os.path.join(PROJ, 'src', 'test', 'resources', 'datasets', 'cbimport')
RECLEN = 500


def first_of(records, rectype):
    for r in records:
        if r[0:1] == rectype:
            return r
    raise SystemExit(f"no '{rectype.decode()}' record in chained sample expdata")


def main():
    sample = os.path.join(DS, 'sample', 'expdata.dat')
    if not os.path.exists(sample):
        raise SystemExit("missing " + sample + " — run data.py + run_oracle.sh for cbexport first")
    data = open(sample, 'rb').read()
    recs = [data[i:i + RECLEN] for i in range(0, len(data), RECLEN)]

    rc = first_of(recs, b'C')
    ra = first_of(recs, b'A')
    rz = bytearray(rc)
    rz[0] = ord('Z')                       # unknown record type -> error output

    out = os.path.join(DS, 'synthetic', 'unknown')
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, 'expdata.dat'), 'wb') as f:
        f.write(rc)
        f.write(bytes(rz))
        f.write(ra)
    print(f"  synthetic/unknown/expdata.dat: 3 records (C, Z=unknown, A)")
    print(f"  sample/expdata.dat: {len(recs)} records (from CBEXPORT oracle chain)")


if __name__ == '__main__':
    main()
