package com.carddemo.batch;

import com.carddemo.batch.cbact01c.Cbact01cMain;

import java.io.IOException;
import java.nio.file.Path;

/** Golden-master test for CBACT01C (account read -> outfile/arryfile/vbrcfile + stdout). */
class Cbact01cGoldenMasterTest extends AbstractGoldenMasterTest {

    @Override
    String program() {
        return "cbact01c";
    }

    @Override
    BatchOutputs run(Path datasetDir, String parmDate) throws IOException {
        return Cbact01cMain.execute(datasetDir, parmDate);
    }
}
