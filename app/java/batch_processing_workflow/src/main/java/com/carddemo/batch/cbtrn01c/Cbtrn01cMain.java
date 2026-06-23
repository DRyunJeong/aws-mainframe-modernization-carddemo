package com.carddemo.batch.cbtrn01c;

import com.carddemo.batch.BatchOutputs;
import com.carddemo.batch.domain.AccountRecord;
import com.carddemo.batch.domain.CardXrefRecord;
import com.carddemo.batch.domain.DailyTranRecord;
import com.carddemo.batch.io.Cobol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java migration of CBTRN01C — verifies each daily transaction by looking up the
 * card cross-reference (by card number) and the account, printing the outcome to
 * SYSOUT. It writes no files; the golden master is the captured stdout.
 *
 * <p>Faithful-migration notes (oracle-verified):
 * <ul>
 *   <li>DALYTRAN is SEQUENTIAL → processed in <b>file order</b> (not sorted).</li>
 *   <li><b>The lookup for the last record runs twice.</b> In the COBOL main loop the
 *       XREF/account lookup (L170-184) sits <i>outside</i> the inner {@code IF
 *       END-OF-FILE='N'} guard, so the iteration in which the read hits EOF still
 *       re-runs the lookup on the previous record's leftover {@code DALYTRAN-CARD-NUM}
 *       (the record DISPLAY at L168 is guarded and is skipped). Reproducing this is
 *       required for byte-for-byte parity.</li>
 *   <li>A missing account prints two messages (3000-READ-ACCOUNT + main loop).</li>
 *   <li>No CURRENT-DATE use → fully deterministic.</li>
 * </ul>
 */
public final class Cbtrn01cMain {

    private Cbtrn01cMain() {}

    public static BatchOutputs execute(Path datasetDir, String parmDate) throws IOException {
        Map<String, CardXrefRecord> xrefByCard = new HashMap<>();
        for (byte[] r : Cobol.readFixed(datasetDir.resolve("cardxref.dat"), CardXrefRecord.WIDTH)) {
            CardXrefRecord x = CardXrefRecord.parse(r);
            xrefByCard.putIfAbsent(x.cardNum(), x);
        }
        Set<String> accountIds = new HashSet<>();
        for (byte[] r : Cobol.readFixed(datasetDir.resolve("acctdata.dat"), AccountRecord.WIDTH)) {
            accountIds.add(Cobol.str(r, 0, 11));
        }
        List<byte[]> dailyTrans = Cobol.readFixed(datasetDir.resolve("dailytran.dat"), DailyTranRecord.WIDTH);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        line(out, "START OF EXECUTION OF PROGRAM CBTRN01C");

        // Mirror the COBOL PERFORM UNTIL loop exactly: the read sets EOF, the record
        // DISPLAY is guarded, but the lookup runs every iteration (including the EOF
        // iteration, on the previous record's leftover data).
        byte[] current = new byte[DailyTranRecord.WIDTH];
        Arrays.fill(current, (byte) ' ');
        int idx = 0;
        boolean eof = false;
        while (!eof) {
            if (idx < dailyTrans.size()) {
                current = dailyTrans.get(idx++);
            } else {
                eof = true;
            }
            if (!eof) {
                out.writeBytes(current);   // DISPLAY DALYTRAN-RECORD (L168, guarded)
                out.write('\n');
            }
            verify(current, xrefByCard, accountIds, out);   // L170-184 (unguarded)
        }

        line(out, "END OF EXECUTION OF PROGRAM CBTRN01C");
        return new BatchOutputs.Builder().add("stdout.txt", out.toByteArray()).build();
    }

    private static void verify(byte[] record, Map<String, CardXrefRecord> xrefByCard,
                               Set<String> accountIds, ByteArrayOutputStream out) {
        DailyTranRecord tran = DailyTranRecord.parse(record);
        CardXrefRecord xref = xrefByCard.get(tran.cardNum());
        if (xref != null) {
            line(out, "SUCCESSFUL READ OF XREF");
            line(out, "CARD NUMBER: " + xref.cardNum());
            line(out, "ACCOUNT ID : " + xref.acctId());
            line(out, "CUSTOMER ID: " + xref.custId());
            if (accountIds.contains(xref.acctId())) {
                line(out, "SUCCESSFUL READ OF ACCOUNT FILE");
            } else {
                line(out, "INVALID ACCOUNT NUMBER FOUND");
                line(out, "ACCOUNT " + xref.acctId() + " NOT FOUND");
            }
        } else {
            line(out, "INVALID CARD NUMBER FOR XREF");
            line(out, "CARD NUMBER " + tran.cardNum()
                    + " COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-" + tran.id());
        }
    }

    private static void line(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(Cobol.CHARSET));
        out.write('\n');
    }

    public static void main(String[] args) throws IOException {
        BatchOutputs result = execute(Path.of(args[0]), "");
        Files.createDirectories(Path.of(args[1]));
        Files.write(Path.of(args[1]).resolve("stdout.txt"), result.get("stdout.txt"));
    }
}
