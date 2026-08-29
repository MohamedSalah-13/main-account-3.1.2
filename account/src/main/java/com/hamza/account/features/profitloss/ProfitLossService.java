package com.hamza.account.features.profitloss;
import com.hamza.controlsfx.database.DaoException;
import java.time.LocalDate;
import java.util.List;
public record ProfitLossService(ProfitLossDao dao) { public List<ProfitLossRow> load(LocalDate from, LocalDate to) throws DaoException { return dao.load(from,to); } }