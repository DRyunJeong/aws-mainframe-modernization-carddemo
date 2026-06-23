package com.carddemo.batch;

import com.carddemo.batch.cbexport.CbexportMain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/** Golden-master test for CBEXPORT (5 masters -> multi-record export file). */
class CbexportGoldenMasterTest extends AbstractGoldenMasterTest {

    @Override
    String program() {
        return "cbexport";
    }

    @Override
    BatchOutputs run(Path datasetDir, String parmDate) throws IOException {
        return CbexportMain.execute(datasetDir, parmDate);
    }

    /** Mask the 26-byte wall-clock EXPORT-TIMESTAMP at offset 1 of each 500-byte record. */
    @Override
    Map<String, int[]> maskSpecs() {
        return Map.of("expfile.dat", new int[]{500, 1, 27});
    }
}
