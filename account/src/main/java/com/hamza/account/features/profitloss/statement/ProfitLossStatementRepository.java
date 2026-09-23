package com.hamza.account.features.profitloss.statement;

import com.hamza.controlsfx.database.DaoException;

import java.util.List;

/** What the statement reads beside the statement's own days. Asks no permission: its caller does. */
public interface ProfitLossStatementRepository {

    SalesBreakdown breakdown(ProfitLossPeriod period) throws DaoException;

    List<ExpenseHeadingTotal> expensesByHeading(ProfitLossPeriod period) throws DaoException;

    OutsideProfitFigures outsideProfit(ProfitLossPeriod period) throws DaoException;

    List<ProfitLossMovement> movements(ProfitLossPeriod period) throws DaoException;
}
