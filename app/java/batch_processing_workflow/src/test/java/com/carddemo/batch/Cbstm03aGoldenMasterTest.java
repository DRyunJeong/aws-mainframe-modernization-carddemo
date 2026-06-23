package com.carddemo.batch;

import com.carddemo.batch.cbstm03a.Cbstm03aMain;

import java.io.IOException;
import java.nio.file.Path;

/** Golden-master test for CBSTM03A/B (account statements: text + HTML). */
class Cbstm03aGoldenMasterTest extends AbstractGoldenMasterTest {

    @Override
    String program() {
        return "cbstm03a";
    }

    @Override
    BatchOutputs run(Path datasetDir, String parmDate) throws IOException {
        return Cbstm03aMain.execute(datasetDir, parmDate);
    }
}
