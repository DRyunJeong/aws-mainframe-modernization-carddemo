package com.carddemo.batch;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Reusable dual-harness golden-master test base. A subclass names a program and
 * how to run it; this base runs every dataset and asserts each declared output is
 * <b>byte-for-byte identical</b> to the captured GnuCOBOL oracle output
 * (src/test/cobol/run_oracle.sh). Per MIGRATION_STRATEGY.md the GnuCOBOL execution
 * is the oracle, never a code interpretation. The only normalization is masking
 * non-deterministic timestamp fields, applied identically on both sides.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractGoldenMasterTest {

    /** Program key = dataset/golden subdirectory name (e.g. "cbact04c"). */
    abstract String program();

    /** Run the Java migration and return its outputs keyed by golden filename. */
    abstract BatchOutputs run(Path datasetDir, String parmDate) throws IOException;

    /**
     * Optional timestamp masking per output filename: {@code {recWidth, start, end}}.
     * The golden is captured already masked, so we mask the Java side the same way.
     */
    Map<String, int[]> maskSpecs() {
        return Map.of();
    }

    Stream<String> datasets() throws IOException {
        return OracleSupport.datasetNames(program()).stream();
    }

    @ParameterizedTest(name = "[{0}] byte-for-byte vs GnuCOBOL oracle")
    @MethodSource("datasets")
    void matchesOracle(String name) throws IOException {
        Path goldenDir = OracleSupport.goldenDir(program(), name);
        Assumptions.assumeTrue(Files.isDirectory(goldenDir),
                "golden not generated for " + program() + "/" + name + " (run src/test/cobol/run_oracle.sh " + program() + ")");

        BatchOutputs outputs = run(OracleSupport.datasetDir(program(), name), OracleSupport.parmDate(program(), name));

        for (Map.Entry<String, byte[]> entry : outputs.files().entrySet()) {
            String filename = entry.getKey();
            Path goldenFile = goldenDir.resolve(filename);
            Assumptions.assumeTrue(Files.exists(goldenFile), "golden file missing: " + program() + "/" + name + "/" + filename);

            byte[] actual = entry.getValue();
            int[] spec = maskSpecs().get(filename);
            int recWidth = 0;
            if (spec != null) {
                recWidth = spec[0];
                actual = OracleSupport.maskRecordField(actual, spec[0], spec[1], spec[2]);
            }
            assertBytesEqual(Files.readAllBytes(goldenFile), actual, recWidth, program() + "/" + name + " :: " + filename);
        }
    }

    /** Byte-equality with a precise first-difference report (record-aware if recWidth>0). */
    private static void assertBytesEqual(byte[] expected, byte[] actual, int recWidth, String label) {
        if (expected.length != actual.length) {
            fail(String.format("%s: length differs — expected %d bytes, actual %d bytes", label, expected.length, actual.length));
        }
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                if (recWidth > 0) {
                    int rec = i / recWidth, col = i % recWidth, from = rec * recWidth;
                    fail(String.format("%s: first diff at record %d, column %d (byte %d)%n  expected: %s%n  actual:   %s",
                            label, rec, col, i, show(expected, from, recWidth), show(actual, from, recWidth)));
                } else {
                    int from = Math.max(0, i - 20);
                    fail(String.format("%s: first diff at byte %d%n  expected: …%s…%n  actual:   …%s…",
                            label, i, show(expected, from, 48), show(actual, from, 48)));
                }
            }
        }
    }

    private static String show(byte[] b, int from, int len) {
        int end = Math.min(from + len, b.length);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < end; i++) {
            int c = b[i] & 0xFF;
            sb.append(c >= 0x20 && c < 0x7F ? (char) c : '.');
        }
        return sb.toString();
    }
}
