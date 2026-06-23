package com.carddemo.batch;

import com.carddemo.batch.cbact03c.Cbact03cMain;

import java.io.IOException;
import java.nio.file.Path;

/** Golden-master test for CBACT03C (print card xref) — output is SYSOUT (stdout.txt). */
class Cbact03cGoldenMasterTest extends AbstractGoldenMasterTest {

    @Override
    String program() {
        return "cbact03c";
    }

    @Override
    BatchOutputs run(Path datasetDir, String parmDate) throws IOException {
        return Cbact03cMain.execute(datasetDir, parmDate);
    }
}
