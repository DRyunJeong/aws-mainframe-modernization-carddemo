"""
oracle_lib.py - shared helpers for per-program dataset generation
(sample normalization + synthetic builders), reused across batch programs.

Encoding matches GnuCOBOL native ASCII zoned decimal (see Java ZonedDecimal):
positive last byte '0'..'9', negative last byte 'p'..'y'. The repo's ASCII
sample data carries signs as EBCDIC zone-overpunch which GnuCOBOL misreads during
arithmetic, so signed fields are transcoded to the native convention here.
"""
import os

# EBCDIC zone-overpunch glyph -> GnuCOBOL native ASCII signed last byte
OVERPUNCH_TO_NATIVE = {
    '{': '0', 'A': '1', 'B': '2', 'C': '3', 'D': '4',
    'E': '5', 'F': '6', 'G': '7', 'H': '8', 'I': '9',   # positive 0..9
    '}': 'p', 'J': 'q', 'K': 'r', 'L': 's', 'M': 't',
    'N': 'u', 'O': 'v', 'P': 'w', 'Q': 'x', 'R': 'y',   # negative 0..9
}


def transcode_sign(rec, fields):
    """fields = [(offset, length), ...] of signed PIC S9..V99 fields; remap their last byte."""
    b = list(rec)
    for off, ln in fields:
        i = off + ln - 1
        if b[i] in OVERPUNCH_TO_NATIVE:
            b[i] = OVERPUNCH_TO_NATIVE[b[i]]
    return ''.join(b)


def normalize_file(src_path, out_path, width, signed_fields):
    """Normalize an app/data/ASCII file: strip CR, pad/truncate to width, transcode signs, LF-terminate."""
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    out = []
    with open(src_path, 'r', newline='') as f:
        for raw in f:
            rec = raw.rstrip('\r\n')
            rec = (rec + ' ' * width)[:width]
            rec = transcode_sign(rec, signed_fields)
            out.append(rec)
    with open(out_path, 'w', newline='') as f:
        for rec in out:
            f.write(rec + '\n')
    return len(out)


# ---- native field encoders for synthetic data ----
def signed(cents, total_digits):
    """Native zoned-decimal string of total_digits; last digit carries the sign."""
    neg = cents < 0
    s = str(abs(cents)).zfill(total_digits)[-total_digits:]
    last = s[-1]
    return s[:-1] + (chr(0x70 + int(last)) if neg else last)


def unum(value, digits):
    return str(int(value)).zfill(digits)[-digits:]


def text(s, width):
    return (str(s) + ' ' * width)[:width]


def write_records(out_dir, filename, records, width):
    os.makedirs(out_dir, exist_ok=True)
    for i, r in enumerate(records):
        assert len(r) == width, f"{filename} rec {i}: width {len(r)} != {width}: {r!r}"
    with open(os.path.join(out_dir, filename), 'w', newline='') as f:
        f.write(''.join(r + '\n' for r in records))
