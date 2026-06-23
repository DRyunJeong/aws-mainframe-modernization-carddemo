package com.carddemo.batch.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Shared helper for the CardDemo "read a KSDS sequentially and print each record
 * to SYSOUT" batch programs (CBACT02C, CBACT03C, CBCUS01C). They share the exact
 * skeleton:
 *
 * <pre>
 *   DISPLAY 'START OF EXECUTION OF PROGRAM &lt;name&gt;'
 *   (read by RECORD KEY, ascending) DISPLAY &lt;record&gt;   [once or twice]
 *   DISPLAY 'END OF EXECUTION OF PROGRAM &lt;name&gt;'
 * </pre>
 *
 * INDEXED SEQUENTIAL read returns ascending key order, so records are sorted by
 * the record key. Some programs DISPLAY each record twice (an active DISPLAY in
 * both the GET-NEXT paragraph and the main loop) — captured by {@code timesPerRecord}.
 */
public final class RecordPrinter {

    private RecordPrinter() {}

    public static byte[] dumpToSysout(Path file, int recordWidth, int keyOffset, int keyLength,
                                      String programName, int timesPerRecord) throws IOException {
        List<byte[]> records = Cobol.readFixed(file, recordWidth);
        records.sort(Comparator.comparing(r -> Cobol.str(r, keyOffset, keyLength)));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        line(out, "START OF EXECUTION OF PROGRAM " + programName);
        for (byte[] record : records) {
            for (int i = 0; i < timesPerRecord; i++) {
                out.writeBytes(record);     // DISPLAY of the fixed-length group: exact bytes, no trimming
                out.write('\n');
            }
        }
        line(out, "END OF EXECUTION OF PROGRAM " + programName);
        return out.toByteArray();
    }

    private static void line(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(Cobol.CHARSET));
        out.write('\n');
    }
}
