package com.hamza.account.controller.reports;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.profitloss.ProfitLossRow;
import com.hamza.account.features.profitloss.ProfitLossService;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.table.Columns;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import com.hamza.account.features.export.ReportExportService;
import java.time.LocalDate;

@FxmlPath(pathFile = "reports/profit-loss.fxml")
public class ProfitLossController {
  private final ProfitLossService service = ServiceRegistry.get(ProfitLossService.class);
  @FXML private DatePicker dateFrom,dateTo;
  @FXML private TableView<ProfitLossRow> tableView;
  @FXML private Label netSales,costOfSales,grossProfit,expenses,netProfit;
  private final ReportExportService exports = new ReportExportService();
  @FXML public void initialize() {
    tableView.getColumns().setAll(Columns.text(LanguageManager.getInstance().getString("profitloss.column.date"), r -> r.date().toString()), Columns.number(LanguageManager.getInstance().getString("profitloss.net.sales"), r -> r.netSales().doubleValue()), Columns.number(LanguageManager.getInstance().getString("profitloss.cost.sales"), r -> r.costOfSales().doubleValue()), Columns.number(LanguageManager.getInstance().getString("profitloss.gross.profit"), r -> r.grossProfit().doubleValue()), Columns.number(LanguageManager.getInstance().getString("profitloss.expenses"), r -> r.expenses().doubleValue()), Columns.number(LanguageManager.getInstance().getString("profitloss.net.profit"), r -> r.netProfit().doubleValue()));
    load();
  }
  @FXML private void load() { try { var rows=service.load(dateFrom.getValue(),dateTo.getValue()); tableView.setItems(FXCollections.observableArrayList(rows)); netSales.setText(sum(rows,0)); costOfSales.setText(sum(rows,1)); grossProfit.setText(sum(rows,2)); expenses.setText(sum(rows,3)); netProfit.setText(sum(rows,4)); } catch(Exception e) { AllAlerts.handleError(LanguageManager.getInstance().getString("profitloss.error.load"),e); } }
  @FXML private void print() { if(tableView.getItems().isEmpty()) return; FileChooser f=new FileChooser(); f.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF","*.pdf")); var file=f.showSaveDialog(tableView.getScene().getWindow()); if(file!=null) exports.exportProfitLossReport(tableView.getItems(), LanguageManager.getInstance().getString("report.profit.loss.title"), new String[]{LanguageManager.getInstance().getString("profitloss.column.date"),LanguageManager.getInstance().getString("profitloss.net.sales"),LanguageManager.getInstance().getString("profitloss.cost.sales"),LanguageManager.getInstance().getString("profitloss.gross.profit"),LanguageManager.getInstance().getString("profitloss.expenses"),LanguageManager.getInstance().getString("profitloss.net.profit")}, file.getAbsolutePath()); }
  private String sum(java.util.List<ProfitLossRow> rows,int i) { return rows.stream().map(r -> switch(i){case 0->r.netSales();case 1->r.costOfSales();case 2->r.grossProfit();case 3->r.expenses();default->r.netProfit();}).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add).toPlainString(); }
}



