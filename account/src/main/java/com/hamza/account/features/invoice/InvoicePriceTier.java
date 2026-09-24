package com.hamza.account.features.invoice;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.pricing.PriceTiers;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The price tier a sales document is priced at, and the list price behind each of its lines
 * (V84, docs/pricing-and-offers-plan.md ق-س٢ and ق-س٤) - the two things the save decides about them.
 * <p>
 * <b>The tier is copied onto the header</b>, as a document copies its exchange rate: moving the
 * customer to another tier tomorrow does not reprice yesterday's invoice. It defaults to the
 * customer's tier, and pricing at any other needs {@code sales.price.tier.change} - except the tier the
 * document was saved at, which an edit keeps without asking, even once the customer has moved or the
 * tier has been switched off. A document saved before V84 has none, and keeps none until somebody
 * chooses one.
 * <p>
 * <b>A price below its line's list needs {@code sales.price.below.list}</b> - except on a line of a
 * saved document whose price has not moved: correcting a note on an invoice somebody else discounted
 * must not ask the corrector for a permission they did not use.
 * <p>
 * Both are asked before the number is allocated - the counter does not roll back - and the tier is
 * written after the header, in the save's transaction, the way {@code ReturnSourceWriter} writes a
 * return's source: the header's statements are pinned and bound from a model that knows no tier.
 */
public final class InvoicePriceTier {

    /** What the save reads and writes about tiers - a seam, so the rules are tested without a database. */
    public interface Repository {

        /** The tier a saved document was priced at, or null - before V84, or none chosen. */
        Integer storedTier(DocumentType type, int number) throws DaoException;

        /** The customer's own tier ({@code custom.price_id}), or 0 when the customer is unknown. */
        int partyTier(int partyId) throws DaoException;

        boolean isActive(int tierId) throws DaoException;

        /** A tier's name, for a refusal that has to say which. */
        String tierName(int tierId) throws DaoException;

        /** A saved sales document's line prices, by line id. */
        Map<Integer, Double> storedLinePrices(int number) throws DaoException;

        void writeTier(DocumentType type, int number, Integer tierId) throws DaoException;
    }

    private final Repository repository;

