package com.hamza.account.features.treasury;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.shift.ShiftCashSource;
import com.hamza.account.features.treasury.statement.TreasuryMovementKind;
import com.hamza.account.treasury.TreasuryStatements;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What the wallets and the bank kept, over a period: per treasury, and per kind of movement the
 * fee was charged on.
 * <p>
 * The fees were findable only as one heading among the expenses, with nothing saying which wallet
 * charged them or for what. The question an owner asks - "what is this wallet costing me, and is
 * it the collections or the transfers" - needs the treasury and the kind side by side.
 * <p>
 * <b>It reads the expense rows and nothing else</b>, under the heading the fee is posted to
 * ({@code system_key = 'WALLET_FEE'}), so its total is the figure the expenses reports and the
 * profit and loss carry for that heading - not a second computation of it. A row with no link
 * ({@link Row#kind()} is {@code null}) is a fee written before V67, or an expense somebody entered
 * under that heading by hand; it is listed rather than hidden, or the totals would not agree.
 */
public final class WalletFeeReport {

    /**
     * @param kind what the fee was charged on, or {@code null} for a fee tied to no movement
     */
    public record Row(int treasuryId, String treasuryName, ShiftCashSource kind, long movements, BigDecimal fees) {

        public Row {
            fees = fees == null ? BigDecimal.ZERO : fees;
        }

        /** The kind as the treasury statement names it - one set of words for one set of movements. */
        public String kindLabelKey() {
            return kind == null ? "treasury.fee.report.kind.unlinked"
                    : TreasuryMovementKind.fromCode(kind.code()).labelKey();
        }
    }

    /** Where the rows come from - a seam so the service is tested without a database. */
    public interface Source {
        List<Row> rows(LocalDate from, LocalDate to, Integer treasuryId) throws DaoException;
    }

    private final Source source;

    public WalletFeeReport() {
        this(new JdbcSource());
    }

    WalletFeeReport(Source source) {
        this.source = source;
    }

    /**
     * @param treasuryId {@code null} for every treasury
     */
    public List<Row> between(LocalDate from, LocalDate to, Integer treasuryId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_SHOW);
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        return List.copyOf(source.rows(from, to, treasuryId));
    }

    public static BigDecimal totalFees(List<Row> rows) {
        return rows.stream().map(Row::fees).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static long totalMovements(List<Row> rows) {
        return rows.stream().mapToLong(Row::movements).sum();
    }

    /** The kind a stored code names, or {@code null} for an unlinked fee. */
    static ShiftCashSource kindOf(Integer code) {
        if (code == null) {
            return null;
        }
        for (ShiftCashSource candidate : ShiftCashSource.values()) {
            if (candidate.code() == code) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown fee source type: " + code);
    }

    private static final class JdbcSource extends AbstractDao<Row> implements Source {

        @Override
        public List<Row> rows(LocalDate from, LocalDate to, Integer treasuryId) throws DaoException {
            return withConnection(connection -> {
                try (var statement = connection.prepareStatement(TreasuryStatements.SELECT_WALLET_FEE_REPORT)) {
                    setData(statement, new Object[]{Date.valueOf(from), Date.valueOf(to), treasuryId, treasuryId});
                    List<Row> rows = new ArrayList<>();
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) rows.add(map(rs));
                    }
                    return rows;
                } catch (SQLException e) {
                    throw new DaoException("Could not read the wallet fee report", e);
                }
            });
        }

        @Override
        public Row map(ResultSet rs) throws DaoException {
            try {
                int code = rs.getInt("fee_source_type");
                Integer kind = rs.wasNull() ? null : code;
                return new Row(rs.getInt("treasury_id"), rs.getString("t_name"), kindOf(kind),
                        rs.getLong("movements"), rs.getBigDecimal("fees"));
            } catch (SQLException | IllegalArgumentException e) {
                throw new DaoException("Could not map a wallet fee report row", e);
            }
        }

        @Override public List<Row> loadAll() { throw new UnsupportedOperationException(); }
        @Override public int insert(Row value) { throw new UnsupportedOperationException(); }
        @Override public int update(Row value) { throw new UnsupportedOperationException(); }
        @Override public int deleteById(int id) { throw new UnsupportedOperationException(); }
        @Override public Row getDataById(int id) { throw new UnsupportedOperationException(); }
        @Override public Object[] getData(Row value) { throw new UnsupportedOperationException(); }
    }
}
