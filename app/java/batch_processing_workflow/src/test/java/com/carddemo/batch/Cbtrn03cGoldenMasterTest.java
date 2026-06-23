package com.carddemo.batch;

import com.carddemo.batch.cbtrn03c.Cbtrn03cMain;

import java.io.IOException;
import java.nio.file.Path;

/** Golden-master test for CBTRN03C (transaction detail report) — report.dat + stdout.txt. */
class Cbtrn03cGoldenMasterTest extends AbstractGoldenMasterTest {

    @Override
    String program() {
        return "cbtrn03c";
    }

    @Override
    BatchOutputs run(Path datasetDir, String parmDate) throws IOException {
        return Cbtrn03cMain.execute(datasetDir, parmDate);
    }
}
