package com.carddemo.batch.cbact02c;

import com.carddemo.batch.BatchOutputs;
import com.carddemo.batch.domain.CardRecord;
import com.carddemo.batch.io.RecordPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Java migration of CBACT02C — "read and print card data file". Reads CARDFILE
 * (KSDS) by record key (card number) and prints each record to SYSOUT
 * <b>once</b> (the DISPLAY inside 1000-CARDFILE-GET-NEXT is commented out, so —
 * unlike CBACT03C/CBCUS01C — there is no duplication). Golden master = stdout.
 */
public final class Cbact02cMain {

    private Cbact02cMain() {}

    public static BatchOutputs execute(Path datasetDir, String parmDate) throws IOException {
        byte[] sysout = RecordPrinter.dumpToSysout(
                datasetDir.resolve("carddata.dat"), CardRecord.WIDTH, 0, 16, "CBACT02C", 1);
        return new BatchOutputs.Builder().add("stdout.txt", sysout).build();
    }

    public static void main(String[] args) throws IOException {
        BatchOutputs result = execute(Path.of(args[0]), "");
        Files.createDirectories(Path.of(args[1]));
        Files.write(Path.of(args[1]).resolve("stdout.txt"), result.get("stdout.txt"));
    }
}
