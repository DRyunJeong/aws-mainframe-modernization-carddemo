package com.carddemo.batch.io;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for fixed-width COBOL record I/O. A byte is mapped 1:1 to a char via
 * ISO-8859-1 so that every value (including the 0x70-0x79 negative sign bytes
 * and 0x00 fillers produced by the program) round-trips losslessly.
 */
public final class Cobol {

    /** Byte<->char charset: ISO-8859-1 is a 1:1 map over all 256 byte values. */
    public static final Charset CHARSET = StandardCharsets.ISO_8859_1;

    private Cobol() {}

    /** Read alphanumeric (PIC X) field {@code buf[off..off+len)} as a String. */
    public static String str(byte[] buf, int off, int len) {
        return new String(buf, off, len, CHARSET);
    }

    /**
     * Write a PIC X field: left-justified, blank-padded to {@code len}
     * (MOVE-to-alphanumeric semantics). Longer input is truncated to {@code len}.
     */
    public static void putText(byte[] buf, int off, int len, String s) {
        byte[] src = s.getBytes(CHARSET);
        for (int i = 0; i < len; i++) {
            buf[off + i] = i < src.length ? src[i] : (byte) 0x20;
        }
    }

    /**
     * Write {@code s} starting at {@code off} WITHOUT padding the remainder —
     * models COBOL {@code STRING ... DELIMITED BY SIZE} which only overwrites the
     * bytes it emits and leaves the rest of the receiver untouched.
     */
    public static void putRaw(byte[] buf, int off, String s) {
        byte[] src = s.getBytes(CHARSET);
        System.arraycopy(src, 0, buf, off, src.length);
    }

    /**
     * COBOL numeric edit of a money amount into a 15-char field PIC
     * {@code <sign>ZZZ,ZZZ,ZZZ.ZZ} (9 integer digits with comma grouping and
     * leading-zero suppression, 2 decimals). The leading sign position is '-' for a
     * negative value; for a non-negative value it is {@code positiveSign}
     * (e.g. '+' for a {@code +} picture, ' ' for a {@code -} picture).
     */
    public static String editFloatingSign(java.math.BigDecimal value, char positiveSign) {
        java.math.BigDecimal v = value.setScale(2);
        String digits = String.format("%011d", v.abs().unscaledValue().longValueExact());   // 9 int + 2 dec
        char[] g = (digits.substring(0, 3) + "," + digits.substring(3, 6) + "," + digits.substring(6, 9)).toCharArray();
        int i = 0;
        while (i < g.length && (g[i] == '0' || g[i] == ',')) {           // suppress leading zeros (and their commas)
            g[i++] = ' ';
        }
        char sign = v.signum() < 0 ? '-' : positiveSign;
        return sign + new String(g) + "." + digits.substring(9);          // 1 + 11 + 1 + 2 = 15
    }

    /**
     * GnuCOBOL's {@code DISPLAY} rendering of a signed numeric DISPLAY item: the
     * {@code totalDigits} magnitude digits followed by a trailing sign ('-'
     * negative, '+' otherwise). E.g. S9(09)V99 -> 11 digits, S9(10)V99 -> 12.
     */
    public static String displaySigned(java.math.BigDecimal value, int totalDigits) {
        return String.format("%0" + totalDigits + "d", value.abs().unscaledValue().longValueExact())
                + (value.signum() < 0 ? "-" : "+");
    }

    /**
     * Read a RECORD SEQUENTIAL fixed-width dataset: the raw file split into
     * {@code width}-byte records with no delimiter. Use this (not {@link #readFixed})
     * when records carry binary data (COMP/COMP-3) where a 0x0A byte must not be
     * mistaken for a line terminator.
     */
    public static List<byte[]> readFixedRaw(Path path, int width) throws IOException {
        byte[] all = Files.readAllBytes(path);
        if (all.length % width != 0) {
            throw new IOException(path + ": length " + all.length + " is not a multiple of record width " + width);
        }
        List<byte[]> records = new ArrayList<>();
        for (int off = 0; off < all.length; off += width) {
            records.add(java.util.Arrays.copyOfRange(all, off, off + width));
        }
        return records;
    }

    /**
     * Read a LINE SEQUENTIAL fixed-width dataset (LF-terminated, no CR) into a
     * list of {@code width}-byte records. Empty trailing line is ignored.
     */
    public static List<byte[]> readFixed(Path path, int width) throws IOException {
        byte[] all = Files.readAllBytes(path);
        List<byte[]> records = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= all.length; i++) {
            boolean atEnd = i == all.length;
            if (atEnd || all[i] == '\n') {
                int len = i - start;
                if (len > 0) {
                    if (len != width) {
                        throw new IOException(path + ": record " + (records.size() + 1)
                                + " has width " + len + ", expected " + width);
                    }
                    byte[] rec = new byte[width];
                    System.arraycopy(all, start, rec, 0, width);
                    records.add(rec);
                }
                start = i + 1;
            }
        }
        return records;
    }
}
