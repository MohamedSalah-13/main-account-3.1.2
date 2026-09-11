package com.hamza.account.controller.name_account;

import com.hamza.account.config.AppIcon;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.DataTable;
import com.hamza.account.interfaces.api.DesignInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.table.ActionButtonToolBar;
import com.hamza.account.table.TableInterface;
import com.hamza.account.table.TableSetting;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DaoList;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.AccountChanged;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.NameChanged;
import com.hamza.controlsfx.observer.AppEvent;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.others.CssToColorHelper;
import com.hamza.controlsfx.table.Columns;
import javafx.beans.property.*;
import javafx.css.PseudoClass;
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
                var kind = nameAndAccountInterface.partyKind();
                return TransactionTemplate.execute(() -> {
                    int rows = DeletionService.shared()
                            .delete(DeleteRegistry.forParty(kind),
                                    t3.getId(), nameInterface::deleteById)
                            .rowsOrThrow();
                    if (rows > 0) {
                        ChangeAnnouncer announcer = ChangeAnnouncer.jdbc();
                        announcer.announce(new NameChanged(kind));
                        announcer.announce(new AccountChanged(kind));
                    }
                    return rows;
                });
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
                addActionsColumn();
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

            /**
             * What is on the party's own row, and nothing derived from the ledger.
             * <p>
             * <b>There is deliberately no balance column here.</b> A party's balance has
             * one definition and one screen - {@code AccountController2} over
             * {@code features/party/balances}, which already carries the area, the credit
             * limit, who is over it and the four figures above the table. Computing it a
             * second time in this list is exactly the shape of defect the party work
             * exists to remove: two screens, two answers, and the one a customer is shown
             * decided by which they happened to open. The opening balance below is not
             * that - it is a column of this table, read from this row.
             */
            @Override
            public @NotNull List<TableColumn<T3, ?>> columns() {
                return List.of(
                        Columns.number(NamesTables.CODE, BaseNames::getId),
                        Columns.text(NamesTables.NAME, BaseNames::getName),
                        Columns.text(NamesTables.TEL, BaseNames::getTel),
                        Columns.text(NamesTables.ADDRESS, BaseNames::getAddress),
                        Columns.text(NamesTables.EMAIL, BaseNames::getEmail),
                        Columns.text(NamesTables.NOTES, BaseNames::getNotes),
                        // Not Columns.number: that prints a double, so an opening balance
                        // that has been through arithmetic reads 1234.5600000000002 and
                        // the same figure appears with and without a thousands separator
                        // on two screens of one ledger.
                        Columns.moneyOfDouble(NamesTables.FIRST_BALANCE, BaseNames::getFirst_balance),
                        statusColumn()
                );
            }
        };
    }

    /**
     * Whether the party is still dealt with (V56).
     * <p>
     * A stopped party is not hidden from this list - this is the screen its row is
     * switched back on from, so hiding it here would make {@code is_active} a one-way
     * door. It is marked instead, and the row is greyed through a {@code PseudoClass}
     * styled in the theme rather than an inline {@code setStyle}, so a themed build can
     * say it differently.
     */
    private TableColumn<T3, String> statusColumn() {
        TableColumn<T3, String> column = Columns.text(NamesTables.STATUS,
                party -> LanguageManager.getInstance().getString(
                        party.isActive() ? "party.active" : "party.inactive.badge"));
        column.setCellFactory(unused -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                T3 party = empty || getIndex() >= getTableView().getItems().size()
                        ? null : getTableView().getItems().get(getIndex());
                pseudoClassStateChanged(STOPPED, party != null && !party.isActive());
            }
        });
        return column;
    }

    /** Styled in the theme; see {@code Columns.NEGATIVE} for the same idea on an amount. */
    private static final PseudoClass STOPPED = PseudoClass.getPseudoClass("party-stopped");

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
    public PermissionKey permAdd() {
        return dataInterface.permAccountAndNameInt().createNames();
    }

    @Override
    public PermissionKey permUpdate() {
        return dataInterface.permAccountAndNameInt().updateNames();
    }

    @Override
    public PermissionKey permDelete() {
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
    public void helper(CssToColorHelper helper) {
        this.helper = helper;
    }

    private void open(int id) throws Exception {
        new AddForAllApplication(id, new AddNameController<>(dataInterface, daoFactory, dataPublisher, id));
    }

    /**
     * The three things you do to one party, in that party's own row.
     * <p>
     * <b>Edit and delete were toolbar buttons acting on "the selected row".</b> That is two
     * gestures - select, then travel to the toolbar - and it carried a refusal that existed
     * only because the control was in the wrong place: "choose a row first". They stay in the
     * toolbar as well, because a keyboard user selects with the arrow keys and never touches a
     * row button; what changes is that the mouse no longer needs both.
     * <p>
     * This replaces a hand-rolled button cell that had the two defects
     * {@link RowActionsColumn} exists to prevent: it read its row with
     * {@code getTableView().getItems().get(getIndex())}, which throws when a reload leaves a
     * cell pointing past the end of the list, and it re-used one {@code Button} across every
     * row the cell was recycled through - correct only because it read the index afresh each
     * time, which is the part that could throw.
     */
    private void addActionsColumn() {
        var permissions = dataInterface.permAccountAndNameInt();
        List<RowAction<T3>> actions = List.of(
                RowAction.of("row.action.show", AppIcon.SHOW, "app-neutral-button",
                        permissions.showNames(), this::showRow),
                RowAction.of("row.action.edit", AppIcon.EDIT, "warning-action-button",
                        permissions.updateNames(), this::editRow),
                RowAction.of("row.action.delete", AppIcon.DELETE, "danger-action-button",
                        permissions.deleteNames(), this::deleteRow));
        // First, not last. This table is wider than the window - it carries ten columns and a
        // horizontal scroll bar - and a column appended to the end lands past the right of that
        // scroll, which for buttons meant for "the row in front of you" is the same as not being
        // there. First puts it against the row's own identity, which is where it was asked for.
        table.getColumns().add(0, RowActionsColumn.of("column.actions", RowAction.permitted(actions)));
    }

    // AllAlerts owns the technical logging behind its reference code, so nothing here logs
    // as well - a second copy of one stack trace makes the log say an error happened twice.
    // ErrorHandlingArchitectureTest fails the build over it.

    private void showRow(T3 party) {
        try {
            nameData.actionColumnShow(party, daoFactory);
        } catch (Exception e) {
            AllAlerts.reportError(LanguageManager.getInstance().getString("row.action.show"), e);
        }
    }

    private void editRow(T3 party) {
        try {
            open(party.getId());
        } catch (Exception e) {
            AllAlerts.reportError(LanguageManager.getInstance().getString("row.action.edit"), e);
        }
    }

    /**
     * Deletes one party, through the same rule the toolbar's button uses.
     * <p>
     * It goes through {@link #actionButton()} rather than reaching for the DAO, so the
     * permission, the protected ids and the reference scan in {@code DeleteRegistry} all still
     * apply - a row button is a second way to ask for an operation, never a second copy of it.
     * The table reloads because {@code afterDelete} publishes {@code NameChanged}, which this
     * screen's own {@code refreshOn} is subscribed to.
     */
    private void deleteRow(T3 party) {
        if (!AllAlerts.confirmDelete()) {
            return;
        }
        ActionButtonToolBar<T3> actions = actionButton();
        try {
            if (actions.delete(party) == 1) {
                AllAlerts.alertDelete();
                actions.afterDelete();
            }
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("row.action.delete"), e);
        }
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
