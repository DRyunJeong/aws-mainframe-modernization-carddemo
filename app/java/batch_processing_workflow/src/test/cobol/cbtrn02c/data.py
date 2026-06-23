#!/usr/bin/env python3
"""Generate CBTRN02C datasets: normalized sample + a posting/reject case."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import oracle_lib as L

PROJ = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
REPO = os.path.abspath(os.path.join(PROJ, '..', '..', '..'))
ASCII = os.path.join(REPO, 'app', 'data', 'ASCII')
DS = os.path.join(PROJ, 'src', 'test', 'resources', 'datasets', 'cbtrn02c')

ACCT_SIGNED = [(12, 12), (24, 12), (36, 12), (78, 12), (90, 12)]
DALY_SIGNED = [(132, 11)]
TCAT_SIGNED = [(17, 11)]


def xref(card, cust, acct):
    return L.text(card, 16) + L.unum(cust, 9) + L.unum(acct, 11) + ' ' * 14


def acct(aid, credit, exp, group='G1', cyc_cr=0, cyc_db=0, curr=10000):
    return (L.unum(aid, 11) + 'Y' + L.signed(curr, 12) + L.signed(credit, 12) + L.signed(20000000, 12)
            + L.text('2020-01-01', 10) + L.text(exp, 10) + L.text('2025-01-01', 10)
            + L.signed(cyc_cr, 12) + L.signed(cyc_db, 12) + L.text('00000', 10) + L.text(group, 10) + ' ' * 178)


def tcat(aid, typ, cat, bal):
    return L.unum(aid, 11) + L.text(typ, 2) + L.unum(cat, 4) + L.signed(bal, 11) + ' ' * 22


def daly(tid, card, typ, cat, amt, orig='2025-01-01.00.00.00.000000'):
    return (L.text(tid, 16) + L.text(typ, 2) + L.unum(cat, 4) + L.text('System', 10) + L.text('desc', 100)
            + L.signed(amt, 11) + L.unum(0, 9) + L.text('', 50) + L.text('', 50) + L.text('', 10)
            + L.text(card, 16) + L.text(orig, 26) + L.text(orig, 26) + ' ' * 20)


def main():
    for name, width, signed in [('dailytran', 350, DALY_SIGNED), ('cardxref', 50, []),
                                ('acctdata', 300, ACCT_SIGNED), ('tcatbal', 50, TCAT_SIGNED)]:
        n = L.normalize_file(os.path.join(ASCII, name + '.txt'),
                             os.path.join(DS, 'sample', name + '.dat'), width, signed)
        print(f"  sample/{name}.dat records={n}")

    out = os.path.join(DS, 'synthetic', 'post')
    L.write_records(out, 'cardxref.dat',
                    [xref('1111000000000001', 1, 1), xref('2222000000000002', 2, 2),
                     xref('3333000000000003', 3, 3), xref('4444000000000004', 4, 4)], 50)
    L.write_records(out, 'acctdata.dat',
                    [acct(1, 1000000, '2030-01-01'),       # A1 high limit, valid
                     acct(2, 10000, '2030-01-01'),         # A2 limit 100.00 -> overlimit
                     acct(3, 1000000, '2000-01-01')], 300)  # A3 expired (A4 absent)
    L.write_records(out, 'tcatbal.dat',
                    [tcat(1, '01', 1, 50000)], 50)          # existing A1/01/0001 = 500.00
    L.write_records(out, 'dailytran.dat',
                    [daly('TXN0000000000001', '1111000000000001', '01', 1, 10000),   # valid: update tcatbal
                     daly('TXN0000000000002', '1111000000000001', '01', 2, 5000),    # valid: CREATE tcatbal
                     daly('TXN0000000000003', '2222000000000002', '01', 1, 999900),  # overlimit (102)
                     daly('TXN0000000000004', '3333000000000003', '01', 1, 10000),   # expired (103)
                     daly('TXN0000000000005', '9999000000000099', '01', 1, 10000),   # invalid card (100)
                     daly('TXN0000000000006', '4444000000000004', '01', 1, 10000)],  # missing account (101)
                    350)
    print("  synthetic/post: 6 daily trans (2 valid + 4 rejects), 1 tcatbal create")


if __name__ == '__main__':
    main()
