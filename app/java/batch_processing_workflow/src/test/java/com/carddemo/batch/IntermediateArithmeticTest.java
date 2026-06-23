package com.carddemo.batch;

import com.carddemo.batch.io.ZonedDecimal;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Explicit intermediate-value comparison for CBACT04C (MIGRATION_STRATEGY §2:
 * "compare not only final outputs but the intermediate arithmetic"). Beyond the
 * byte-level golden master, this decodes the actual decimal amounts and asserts
 * them value-by-value against the GnuCOBOL oracle: every per-category monthly
 * interest (each TRAN-AMT = one WS-MONTHLY-INT) and every post-run account balance.
 */
class IntermediateArithmeticTest {

    private static final String PROG = "cbact04c";

    static List<String> datasets() throws IOException {
        return OracleSupport.datasetNames(PROG);
    }

    @ParameterizedTest(name = "[{0}] intermediate amounts vs oracle")
    @MethodSource("datasets")
    void intermediateAmountsMatchOracle(String name) throws IOException {
        Path goldenTx = OracleSupport.goldenDir(PROG, name).resolve("transact.dat");
        Path goldenAcct = OracleSupport.goldenDir(PROG, name).resolve("acctdump.dat");
        Assumptions.assumeTrue(Files.exists(goldenTx) && Files.exists(goldenAcct), "golden missing for " + name);

        BatchOutputs result = Cbact04cMain.execute(OracleSupport.datasetDir(PROG, name), OracleSupport.parmDate(PROG, name));

        // Per-category monthly interest (TRAN-AMT, S9(09)V99 at offset 132, len 11).
        List<BigDecimal> oracleInterest = decodeAmounts(Files.readAllBytes(goldenTx), 350, 132, 11);
        List<BigDecimal> javaInterest = decodeAmounts(result.get("transact.dat"), 350, 132, 11);
        assertEquals(oracleInterest, javaInterest, name + ": per-category monthly interest");

        // Per-account final balance (ACCT-CURR-BAL, S9(10)V99 at offset 12, len 12).
        List<BigDecimal> oracleBalances = decodeAmounts(Files.readAllBytes(goldenAcct), 300, 12, 12);
        List<BigDecimal> javaBalances = decodeAmounts(result.get("acctdump.dat"), 300, 12, 12);
        assertEquals(oracleBalances, javaBalances, name + ": post-run account balances");
    }

    private static List<BigDecimal> decodeAmounts(byte[] data, int recWidth, int fieldOff, int fieldLen) {
        List<BigDecimal> values = new ArrayList<>();
        for (int rec = 0; rec + recWidth <= data.length; rec += recWidth) {
            values.add(ZonedDecimal.decode(data, rec + fieldOff, fieldLen, 2));
        }
        return values;
    }
}
