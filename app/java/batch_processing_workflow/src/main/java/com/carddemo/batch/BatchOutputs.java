package com.carddemo.batch;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The byte-image outputs of one batch program run, keyed by the golden filename
 * each maps to (e.g. {@code "transact.dat"}, {@code "acctdump.dat"},
 * {@code "stdout.txt"}). A program declares exactly the outputs it produces; the
 * golden-master test compares each against {@code golden/<prog>/<case>/<filename>}
 * byte-for-byte (with optional, per-output timestamp masking).
 *
 * <p>This generalizes the single-program {@code BatchResult} so that programs with
 * many outputs (TRANSACT + ACCTFILE + TCATBAL dumps + rejects, or just SYSOUT) all
 * fit one shape.
 */
public record BatchOutputs(Map<String, byte[]> files) {

    public BatchOutputs {
        files = new LinkedHashMap<>(files);
    }

    public byte[] get(String filename) {
        return files.get(filename);
    }

    /** Builder helper for the common case of assembling outputs incrementally. */
    public static final class Builder {
        private final Map<String, byte[]> map = new LinkedHashMap<>();

        public Builder add(String filename, byte[] bytes) {
            map.put(filename, bytes);
            return this;
        }

        public BatchOutputs build() {
            return new BatchOutputs(map);
        }
    }
}
