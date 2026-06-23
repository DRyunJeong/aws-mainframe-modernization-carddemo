#!/usr/bin/env python3
"""
normalize_dataset.py - turn the repo's app/data/ASCII sample files into clean,
fixed-width datasets that GnuCOBOL (ASCII mode) reads correctly.

Two adaptations are applied (FILE-I/O only; numeric VALUES are preserved):
  1. line endings stripped/normalised, records padded to exact fixed width.
  2. SIGN TRANSCODE: signed DISPLAY (PIC S9..V99) fields in the sample carry
     their sign as an EBCDIC zone-overpunch in the LAST byte (e.g. '{' = +0,
     'J' = -1). GnuCOBOL on an ASCII platform (native EBCDIC: no) MISREADS
     those bytes during arithmetic, so the trailing sign byte is remapped to
     GnuCOBOL's native ASCII convention: positive -> '0'..'9' (0x30-0x39),
     negative -> 'p'..'y' (0x70-0x79). This changes the encoding glyph only,
     never the value, and is required for the oracle to compute correctly.
     The Java ZonedDecimal codec implements the SAME native convention.

Usage: normalize_dataset.py <src-ascii-dir> <out-dataset-dir>
"""
import sys, os

# EBCDIC zone-overpunch glyph -> GnuCOBOL native ASCII signed last-byte
OVERPUNCH_TO_NATIVE = {
    '{': '0', 'A': '1', 'B': '2', 'C': '3', 'D': '4',
    'E': '5', 'F': '6', 'G': '7', 'H': '8', 'I': '9',   # positive 0..9
    '}': 'p', 'J': 'q', 'K': 'r', 'L': 's', 'M': 't',
    'N': 'u', 'O': 'v', 'P': 'w', 'Q': 'x', 'R': 'y',   # negative 0..9
}

# filename -> (record-width, [ (0-based offset, length) of each signed field ])
SPECS = {
    'tcatbal':  ('tcatbal.txt',  'tcatbal.dat',   50, [(17, 11)]),                       # TRAN-CAT-BAL
    'cardxref': ('cardxref.txt', 'cardxref.dat',  50, []),                               # no signed fields
    'discgrp':  ('discgrp.txt',  'discgrp.dat',   50, [(16, 6)]),                         # DIS-INT-RATE
    'acctdata': ('acctdata.txt', 'acctdata.dat', 300, [(12, 12), (24, 12), (36, 12),      # CURR-BAL, CREDIT-LIMIT, CASH-LIMIT
                                                        (78, 12), (90, 12)]),            # CYC-CREDIT, CYC-DEBIT
}


def transcode_sign(rec: str, fields):
    b = list(rec)
    for off, ln in fields:
        i = off + ln - 1                  # last byte carries the sign
        c = b[i]
        if c in OVERPUNCH_TO_NATIVE:
            b[i] = OVERPUNCH_TO_NATIVE[c]
        # a plain digit '0'-'9' is already native positive -> leave as is
    return ''.join(b)


def normalize_file(src_path, out_path, width, fields):
    out = []
    with open(src_path, 'r', newline='') as f:
        for raw in f:
            rec = raw.rstrip('\r\n')
            rec = (rec + ' ' * width)[:width]      # pad/truncate to exact width
            rec = transcode_sign(rec, fields)
            out.append(rec)
    with open(out_path, 'w', newline='') as f:
        for rec in out:
            f.write(rec + '\n')
    return len(out)


def main():
    if len(sys.argv) != 3:
        sys.exit("usage: normalize_dataset.py <src-ascii-dir> <out-dataset-dir>")
    src_dir, out_dir = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    for key, (src_name, out_name, width, fields) in SPECS.items():
        n = normalize_file(os.path.join(src_dir, src_name),
                           os.path.join(out_dir, out_name), width, fields)
        print(f"  {out_name:14} width={width} records={n} signed-fields={len(fields)}")


if __name__ == '__main__':
    main()
