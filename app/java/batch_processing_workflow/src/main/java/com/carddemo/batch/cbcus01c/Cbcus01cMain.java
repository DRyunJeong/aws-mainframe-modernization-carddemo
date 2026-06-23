package com.carddemo.batch.cbcus01c;

import com.carddemo.batch.BatchOutputs;
import com.carddemo.batch.domain.CustomerRecord;
import com.carddemo.batch.io.RecordPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Java migration of CBCUS01C — "read and print customer data file". Reads CUSTFILE
 * (KSDS) by record key (customer id) and prints each record to SYSOUT
 * <b>twice</b> (active DISPLAY in both 1000-CUSTFILE-GET-NEXT and the main loop).
 * Golden master = stdout.
 */
public final class Cbcus01cMain {

    private Cbcus01cMain() {}

    public static BatchOutputs execute(Path datasetDir, String parmDate) throws IOException {
        byte[] sysout = RecordPrinter.dumpToSysout(
                datasetDir.resolve("custdata.dat"), CustomerRecord.WIDTH, 0, 9, "CBCUS01C", 2);
        return new BatchOutputs.Builder().add("stdout.txt", sysout).build();
    }

    public static void main(String[] args) throws IOException {
        BatchOutputs result = execute(Path.of(args[0]), "");
        Files.createDirectories(Path.of(args[1]));
        Files.write(Path.of(args[1]).resolve("stdout.txt"), result.get("stdout.txt"));
    }
}
