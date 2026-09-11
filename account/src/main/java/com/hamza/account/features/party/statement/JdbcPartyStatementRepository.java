package com.hamza.account.features.party.statement;

import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The read-only JDBC side of a party statement.
 * <p>
 * Extends {@link AbstractDao} for its pooled {@code withConnection} and
 * {@code queryForObjects} and nothing else: the six write methods are refused, the way
 * {@code JdbcTreasuryStatementRepository} refuses them. A statement is a question, and
 * this class has no answer to "insert".
 */
public final class JdbcPartyStatementRepository extends AbstractDao<PartyStatementRow>
        implements PartyStatementRepository {

    @Override
    public List<PartyStatementRow> search(PartyStatementFilter filter) throws DaoException {
        List<Object> values = new ArrayList<>();
        values.add(filter.partyId());
        values.add(Date.valueOf(filter.from()));
        values.add(filter.partyId());
        values.add(Date.valueOf(filter.from()));
        values.add(Date.valueOf(filter.to()));
        addRowFilters(values, filter);
        values.add(filter.queryLimit());
        values.add(filter.offset());
        return queryForObjects(PartyStatementQuery.pageSql(filter.partyKind()), this::map,
                values.toArray());
    }

    @Override
    public PartyStatementSummary summarize(PartyStatementFilter filter) throws DaoException {
        List<Object> values = new ArrayList<>();
        values.add(Date.valueOf(filter.from()));
        // The debit total and the credit total each repeat the period and every row filter:
        // both are conditional sums over the one scan, so the condition is written twice.
        for (int half = 0; half < 2; half++) {
            values.add(Date.valueOf(filter.from()));
            values.add(Date.valueOf(filter.to()));
            addRowFilters(values, filter);
        }
        values.add(Date.valueOf(filter.to()));
        values.add(filter.partyId());
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    PartyStatementQuery.summarySql(filter.partyKind()))) {
                setData(statement, values.toArray());
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return PartyStatementSummary.EMPTY;
                    }
                    return new PartyStatementSummary(
                            rs.getBigDecimal("opening_balance"),
                            rs.getBigDecimal("total_debit"),
                            rs.getBigDecimal("total_credit"),
                            rs.getBigDecimal("closing_balance"));
                }
            }
        });
    }

    @Override
    public PartyStatementOptions options() throws DaoException {
        return withConnection(connection -> {
            List<PartyStatementTreasuryOption> treasuries = new ArrayList<>();
            try (var statement = connection.prepareStatement(PartyStatementQuery.TREASURIES_SQL);
                 var rs = statement.executeQuery()) {
                while (rs.next()) {
                    treasuries.add(new PartyStatementTreasuryOption(
                            rs.getInt("id"), rs.getString("t_name"), rs.getBoolean("is_active")));
                }
            }
            List<PartyStatementUserOption> users = new ArrayList<>();
            try (var statement = connection.prepareStatement(PartyStatementQuery.USERS_SQL);
                 var rs = statement.executeQuery()) {
                while (rs.next()) {
                    users.add(new PartyStatementUserOption(rs.getInt("id"), rs.getString("user_name")));
                }
            }
            return new PartyStatementOptions(treasuries, users);
        });
    }

    @Override
    public LocalDate earliestMovement(PartyKind kind, int partyId) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    PartyStatementQuery.earliestMovementSql(kind))) {
                statement.setInt(1, partyId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    Date earliest = rs.getDate("earliest");
                    return earliest == null ? null : earliest.toLocalDate();
                }
            }
        });
    }

    @Override
    public BigDecimal currentBalance(PartyKind kind, int partyId) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    PartyStatementQuery.currentBalanceSql(kind))) {
                statement.setInt(1, partyId);
                try (ResultSet rs = statement.executeQuery()) {
                    BigDecimal balance = rs.next() ? rs.getBigDecimal("balance") : null;
                    return balance == null ? BigDecimal.ZERO : balance;
                }
            }
        });
    }

    /**
     * The fifteen values {@link PartyStatementQuery#rowFilterSql(String)} binds, in its
     * order. Each optional filter is bound twice — once for the {@code ? IS NULL} test and
     * once for the comparison — so that one pinned statement can serve a filter that is set
     * and one that is not.
     */
    private static void addRowFilters(List<Object> values, PartyStatementFilter filter) {
        String kinds = filter.kindCodes();
        values.add(kinds);
        values.add(kinds);
        values.add(filter.treasuryId());
        values.add(filter.treasuryId());
        values.add(filter.userId());
        values.add(filter.userId());
        values.add(filter.minAmount());
        values.add(filter.minAmount());
        values.add(filter.maxAmount());
        values.add(filter.maxAmount());
        String text = filter.text().isEmpty() ? null : filter.text();
        values.add(text);
        values.add(text == null ? null : PartyStatementQuery.pattern(text));
        values.add(text);
        values.add(text);
        values.add(filter.deferredOnly());
    }

    @Override
    public PartyStatementRow map(ResultSet rs) throws DaoException {
        try {
            Timestamp enteredAt = rs.getTimestamp("created_at");
            return new PartyStatementRow(
                    rs.getLong("account_num"),
                    rs.getDate("account_date").toLocalDate(),
                    enteredAt == null ? null : enteredAt.toLocalDateTime(),
                    PartyMovementKind.fromCode(rs.getInt("information")),
                    rs.getInt("type") != 2,
                    reference(rs),
                    rs.getBigDecimal("purchase"),
                    rs.getBigDecimal("discount"),
                    rs.getBigDecimal("paid"),
                    rs.getBigDecimal("running_balance"),
                    rs.getInt("treasury_id"),
                    rs.getString("treasury_name"),
                    rs.getInt("user_id"),
                    rs.getString("user_name"),
                    rs.getString("notes"));
        } catch (SQLException | IllegalArgumentException e) {
            throw new DaoException("Could not map party statement row", e);
        }
    }

    /**
     * The number shown to the user.
     * <p>
     * A document's own number is {@code account_num} — the view puts the invoice number
     * there — while a payment's reference to the invoice it settles is {@code numberInv}.
     * A payment that names an invoice shows that invoice; everything else shows its own
     * key. {@code numberInv} is always zero today, since nothing writes it: phase ب of
     * {@code docs/party-plan.md} is what gives it a value.
     */
    private static long reference(ResultSet rs) throws SQLException {
        long linked = rs.getLong("numberInv");
        return linked > 0 ? linked : rs.getLong("account_num");
    }

    @Override
    public List<PartyStatementRow> loadAll() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int insert(PartyStatementRow value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(PartyStatementRow value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int deleteById(int id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PartyStatementRow getDataById(int id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object[] getData(PartyStatementRow value) {
        throw new UnsupportedOperationException();
    }
}
