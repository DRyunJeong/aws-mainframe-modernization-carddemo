#!/usr/bin/env python3
"""Generate CBACT03C datasets: normalized sample + synthetic edge cases."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))               # src/test/cobol/cbact03c
sys.path.insert(0, os.path.dirname(HERE))                       # src/test/cobol (for oracle_lib)
import oracle_lib as L

PROJ = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))   # batch_processing_workflow
REPO = os.path.abspath(os.path.join(PROJ, '..', '..', '..'))         # repo root
ASCII = os.path.join(REPO, 'app', 'data', 'ASCII')
DS = os.path.join(PROJ, 'src', 'test', 'resources', 'datasets', 'cbact03c')


def xref(card, cust, acct):
    # CVACT03Y: XREF-CARD-NUM X(16) + XREF-CUST-ID 9(9) + XREF-ACCT-ID 9(11) + FILLER X(14)
    return L.text(card, 16) + L.unum(cust, 9) + L.unum(acct, 11) + ' ' * 14


def main():
    # sample: normalize the repo's cardxref (no signed fields)
    n = L.normalize_file(os.path.join(ASCII, 'cardxref.txt'),
                         os.path.join(DS, 'sample', 'cardxref.dat'), 50, [])
    print(f"  sample/cardxref.dat records={n}")

    # synthetic 'unsorted': records deliberately out of card-number order — the program reads
    # XREFFILE by RECORD KEY (card number) SEQUENTIALLY, so output must be in card-number order.
    recs = [xref('5500000000000005', 5, 5),
            xref('1100000000000001', 1, 1),
            xref('9900000000000009', 9, 9),
            xref('3300000000000003', 3, 3)]
    L.write_records(os.path.join(DS, 'synthetic', 'unsorted'), 'cardxref.dat', recs, 50)
    print(f"  synthetic/unsorted/cardxref.dat records={len(recs)}")


if __name__ == '__main__':
    main()
