package com.hamza.account.interfaces.impl_totalDesgin;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.interfaces.api.TotalDesignInterface;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.interfaces.totals.TotalsSalesReturnData;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.domain.Total_Sales_Re;
import com.hamza.account.service.TotalSalesReturnService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Callback;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.hamza.controlsfx.table.columnEdit.ColumnSetting.addColumn;

@RequiredArgsConstructor
public class TotalSalesReturnImplDesign implements TotalDesignInterface {

    private final TotalSalesReturnService totalSalesReturnService;

    /** Every row on this screen is a {@link Total_Sales_Re}; see {@link TotalsDataInterface}. */
    private static Total_Sales_Re cast(BaseTotals t2) {
        return (Total_Sales_Re) t2;
    }

    @Override
    public void getTable(TableView<BaseTotals> tableView) {
        Callback<TableColumn.CellDataFeatures<BaseTotals, String>, ObservableValue<String>> cellName = f -> cast(f.getValue()).getCustomer().nameProperty();
        addColumn(tableView, Setting_Language.WORD_NAME, 2, cellName);

        Callback<TableColumn.CellDataFeatures<BaseTotals, String>, ObservableValue<String>> colNameType = f -> f.getValue().getInvoiceType().typeProperty();
        addColumn(tableView, LanguageManager.getInstance().getString("type"), 3, colNameType);


        Callback<TableColumn.CellDataFeatures<BaseTotals, String>, ObservableValue<String>> colDelegate = f -> cast(f.getValue()).getEmployeeObject().nameProperty();
        addColumn(tableView, LanguageManager.getInstance().getString("user.type.delegate"), tableView.getColumns().size(), colDelegate);

        if (AuthorizationGuard.isGranted(AppPermissions.INVOICE_PROFIT_SHOW)) {
            Callback<TableColumn.CellDataFeatures<BaseTotals, Double>, ObservableValue<Double>> totalProfit =
                    cellData -> new SimpleDoubleProperty(cast(cellData.getValue()).getTotal_profit()).asObject();
            addColumn(tableView, LanguageManager.getInstance().getString("report.column.invoice.profit"), tableView.getColumns().size(), totalProfit);

            Callback<TableColumn.CellDataFeatures<BaseTotals, Double>, ObservableValue<Double>> totalProfitPercent =
                    cellData -> new SimpleDoubleProperty(cast(cellData.getValue()).getProfit_percent()).asObject();
            addColumn(tableView, LanguageManager.getInstance().getString("report.column.profit.percent"), tableView.getColumns().size(), totalProfitPercent);
        }

    }

    @Override
    public @NotNull List<TableColumn<BaseTotals, ?>> columns() {
        return List.of();
    }

    @NotNull
    @Override
    public TotalsDataInterface totalsDataInterface() {
        return new TotalsSalesReturnData();
    }

    @Override
    public int deleteData(int id) throws DaoException {
        return totalSalesReturnService.deleteById(id);
    }

    @Override
    public int deleteMultiData(@NotNull Integer... ids) throws Exception {
        return totalSalesReturnService.deleteMultiData(ids);
    }

    @Override
    public int deleteMultiData(String correctionReason, @NotNull Integer... ids) throws Exception {
        return totalSalesReturnService.deleteMultiData(ids, correctionReason);
    }

}
