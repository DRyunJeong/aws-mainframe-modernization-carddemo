package com.carddemo.batch.cbstm03a;

import com.carddemo.batch.BatchOutputs;
import com.carddemo.batch.io.Cobol;
import com.carddemo.batch.io.ZonedDecimal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java migration of CBSTM03A (+ its file-handler subprogram CBSTM03B) — generates per-account
 * statements in two record-sequential outputs: plain text ({@code stmt.txt}, 80-byte records) and
 * HTML ({@code html.txt}, 100-byte records).
 *
 * <p>The COBOL drives I/O through CBSTM03B and uses ALTER/GO TO to sequence the phases; the net
 * behaviour is: read all TRNXFILE records (keyed card+id) into an in-memory table grouped by card,
 * then read XREFFILE sequentially and, for each cross-reference, random-read CUSTFILE and ACCTFILE
 * by id and emit one statement (header + customer/address + basic details + that card's
 * transactions + total). This migration reproduces that net behaviour directly.
 *
 * <p>Faithful-migration notes pinned by the GnuCOBOL oracle (see src/test/cobol/cbstm03a):
 * <ul>
 *   <li>both files are RECORD SEQUENTIAL (fixed records, no line terminators);</li>
 *   <li>amounts use COBOL numeric edits — {@code 9(9).99-} (zero-filled) for the balance and
 *       {@code Z(9).99-} (zero-suppressed) for transaction/total amounts, both with a <b>trailing</b>
 *       sign (space for non-negative, '-' for negative);</li>
 *   <li>name/address HTML lines come from {@code STRING ... DELIMITED BY '  '} (the field up to the
 *       first double space) then a literal two spaces; name parts use {@code DELIMITED BY ' '}
 *       (each up to its first single space);</li>
 *   <li>the mainframe PSA/TCB/TIOT control-block DISPLAY block is non-portable diagnostic output and
 *       is not part of the data product (the oracle stubs it; nothing is emitted to the two files).</li>
 * </ul>
 */
public final class Cbstm03aMain {

    private static final int STMT_W = 80;
    private static final int HTML_W = 100;

    // Fixed HTML lines (verbatim from the COSTM01 / CBSTM03A 88-level VALUEs).
    private static final String H01 = "<!DOCTYPE html>";
    private static final String H02 = "<html lang=\"en\">";
    private static final String H03 = "<head>";
    private static final String H04 = "<meta charset=\"utf-8\">";
    private static final String H05 = "<title>HTML Table Layout</title>";
    private static final String H06 = "</head>";
    private static final String H07 = "<body style=\"margin:0px;\">";
    private static final String H08 = "<table  align=\"center\" frame=\"box\" style=\"width:70%; font:12px Segoe UI,sans-serif;\">";
    private static final String TRS = "<tr>";
    private static final String TRE = "</tr>";
    private static final String TDE = "</td>";
    private static final String H10 = "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#1d1d96b3;\">";
    private static final String H15 = "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#FFAF33;\">";
    private static final String H16 = "<p style=\"font-size:16px\">Bank of XYZ</p>";
    private static final String H17 = "<p>410 Terry Ave N</p>";
    private static final String H18 = "<p>Seattle WA 99999</p>";
    private static final String H22 = "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#f2f2f2;\">";
    private static final String H30 = "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#33FFD1; text-align:center;\">";
    private static final String H31 = "<p style=\"font-size:16px\">Basic Details</p>";
    private static final String H43 = "<p style=\"font-size:16px\">Transaction Summary</p>";
    private static final String H47 = "<td style=\"width:25%; padding:0px 5px; background-color:#33FF5E; text-align:left;\">";
    private static final String H48 = "<p style=\"font-size:16px\">Tran ID</p>";
    private static final String H50 = "<td style=\"width:55%; padding:0px 5px; background-color:#33FF5E; text-align:left;\">";
    private static final String H51 = "<p style=\"font-size:16px\">Tran Details</p>";
    private static final String H53 = "<td style=\"width:20%; padding:0px 5px; background-color:#33FF5E; text-align:right;\">";
    private static final String H54 = "<p style=\"font-size:16px\">Amount</p>";
    private static final String H58 = "<td style=\"width:25%; padding:0px 5px; background-color:#f2f2f2; text-align:left;\">";
    private static final String H61 = "<td style=\"width:55%; padding:0px 5px; background-color:#f2f2f2; text-align:left;\">";
    private static final String H64 = "<td style=\"width:20%; padding:0px 5px; background-color:#f2f2f2; text-align:right;\">";
    private static final String H75 = "<h3>End of Statement</h3>";
    private static final String H78 = "</table>";
    private static final String H79 = "</body>";
    private static final String H80 = "</html>";

    // Basic-details labels (20 chars), shared by the text statement and the HTML lines.
    private static final String LBL_ACCT = "Account ID         :";
    private static final String LBL_BAL = "Current Balance    :";
    private static final String LBL_FICO = "FICO Score         :";

