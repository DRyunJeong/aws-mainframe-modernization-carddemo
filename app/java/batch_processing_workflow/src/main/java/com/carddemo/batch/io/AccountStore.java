package com.carddemo.batch.io;

import com.carddemo.batch.AbendException;
import com.carddemo.batch.domain.AccountRecord;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;

/**
 * In-memory analog of the ACCTFILE VSAM KSDS (opened I-O): random read by
 * account-id and in-place REWRITE. Backed by a {@link TreeMap} so a full dump is
 * emitted in ascending key order, exactly as a sequential read of the KSDS (and
 * the GnuCOBOL oracle's dump) would produce.
 */
public final class AccountStore {

    private final TreeMap<String, byte[]> imagesByAcct = new TreeMap<>();

    public static AccountStore load(Path acctData) throws IOException {
        AccountStore store = new AccountStore();
        List<byte[]> records = Cobol.readFixed(acctData, AccountRecord.WIDTH);
        for (byte[] rec : records) {
            store.imagesByAcct.put(Cobol.str(rec, 0, 11), rec);
        }
        return store;
    }

    /** Non-throwing existence check (for programs where a missing account is a handled condition, not an ABEND). */
    public boolean contains(String acctId) {
        return imagesByAcct.containsKey(acctId);
    }

    /** {@code 1100-GET-ACCT-DATA}: random read; a missing account ABENDs. */
    public AccountRecord read(String acctId) {
        byte[] image = imagesByAcct.get(acctId);
        if (image == null) {
            throw new AbendException("ACCOUNT NOT FOUND: " + acctId);
        }
        return new AccountRecord(image);
    }

    /** {@code 1050-UPDATE-ACCOUNT}: post accumulated interest and REWRITE the record. */
    public void postInterest(String acctId, BigDecimal totalInterest) {
        AccountRecord current = read(acctId);
        imagesByAcct.put(acctId, current.withPostedInterest(totalInterest));
    }

    /** {@code 2800-UPDATE-ACCOUNT-REC}: apply a transaction amount to balance + cycle totals and REWRITE. */
    public void applyTransaction(String acctId, BigDecimal amount) {
        AccountRecord current = read(acctId);
        imagesByAcct.put(acctId, current.withPostedTransaction(amount));
    }

    /** Flat dump of every account image in ascending key order (300 bytes each, no separators). */
    public byte[] dump() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] image : imagesByAcct.values()) {
            out.writeBytes(image);
        }
        return out.toByteArray();
    }
}
