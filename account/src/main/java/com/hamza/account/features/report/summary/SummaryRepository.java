package com.hamza.account.features.report.summary;

import com.hamza.account.features.report.monthly.DayFigures;
import com.hamza.account.features.report.monthly.MonthlySide;
import com.hamza.account.model.domain.TopSellingItem;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

/** What the summary reads - each part through the report or the screen that answers it already. */
public interface SummaryRepository {

    /** The side's documents by the day between two dates - the monthly totals' own statement, bounded. */
    List<DayFigures> days(MonthlySide side, LocalDate from, LocalDate to) throws DaoException;

    CashFlow cash(LocalDate from, LocalDate to) throws DaoException;

    /** The customer balances screen's "debtors today", with the {@code top} who owe most. */
    Receivables receivables(int top) throws DaoException;

    LowStock lowStock(int limit) throws DaoException;

    List<TopSellingItem> topItems(LocalDate from, LocalDate to) throws DaoException;

    List<TreasuryBalanceSummary> treasuries() throws DaoException;
}
