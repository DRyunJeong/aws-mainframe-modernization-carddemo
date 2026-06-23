package com.carddemo.batch;

import com.carddemo.batch.cbcus01c.Cbcus01cMain;

import java.io.IOException;
import java.nio.file.Path;

/** Golden-master test for CBCUS01C (print customer file) — output is SYSOUT (stdout.txt). */
class Cbcus01cGoldenMasterTest extends AbstractGoldenMasterTest {

    @Override
    String program() {
        return "cbcus01c";
    }

    @Override
    BatchOutputs run(Path datasetDir, String parmDate) throws IOException {
        return Cbcus01cMain.execute(datasetDir, parmDate);
    }
}
