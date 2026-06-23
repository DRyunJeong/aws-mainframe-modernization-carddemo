#!/usr/bin/env python3
"""Generate CBEXPORT datasets: normalized real masters (customer/account/xref/card)
plus a synthetic transaction master (none ships in app/data/ASCII), and a small
edge case that stresses the binary/packed encoders (max IDs, negative/zero money)."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import oracle_lib as L

PROJ = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
REPO = os.path.abspath(os.path.join(PROJ, '..', '..', '..'))
ASCII = os.path.join(REPO, 'app', 'data', 'ASCII')
DS = os.path.join(PROJ, 'src', 'test', 'resources', 'datasets', 'cbexport')

# signed S9(10)V99 DISPLAY fields in CVACT01Y (300B): curr-bal, credit, cash, cyc-cr, cyc-db
ACCT_SIGNED = [(12, 12), (24, 12), (36, 12), (78, 12), (90, 12)]
TRAN_SIGNED = [(132, 11)]   # TRAN-AMT S9(09)V99


def tran(tid, typ, cat, amt, merchid, card='4111000000000001'):
    """CVTRA05Y transaction-master record (350B), all DISPLAY fields."""
    r = (L.text(tid, 16) + L.text(typ, 2) + L.unum(cat, 4) + L.text('POS', 10)
         + L.text('Purchase ' + tid, 100) + L.signed(amt, 11) + L.unum(merchid, 9)
         + L.text('MERCHANT ' + str(merchid), 50) + L.text('CITY', 50) + L.text('12345', 10)
         + L.text(card, 16) + L.text('2025-01-01.12.00.00.000000', 26)
         + L.text('2025-01-02.12.00.00.000000', 26) + ' ' * 20)
    assert len(r) == 350, len(r)
    return r


def cust(cid, ssn, fico, first='JOHN', last='DOE'):
    """CVCUS01Y customer record (500B), unsigned numeric ID/SSN/FICO."""
    r = (L.unum(cid, 9) + L.text(first, 25) + L.text('Q', 25) + L.text(last, 25)
         + L.text('1 MAIN ST', 50) + L.text('', 50) + L.text('', 50)
         + L.text('NY', 2) + L.text('USA', 3) + L.text('10001', 10)
         + L.text('555-111-2222', 15) + L.text('', 15) + L.unum(ssn, 9)
         + L.text('GOV' + str(cid), 20) + L.text('1980-01-01', 10) + L.text('', 10)
         + L.text('Y', 1) + L.unum(fico, 3) + ' ' * 168)
    assert len(r) == 500, len(r)
    return r


def acct(aid, status, bal, credit, cash, cyc_cr, cyc_db, group='G1'):
    r = (L.unum(aid, 11) + L.text(status, 1) + L.signed(bal, 12) + L.signed(credit, 12)
         + L.signed(cash, 12) + L.text('2020-01-01', 10) + L.text('2030-01-01', 10)
         + L.text('2025-01-01', 10) + L.signed(cyc_cr, 12) + L.signed(cyc_db, 12)
         + L.text('10001', 10) + L.text(group, 10) + ' ' * 178)
    assert len(r) == 300, len(r)
    return r


def xref(card, cust_id, acct_id):
    r = L.text(card, 16) + L.unum(cust_id, 9) + L.unum(acct_id, 11) + ' ' * 14
    assert len(r) == 50, len(r)
    return r


def card(num, acct_id, cvv, name='JOHN Q DOE', status='Y'):
    r = (L.text(num, 16) + L.unum(acct_id, 11) + L.unum(cvv, 3) + L.text(name, 50)
         + L.text('2030-01-01', 10) + L.text(status, 1) + ' ' * 59)
    assert len(r) == 150, len(r)
    return r


def main():
    # ---- sample: 4 real masters from app/data/ASCII + synthetic transactions ----
    smp = os.path.join(DS, 'sample')
    for name, width, signed in [('custdata', 500, []), ('acctdata', 300, ACCT_SIGNED),
                                ('cardxref', 50, []), ('carddata', 150, [])]:
        n = L.normalize_file(os.path.join(ASCII, name + '.txt'),
                             os.path.join(smp, name + '.dat'), width, signed)
        print(f"  sample/{name}.dat records={n}")
    L.write_records(smp, 'trandata.dat',
                    [tran('TXN0000000000001', '01', 1, 12345, 100),       # +123.45
                     tran('TXN0000000000002', '02', 5, -2500, 200),       # -25.00 (negative COMP-3)
                     tran('TXN0000000000003', '03', 99, 0, 300),          # 0.00
                     tran('TXN0000000000004', '07', 1, 99999999999, 999999999)],  # max S9(09)V99 amt + max merchid
                    350)
    print("  sample/trandata.dat records=4 (synthetic)")

    # ---- edge: stress binary/packed encoders across all 5 files ----
    edge = os.path.join(DS, 'synthetic', 'edge')
    L.write_records(edge, 'custdata.dat',
                    [cust(999999999, 123456789, 999, 'MAX', 'CUST'),       # max 9-digit id, max FICO
                     cust(1, 0, 1, 'MIN', 'CUST')], 500)
    L.write_records(edge, 'acctdata.dat',
                    [acct(99999999999, 'Y', -999999999999, -100, 0, 0, -1),   # max-neg bal(COMP-3), -0.01 cyc-db(COMP)
                     acct(2, 'N', 0, 0, 0, 0, 0)], 300)
    L.write_records(edge, 'cardxref.dat',
                    [xref('4111000000000001', 1, 99999999999),
                     xref('4111000000000002', 2, 2)], 50)
    L.write_records(edge, 'trandata.dat',
                    [tran('EDGE000000000001', '01', 1, -1, 1),
                     tran('EDGE000000000002', '99', 9999, 99999999999, 999999999)], 350)
    L.write_records(edge, 'carddata.dat',
                    [card('4111000000000001', 99999999999, 999),
                     card('4111000000000002', 2, 1)], 150)
    print("  synthetic/edge: 2 records each across 5 masters")


if __name__ == '__main__':
    main()
