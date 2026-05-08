package com.hamza.account.controller.convert_stock;

import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.interfaces.api.DataTable;
import com.hamza.account.model.base.DForColumnTable;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.StockTransfer;
import com.hamza.account.model.domain.StockTransferListItems;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.table.ActionButtonToolBar;
import com.hamza.account.table.TableInterface;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.OpenApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@Log4j2
public class ConvertStockDataController extends ServiceData implements TableInterface<StockTransfer> {

    private final DaoFactory daoFactory;
    private final Publisher<String> publisherAfterInsertData;
    private TableView<StockTransfer> tableView;

    public ConvertStockDataController(DaoFactory daoFactory) throws Exception {
        super(daoFactory);
        this.daoFactory = daoFactory;
        publisherAfterInsertData = new Publisher<>();
    }

    @Override
    public String titleName() {
        return Setting_Language.STORE_TRANSFERS;
    }

    @Override
    public void addToLastPane(GridPane gridPane, HBox hBox, ToolBar toolBar) {
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    @Override
    public ActionButtonToolBar<StockTransfer> actionButton() {
        return new ActionButtonToolBar<>() {
            @Override
            public void openNew() throws Exception {
                openApp(0);
            }

            @Override
            public void print() throws DaoException {
                List<Integer> selectedIndices = tableView.getItems().stream().filter(DForColumnTable::isSelectedRow).map(StockTransfer::getId).toList();
                Integer min = Collections.min(selectedIndices);
                Integer max = Collections.max(selectedIndices);
                new Print_Reports().printStockTransfer(min, max);
            }

            @Override
            public void update(StockTransfer stockTransfer) throws Exception {
                openApp(stockTransfer.getId());
            }

            @Override
            public int delete(StockTransfer stockTransfer) throws Exception {
                //TODO 12/22/2024 4:32 PM m13id: حذف فى حالة رصيد المخزن المحول له يسمح
                return stockTransferService.deleteTransfer(stockTransfer);
            }

            @Override
            public void afterDelete() {

            }
        };
    }

    @Override
    public DataTable<StockTransfer> table_data() {
        return new DataTable<>() {
            @Override
            public void getTable(TableView<StockTransfer> tableView) {
                DataTable.super.getTable(tableView);
                ConvertStockDataController.this.tableView = tableView;

                // add column
                Callback<TableColumn.CellDataFeatures<StockTransfer, String>, ObservableValue<String>> from = f -> f.getValue().getStockFrom().nameProperty();
                Callback<TableColumn.CellDataFeatures<StockTransfer, String>, ObservableValue<String>> to = f -> f.getValue().getStockTo().nameProperty();
                ColumnSetting.addColumn(tableView, Setting_Language.WORD_FROM, 2, from);
                ColumnSetting.addColumn(tableView, Setting_Language.WORD_TO, 3, to);

                tableView.setOnMouseClicked(keyEvent -> {
                    if (keyEvent.getClickCount() == 2) {
                        try {
                            new ShowDataTransfer<>(new ShowDataListStock(daoFactory, tableView.getSelectionModel().getSelectedItem()));
                        } catch (Exception e) {
                            errorLog(e);
                        }
                    }
                });
            }

            @Override
            public List<StockTransfer> dataList() throws Exception {
                return stockTransferService.getStockTransferList();
            }

            @Override
            public @NotNull Class<? super StockTransfer> classForColumn() {
                return StockTransfer.class;
            }
        };
    }

    @Override
    public BooleanProperty getColumnSelected(StockTransfer stockTransfer) {
        return stockTransfer.getSelectedRow();
    }

    @Override
    public Publisher<String> publisherTable() {
        return publisherAfterInsertData;
    }


    @Override
    public UserPermissionType permAdd() {
        return UserPermissionType.STOCK_CONVERT_SHOW;
    }

    @Override
    public UserPermissionType permUpdate() {
        return UserPermissionType.STOCK_CONVERT_UPDATE;
    }

    @Override
    public UserPermissionType permDelete() {
        return UserPermissionType.STOCK_CONVERT_DELETE;
    }

    @Override
    public List<StockTransfer> getProducts(int rowsPerPage, int offset) throws Exception {
        return List.of();
    }

    @Override
    public List<StockTransfer> getFilterItems(String newValue) {
        return null;
    }

    @Override
    public int getCountItems() {
        return 0;
    }

    private void openApp(int code) throws Exception {
        new OpenApplication<>(new ConvertStockMainController(daoFactory, publisherAfterInsertData, code));
    }

    private void errorLog(Exception e) {
        log.error(e.getMessage(), e.getCause());
        AllAlerts.alertError(e.getMessage());
    }
}

class ShowDataListStock extends ServiceData implements ShowDataTransferList<StockTransferListItems> {

    private final StockTransfer stockTransfer;

    public ShowDataListStock(DaoFactory daoFactory, StockTransfer stockTransfer) throws Exception {
        super(daoFactory);
        this.stockTransfer = stockTransfer;
    }


    @Override
    public Class<StockTransferListItems> classOfColumns() {
        return StockTransferListItems.class;
    }

    @Override
    public List<StockTransferListItems> listTable() throws DaoException {
        return stockTransferListService.getStockTransferListItemsById(stockTransfer.getId());
    }

    @Override
    public String titlePane() {
        return Setting_Language.STORE_TRANSFERS + " - " + stockTransfer.getDate();
    }

    @Override
    public void tableData(TableView<StockTransferListItems> tableView) {
        // add column name
        Callback<TableColumn.CellDataFeatures<StockTransferListItems, String>, ObservableValue<String>> callback_name = f -> f.getValue().getItem().nameItemProperty();
        ColumnSetting.addColumn(tableView, Setting_Language.WORD_NAME, 1, callback_name);

    }
}

