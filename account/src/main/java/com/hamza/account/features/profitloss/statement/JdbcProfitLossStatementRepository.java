package com.hamza.account.features.profitloss.statement;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Reads {@link ProfitLossStatementQuery}'s statements. */
public final class JdbcProfitLossStatementRepository extends AbstractDao<Object>
        implements ProfitLossStatementRepository {

    @Override
    public SalesBreakdown breakdown(ProfitLossPeriod period) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(ProfitLossStatementQuery.BREAKDOWN_SQL)) {
                bindPeriod(statement, period, 2);
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) {
                        return SalesBreakdown.ZERO;
                    }
                    return new SalesBreakdown(row.getBigDecimal("gross_sales"), row.getBigDecimal("invoice_discounts"),
                            row.getBigDecimal("returns_net"), row.getBigDecimal("cost_of_sold"),
                            row.getBigDecimal("cost_of_returned"));
                }
            }
        });
    }

    @Override
    public List<ExpenseHeadingTotal> expensesByHeading(ProfitLossPeriod period) throws DaoException {
        return withConnection(connection -> {
            List<ExpenseHeadingTotal> headings = new ArrayList<>();
            try (PreparedStatement statement =
                         connection.prepareStatement(ProfitLossStatementQuery.EXPENSES_BY_HEADING_SQL)) {
                bindPeriod(statement, period, 1);
                try (ResultSet row = statement.executeQuery()) {
                    while (row.next()) {
                        headings.add(new ExpenseHeadingTotal(row.getInt("heading_id"), row.getString("heading_name"),
                                row.getBigDecimal("total")));
                    }
                }
            }
            return headings;
        });
    }

    @Override
    public OutsideProfitFigures outsideProfit(ProfitLossPeriod period) throws DaoException {
        return withConnection(connection -> {
            BigDecimal stockShortage;
            BigDecimal stockSurplus;
            try (PreparedStatement statement = connection.prepareStatement(ProfitLossStatementQuery.STOCK_COUNT_SQL)) {
                bindPeriod(statement, period, 1);
                try (ResultSet row = statement.executeQuery()) {
                    row.next();
                    stockShortage = row.getBigDecimal("shortage");
                    stockSurplus = row.getBigDecimal("surplus");
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(ProfitLossStatementQuery.TILL_SQL)) {
                statement.setObject(1, period.from().atStartOfDay());
                statement.setObject(2, period.to().plusDays(1).atStartOfDay());
                try (ResultSet row = statement.executeQuery()) {
                    row.next();
                    return new OutsideProfitFigures(stockShortage, stockSurplus, row.getBigDecimal("shortage"),
                            row.getBigDecimal("surplus"));
                }
            }
        });
    }

    @Override
    public List<ProfitLossMovement> movements(ProfitLossPeriod period) throws DaoException {
        return withConnection(connection -> {
            List<ProfitLossMovement> movements = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(ProfitLossStatementQuery.MOVEMENTS_SQL)) {
                bindPeriod(statement, period, 3);
                try (ResultSet row = statement.executeQuery()) {
                    while (row.next()) {
                        movements.add(new ProfitLossMovement(
                                ProfitLossMovement.Kind.valueOf(row.getString("kind")), row.getLong("number"),
                                row.getObject("movement_date", java.time.LocalDate.class), row.getString("name"),
                                row.getString("note"), row.getBigDecimal("net_sales"), row.getBigDecimal("cost"),
                                row.getBigDecimal("expense")));
                    }
                }
            }
            return movements;
        });
    }

    /** Binds the period's first and last day, {@code times} times over - once per branch of the statement. */
    private static void bindPeriod(PreparedStatement statement, ProfitLossPeriod period, int times) throws SQLException {
        int index = 1;
        for (int i = 0; i < times; i++) {
            statement.setObject(index++, period.from());
            statement.setObject(index++, period.to());
        }
    }

    @Override
    public Object map(ResultSet resultSet) {
        throw new UnsupportedOperationException("Read through the named methods");
    }
}
