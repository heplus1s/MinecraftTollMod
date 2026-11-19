package dev.jdguo.bank.persistence;

import java.math.BigDecimal;
import java.util.UUID;

/** Test double models the distinct exact balances in the live BankBooks. */
public final class BankRuntime {
    public static Object state;
    public static Books books() { return (Books) state; }
    public record Definition(String name) {}
    public record Books(UUID coin, UUID payer) {
        public Definition currency(String key) {
            return "note".equals(key) ? new Definition("央行券") : null;
        }
        public Definition personalNoteById(UUID key) {
            return coin.equals(key) ? new Definition("星币") : null;
        }
        public BigDecimal balanceExact(UUID who, String key) {
            return payer.equals(who) && "note".equals(key) ? new BigDecimal("10.0123") : BigDecimal.ZERO;
        }
        public BigDecimal personalHoldingExact(UUID key, UUID who) {
            return coin.equals(key) && payer.equals(who) ? new BigDecimal("40.0049") : BigDecimal.ZERO;
        }
    }
}
