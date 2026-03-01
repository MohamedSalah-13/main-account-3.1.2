package com.hamza.account.controller.name_account;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.DataTable;
import com.hamza.account.interfaces.api.DesignInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.otherSetting.AddSumToColumn;
import com.hamza.account.table.ActionButtonToolBar;
import com.hamza.account.table.TableInterface;
import com.hamza.account.table.TableSetting;
import com.hamza.account.type.UserPermissionType;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DaoList;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.others.CssToColorHelper;
import javafx.beans.property.*;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.List;


@Log4j2
public class NameController<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T1, T2, T3, T4> implements TableInterface<T3> {

    private final DaoList<T3> nameInterface;
    private final Publisher<String> publisherAddName;
    private final Publisher<String> publisherAddAccount;
    private final DesignInterface designInterface;
    private final StringProperty textSearchData = new SimpleStringProperty("");
    private final AddSumToColumn sumBalance = new AddSumToColumn(Setting_Language.WORD_BALANCE);
    private final ObjectProperty<T3> objectProperty = new SimpleObjectProperty<>();
    private TableView<T3> table;
    private CssToColorHelper helper;

    public NameController(DataInterface<T1, T2, T3, T4> dataInterface
            , DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.designInterface = dataInterface.designInterface();
        this.nameInterface = nameAndAccountInterface.nameDao();
        this.publisherAddName = nameAndAccountInterface.addNamePublisher();
        this.publisherAddAccount = nameAndAccountInterface.addAccountPublisher();
    }

    @Override
    public void addToLastPane(GridPane gridPane, HBox hBox, ToolBar toolBar, FilteredList<T3> filteredTable) {
        hBox.getChildren().addAll(sumBalance);
        sumTable();
//        table.itemsProperty().addListener(observable -> sumTable());
        filteredTable.addListener((ListChangeListener<? super T3>) change -> {
            sumTable();
            table.refresh();
        });
    }

    @Override
    public void textData(TableView<T3> tableView, TextField textField) {
        this.table = tableView;
        textSearchData.bind(textField.textProperty().orElse(""));

        tableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                setObjectProperty(newValue);
            }
        });
    }

    @Override
    public ActionButtonToolBar<T3> actionButton() {
        return new ActionButtonToolBar<>() {

            @Override
            public void openNew() throws Exception {
                open(0);
            }

            @Override
            public void print() {
                List<T3> list = table.getItems();
                printReports.printDetailsOfNames(dataInterface.designInterface().nameTextOfReport(), list, helper);
            }

            @Override
            public void update(T3 t3) throws Exception {
                open(t3.getId());
            }

            @Override
            public int delete(T3 t3) throws DaoException {
                if (t3.getId() == 1) throw new DaoException(Error_Text_Show.CANT_DELETE);
                return nameInterface.deleteById(t3.getId());
            }

            @Override
            public void afterDelete() {
                publisherAddName.setAvailability(dataInterface.designInterface().nameTextOfData());
                publisherAddAccount.setAvailability(dataInterface.designInterface().nameTextOfAccount());
            }
        };
    }

    @Override
    public DataTable<T3> table_data() {
        return new DataTable<>() {
            @Override
            public void getTable(TableView<T3> tableView) {
                NameController.this.table = tableView;
                nameData.addColumns(tableView);
                TableSetting.tableMenuSetting(getClass(), tableView);

                table.setOnMouseClicked(keyEvent -> {
                    if (keyEvent.getClickCount() == 2) {
                        try {
                            var selectedItem = tableView.getSelectionModel().getSelectedItem();
                            open(selectedItem.getId());
                        } catch (Exception e) {
                            log.error(e.getMessage(), e.getCause());
                        }
                    }
                });
            }

            @Override
            public List<T3> dataList() throws Exception {
                return nameAndAccountInterface.nameList();
            }

            @Override
            public @NotNull Class<? super T3> classForColumn() {
                return BaseNames.class;
            }
        };
    }

    @Override
    public BooleanProperty getColumnSelected(BaseNames t3) {
        return t3.getSelectedRow();
    }

    @Override
    public Publisher<String> publisherTable() {
        return publisherAddName;
    }

    @Override
    public UserPermissionType permAdd() {
        return dataInterface.permAccountAndNameInt().showNames();
    }

    @Override
    public UserPermissionType permUpdate() {
        return dataInterface.permAccountAndNameInt().updateNames();
    }

    @Override
    public UserPermissionType permDelete() {
        return dataInterface.permAccountAndNameInt().deleteNames();
    }

    @Override
    public void loadData() throws Exception {
        dataInterface.loadNameAndAccount();
    }

    @Override
    public String styleSheet() {
        return designInterface.styleSheet();
    }

    @Override
    public void helper(CssToColorHelper helper) {
        this.helper = helper;
    }

    private void open(int id) throws Exception {
        new AddForAllApplication(id, new AddNameController<>(dataInterface, daoFactory, dataPublisher, id));
    }

    private void sumTable() {
        double purchase = table.getItems().stream().mapToDouble(BaseNames::getFirst_balance).sum();
        sumBalance.setSum(purchase);
    }

    public BaseNames getObjectProperty() {
        return objectProperty.get();
    }

    public void setObjectProperty(BaseNames objectProperty) {
        this.objectProperty.set((T3) objectProperty);
    }

    public ObjectProperty<T3> objectPropertyProperty() {
        return objectProperty;
    }
}
