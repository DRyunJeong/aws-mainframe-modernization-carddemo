package com.carddemo.batch;

/**
 * Raised where the original program performs {@code CALL 'CEE3ABD'} to ABEND —
 * i.e. an unrecoverable I/O condition (account/xref/disclosure record missing
 * after the DEFAULT fallback, or a bad file status). The JVM has no ABEND, so
 * the job is failed via an uncaught exception, which a scheduler maps to a
 * non-zero return code.
 */
public class AbendException extends RuntimeException {
    public AbendException(String message) {
        super(message);
    }
}
