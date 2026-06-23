package com.carddemo.batch;

import com.carddemo.batch.cbtrn01c.Cbtrn01cMain;

import java.io.IOException;
import java.nio.file.Path;

/** Golden-master test for CBTRN01C (verify daily transactions) — output is SYSOUT (stdout.txt). */
class Cbtrn01cGoldenMasterTest extends AbstractGoldenMasterTest {

    @Override
    String program() {
        return "cbtrn01c";
    }

    @Override
    BatchOutputs run(Path datasetDir, String parmDate) throws IOException {
        return Cbtrn01cMain.execute(datasetDir, parmDate);
    }
}
