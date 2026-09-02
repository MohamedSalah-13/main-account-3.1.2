package com.hamza.account.interfaces.impl_totalDesgin;

import com.hamza.account.interfaces.api.TotalDesignInterface;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.interfaces.totals.TotalsBuyData;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.domain.Total_buy;
import com.hamza.account.service.TotalBuyService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Callback;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.hamza.controlsfx.table.columnEdit.ColumnSetting.addColumn;

@Log4j2
@RequiredArgsConstructor
public class TotalsPurchaseImplDesign implements TotalDesignInterface {

    private final TotalBuyService totalBuyService;

    /** Every row on this screen is a {@link Total_buy}; see {@link TotalsDataInterface}. */
    private static Total_buy cast(BaseTotals t2) {
        return (Total_buy) t2;
    }

    @Override
    public void getTable(TableView<BaseTotals> tableView) {
        Callback<TableColumn.CellDataFeatures<BaseTotals, String>, ObservableValue<String>> cellName = f -> cast(f.getValue()).getSupplierData().nameProperty();
        addColumn(tableView, Setting_Language.WORD_NAME, 2, cellName);

        Callback<TableColumn.CellDataFeatures<BaseTotals, String>, ObservableValue<String>> colNameType = f -> f.getValue().getInvoiceType().typeProperty();
        addColumn(tableView, Setting_Language.WORD_TYPE, 3, colNameType);


    }

    @NotNull
    @Override
    public List<TableColumn<BaseTotals, ?>> columns() {
        return List.of();
    }


    @Override
    public TotalsDataInterface totalsDataInterface() {
        return new TotalsBuyData();
    }

    @Override
    public int deleteData(int id) throws DaoException {
        return totalBuyService.deleteById(id);
    }

    @Override
    public int deleteMultiData(@NotNull Integer... ids) throws Exception {
        return totalBuyService.deleteMultiData(ids);
    }

    @Override
    public int deleteMultiData(String correctionReason, @NotNull Integer... ids) throws Exception {
        return totalBuyService.deleteMultiData(ids, correctionReason);
    }

}
