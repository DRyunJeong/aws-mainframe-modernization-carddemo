package com.carddemo.batch.io;

import com.carddemo.batch.domain.TranCatBalRecord;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;

/**
 * In-memory analog of the TCATBALF VSAM KSDS opened I-O by CBTRN02C: random read
 * by the composite key (account-id + type-cd + cat-cd), with create-if-absent
 * ({@code 2700-A}) or accumulate-and-rewrite ({@code 2700-B}). Backed by a
 * {@link TreeMap} so the dump is in ascending key order.
 *
 * <p>The balance field is the only thing mutated; for an existing record the rest
 * of the raw image is preserved, and a created record uses INITIALIZE semantics
 * (numeric 0, the 22-byte trailing FILLER = spaces) exactly as the COBOL does.
 */
public final class TcatbalStore {

    private static final int KEY_LEN = 17;        // acct(11) + type(2) + cat(4)
    private static final int BAL_OFF = 17;
    private static final int BAL_LEN = 11;        // S9(09)V99
    private static final int BAL_SCALE = 2;
    private static final int BAL_INT_DIGITS = 9;
    private static final int FILLER_OFF = 28;

    private final TreeMap<String, byte[]> imagesByKey = new TreeMap<>();
    /**
     * Mirrors the program's WORKING-STORAGE TRAN-CAT-BAL-RECORD across transactions.
     * It matters because {@code 2700-A-CREATE} uses {@code INITIALIZE}, which does NOT
     * touch the 22-byte FILLER — so a created record inherits the FILLER left in this
     * buffer by the previous READ/create (GnuCOBOL initialises it to LOW-VALUE).
     */
    private final byte[] recordBuffer = new byte[TranCatBalRecord.WIDTH];

    public static TcatbalStore load(Path tcatbal) throws IOException {
        TcatbalStore store = new TcatbalStore();
        for (byte[] rec : Cobol.readFixed(tcatbal, TranCatBalRecord.WIDTH)) {
            store.imagesByKey.put(Cobol.str(rec, 0, KEY_LEN), rec);
        }
        return store;
    }

    /** Whether a record exists for the (acct,type,cat) key (drives the "Creating." message). */
    public boolean contains(String acctId, String typeCd, String catCd) {
        return imagesByKey.containsKey(acctId + typeCd + catCd);
    }

    /**
     * {@code 2700-UPDATE-TCATBAL}: add {@code amount} to the balance for the
     * (acct,type,cat) key, creating the record if it does not yet exist.
     */
    public void accumulate(String acctId, String typeCd, String catCd, BigDecimal amount) {
        String key = acctId + typeCd + catCd;
        byte[] existing = imagesByKey.get(key);
        if (existing != null) {
            // 2700-B: READ INTO TRAN-CAT-BAL-RECORD (success), ADD amount to balance, REWRITE.
            System.arraycopy(existing, 0, recordBuffer, 0, TranCatBalRecord.WIDTH);
            BigDecimal newBal = ZonedDecimal.truncateToField(
                    ZonedDecimal.decode(recordBuffer, BAL_OFF, BAL_LEN, BAL_SCALE).add(amount), BAL_INT_DIGITS, BAL_SCALE);
            ZonedDecimal.encodeInto(recordBuffer, BAL_OFF, newBal, BAL_LEN, BAL_SCALE);
        } else {
            // 2700-A: READ failed (INVALID KEY) -> buffer unchanged; INITIALIZE leaves FILLER as-is;
            // MOVE key fields; balance = 0 + amount.
            Cobol.putText(recordBuffer, 0, 11, acctId);
            Cobol.putText(recordBuffer, 11, 2, typeCd);
            Cobol.putText(recordBuffer, 13, 4, catCd);
            ZonedDecimal.encodeInto(recordBuffer, BAL_OFF,
                    ZonedDecimal.truncateToField(amount, BAL_INT_DIGITS, BAL_SCALE), BAL_LEN, BAL_SCALE);
        }
        imagesByKey.put(key, recordBuffer.clone());
    }

    /** Flat dump of every record in ascending key order (50 bytes each, no separators). */
    public byte[] dump() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] image : imagesByKey.values()) {
            out.writeBytes(image);
        }
        return out.toByteArray();
    }
}
