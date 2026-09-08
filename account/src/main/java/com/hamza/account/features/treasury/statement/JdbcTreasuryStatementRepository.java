package com.hamza.account.features.treasury.statement;

import com.hamza.account.treasury.TreasuryStatements;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Read-only JDBC query side of the unified treasury statement. */
public final class JdbcTreasuryStatementRepository extends AbstractDao<TreasuryStatementRow>
        implements TreasuryStatementRepository {

    @Override
    public List<TreasuryStatementRow> search(TreasuryStatementFilter filter) throws DaoException {
        Integer kind = filter.kind() == null ? null : filter.kind().code();
        return queryForObjects(TreasuryStatements.SELECT_STATEMENT_PAGE, this::map,
                filter.from(), filter.from(), filter.to(), filter.treasuryId(), filter.treasuryId(),
                kind, kind, filter.userId(), filter.userId(), filter.queryLimit(), filter.offset());
    }

    @Override
    public TreasuryStatementSummary summarize(TreasuryStatementFilter filter) throws DaoException {
        Integer kind = filter.kind() == null ? null : filter.kind().code();
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(TreasuryStatements.SELECT_STATEMENT_SUMMARY)) {
                Object[] values = {
                        filter.from(),
                        filter.from(), filter.to(), kind, kind, filter.userId(), filter.userId(),
                        filter.from(), filter.to(), kind, kind, filter.userId(), filter.userId(),
                        filter.to(), filter.to(), filter.treasuryId(), filter.treasuryId()
                };
                setData(statement, values);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) return new TreasuryStatementSummary(null, null, null, null);
                    return new TreasuryStatementSummary(
                            rs.getBigDecimal("opening_balance"), rs.getBigDecimal("total_income"),
                            rs.getBigDecimal("total_output"), rs.getBigDecimal("closing_balance"));
                }
            }
        });
    }

    @Override
    public TreasuryStatementOptions options() throws DaoException {
        return withConnection(connection -> {
            List<TreasuryOption> treasuries = new ArrayList<>();
            try (var statement = connection.prepareStatement(TreasuryStatements.SELECT_STATEMENT_TREASURIES);
                 var rs = statement.executeQuery()) {
                while (rs.next()) {
                    treasuries.add(new TreasuryOption(rs.getInt("id"), rs.getString("t_name"),
                            rs.getBoolean("is_active")));
                }
            }
            List<TreasuryUserOption> users = new ArrayList<>();
            try (var statement = connection.prepareStatement(TreasuryStatements.SELECT_STATEMENT_USERS);
                 var rs = statement.executeQuery()) {
                while (rs.next()) users.add(new TreasuryUserOption(rs.getInt("id"), rs.getString("user_name")));
            }
            return new TreasuryStatementOptions(treasuries, users);
        });
    }

    @Override
    public TreasuryStatementRow map(ResultSet rs) throws DaoException {
        try {
            var timestamp = rs.getTimestamp("date_insert");
            return new TreasuryStatementRow(
                    rs.getInt("id_no"), rs.getDate("date_val").toLocalDate(),
                    timestamp == null ? null : timestamp.toLocalDateTime(),
                    TreasuryMovementKind.fromCode(rs.getInt("source_type")),
                    rs.getString("information"), rs.getInt("treasury_id"),
                    rs.getString("treasury_name"), rs.getBigDecimal("income"),
                    rs.getBigDecimal("output"), rs.getBigDecimal("running_balance"),
                    rs.getInt("user_id"), rs.getString("user_name"));
        } catch (SQLException | IllegalArgumentException e) {
            throw new DaoException("Could not map treasury statement row", e);
        }
    }

    @Override public List<TreasuryStatementRow> loadAll() { throw new UnsupportedOperationException(); }
    @Override public int insert(TreasuryStatementRow value) { throw new UnsupportedOperationException(); }
    @Override public int update(TreasuryStatementRow value) { throw new UnsupportedOperationException(); }
    @Override public int deleteById(int id) { throw new UnsupportedOperationException(); }
    @Override public TreasuryStatementRow getDataById(int id) { throw new UnsupportedOperationException(); }
    @Override public Object[] getData(TreasuryStatementRow value) { throw new UnsupportedOperationException(); }
}
