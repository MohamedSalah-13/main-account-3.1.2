package com.hamza.account.controller.name_account;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.DataTable;
import com.hamza.account.interfaces.api.DesignInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.table.ActionButtonToolBar;
import com.hamza.account.table.TableInterface;
import com.hamza.account.table.TableSetting;
import com.hamza.account.type.UserPermissionType;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DaoList;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.AccountChanged;
import com.hamza.account.features.events.NameChanged;
import com.hamza.controlsfx.observer.AppEvent;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.others.CssToColorHelper;
import javafx.beans.property.*;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.List;


@Log4j2
public class NameController<T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T3, T4> implements TableInterface<T3> {

    private final DaoList<T3> nameInterface;
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final DesignInterface designInterface;
    private final StringProperty textSearchData = new SimpleStringProperty("");
    private final ObjectProperty<T3> objectProperty = new SimpleObjectProperty<>();
    private TableView<T3> table;
    private CssToColorHelper helper;

    public NameController(DataInterface<?, ?, T3, T4> dataInterface
            , DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.designInterface = dataInterface.designInterface();
        this.nameInterface = nameAndAccountInterface.nameDao();
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
                // Customers and suppliers are deleted straight through the DAO, with
                // no service in between, so the rule is applied here - the last point
                // that still knows which side is being deleted. It carries the
                // permission and the protected id that used to be written out at this
                // spot, and adds the invoices and account movements that make a name
                // undeletable, counted, in place of the one general sentence a foreign
                // key failure was turned into.
                return DeletionService.shared()
                        .delete(DeleteRegistry.forParty(nameAndAccountInterface.partyKind()),
                                t3.getId(), nameInterface::deleteById)
                        .rowsOrThrow();
            }

            @Override
            public void afterDelete() {
                if (eventBus == null) return;
                var kind = nameAndAccountInterface.partyKind();
                eventBus.publish(new NameChanged(kind));
                eventBus.publish(new AccountChanged(kind));
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
                addColumnShow();
                TableSetting.tableMenuSetting(getClass(), tableView);

                table.setOnMouseClicked(keyEvent -> {
                    if (keyEvent.getClickCount() == 2 && keyEvent.getButton() == MouseButton.PRIMARY) {
                        try {
                            var selectedItem = tableView.getSelectionModel().getSelectedItem();
                            if (selectedItem != null) {
                                open(selectedItem.getId());
                            }
                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
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
    public Class<NameChanged> refreshOn() {
        return NameChanged.class;
    }

    /**
     * A customers list has no reason to reload because a supplier changed.
     */
    @Override
    public boolean refreshFor(AppEvent event) {
        return event instanceof NameChanged changed && changed.kind() == nameAndAccountInterface.partyKind();
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
    public List<T3> getProducts(int rowsPerPage, int offset) throws Exception {
        return nameAndAccountInterface.getCustomers(rowsPerPage, offset);
    }

    @Override
    public List<T3> getFilterItems(String newValue) throws Exception {
        return nameAndAccountInterface.getFilterItems(newValue);
    }

    @Override
    public int getCountItems() {
        return nameAndAccountInterface.getCountItems();
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

    private void addColumnShow() {
        TableColumn<T3, String> column = new TableColumn<>("Show");
        column.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button(Setting_Language.WORD_SHOW);

            {
                btn.getStyleClass().add("app-neutral-button");
                btn.setOnAction(e -> {
                    try {
                        nameData.actionColumnShow(getTableView().getItems().get(getIndex()), daoFactory);
                    } catch (Exception ex) {
                        log.error("Error to open Button", ex);
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
        table.getColumns().add(column);
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