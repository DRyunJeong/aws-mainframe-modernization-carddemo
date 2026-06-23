#!/usr/bin/env python3
"""
make_synthetic.py - generate native-encoded synthetic datasets that exercise the
interest arithmetic and edge cases the all-zero sample data cannot. Output goes
to src/test/resources/datasets/synthetic/<case>/{tcatbal,cardxref,discgrp,acctdata}.dat

Encoding matches GnuCOBOL native ASCII zoned decimal (see ZonedDecimal / normalize_dataset.py):
positive last byte '0'..'9', negative last byte 'p'..'y'. Amounts are given in CENTS (scaled ints).
"""
import os, sys

def signed(cents: int, total_digits: int) -> str:
    """Native zoned-decimal string of `total_digits` (last digit carries sign)."""
    neg = cents < 0
    s = str(abs(cents)).zfill(total_digits)[-total_digits:]
    last = s[-1]
    last_b = chr(0x70 + int(last)) if neg else last       # 'p'..'y' for negative
    return s[:-1] + last_b

def unum(value: int, digits: int) -> str:
    return str(value).zfill(digits)[-digits:]

def text(s: str, width: int) -> str:
    return (s + ' ' * width)[:width]


class Dataset:
    def __init__(self):
        self.tcat = []      # (acctId, typeCd, catCd, bal_cents)
        self.accts = {}     # acctId -> record dict
        self.cards = {}      # acctId -> cardNum
        self.disc = []      # (group, typeCd, catCd, rate_cents)

    def acct(self, acct_id, group, curr_bal, cyc_credit=0, cyc_debit=0,
             credit=50000000, cash=20000000, card=None):
        self.accts[acct_id] = dict(group=group, curr_bal=curr_bal, cyc_credit=cyc_credit,
                                   cyc_debit=cyc_debit, credit=credit, cash=cash)
        self.cards[acct_id] = card or ("9" + acct_id.rjust(15, '0'))[:16]

    def cat(self, acct_id, type_cd, cat_cd, bal):
        self.tcat.append((acct_id, type_cd, cat_cd, bal))

    def rate(self, group, type_cd, cat_cd, rate):
        self.disc.append((group, type_cd, cat_cd, rate))

    # ---- record renderers (native-encoded, exact widths) ----
    def _tcat_rec(self, acct_id, type_cd, cat_cd, bal):
        return unum(int(acct_id), 11) + text(type_cd, 2) + unum(int(cat_cd), 4) + signed(bal, 11) + ' ' * 22

    def _xref_rec(self, acct_id, card):
        return text(card, 16) + unum(1, 9) + unum(int(acct_id), 11) + ' ' * 14

    def _disc_rec(self, group, type_cd, cat_cd, rate):
        return text(group, 10) + text(type_cd, 2) + unum(int(cat_cd), 4) + signed(rate, 6) + ' ' * 28

    def _acct_rec(self, acct_id, a):
        return (unum(int(acct_id), 11) + 'Y'
                + signed(a['curr_bal'], 12) + signed(a['credit'], 12) + signed(a['cash'], 12)
                + text('2020-01-01', 10) + text('2030-01-01', 10) + text('2025-01-01', 10)
                + signed(a['cyc_credit'], 12) + signed(a['cyc_debit'], 12)
                + text('00000', 10) + text(a['group'], 10) + ' ' * 178)

    def write(self, out_dir):
        os.makedirs(out_dir, exist_ok=True)
        for fn, recs, width in [
            ('tcatbal.dat', [self._tcat_rec(*t) for t in self.tcat], 50),
            ('cardxref.dat', [self._xref_rec(a, self.cards[a]) for a in sorted(self.cards)], 50),
            ('discgrp.dat', [self._disc_rec(*d) for d in self.disc], 50),
            ('acctdata.dat', [self._acct_rec(a, self.accts[a]) for a in sorted(self.accts)], 300),
        ]:
            for i, r in enumerate(recs):
                assert len(r) == width, f"{fn} rec {i} width {len(r)} != {width}: {r!r}"
            with open(os.path.join(out_dir, fn), 'w', newline='') as f:
                f.write(''.join(r + '\n' for r in recs))
        # referential integrity check
        for (acct_id, *_rest) in self.tcat:
            assert acct_id in self.accts, f"tcat acct {acct_id} missing from acctdata"
            assert acct_id in self.cards, f"tcat acct {acct_id} missing from cardxref"


def case_arith(ds):
    # multi-category accumulation, exact + truncating interest; last account NOT flushed
    ds.acct('00000000001', 'G1', 19400)                      # 194.00, cyc 0
    ds.acct('00000000002', 'G1', 5000)                       # 50.00
    ds.acct('00000000003', 'G1', 100000, cyc_credit=5000, cyc_debit=2500)  # LAST: cyc 50.00/25.00
    # categories (some out of key order to exercise the sort)
    ds.cat('00000000002', '01', '0001', 777)                 # 7.77
    ds.cat('00000000001', '02', '0001', 33333)               # 333.33 (truncating)
    ds.cat('00000000001', '01', '0001', 100000)              # 1000.00
    ds.cat('00000000001', '01', '0002', 10000)               # 100.00
    ds.cat('00000000003', '01', '0001', 500000)              # 5000.00 (last acct)
    ds.rate('G1', '01', '0001', 1800)                        # 18.00
    ds.rate('G1', '01', '0002', 1800)
    ds.rate('G1', '02', '0001', 1300)                        # 13.00
    ds.rate('DEFAULT', '01', '0001', 100)                    # wrong on purpose: specific must win
    ds.rate('DEFAULT', '01', '0002', 100)
    ds.rate('DEFAULT', '02', '0001', 100)


