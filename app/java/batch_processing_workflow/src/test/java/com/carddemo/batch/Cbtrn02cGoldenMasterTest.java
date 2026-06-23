package com.carddemo.batch;

import com.carddemo.batch.cbtrn02c.Cbtrn02cMain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Golden-master test for CBTRN02C (post daily transactions). Compares all five
 * outputs (TRANSACT/TCATBAL/ACCTFILE dumps, DALYREJS, SYSOUT) byte-for-byte.
 */
class Cbtrn02cGoldenMasterTest extends AbstractGoldenMasterTest {

    @Override
    String program() {
        return "cbtrn02c";
    }

    @Override
    BatchOutputs run(Path datasetDir, String parmDate) throws IOException {
        return Cbtrn02cMain.execute(datasetDir, parmDate);
    }

    @Override
    Map<String, int[]> maskSpecs() {
        // posted TRANSACT: mask TRAN-PROC-TS [304,330) (CURRENT-DATE); ORIG-TS is from input.
        return Map.of("transact.dat", new int[]{350, 304, 330});
    }
}
