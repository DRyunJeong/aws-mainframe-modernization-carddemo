#!/usr/bin/env python3
"""Generate CBACT01C datasets: synthetic accounts (exercise COMP-3, dates, +/-)."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import oracle_lib as L

PROJ = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
DS = os.path.join(PROJ, 'src', 'test', 'resources', 'datasets', 'cbact01c')


def acct(aid, curr, credit, cash, cyc_cr, cyc_db, reissue, group):
    return (L.unum(aid, 11) + 'Y' + L.signed(curr, 12) + L.signed(credit, 12) + L.signed(cash, 12)
            + L.text('2020-01-01', 10) + L.text('2030-01-01', 10) + L.text(reissue, 10)
            + L.signed(cyc_cr, 12) + L.signed(cyc_db, 12) + L.text('00000', 10) + L.text(group, 10) + ' ' * 178)


def main():
    out = os.path.join(DS, 'synthetic', 'accounts')
    L.write_records(out, 'acctdata.dat', [
        # cyc_db = 0 -> program substitutes 2525.00 in OUTFILE COMP-3 field
        acct(1, 19400, 500000, 200000, 0, 0, '2025-05-20', 'A001'),
        # negative balance; nonzero cyc_db (kept)
        acct(2, -50000, 1000000, 300000, 0, 123456, '2024-12-31', 'B002'),
        # negative cyc_db (COMP-3 sign test)
        acct(3, 0, 750000, 250000, 0, -9999, '2023-01-15', 'C003'),
    ], 300)
    print("  synthetic/accounts: 3 accounts (COMP-3, dates, +/-)")


if __name__ == '__main__':
    main()
