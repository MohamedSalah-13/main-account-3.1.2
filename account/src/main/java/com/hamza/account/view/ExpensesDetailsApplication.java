package com.hamza.account.view;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.AddExpensesController;
import com.hamza.account.interfaces.api.DataTable;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ExpensesDetails;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.otherSetting.AddSumToColumn;
import com.hamza.account.service.ExpensesDetailsService;
import com.hamza.account.table.ActionButtonToolBar;
import com.hamza.account.table.TableInterface;
import com.hamza.account.table.TableOpen;
import com.hamza.account.type.UserPermissionType;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.application.Application;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Callback;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.hamza.controlsfx.language.Setting_Language.TOTAL;

@Log4j2
public class ExpensesDetailsApplication extends Application implements TableInterface<ExpensesDetails> {

    private final DaoFactory daoFactory;
    private final Publisher<String> stringPublisher;
    private final ExpensesDetailsService expensesDetailsService;
    private TableView<ExpensesDetails> tableView;

    public ExpensesDetailsApplication(DaoFactory daoFactory, DataPublisher dataPublisher) {
        this.daoFactory = daoFactory;
        this.expensesDetailsService = new ExpensesDetailsService(daoFactory);
        this.stringPublisher = dataPublisher.getPublisherAddExpenses();
    }


    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public String titleName() {
        return Setting_Language.EXPENSES;
    }

    @Override
    public void addToLastPane(GridPane gridPane, HBox hBox, ToolBar toolBar, FilteredList<ExpensesDetails> filteredTable) {
        AddSumToColumn boxActive = new AddSumToColumn(TOTAL);
        double v = tableView.getItems().stream().mapToDouble(ExpensesDetails::getAmount).sum();
        boxActive.setSum(v);

        tableView.itemsProperty().addListener((observableValue, itemsPrices, t1) -> {
            double v2 = t1.stream().mapToDouble(ExpensesDetails::getAmount).sum();
            boxActive.setSum(v2);
        });
        hBox.getChildren().addAll(boxActive);
    }

    @Override
    public ActionButtonToolBar<ExpensesDetails> actionButton() {

        return new ActionButtonToolBar<>() {
            @Override
            public void openNew() throws Exception {
                openApp(0);
            }

            @Override
            public void update(ExpensesDetails expensesDetails) throws Exception {
                openApp(expensesDetails.getId());
            }

            @Override
            public int delete(ExpensesDetails expensesDetails) throws Exception {
                return expensesDetailsService.deleteById(expensesDetails.getId());
            }

            @Override
            public void afterDelete() {

            }
        };
    }

    @Override
    public DataTable<ExpensesDetails> table_data() {
        return new DataTable<>() {
            @Override
            public void getTable(TableView<ExpensesDetails> tableView) {
                ExpensesDetailsApplication.this.tableView = tableView;

                Callback<TableColumn.CellDataFeatures<ExpensesDetails, String>, ObservableValue<String>> columnProcessesDataType = f -> f.getValue().getExpenses().nameProperty();
                ColumnSetting.addColumn(tableView, Setting_Language.WORD_TYPE, 2, columnProcessesDataType);
                Callback<TableColumn.CellDataFeatures<ExpensesDetails, String>, ObservableValue<String>> columnEmployee = f -> f.getValue().getEmployees().nameProperty();
                ColumnSetting.addColumn(tableView, Setting_Language.WORD_NAME, 3, columnEmployee);
            }

            @Override
            public List<ExpensesDetails> dataList() throws Exception {
                return expensesDetailsService.fetchAllExpensesDetailsList();
            }

            @Override
            public @NotNull Class<? super ExpensesDetails> classForColumn() {
                return ExpensesDetails.class;
            }
        };
    }

    @Override
    public BooleanProperty getColumnSelected(ExpensesDetails expensesDetails) {
        return expensesDetails.getSelectedRow();
    }

    @Override
    public Publisher<String> publisherTable() {
        return stringPublisher;
    }

    @Override
    public UserPermissionType permAdd() {
        return UserPermissionType.EXPENSES_SHOW;
    }

    @Override
    public UserPermissionType permUpdate() {
        return UserPermissionType.EXPENSES_UPDATE;
    }

    @Override
    public UserPermissionType permDelete() {
        return UserPermissionType.EXPENSES_DELETE;
    }

    @Override
    public void loadData() throws Exception {
        //TODO 10/10/2025 7:20 PM Mohamed: add load data to table
    }

    private void openApp(int id) throws Exception {
        new AddForAllApplication(id, new AddExpensesController(id, stringPublisher, daoFactory));
    }

    @Override
    public void start(Stage stage) throws Exception {
        new TableOpen<>(this).start(stage);
    }
}
