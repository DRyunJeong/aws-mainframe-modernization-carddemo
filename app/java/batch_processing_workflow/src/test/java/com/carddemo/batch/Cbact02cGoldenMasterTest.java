package com.carddemo.batch;

import com.carddemo.batch.cbact02c.Cbact02cMain;

import java.io.IOException;
import java.nio.file.Path;

/** Golden-master test for CBACT02C (print card file) — output is SYSOUT (stdout.txt). */
class Cbact02cGoldenMasterTest extends AbstractGoldenMasterTest {

    @Override
    String program() {
        return "cbact02c";
    }

    @Override
    BatchOutputs run(Path datasetDir, String parmDate) throws IOException {
        return Cbact02cMain.execute(datasetDir, parmDate);
    }
}
