package com.carddemo.batch;

import com.carddemo.batch.domain.AccountRecord;
import com.carddemo.batch.domain.TranCatBalRecord;
import com.carddemo.batch.io.AccountStore;
import com.carddemo.batch.io.Cobol;
import com.carddemo.batch.io.DiscgrpStore;
import com.carddemo.batch.io.XrefStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Java migration of CBACT04C — the CardDemo interest-calculation batch.
 *
 * <p>Reads the four normalized fixed-width inputs from a dataset directory
 * ({@code tcatbal.dat}, {@code acctdata.dat}, {@code cardxref.dat},
 * {@code discgrp.dat}), runs {@link InterestCalculator}, and writes
 * {@code transact.dat} and {@code acctdump.dat} — byte-for-byte equivalent to the
 * GnuCOBOL oracle (timestamp fields excepted; see the golden-master test).
 *
 * <p>{@code parmDate} corresponds to the JCL {@code PARM} (e.g. {@code 2022071800})
 * and forms the prefix of every generated TRAN-ID.
 */
public final class Cbact04cMain {

    private Cbact04cMain() {}

    /** Run the batch against a dataset directory and return the raw output images. */
    public static BatchOutputs execute(Path datasetDir, String parmDate) throws IOException {
        List<TranCatBalRecord> tranCatBalances =
                Cobol.readFixed(datasetDir.resolve("tcatbal.dat"), TranCatBalRecord.WIDTH)
                        .stream().map(TranCatBalRecord::parse).toList();
        AccountStore accounts = AccountStore.load(datasetDir.resolve("acctdata.dat"));
        XrefStore xref = XrefStore.load(datasetDir.resolve("cardxref.dat"));
        DiscgrpStore disclosure = DiscgrpStore.load(datasetDir.resolve("discgrp.dat"));

        return new InterestCalculator().run(tranCatBalances, accounts, xref, disclosure, parmDate);
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("usage: Cbact04cMain <datasetDir> <parmDate> <outDir>");
            System.exit(2);
        }
        Path datasetDir = Path.of(args[0]);
        String parmDate = args[1];
        Path outDir = Path.of(args[2]);

        BatchOutputs result = execute(datasetDir, parmDate);

        Files.createDirectories(outDir);
        for (var entry : result.files().entrySet()) {
            Files.write(outDir.resolve(entry.getKey()), entry.getValue());
        }
        System.out.printf("CBACT04C(Java): %d transactions, %d account records%n",
                result.get("transact.dat").length / com.carddemo.batch.domain.TransactionRecord.WIDTH,
                result.get("acctdump.dat").length / AccountRecord.WIDTH);
    }
}
