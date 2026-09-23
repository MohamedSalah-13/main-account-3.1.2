package com.hamza.account.features.profitloss.yearly;

import com.hamza.account.features.profitloss.ProfitLossRow;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

/**
 * The profit and loss statement's days over a period. The application passes
 * {@code ProfitLossService::load}, which asks {@code reports.show.profit} before it reads anything - so the
 * yearly report is guarded by the same key as the statement, and in the same place.
 */
@FunctionalInterface
public interface DailyProfitSource {

    List<ProfitLossRow> load(LocalDate from, LocalDate to) throws DaoException;
}
