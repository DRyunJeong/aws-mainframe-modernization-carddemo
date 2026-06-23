#!/usr/bin/env python3
"""Generate CBCUS01C datasets: normalized sample + a deliberately-unsorted case."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import oracle_lib as L

PROJ = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
REPO = os.path.abspath(os.path.join(PROJ, '..', '..', '..'))
ASCII = os.path.join(REPO, 'app', 'data', 'ASCII')
DS = os.path.join(PROJ, 'src', 'test', 'resources', 'datasets', 'cbcus01c')


def cust(cid, first, last, ssn, fico):
    # CVCUS01Y (500): all numeric fields unsigned. Only key fields matter for this read/print program.
    rec = (L.unum(cid, 9) + L.text(first, 25) + L.text('', 25) + L.text(last, 25)
           + L.text('123 MAIN ST', 50) + L.text('', 50) + L.text('', 50)
           + L.text('CA', 2) + L.text('USA', 3) + L.text('90001', 10)
           + L.text('555-0001', 15) + L.text('', 15) + L.unum(ssn, 9) + L.text('DL12345', 20)
           + L.text('1990-01-01', 10) + L.text('', 10) + L.text('Y', 1) + L.unum(fico, 3))
    return (rec + ' ' * 500)[:500]


def main():
    n = L.normalize_file(os.path.join(ASCII, 'custdata.txt'),
                         os.path.join(DS, 'sample', 'custdata.dat'), 500, [])
    print(f"  sample/custdata.dat records={n}")

    recs = [cust(900, 'CARL', 'ZULU', 111111111, 700),
            cust(100, 'ANNA', 'ALPHA', 222222222, 650),
            cust(500, 'BETH', 'MIKE', 333333333, 800)]
    L.write_records(os.path.join(DS, 'synthetic', 'unsorted'), 'custdata.dat', recs, 500)
    print(f"  synthetic/unsorted/custdata.dat records={len(recs)}")


if __name__ == '__main__':
    main()
