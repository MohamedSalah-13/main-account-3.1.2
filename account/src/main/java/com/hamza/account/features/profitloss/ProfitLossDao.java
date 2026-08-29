package com.hamza.account.features.profitloss;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** Calculates P&L from recognised revenue, recorded sale costs, returns and expenses. */
public final class ProfitLossDao extends AbstractDao<ProfitLossRow> {
  public List<ProfitLossRow> load(LocalDate from, LocalDate to) throws DaoException {
    String where = from != null && to != null ? " WHERE report_date BETWEEN ? AND ? " : "";
    String sql = """
      SELECT report_date,SUM(revenue) net_sales,SUM(cost) cost_of_sales,SUM(revenue)-SUM(cost) gross_profit,SUM(expense) expenses,SUM(revenue)-SUM(cost)-SUM(expense) net_profit FROM (
      SELECT invoice_date report_date,total-discount revenue,0 cost,0 expense FROM total_sales
      UNION ALL SELECT ts.invoice_date,0,s.total_buy_price,0 FROM total_sales ts JOIN sales s ON s.invoice_number=ts.invoice_number
      UNION ALL SELECT invoice_date,-(total-discount),0,0 FROM total_sales_re
      UNION ALL SELECT ts.invoice_date,0,-s.total_buy_price,0 FROM total_sales_re ts JOIN sales_re s ON s.invoice_number=ts.id
      UNION ALL SELECT date,0,0,amount FROM expenses_details) entries %s GROUP BY report_date ORDER BY report_date DESC
      """.formatted(where);
    return from != null && to != null ? queryForObjects(sql,this::map,from,to) : queryForObjects(sql,this::map);
  }
  public ProfitLossRow map(ResultSet r) throws DaoException { try { return new ProfitLossRow(r.getObject("report_date",LocalDate.class),r.getBigDecimal("net_sales"),r.getBigDecimal("cost_of_sales"),r.getBigDecimal("gross_profit"),r.getBigDecimal("expenses"),r.getBigDecimal("net_profit")); } catch(SQLException e) { throw new DaoException(e); } }
}
