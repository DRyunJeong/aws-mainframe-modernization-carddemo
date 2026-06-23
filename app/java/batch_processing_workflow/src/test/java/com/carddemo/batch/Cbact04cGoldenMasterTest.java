package com.carddemo.batch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/** Golden-master test for the CBACT04C (interest calculator) migration. */
class Cbact04cGoldenMasterTest extends AbstractGoldenMasterTest {

    @Override
    String program() {
        return "cbact04c";
    }

    @Override
    BatchOutputs run(Path datasetDir, String parmDate) throws IOException {
        return Cbact04cMain.execute(datasetDir, parmDate);
    }

    @Override
    Map<String, int[]> maskSpecs() {
        // TRANSACT: mask TRAN-ORIG-TS+TRAN-PROC-TS [278,330) in each 350-byte record.
        return Map.of("transact.dat", new int[]{350, 278, 330});
    }
}
