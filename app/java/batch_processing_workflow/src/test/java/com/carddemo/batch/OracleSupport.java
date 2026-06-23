package com.carddemo.batch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Per-program locations and helpers for the GnuCOBOL dual-harness tests. Datasets
 * live under {@code src/test/resources/datasets/<prog>/{sample,synthetic/<case>}}
 * and the captured oracle goldens under {@code src/test/resources/golden/<prog>/<case>/}.
 */
final class OracleSupport {

    static final Path DATASETS = Path.of("src/test/resources/datasets");
    static final Path GOLDEN = Path.of("src/test/resources/golden");
    static final String DEFAULT_PARM = "2022071800";

    private OracleSupport() {}

    /** Names of all datasets that have a generated golden for {@code prog} (sample + synthetic/*). */
    static List<String> datasetNames(String prog) throws IOException {
        Path g = GOLDEN.resolve(prog);
        if (!Files.isDirectory(g)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(g)) {
            return dirs.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    static Path datasetDir(String prog, String name) {
        Path base = DATASETS.resolve(prog);
        return name.equals("sample") ? base.resolve("sample") : base.resolve("synthetic").resolve(name);
    }

    static Path goldenDir(String prog, String name) {
        return GOLDEN.resolve(prog).resolve(name);
    }

    static String parmDate(String prog, String name) throws IOException {
        Path parm = datasetDir(prog, name).resolve("parm.txt");
        return Files.exists(parm) ? Files.readString(parm).trim() : DEFAULT_PARM;
    }

    /** Blank out {@code [start,end)} within every {@code recWidth}-byte record (timestamp masking). */
    static byte[] maskRecordField(byte[] data, int recWidth, int start, int end) {
        byte[] out = data.clone();
        for (int rec = 0; rec + recWidth <= out.length; rec += recWidth) {
            Arrays.fill(out, rec + start, rec + end, (byte) '#');
        }
        return out;
    }
}
