#!/usr/bin/env python3
"""Generate CBTRN03C datasets: a transaction-report case (synthetic)."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import oracle_lib as L

PROJ = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
DS = os.path.join(PROJ, 'src', 'test', 'resources', 'datasets', 'cbtrn03c')


def xref(card, cust, acct):
    return L.text(card, 16) + L.unum(cust, 9) + L.unum(acct, 11) + ' ' * 14


def ttype(typ, desc):
    return L.text(typ, 2) + L.text(desc, 50) + ' ' * 8


def tcatg(typ, cat, desc):
    return L.text(typ, 2) + L.unum(cat, 4) + L.text(desc, 50) + ' ' * 4


def tran(tid, card, typ, cat, amt, proc):
    procts = proc + '.12.00.00.000000'          # 'YYYY-MM-DD.12.00.00.000000' = 26
    return (L.text(tid, 16) + L.text(typ, 2) + L.unum(cat, 4) + L.text('POS', 10) + L.text('desc', 100)
            + L.signed(amt, 11) + L.unum(0, 9) + L.text('MERCH', 50) + L.text('CITY', 50) + L.text('00000', 10)
            + L.text(card, 16) + L.text(procts, 26) + L.text(procts, 26) + ' ' * 20)


def dateparm(start, end):
    return (L.text(start, 10) + ' ' + L.text(end, 10) + ' ' * 59)      # 80 bytes


def main():
    out = os.path.join(DS, 'synthetic', 'report')
    C1, C2 = '1111000000000001', '2222000000000002'
    L.write_records(out, 'cardxref.dat', [xref(C1, 1, 1), xref(C2, 2, 2)], 50)
    L.write_records(out, 'trantype.dat', [ttype('01', 'Regular Sales Draft'),
                                          ttype('02', 'Payment Thank You')], 60)
    L.write_records(out, 'trancatg.dat', [tcatg('01', 1, 'Regular Sales'),
                                          tcatg('02', 2, 'Payment')], 60)
    # all in date range; grouped by card; mix of +/- and a large amount (edited-field test)
    L.write_records(out, 'transact.dat', [
        tran('TXN0000000000001', C1, '01', 1, 10050, '2025-03-01'),     # +100.50
        tran('TXN0000000000002', C1, '01', 1, -2500, '2025-03-02'),     # -25.00
        tran('TXN0000000000003', C1, '02', 2, 500000, '2025-03-03'),    # +5000.00
        tran('TXN0000000000004', C2, '01', 1, 199, '2025-03-04'),       # +1.99 (new account)
        tran('TXN0000000000005', C2, '02', 2, -99999999, '2025-03-05'), # -999999.99
    ], 350)
    L.write_records(out, 'dateparm.dat', [dateparm('2025-01-01', '2025-12-31')], 80)
    print("  synthetic/report: 5 trans (2 cards), trantype/trancatg/xref, dateparm")

    # pagebreak: one card, 25 transactions -> crosses the 20-line page boundary
    out2 = os.path.join(DS, 'synthetic', 'pagebreak')
    L.write_records(out2, 'cardxref.dat', [xref(C1, 1, 1)], 50)
    L.write_records(out2, 'trantype.dat', [ttype('01', 'Regular Sales Draft')], 60)
    L.write_records(out2, 'trancatg.dat', [tcatg('01', 1, 'Regular Sales')], 60)
    L.write_records(out2, 'transact.dat',
                    [tran('TXN%013d' % (n + 1), C1, '01', 1, (n + 1) * 111, '2025-06-%02d' % (n + 1))
                     for n in range(25)], 350)
    L.write_records(out2, 'dateparm.dat', [dateparm('2025-01-01', '2025-12-31')], 80)
    print("  synthetic/pagebreak: 25 trans (1 card) -> page break")


if __name__ == '__main__':
    main()
