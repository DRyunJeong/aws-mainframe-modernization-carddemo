package com.carddemo.batch.io;

import com.carddemo.batch.AbendException;
import com.carddemo.batch.domain.DisclosureGroupRecord;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory analog of the DISCGRP VSAM KSDS (interest-rate master), random read
 * by (group, type, category) with CBACT04C's DEFAULT-group fallback:
 * {@code 1200-GET-INTEREST-RATE} re-reads with group id {@code 'DEFAULT'} when
 * the specific key is absent (file status '23'); if neither exists the program
 * ABENDs.
 */
public final class DiscgrpStore {

    /** {@code MOVE 'DEFAULT' TO FD-DIS-ACCT-GROUP-ID} (PIC X(10)) -> "DEFAULT   ". */
    private static final String DEFAULT_GROUP = padGroup("DEFAULT");

    private final Map<String, BigDecimal> rateByKey = new HashMap<>();

    public static DiscgrpStore load(Path discgrp) throws IOException {
        DiscgrpStore store = new DiscgrpStore();
        List<byte[]> records = Cobol.readFixed(discgrp, DisclosureGroupRecord.WIDTH);
        for (byte[] rec : records) {
            DisclosureGroupRecord g = DisclosureGroupRecord.parse(rec);
            store.rateByKey.putIfAbsent(g.key(), g.intRate());
        }
        return store;
    }

    /**
     * Look up the annual interest rate (percent). Tries the account's group, then
     * the DEFAULT group with the same type/category; ABENDs if neither is found.
     */
    public BigDecimal findRate(String groupId, String typeCd, String catCd) {
        BigDecimal specific = rateByKey.get(groupId + typeCd + catCd);
        if (specific != null) {
            return specific;
        }
        BigDecimal fallback = rateByKey.get(DEFAULT_GROUP + typeCd + catCd);
        if (fallback != null) {
            return fallback;
        }
        throw new AbendException("DISCLOSURE GROUP NOT FOUND (incl DEFAULT): "
                + groupId.trim() + "/" + typeCd + "/" + catCd);
    }

    private static String padGroup(String g) {
        return (g + "          ").substring(0, 10);
    }
}
