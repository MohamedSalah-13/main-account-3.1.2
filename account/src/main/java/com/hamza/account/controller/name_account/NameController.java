package com.hamza.account.controller.name_account;

import com.hamza.account.config.AppIcon;
import com.hamza.account.table.RowAction;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.DataTable;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.table.ActionButtonToolBar;
import com.hamza.account.table.TableInterface;
import com.hamza.account.table.TableScreenProfile;
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
import javafx.beans.property.*;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.util.List;


@Log4j2
public class NameController<T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T3, T4> implements TableInterface<T3> {

    private final DaoList<T3> nameInterface;
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final PartyNamesTable<T3, T4> tableData;
    private final StringProperty textSearchData = new SimpleStringProperty("");
    private final ObjectProperty<T3> objectProperty = new SimpleObjectProperty<>();
    private TableView<T3> table;

    public NameController(DataInterface<?, ?, T3, T4> dataInterface
            , DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.nameInterface = nameAndAccountInterface.nameDao();
        this.tableData = new PartyNamesTable<>(
                nameAndAccountInterface,
                nameData,
                rowActions(),
                this::editRow);
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
                printPartyPdf();
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
        return tableData;
    }

    @Override
    public TableScreenProfile screenProfile() {
        return PartyScreenIdentity.forKind(nameAndAccountInterface.partyKind()).listProfile();
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

    private void open(int id) throws Exception {
        new AddForAllApplication(id, new AddNameController<>(dataInterface, daoFactory, dataPublisher, id));
    }

    /** The actions that belong to one party stay beside that party's row. */
    private List<RowAction<T3>> rowActions() {
        var permissions = dataInterface.permAccountAndNameInt();
        return List.of(
                RowAction.of("row.action.show", AppIcon.SHOW, "app-neutral-button",
                        permissions.showNames(), this::showRow),
                RowAction.of("row.action.edit", AppIcon.EDIT, "warning-action-button",
                        permissions.updateNames(), this::editRow),
                RowAction.of("row.action.delete", AppIcon.DELETE, "danger-action-button",
                        permissions.deleteNames(), this::deleteRow));
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

    /** Prints selected rows, or every row matching the search when none are selected. */
    private void printPartyPdf() {
        File target = reportTarget();
        if (target == null) {
            return;
        }
        List<T3> selected = table.getItems().stream()
                .filter(row -> row.getSelectedRow().get())
                .toList();
        String query = textSearchData.get().trim();
        if (!selected.isEmpty()) {
            exportPartyPdf(target, selected, query);
            return;
        }

        Task<List<T3>> load = new Task<>() {
            @Override
            protected List<T3> call() throws Exception {
                return query.isBlank()
                        ? nameAndAccountInterface.nameList()
                        : nameAndAccountInterface.getFilterItems(query);
            }
        };
        load.setOnSucceeded(event -> {
            List<T3> rows = load.getValue();
            if (rows.isEmpty()) {
                AllAlerts.alertError(text("party.error.no.data.print"));
                return;
            }
            exportPartyPdf(target, rows, query);
        });
        AllAlerts.handleTaskFailure(text("party.error.export.generic"), load);
        PartyPdfReport.start(load, "party-list-pdf-load");
    }

    /** Captures the table on the FX thread, then writes the potentially large PDF in the background. */
    private void exportPartyPdf(File target, List<T3> rows, String query) {
        String subtitle = query.isBlank() ? "" : text("search") + ": " + query;
        PartyPdfReport.write(target, dataInterface.designInterface().nameTextOfReport(), subtitle,
                PartyListPdfLayout.from(table, rows), () -> { });
    }

    private File reportTarget() {
        return PartyPdfReport.chooseTarget(table.getScene().getWindow(),
                dataInterface.designInterface().nameTextOfReport());
    }

    private String text(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
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
