package com.hamza.account.features.profitloss;

import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

/**
 * The profit and loss statement's days over a period. The application passes
 * {@code ProfitLossService::load}, which asks {@code reports.show.profit} before it reads anything - so the
 * reports built on it (the statement's screen and the yearly report) are guarded by that key, in that
 * place.
 */
@FunctionalInterface
public interface DailyProfitSource {

    List<ProfitLossRow> load(LocalDate from, LocalDate to) throws DaoException;
}