def case_negative(ds):
    ds.acct('00000000001', 'GN', 100000)                     # 1000.00
    ds.acct('00000000002', 'GN', 1000)                       # LAST dummy
    ds.cat('00000000001', '01', '0001', -100000)             # -1000.00 -> -15.00
    ds.cat('00000000001', '01', '0002', -33333)              # -333.33 -> -3.61 (toward zero)
    ds.cat('00000000001', '01', '0003', -777)                # -7.77   -> -0.14
    ds.cat('00000000002', '02', '0001', 500)                 # rate 0 -> no tx
    ds.rate('GN', '01', '0001', 1800)
    ds.rate('GN', '01', '0002', 1300)
    ds.rate('GN', '01', '0003', 2200)
    ds.rate('GN', '02', '0001', 0)


def case_zero_rate(ds):
    ds.acct('00000000001', 'GZ', 10000)                      # 100.00
    ds.acct('00000000002', 'GZ', 1000)                       # LAST dummy
    ds.cat('00000000001', '01', '0001', 100000)              # rate 18 -> 15.00 (tx)
    ds.cat('00000000001', '02', '0001', 100000)              # rate 0  -> no tx, no accrual
    ds.cat('00000000001', '03', '0001', 100000)              # rate 12 -> 10.00 (tx)
    ds.cat('00000000002', '01', '0001', 0)                   # last (not flushed)
    ds.rate('GZ', '01', '0001', 1800)
    ds.rate('GZ', '02', '0001', 0)
    ds.rate('GZ', '03', '0001', 1200)


def case_default_fallback(ds):
    ds.acct('00000000001', 'HASGRP', 100000)                 # specific group present
    ds.acct('00000000002', 'NOGRP', 100000)                  # group absent -> DEFAULT
    ds.acct('00000000003', 'HASGRP', 1000)                   # LAST dummy
    ds.cat('00000000001', '01', '0001', 100000)              # 1000.00 * 24% -> 20.00
    ds.cat('00000000002', '01', '0001', 100000)              # 1000.00 * 6%  -> 5.00 (DEFAULT)
    ds.cat('00000000003', '09', '0009', 100000)              # last (not flushed)
    ds.rate('HASGRP', '01', '0001', 2400)                    # 24.00
    ds.rate('DEFAULT', '01', '0001', 600)                    # 6.00
    ds.rate('DEFAULT', '09', '0009', 600)


def case_rounding(ds):
    # monthly interest with a 3rd decimal >= 5: DOWN (COBOL) vs HALF_UP must differ
    ds.acct('00000000001', 'GR', 10000)
    ds.acct('00000000002', 'GR', 1000)                       # LAST dummy
    ds.cat('00000000001', '01', '0001', 10040)               # 100.40 * 15% = 1.255  -> DOWN 1.25
    ds.cat('00000000001', '01', '0002', 10050)               # 100.50 * 18% = 1.5075 -> DOWN 1.50
    ds.cat('00000000002', '01', '0001', 0)                   # last
    ds.rate('GR', '01', '0001', 1500)
    ds.rate('GR', '01', '0002', 1800)


def case_boundary(ds):
    # WS-MONTHLY-INT (S9(09)V99) overflow: max balance * max rate / 1200 exceeds
    # 9 integer digits, so COBOL silently drops the high-order digits. The oracle
    # defines the exact truncated value; Java must reproduce it.
    #   999,999,999.99 * 9999.99 / 1200 ~= 8,333,333,249.99  (10 int digits -> overflow)
    ds.acct('00000000001', 'GB', 100000, credit=99999999999, cash=99999999999)
    ds.acct('00000000002', 'GB', 1000)                       # LAST dummy
    ds.cat('00000000001', '01', '0001', 99999999999)         # 999,999,999.99 (max S9(09)V99)
    ds.cat('00000000002', '01', '0001', 0)                   # last
    ds.rate('GB', '01', '0001', 999999)                      # 9999.99 (max S9(04)V99)


CASES = {
    'arith': case_arith,
    'negative': case_negative,
    'zero-rate': case_zero_rate,
    'default-fallback': case_default_fallback,
    'rounding': case_rounding,
    'boundary': case_boundary,
}


def main():
    out_root = sys.argv[1]
    for name, builder in CASES.items():
        ds = Dataset()
        builder(ds)
        ds.write(os.path.join(out_root, name))
        print(f"  {name:18} accts={len(ds.accts)} categories={len(ds.tcat)} disc={len(ds.disc)}")


if __name__ == '__main__':
    main()
