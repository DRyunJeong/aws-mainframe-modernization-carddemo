package com.carddemo.batch;

import com.carddemo.batch.domain.AccountRecord;
import com.carddemo.batch.domain.TranCatBalRecord;
import com.carddemo.batch.domain.TransactionRecord;
import com.carddemo.batch.io.AccountStore;
import com.carddemo.batch.io.DiscgrpStore;
import com.carddemo.batch.io.XrefStore;
import com.carddemo.batch.io.ZonedDecimal;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The interest calculator (CBACT04C main loop) as a control-break over the
 * transaction-category-balance file, ordered by account.
 *
 * <p>Faithful-migration notes (verified against the GnuCOBOL oracle):
 * <ul>
 *   <li>The driving records are processed in ascending KSDS key order
 *       (account + type + category), as an INDEXED SEQUENTIAL read would.</li>
 *   <li>On an account boundary the <i>previous</i> account is posted first, then
 *       the accumulator resets and the new account/card are read.</li>
 *   <li><b>The last account is intentionally NOT posted.</b> CBACT04C's main-loop
 *       {@code ELSE} branch (post-last-account on EOF) is unreachable because the
 *       {@code PERFORM UNTIL} exits at the top once EOF is set. Replicating this
 *       latent behaviour is required for byte-for-byte parity (the existing
 *       docs/cbl/CBACT04C.md describes the intended-but-dead behaviour).</li>
 *   <li>Interest is only computed/written when the rate is non-zero
 *       ({@code IF DIS-INT-RATE NOT = 0}); the TRAN-ID suffix advances per written
 *       transaction across the whole run.</li>
 * </ul>
 */
public final class InterestCalculator {

    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2);
    private static final int TOTAL_INT_DIGITS = 9;   // WS-TOTAL-INT PIC S9(09)V99
    private static final int TOTAL_SCALE = 2;

    public BatchOutputs run(List<TranCatBalRecord> tranCatBalances, AccountStore accounts,
                            XrefStore xref, DiscgrpStore disclosure, String parmDate) {

        List<TranCatBalRecord> sorted = new ArrayList<>(tranCatBalances);
        sorted.sort(Comparator.comparing(TranCatBalRecord::key));

        ByteArrayOutputStream transactions = new ByteArrayOutputStream();
        String currentAcctId = null;
        BigDecimal totalInterest = ZERO_AMOUNT;
        AccountRecord currentAccount = null;
        String currentCard = null;
        int tranIdSuffix = 0;

        for (TranCatBalRecord row : sorted) {
            if (currentAcctId == null || !row.acctId().equals(currentAcctId)) {
                if (currentAcctId != null) {
                    accounts.postInterest(currentAcctId, totalInterest);   // 1050-UPDATE-ACCOUNT (previous acct)
                }
                totalInterest = ZERO_AMOUNT;
                currentAcctId = row.acctId();
                currentAccount = accounts.read(row.acctId());              // 1100-GET-ACCT-DATA
                currentCard = xref.cardNumberForAccount(row.acctId());     // 1110-GET-XREF-DATA
            }

            BigDecimal rate = disclosure.findRate(currentAccount.groupId(), row.typeCd(), row.catCd());
            if (rate.signum() != 0) {                                      // IF DIS-INT-RATE NOT = 0
                BigDecimal monthly = Interest.monthly(row.balance(), rate);
                totalInterest = ZonedDecimal.truncateToField(totalInterest.add(monthly), TOTAL_INT_DIGITS, TOTAL_SCALE);
                tranIdSuffix++;
                transactions.writeBytes(
                        TransactionRecord.build(parmDate, tranIdSuffix, currentAccount, currentCard, monthly));
            }
        }
        // No post-loop flush: see class note — the last account is never updated.

        return new BatchOutputs.Builder()
                .add("transact.dat", transactions.toByteArray())
                .add("acctdump.dat", accounts.dump())
                .build();
    }
}
