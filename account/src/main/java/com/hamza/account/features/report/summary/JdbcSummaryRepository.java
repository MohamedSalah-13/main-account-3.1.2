package com.hamza.account.features.report.summary;

import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.balances.PartyBalanceFilter;
import com.hamza.account.features.party.balances.PartyBalancePage;
import com.hamza.account.features.party.balances.PartyBalanceService;
import com.hamza.account.features.report.monthly.DayFigures;
import com.hamza.account.features.report.monthly.JdbcMonthlyTotalsRepository;
import com.hamza.account.features.report.monthly.MonthlySide;
import com.hamza.account.features.treasury.statement.TreasuryMovementKind;
import com.hamza.account.model.dao.TopSellingItemDao;
import com.hamza.account.model.dao.TreasuryCurrentBalanceDao;
import com.hamza.account.model.domain.TopSellingItem;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * The JDBC side of the summary. Nothing here defines a figure of its own: the days are the monthly totals'
 * statement, the cash is {@code treasury_balance}, the debts the customer balances screen's service, the
 * low stock the notification's view, the best sellers {@code ItemNetLines} and the treasuries
 * {@code treasury_current_balance}.
 */
public final class JdbcSummaryRepository implements SummaryRepository {

    /**
     * What the treasuries took in and paid out: every movement of {@code treasury_balance} but an opening
     * balance and a transfer between two treasuries, which move no money into or out of the business.
     * Two parameters: the period.
     */
    static final String CASH_SQL = """
            SELECT COALESCE(SUM(income), 0) AS cash_in, COALESCE(SUM(output), 0) AS cash_out
            FROM treasury_balance
            WHERE date_val BETWEEN ? AND ?
              AND source_type NOT IN (%d, %d, %d)""".formatted(TreasuryMovementKind.OPENING.code(),
            TreasuryMovementKind.TRANSFER_IN.code(), TreasuryMovementKind.TRANSFER_OUT.code());

    /** The lowest items of the low stock notification's own view, with how many there are in all. */
    static final String LOW_STOCK_SQL = """
            SELECT v.id, v.nameItem, v.mini_quantity, v.balance, u.unit_name, COUNT(*) OVER () AS total
            FROM mini_quantity_view v
                     JOIN items i ON i.id = v.id
                     LEFT JOIN units u ON u.unit_id = i.unit_id
            ORDER BY v.balance, v.nameItem
            LIMIT ?""";

    private final JdbcMonthlyTotalsRepository documents = new JdbcMonthlyTotalsRepository();
    private final CashDao cashDao = new CashDao();
    private final LowStockDao lowStockDao = new LowStockDao();
    private final PartyBalanceService balances = new PartyBalanceService();

    @Override
    public List<DayFigures> days(MonthlySide side, LocalDate from, LocalDate to) throws DaoException {
        return documents.daysBetween(side, from, to);
    }

    @Override
    public CashFlow cash(LocalDate from, LocalDate to) throws DaoException {
        CashFlow flow = cashDao.queryForObject(CASH_SQL, cashDao::map, Date.valueOf(from), Date.valueOf(to));
        return flow == null ? CashFlow.NONE : flow;
    }

    @Override
    public Receivables receivables(int top) throws DaoException {
        PartyBalancePage page = balances.search(PartyBalanceFilter.debtorsToday(PartyKind.CUSTOMER)
                .firstPageWithSize(top));
        return new Receivables(page.summary().parties(), page.summary().totalOwed(), page.rows().stream()
                .map(row -> new Receivables.Debtor(row.name(), row.balance())).toList());
    }

    @Override
    public LowStock lowStock(int limit) throws DaoException {
        List<LowStockRow> rows = lowStockDao.queryForObjects(LOW_STOCK_SQL, lowStockDao::map, limit);
        return new LowStock(rows.isEmpty() ? 0 : rows.getFirst().total(),
                rows.stream().map(LowStockRow::item).toList());
    }

    @Override
    public List<TopSellingItem> topItems(LocalDate from, LocalDate to) throws DaoException {
        return new TopSellingItemDao().getTopSellingItems(from, to);
    }

    @Override
    public List<TreasuryBalanceSummary> treasuries() throws DaoException {
        return new TreasuryCurrentBalanceDao().loadAll();
    }

    private static final class CashDao extends AbstractDao<CashFlow> {
        @Override
        public CashFlow map(ResultSet rs) throws DaoException {
            try {
                return new CashFlow(rs.getBigDecimal("cash_in"), rs.getBigDecimal("cash_out"));
            } catch (SQLException e) {
                throw new DaoException(e);
            }
        }
    }

    private record LowStockRow(LowStockItem item, int total) {
    }

    private static final class LowStockDao extends AbstractDao<LowStockRow> {
        @Override
        public LowStockRow map(ResultSet rs) throws DaoException {
            try {
                BigDecimal minimum = rs.getBigDecimal("mini_quantity");
                return new LowStockRow(new LowStockItem(rs.getInt("id"), rs.getString("nameItem"),
                        rs.getString("unit_name"), minimum, rs.getBigDecimal("balance")), rs.getInt("total"));
            } catch (SQLException e) {
                throw new DaoException(e);
            }
        }
    }
}
