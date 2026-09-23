package com.hamza.account.features.treasury;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Keeps a treasury in a foreign currency to the movements that can carry one (docs/currency-plan.md §11
 * ق-ب٦).
 * <p>
 * A deposit, a withdrawal and a transfer record what moved in the treasury's own currency beside its
 * base value (V81). An invoice, a collection, a payment and an expense do not yet: their rows have one
 * amount, in the base, and written into a dollar drawer it would claim pounds had gone into it - its
 * dollar balance would not move while its book value did. So each of those writers asks here before it
 * writes, and the pickers leave such a treasury out ({@code TreasuryScope}). The refusal names the
 * treasury and says what it does accept. Phases C and D lift it one writer at a time.
 */
public final class TreasuryCurrencyGuard {

    /** The name of a treasury in a foreign currency, or {@code null} for one in the base. */
    @FunctionalInterface
    public interface Lookup {
        String foreignTreasuryName(int treasuryId) throws DaoException;
    }

    private final Lookup lookup;

    public TreasuryCurrencyGuard(Lookup lookup) {
        this.lookup = lookup;
    }

    public static TreasuryCurrencyGuard jdbc() {
        return new TreasuryCurrencyGuard(new JdbcLookup()::foreignTreasuryName);
    }

    /** Refuses a treasury in a foreign currency for a movement that carries no foreign amount yet. */
    public void requireBaseCurrency(int treasuryId) throws DaoException {
        requireBaseCurrency(treasuryId, "treasury.currency.error.base.only");
    }

    /** The same refusal in the caller's words - {@code key}'s sentence takes the treasury's name. */
    public void requireBaseCurrency(int treasuryId, String key) throws DaoException {
        String foreign = lookup.foreignTreasuryName(treasuryId);
        if (foreign != null) {
            throw new BusinessRuleException(LanguageManager.getInstance().getString(key, foreign));
        }
    }

    /** The sentence, for a caller that already holds the treasury and throws its own exception type. */
    public static String refusal(String treasuryName) {
        return LanguageManager.getInstance().getString("treasury.currency.error.base.only",
                treasuryName == null ? "" : treasuryName);
    }

    /** One read by primary key; nothing for a treasury in the base. */
    static final String FOREIGN_SQL = """
            SELECT t.t_name
            FROM treasury t
            WHERE t.id = ?
              AND t.currency_id IS NOT NULL""";

    private static final class JdbcLookup extends AbstractDao<Object> {
        String foreignTreasuryName(int treasuryId) throws DaoException {
            return withConnection(connection -> {
                try (var statement = connection.prepareStatement(FOREIGN_SQL)) {
                    statement.setInt(1, treasuryId);
                    try (ResultSet rs = statement.executeQuery()) {
                        return rs.next() ? rs.getString(1) : null;
                    }
                } catch (SQLException e) {
                    throw new DaoException("Could not read a treasury's currency", e);
                }
            });
        }
    }
}