    private final ByteArrayOutputStream stmt = new ByteArrayOutputStream();
    private final ByteArrayOutputStream html = new ByteArrayOutputStream();
    private BigDecimal totalAmt = BigDecimal.ZERO.setScale(2);

    private Cbstm03aMain() {}

    public static BatchOutputs execute(Path dir, String parmDate) throws IOException {
        // TRNXFILE (COSTM01, 350B keyed card+id) read in key order, grouped by card into a table.
        List<byte[]> trnx = Cobol.readFixed(dir.resolve("trnxdata.dat"), 350);
        trnx.sort(Comparator.comparing(r -> Cobol.str(r, 0, 32)));
        Map<String, List<byte[]>> byCard = new LinkedHashMap<>();
        for (byte[] t : trnx) {
            byCard.computeIfAbsent(Cobol.str(t, 0, 16), k -> new ArrayList<>()).add(t);
        }

        List<byte[]> xrefs = Cobol.readFixed(dir.resolve("cardxref.dat"), 50);
        xrefs.sort(Comparator.comparing(r -> Cobol.str(r, 0, 16)));   // XREFFILE read ascending by card
        Map<String, byte[]> custs = keyed(Cobol.readFixed(dir.resolve("custdata.dat"), 500), 0, 9);
        Map<String, byte[]> accts = keyed(Cobol.readFixed(dir.resolve("acctdata.dat"), 300), 0, 11);

        Cbstm03aMain job = new Cbstm03aMain();
        for (byte[] x : xrefs) {
            String card = Cobol.str(x, 0, 16);
            byte[] cust = custs.get(Cobol.str(x, 16, 9));    // CUSTFILE random read by xref cust-id
            byte[] acct = accts.get(Cobol.str(x, 25, 11));   // ACCTFILE random read by xref acct-id
            job.createStatement(cust, acct);
            job.writeTransactions(byCard.getOrDefault(card, List.of()));
        }
        return new BatchOutputs.Builder()
                .add("stmt.txt", job.stmt.toByteArray())
                .add("html.txt", job.html.toByteArray())
                .build();
    }

    /** 5000-CREATE-STATEMENT + 5100/5200: statement header (text) and the header+name+basic HTML. */
    private void createStatement(byte[] cust, byte[] acct) {
        totalAmt = BigDecimal.ZERO.setScale(2);
        String name = firstWord(Cobol.str(cust, 9, 25)) + " " + firstWord(Cobol.str(cust, 34, 25))
                + " " + firstWord(Cobol.str(cust, 59, 25)) + " ";       // ST-NAME (STRING DELIMITED BY ' ')
        String stName = pad(name, 75);
        String add1 = Cobol.str(cust, 84, 50);                          // ST-ADD1 = CUST-ADDR-LINE-1
        String add2 = Cobol.str(cust, 134, 50);                         // ST-ADD2 = CUST-ADDR-LINE-2
        String add3 = pad(firstWord(Cobol.str(cust, 184, 50)) + " " + firstWord(Cobol.str(cust, 234, 2))
                + " " + firstWord(Cobol.str(cust, 236, 3)) + " " + firstWord(Cobol.str(cust, 239, 10)) + " ", 80);
        String acctId = pad(Cobol.str(acct, 0, 11), 20);                // ACCT-ID 9(11) -> X(20)
        String currBal = editAmt(ZonedDecimal.decode(acct, 12, 12, 2), false);   // 9(9).99-
        String fico = pad(Cobol.str(cust, 329, 3), 20);                 // CUST-FICO-CREDIT-SCORE -> X(20)

        sl("*".repeat(31) + "START OF STATEMENT" + "*".repeat(31));     // ST-LINE0

        // 5100-WRITE-HTML-HEADER
        hl(H01); hl(H02); hl(H03); hl(H04); hl(H05); hl(H06); hl(H07); hl(H08);
        hl(TRS); hl(H10);
        hl("<h3>Statement for Account Number: " + acctId + "</h3>");    // HTML-L11
        hl(TDE); hl(TRE);
        hl(TRS); hl(H15); hl(H16); hl(H17); hl(H18); hl(TDE); hl(TRE);
        hl(TRS); hl(H22);

        // 5200-WRITE-HTML-NMADBS
        hl("<p style=\"font-size:16px\">" + upToDouble(pad(stName, 50)) + "  </p>");   // L23-NAME
        hl("<p>" + upToDouble(add1) + "  </p>");
        hl("<p>" + upToDouble(add2) + "  </p>");
        hl("<p>" + upToDouble(add3) + "  </p>");
        hl(TDE); hl(TRE);
        hl(TRS); hl(H30); hl(H31); hl(TDE); hl(TRE);
        hl(TRS); hl(H22);
        hl("<p>" + LBL_ACCT + " " + acctId + "</p>");
        hl("<p>" + LBL_BAL + " " + currBal + "</p>");
        hl("<p>" + LBL_FICO + " " + fico + "</p>");
        hl(TDE); hl(TRE);
        hl(TRS); hl(H30); hl(H43); hl(TDE); hl(TRE);
        hl(TRS); hl(H47); hl(H48); hl(TDE); hl(H50); hl(H51); hl(TDE); hl(H53); hl(H54); hl(TDE); hl(TRE);

        // text statement body (ST-LINE1..13)
        sl(pad(stName, 75) + " ".repeat(5));                            // ST-LINE1 (name)
        sl(pad(add1, 50) + " ".repeat(30));                             // ST-LINE2
        sl(pad(add2, 50) + " ".repeat(30));                             // ST-LINE3
        sl(pad(add3, 80));                                              // ST-LINE4
        sl(dashes());                                                   // ST-LINE5
        sl(" ".repeat(33) + pad("Basic Details", 14) + " ".repeat(33));// ST-LINE6
        sl(dashes());                                                   // ST-LINE5
        sl(LBL_ACCT + acctId + " ".repeat(40));                         // ST-LINE7
        sl(LBL_BAL + currBal + " ".repeat(7) + " ".repeat(40));         // ST-LINE8
        sl(LBL_FICO + fico + " ".repeat(40));                           // ST-LINE9
        sl(dashes());                                                   // ST-LINE10
        sl(" ".repeat(30) + "TRANSACTION SUMMARY " + " ".repeat(30));   // ST-LINE11
        sl(dashes());                                                   // ST-LINE12
        sl(pad("Tran ID", 16) + pad("Tran Details", 51) + "  Tran Amount");  // ST-LINE13
        sl(dashes());                                                   // ST-LINE12
    }

