package com.carddemo.batch.cbact03c;

import com.carddemo.batch.BatchOutputs;
import com.carddemo.batch.domain.CardXrefRecord;
import com.carddemo.batch.io.RecordPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Java migration of CBACT03C — "read and print account cross reference data file".
 * Reads XREFFILE (KSDS) by record key (card number) and prints each record to
 * SYSOUT. Faithful-migration note (oracle-verified): each record is DISPLAYed
 * <b>twice</b> (active DISPLAY in both 1000-XREFFILE-GET-NEXT and the main loop).
 * Output is SYSOUT, so the golden master is the captured stdout.
 */
public final class Cbact03cMain {

    private Cbact03cMain() {}

    public static BatchOutputs execute(Path datasetDir, String parmDate) throws IOException {
        byte[] sysout = RecordPrinter.dumpToSysout(
                datasetDir.resolve("cardxref.dat"), CardXrefRecord.WIDTH, 0, 16, "CBACT03C", 2);
        return new BatchOutputs.Builder().add("stdout.txt", sysout).build();
    }

    public static void main(String[] args) throws IOException {
        BatchOutputs result = execute(Path.of(args[0]), "");
        Files.createDirectories(Path.of(args[1]));
        Files.write(Path.of(args[1]).resolve("stdout.txt"), result.get("stdout.txt"));
    }
}
