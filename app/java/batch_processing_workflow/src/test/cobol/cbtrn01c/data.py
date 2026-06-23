#!/usr/bin/env python3
"""Generate CBTRN01C datasets: normalized sample + a 3-branch verification case."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import oracle_lib as L

PROJ = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
REPO = os.path.abspath(os.path.join(PROJ, '..', '..', '..'))
ASCII = os.path.join(REPO, 'app', 'data', 'ASCII')
DS = os.path.join(PROJ, 'src', 'test', 'resources', 'datasets', 'cbtrn01c')

# signed-field maps (0-based offset, length)
ACCT_SIGNED = [(12, 12), (24, 12), (36, 12), (78, 12), (90, 12)]
DALY_SIGNED = [(132, 11)]


def xref(card, cust, acct):
    return L.text(card, 16) + L.unum(cust, 9) + L.unum(acct, 11) + ' ' * 14


def acct(aid, group='G1'):
    return (L.unum(aid, 11) + 'Y' + L.signed(10000, 12) + L.signed(500000, 12) + L.signed(200000, 12)
            + L.text('2020-01-01', 10) + L.text('2030-01-01', 10) + L.text('2025-01-01', 10)
            + L.signed(0, 12) + L.signed(0, 12) + L.text('00000', 10) + L.text(group, 10) + ' ' * 178)


def daly(tid, card, amt=0):
    ts = '2022-07-18.00.00.00.000000'
    return (L.text(tid, 16) + L.text('01', 2) + L.unum(1, 4) + L.text('System', 10) + L.text('desc', 100)
            + L.signed(amt, 11) + L.unum(0, 9) + L.text('', 50) + L.text('', 50) + L.text('', 10)
            + L.text(card, 16) + L.text(ts, 26) + L.text(ts, 26) + ' ' * 20)


def main():
    # sample: normalize the repo inputs the program reads
    for name, width, signed in [('dailytran', 350, DALY_SIGNED), ('cardxref', 50, []), ('acctdata', 300, ACCT_SIGNED)]:
        n = L.normalize_file(os.path.join(ASCII, name + '.txt'),
                             os.path.join(DS, 'sample', name + '.dat'), width, signed)
        print(f"  sample/{name}.dat records={n}")

    # synthetic 'verify': exercises all three branches
    #   C1 -> A1 (present)  | C2 -> A2 (absent)  | CX (no xref)
    L.write_records(os.path.join(DS, 'synthetic', 'verify'), 'cardxref.dat',
                    [xref('1111000000000001', 1, 1), xref('2222000000000002', 2, 2)], 50)
    L.write_records(os.path.join(DS, 'synthetic', 'verify'), 'acctdata.dat',
                    [acct(1)], 300)
    L.write_records(os.path.join(DS, 'synthetic', 'verify'), 'dailytran.dat',
                    [daly('TXN0000000000001', '1111000000000001', 1000),    # valid
                     daly('TXN0000000000002', '2222000000000002', 2000),    # xref ok, account missing
                     daly('TXN0000000000003', '9999000000000099', 3000)],   # card not in xref
                    350)
    print("  synthetic/verify: 3 daily trans (valid / missing-account / invalid-card)")


if __name__ == '__main__':
    main()