    /** 4000-TRNXFILE-GET + 6000-WRITE-TRANS: one row per transaction, then per-account totals/close. */
    private void writeTransactions(List<byte[]> txns) {
        for (byte[] t : txns) {
            String tranId = Cobol.str(t, 16, 16);                       // TRNX-ID
            String tranDt = pad(Cobol.str(t, 48, 100), 49);            // TRNX-DESC -> ST-TRANDT X(49)
            BigDecimal amt = ZonedDecimal.decode(t, 148, 11, 2);        // TRNX-AMT S9(09)V99
            String tranAmt = editAmt(amt, true);                        // Z(9).99-
            sl(tranId + " " + tranDt + "$" + tranAmt);                  // ST-LINE14

            hl(TRS);
            hl(H58); hl("<p>" + tranId + "</p>"); hl(TDE);
            hl(H61); hl("<p>" + tranDt + "</p>"); hl(TDE);
            hl(H64); hl("<p>" + tranAmt + "</p>"); hl(TDE);
            hl(TRE);
            totalAmt = totalAmt.add(amt);
        }
        sl(dashes());                                                   // ST-LINE12
        sl(pad("Total EXP:", 10) + " ".repeat(56) + "$" + editAmt(totalAmt, true));  // ST-LINE14A
        sl("*".repeat(32) + "END OF STATEMENT" + "*".repeat(32));       // ST-LINE15

        hl(TRS); hl(H10); hl(H75); hl(TDE); hl(TRE); hl(H78); hl(H79); hl(H80);
    }

    // ---- COBOL numeric edit: 9 int + '.' + 2 dec + trailing sign; suppress = Z (blank leading zeros) ----
    private static String editAmt(BigDecimal v, boolean suppress) {
        BigInteger cents = v.abs().movePointRight(2).toBigInteger().mod(BigInteger.TEN.pow(11));
        String d = String.format("%011d", cents);
        char[] intp = d.substring(0, 9).toCharArray();
        if (suppress) {
            for (int i = 0; i < intp.length && intp[i] == '0'; i++) {
                intp[i] = ' ';
            }
        }
        char sign = v.signum() < 0 ? '-' : ' ';
        return new String(intp) + "." + d.substring(9) + sign;
    }

    private static String firstWord(String s) {
        int i = s.indexOf(' ');
        return i < 0 ? s : s.substring(0, i);
    }

    private static String upToDouble(String s) {
        int i = s.indexOf("  ");
        return i < 0 ? s : s.substring(0, i);
    }

    private static String dashes() {
        return "-".repeat(80);
    }

    private static String pad(String s, int n) {
        if (s.length() >= n) {
            return s.substring(0, n);
        }
        return s + " ".repeat(n - s.length());
    }

    private void sl(String line) {
        write(stmt, line, STMT_W);
    }

    private void hl(String line) {
        write(html, line, HTML_W);
    }

    private static void write(ByteArrayOutputStream out, String line, int width) {
        out.writeBytes(pad(line, width).getBytes(Cobol.CHARSET));
    }

    private static Map<String, byte[]> keyed(List<byte[]> recs, int off, int len) {
        Map<String, byte[]> m = new LinkedHashMap<>();
        for (byte[] r : recs) {
            m.put(Cobol.str(r, off, len), r);
        }
        return m;
    }

    public static void main(String[] args) throws IOException {
        BatchOutputs out = execute(Path.of(args[0]), "");
        Files.createDirectories(Path.of(args[1]));
        for (var e : out.files().entrySet()) {
            Files.write(Path.of(args[1]).resolve(e.getKey()), e.getValue());
        }
    }
}