    public InvoicePriceTier(Repository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public static InvoicePriceTier jdbc() {
        return new InvoicePriceTier(new Jdbc());
    }

    /** Every document family without a tier, and every test that does not care about one. */
    public static InvoicePriceTier none() {
        return new InvoicePriceTier(new Repository() {
            @Override
            public Integer storedTier(DocumentType type, int number) {
                return null;
            }

            @Override
            public int partyTier(int partyId) {
                return PriceTiers.FIRST;
            }

            @Override
            public boolean isActive(int tierId) {
                return true;
            }

            @Override
            public String tierName(int tierId) {
                return String.valueOf(tierId);
            }

            @Override
            public Map<Integer, Double> storedLinePrices(int number) {
                return Map.of();
            }

            @Override
            public void writeTier(DocumentType type, int number, Integer tierId) {
            }
        });
    }

    /** The tier a saved document was priced at, for a screen reopening it - null before V84. */
    public Integer storedTier(DocumentType type, int number) throws DaoException {
        return carriesTier(type) && number > 0 ? repository.storedTier(type, number) : null;
    }

    /** Whether a document family is priced from a tier at all: the customer's two. */
    public static boolean carriesTier(DocumentType type) {
        return type == DocumentType.SALES || type == DocumentType.SALES_RETURN;
    }

    /**
     * The tier to store on the document, refusing one this user may not price at.
     *
     * @param requested the tier the screen priced at, or null for a caller that does not know tiers -
     *                  which keeps whatever the document already had
     * @return the tier to write, or null for a family without tiers or a document that has none
     */
    public Integer decide(DocumentType type, int partyId, int existingNumber, Integer requested) throws DaoException {
        if (!carriesTier(type)) {
            return null;
        }
        Integer stored = existingNumber > 0 ? repository.storedTier(type, existingNumber) : null;
        if (requested == null) {
            return stored;
        }
        if (!PriceTiers.exists(requested)) {
            throw new BusinessRuleException(text("invoice.tier.error.unknown", requested));
        }
        if (requested.equals(stored)) {
            return requested;
        }
        if (!repository.isActive(requested)) {
            throw new BusinessRuleException(text("invoice.tier.error.inactive", repository.tierName(requested)));
        }
        if (requested != customersTier(partyId)) {
            AuthorizationGuard.require(AppPermissions.SALES_PRICE_TIER_CHANGE);
        }
        return requested;
    }

    /** The customer's tier as an invoice for them defaults to it: tier 1 when theirs is switched off. */
    int customersTier(int partyId) throws DaoException {
        int own = repository.partyTier(partyId);
        if (!PriceTiers.exists(own) || !repository.isActive(own)) {
            return PriceTiers.FIRST;
        }
        return own;
    }

    /**
     * Refuses a sale with a line priced below its list for a user who may not sell below it.
     *
     * @param rows the lines as they will be stored - in the base, for a document typed in a currency
     */
    public void requireListPrices(DocumentType type, int existingNumber,
                                  List<? extends BasePurchasesAndSales> rows) throws DaoException {
        if (type != DocumentType.SALES || rows == null || rows.isEmpty()) {
            return;
        }
        Map<Integer, Double> stored = null;
        for (BasePurchasesAndSales row : rows) {
            if (row == null || row.getListPrice() == null
                    || row.getPrice() >= row.getListPrice().doubleValue() - InvoiceLineService.LIST_TOLERANCE) {
                continue;
            }
            if (existingNumber > 0 && row.getId() > 0) {
                if (stored == null) {
                    stored = repository.storedLinePrices(existingNumber);
                }
                Double before = stored.get(row.getId());
                if (before != null && Math.abs(before - row.getPrice()) < InvoiceLineService.LIST_TOLERANCE) {
                    continue;
                }
            }
            AuthorizationGuard.require(AppPermissions.SALES_PRICE_BELOW_LIST);
            return;
        }
    }

    public void write(DocumentType type, int number, Integer tierId) throws DaoException {
        if (carriesTier(type)) {
            repository.writeTier(type, number, tierId);
        }
    }

    private static String text(String key, Object... args) {
        return LanguageManager.getInstance().getString(key, args);
    }

    /** The statements, beside the rules that use them. */
    static final class Jdbc implements Repository {

        static String storedTierSql(DocumentType type) {
            DocumentTableSpec spec = DocumentTableSpec.of(type);
            return "SELECT price_tier_id FROM " + spec.table() + " WHERE " + spec.key() + " = ?";
        }

        static final String PARTY_TIER_SQL = "SELECT price_id FROM custom WHERE id = ?";
        static final String ACTIVE_SQL = "SELECT is_active FROM type_price WHERE id = ?";
        static final String NAME_SQL = "SELECT name FROM type_price WHERE id = ?";
        static final String LINE_PRICES_SQL = "SELECT id, price FROM sales WHERE invoice_number = ?";

        static String writeTierSql(DocumentType type) {
            DocumentTableSpec spec = DocumentTableSpec.of(type);
            return "UPDATE " + spec.table() + " SET price_tier_id = ? WHERE " + spec.key() + " = ?";
        }

        @Override
        public Integer storedTier(DocumentType type, int number) throws DaoException {
            return read(storedTierSql(type), number, rs -> {
                if (!rs.next()) {
                    return null;
                }
                int tier = rs.getInt(1);
                return rs.wasNull() ? null : tier;
            });
        }

        @Override
        public int partyTier(int partyId) throws DaoException {
            return read(PARTY_TIER_SQL, partyId, rs -> rs.next() ? rs.getInt(1) : 0);
        }

        @Override
        public boolean isActive(int tierId) throws DaoException {
            return read(ACTIVE_SQL, tierId, rs -> rs.next() && rs.getInt(1) == 1);
        }

        @Override
        public String tierName(int tierId) throws DaoException {
            return read(NAME_SQL, tierId, rs -> rs.next() ? rs.getString(1) : String.valueOf(tierId));
        }

        @Override
        public Map<Integer, Double> storedLinePrices(int number) throws DaoException {
            return read(LINE_PRICES_SQL, number, rs -> {
                Map<Integer, Double> prices = new HashMap<>();
                while (rs.next()) {
                    prices.put(rs.getInt(1), rs.getDouble(2));
                }
                return prices;
            });
        }

        @Override
        public void writeTier(DocumentType type, int number, Integer tierId) throws DaoException {
            int affected = withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(writeTierSql(type))) {
                    if (tierId == null) {
                        statement.setNull(1, Types.INTEGER);
                    } else {
                        statement.setInt(1, tierId);
                    }
                    statement.setInt(2, number);
                    return statement.executeUpdate();
                }
            });
            if (affected != 1) {
                throw new DaoException("Could not record the document's price tier");
            }
        }

        private static <T> T read(String sql, int parameter, RowsWork<T> work) throws DaoException {
            return withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setInt(1, parameter);
                    try (ResultSet rs = statement.executeQuery()) {
                        return work.run(rs);
                    }
                }
            });
        }

        private static <T> T withConnection(SqlWork<T> work) throws DaoException {
            Connection connection = null;
            try {
                connection = ConnectionManager.acquire();
                return work.run(connection);
            } catch (SQLException e) {
                throw new DaoException("Could not read or write a document's price tier", e);
            } finally {
                ConnectionManager.release(connection);
            }
        }

        @FunctionalInterface
        private interface SqlWork<T> {
            T run(Connection connection) throws SQLException;
        }

        @FunctionalInterface
        private interface RowsWork<T> {
            T run(ResultSet rs) throws SQLException;
        }
    }
}
