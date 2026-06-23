package com.carddemo.batch.io;

import com.carddemo.batch.AbendException;
import com.carddemo.batch.domain.CardXrefRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory analog of the XREFFILE VSAM KSDS read by its ALTERNATE key
 * (account-id). A VSAM alternate-key read without DUPLICATES returns the record
 * with the lowest primary key (card number); we mirror that by keeping the
 * lowest card number per account.
 */
public final class XrefStore {

    private final Map<String, String> cardByAcct = new HashMap<>();

    public static XrefStore load(Path cardXref) throws IOException {
        XrefStore store = new XrefStore();
        List<byte[]> records = Cobol.readFixed(cardXref, CardXrefRecord.WIDTH);
        for (byte[] rec : records) {
            CardXrefRecord x = CardXrefRecord.parse(rec);
            store.cardByAcct.merge(x.acctId(), x.cardNum(),
                    (existing, candidate) -> candidate.compareTo(existing) < 0 ? candidate : existing);
        }
        return store;
    }

    /** {@code 1110-GET-XREF-DATA}: read by account-id; a missing entry ABENDs. */
    public String cardNumberForAccount(String acctId) {
        String card = cardByAcct.get(acctId);
        if (card == null) {
            throw new AbendException("XREF (CARD) NOT FOUND FOR ACCOUNT: " + acctId);
        }
        return card;
    }
}
