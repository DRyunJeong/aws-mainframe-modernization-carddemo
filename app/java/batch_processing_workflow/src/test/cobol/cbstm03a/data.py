#!/usr/bin/env python3
"""Generate CBSTM03A datasets: a small referentially-consistent set of 4 INDEXED
masters (TRNXFILE keyed card+id, XREFFILE, CUSTFILE, ACCTFILE) that exercises the
statement formatting — multiple transactions per card, a negative amount (trailing
minus in the Z(9).99- / 9(9).99- edits), and name/address STRING assembly."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import oracle_lib as L

PROJ = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
DS = os.path.join(PROJ, 'src', 'test', 'resources', 'datasets', 'cbstm03a')

CARD1 = '4111000000000001'
CARD2 = '4111000000000002'


def xref(card, cust, acct):
    r = L.text(card, 16) + L.unum(cust, 9) + L.unum(acct, 11) + ' ' * 14
    assert len(r) == 50, len(r)
    return r


def cust(cid, first, mid, last, a1, a2, a3, state, ctry, zipc, fico):
    r = (L.unum(cid, 9) + L.text(first, 25) + L.text(mid, 25) + L.text(last, 25)
         + L.text(a1, 50) + L.text(a2, 50) + L.text(a3, 50)
         + L.text(state, 2) + L.text(ctry, 3) + L.text(zipc, 10)
         + L.text('555-000-0000', 15) + L.text('', 15) + L.unum(123456789, 9)
         + L.text('GOVID', 20) + L.text('1980-01-01', 10) + L.text('', 10)
         + L.text('Y', 1) + L.unum(fico, 3) + ' ' * 168)
    assert len(r) == 500, len(r)
    return r


def acct(aid, bal):
    r = (L.unum(aid, 11) + 'Y' + L.signed(bal, 12) + L.signed(500000, 12) + L.signed(100000, 12)
         + L.text('2020-01-01', 10) + L.text('2030-01-01', 10) + L.text('2025-01-01', 10)
         + L.signed(0, 12) + L.signed(0, 12) + L.text('10001', 10) + L.text('G1', 10) + ' ' * 178)
    assert len(r) == 300, len(r)
    return r


def trnx(card, tid, typ, cat, amt, desc):
    r = (L.text(card, 16) + L.text(tid, 16) + L.text(typ, 2) + L.unum(cat, 4)
         + L.text('POS', 10) + L.text(desc, 100) + L.signed(amt, 11) + L.unum(1, 9)
         + L.text('MERCH', 50) + L.text('CITY', 50) + L.text('12345', 10)
         + L.text('2025-01-01.00.00.00.000000', 26) + L.text('2025-01-02.00.00.00.000000', 26) + ' ' * 20)
    assert len(r) == 350, len(r)
    return r


def main():
    smp = os.path.join(DS, 'sample')
    L.write_records(smp, 'cardxref.dat', [xref(CARD1, 1, 1), xref(CARD2, 2, 2)], 50)
    L.write_records(smp, 'custdata.dat',
                    [cust(1, 'John', 'Q', 'Public', '123 Main St', 'Apt 4',
                          'Springfield', 'IL', 'USA', '62704', 750),
                     cust(2, 'Jane', 'A', 'Doe', '9 Oak Ave', '',
                          'Portland', 'OR', 'USA', '97201', 820)], 500)
    L.write_records(smp, 'acctdata.dat', [acct(1, 123456), acct(2, -5000)], 300)  # +1234.56 / -50.00
    L.write_records(smp, 'trnxdata.dat',
                    [trnx(CARD1, 'TXN0000000000001', '01', 1, 12345, 'Grocery Store'),   # +123.45
                     trnx(CARD1, 'TXN0000000000002', '02', 5, -2500, 'Refund'),          # -25.00
                     trnx(CARD2, 'TXN0000000000003', '03', 1, 99999, 'Electronics')], 350)  # +999.99
    print("  sample: 2 cards / 2 custs / 2 accts / 3 trnx (card1=2, card2=1)")

    # ---- edge: card with NO transactions (0.00 total), empty middle name (double space),
    #            negative total, and a 10-digit balance (9(9).99- high-order truncation) ----
    card3, card4 = '4111000000000003', '4111000000000004'
    edge = os.path.join(DS, 'synthetic', 'edge')
    L.write_records(edge, 'cardxref.dat', [xref(card3, 3, 3), xref(card4, 4, 4)], 50)
    L.write_records(edge, 'custdata.dat',
                    [cust(3, 'Solo', '', 'Name', '1 Solo Way', '',
                          'Lonelytown', 'TX', 'USA', '00001', 600),       # empty middle -> "Solo  Name"
                     cust(4, 'Max', 'Q', 'Power', '9 Power Rd', 'Suite 1',
                          'Metropolis', 'NY', 'USA', '10002', 999)], 500)
    L.write_records(edge, 'acctdata.dat', [acct(3, 0), acct(4, -999999999999)], 300)  # 0 / -10^10 trunc to 9(9)
    L.write_records(edge, 'trnxdata.dat',
                    [trnx(card4, 'TXN0000000000004', '01', 1, -50000, 'Big Refund')], 350)  # card3: none; card4: -500.00
    print("  edge: card3 (no trnx, 0 total, empty middle) + card4 (1 trnx -500.00, -10^10 bal)")


if __name__ == '__main__':
    main()
