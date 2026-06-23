#!/usr/bin/env python3
"""Generate CBACT02C datasets: normalized sample + a deliberately-unsorted case."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import oracle_lib as L

PROJ = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
REPO = os.path.abspath(os.path.join(PROJ, '..', '..', '..'))
ASCII = os.path.join(REPO, 'app', 'data', 'ASCII')
DS = os.path.join(PROJ, 'src', 'test', 'resources', 'datasets', 'cbact02c')


def card(num, acct, cvv, name, exp, status):
    # CVACT02Y: CARD-NUM X(16) + ACCT-ID 9(11) + CVV 9(3) + NAME X(50) + EXP X(10) + STATUS X(1) + FILLER X(59)
    return (L.text(num, 16) + L.unum(acct, 11) + L.unum(cvv, 3) + L.text(name, 50)
            + L.text(exp, 10) + L.text(status, 1) + ' ' * 59)


def main():
    n = L.normalize_file(os.path.join(ASCII, 'carddata.txt'),
                         os.path.join(DS, 'sample', 'carddata.dat'), 150, [])
    print(f"  sample/carddata.dat records={n}")

    # unsorted: records out of card-number order -> verifies key-order (sequential) read
    recs = [card('7777000000000007', 7, 777, 'GAMMA NAME', '2030-01-01', 'Y'),
            card('2222000000000002', 2, 222, 'ALPHA NAME', '2029-12-31', 'Y'),
            card('5555000000000005', 5, 555, 'BETA NAME', '2031-06-30', 'N')]
    L.write_records(os.path.join(DS, 'synthetic', 'unsorted'), 'carddata.dat', recs, 150)
    print(f"  synthetic/unsorted/carddata.dat records={len(recs)}")


if __name__ == '__main__':
    main()
