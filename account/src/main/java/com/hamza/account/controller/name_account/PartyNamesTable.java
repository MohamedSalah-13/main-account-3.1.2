package com.hamza.account.controller.name_account;

import com.hamza.account.config.NamesTables;
import com.hamza.account.interfaces.api.DataTable;
import com.hamza.account.interfaces.api.NameAndAccountInterface;
import com.hamza.account.interfaces.api.NameData;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.TableColumnViews;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.css.PseudoClass;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * The JavaFX adapter for the shared customers/suppliers list.
 *
 * <p>It is named rather than an anonymous {@link DataTable}: the column contract,
 * row actions and double-click behaviour now have one stable home and the owning
 * controller is left to coordinate operations.</p>
 */
final class PartyNamesTable<T extends BaseNames, A extends BaseAccount> implements DataTable<T> {

    private static final PseudoClass STOPPED = PseudoClass.getPseudoClass("party-stopped");
    private static final String VIEW_MODE = "party.list.view.mode";
    private static final String ACTIONS_COLUMN = "party-actions";
    private static final String SELECTION_COLUMN = "party-selection";
    /** The two columns that are controls rather than data, and so stay off the printed list. */
    static final Set<String> SCREEN_ONLY_COLUMNS = Set.of(SELECTION_COLUMN, ACTIONS_COLUMN);

    private final NameAndAccountInterface<T, A> source;
    private final NameData<T> nameData;
    private final List<RowAction<T>> actions;
    private final Consumer<T> edit;
    private final ContentSizedColumns<T> sizing = new ContentSizedColumns<>();

    PartyNamesTable(NameAndAccountInterface<T, A> source, NameData<T> nameData,
                    List<RowAction<T>> actions, Consumer<T> edit) {
        this.source = source;
        this.nameData = nameData;
        this.actions = List.copyOf(actions);
        this.edit = edit;
    }

    @Override
    public void getTable(TableView<T> tableView) {
        tableView.setId("party-" + source.partyKind().name().toLowerCase() + "-list");
        nameData.addColumns(tableView);
        nameDetailColumns(tableView);
        tableView.getColumns().add(0, named(ACTIONS_COLUMN,
                RowActionsColumn.of("column.actions", RowAction.permitted(actions))));
        TableSetting.tableMenuSetting(NameController.class, tableView);
        // The shell has a clearer, named view menu for this feature. Leaving JavaFX's header
        // menu on would offer the same choices twice and make the compact headers harder to read.
        tableView.setTableMenuButtonVisible(false);
        sizing.install(tableView);
        tableView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
                T selected = tableView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    edit.accept(selected);
                }
            }
        });
    }

    @Override
    public boolean usesContentSizedColumns() {
        return true;
    }

    @Override
    public boolean supportsColumnViews() {
        return true;
    }

    /**
     * Offers the common views first, then keeps fine-grained column choices one level deeper.
     * The chosen preset is remembered independently for customers and suppliers.
     */
    @Override
    public void configureColumnViews(MenuButton viewMenu, TableView<T> tableView) {
        tableView.getColumns().getFirst().setId(SELECTION_COLUMN);
        Preferences preferences = Preferences.userNodeForPackage(PartyNamesTable.class)
                .node(source.partyKind().name().toLowerCase());
        new TableColumnViews<T>(preferences, VIEW_MODE, TableColumnViews.Preset.COMPACT,
                Set.of("party-code", "party-name", "party-phone", "party-status"),
                Set.of(SELECTION_COLUMN, ACTIONS_COLUMN))
                .install(viewMenu, tableView);
    }

    /**
     * Keeps party columns together at their useful width. A contact field that is empty for the
     * entire loaded result deliberately becomes a narrow divider instead of receiving a share of
     * the unused screen width. Fixed action and selection columns already declare their widths.
     */
    @Override
    public void layoutColumns(TableView<T> tableView) {
        sizing.layout(tableView);
    }

    @Override
    public List<T> dataList() throws Exception {
        return source.nameList();
    }

    /**
     * Data stored on the party row only. The current ledger balance deliberately
     * remains on the balances screen, which owns that one accounting definition.
     */
    @Override
    public @NotNull List<TableColumn<T, ?>> columns() {
        return List.of(
                named("party-code", Columns.number(NamesTables.CODE, BaseNames::getId)),
                named("party-name", Columns.text(NamesTables.NAME, BaseNames::getName)),
                named("party-phone", Columns.text(NamesTables.TEL, BaseNames::getTel)),
                named("party-address", Columns.text(NamesTables.ADDRESS, BaseNames::getAddress)),
                named("party-email", Columns.text(NamesTables.EMAIL, BaseNames::getEmail)),
                named("party-notes", Columns.text(NamesTables.NOTES, BaseNames::getNotes)),
                named("party-opening-balance", Columns.moneyOfDouble(NamesTables.FIRST_BALANCE,
                        BaseNames::getFirst_balance)),
                named("party-status", statusColumn()));
    }

    private TableColumn<T, String> statusColumn() {
        TableColumn<T, String> column = Columns.text(NamesTables.STATUS,
                party -> LanguageManager.getInstance().getString(
                        party.isActive() ? "party.active" : "party.inactive.badge"));
        column.setCellFactory(unused -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                T party = empty ? null : getTableRow().getItem();
                pseudoClassStateChanged(STOPPED, party != null && !party.isActive());
            }
        });
        return column;
    }

    private void nameDetailColumns(TableView<T> tableView) {
        for (int index = 0; index < tableView.getColumns().size(); index++) {
            TableColumn<T, ?> column = tableView.getColumns().get(index);
            if (column.getId() == null || column.getId().isBlank()) {
                column.setId("party-detail-" + index);
            }
        }
    }

    private static <S, V> TableColumn<S, V> named(String id, TableColumn<S, V> column) {
        column.setId(id);
        return column;
    }
}
