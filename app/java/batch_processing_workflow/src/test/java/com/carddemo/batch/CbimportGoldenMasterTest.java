package com.carddemo.batch;

import com.carddemo.batch.cbimport.CbimportMain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/** Golden-master test for CBIMPORT (multi-record export file -> 5 split outputs + errors). */
class CbimportGoldenMasterTest extends AbstractGoldenMasterTest {

    @Override
    String program() {
        return "cbimport";
    }

    @Override
    BatchOutputs run(Path datasetDir, String parmDate) throws IOException {
        return CbimportMain.execute(datasetDir, parmDate);
    }

    /** Mask the 26-byte wall-clock ERR-TIMESTAMP at the start of each 132-byte error record. */
    @Override
    Map<String, int[]> maskSpecs() {
        return Map.of("errout.dat", new int[]{132, 0, 26});
    }
}
