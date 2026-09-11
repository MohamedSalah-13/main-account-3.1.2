package com.hamza.account.controller.name_account;

import com.hamza.account.config.NamesTables;
import com.hamza.account.interfaces.api.DataTable;
import com.hamza.account.interfaces.api.NameAndAccountInterface;
import com.hamza.account.interfaces.api.NameData;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.css.PseudoClass;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.text.Text;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.function.Consumer;

/**
 * The JavaFX adapter for the shared customers/suppliers list.
 *
 * <p>It is named rather than an anonymous {@link DataTable}: the column contract,
 * row actions and double-click behaviour now have one stable home and the owning
 * controller is left to coordinate operations.</p>
 */
final class PartyNamesTable<T extends BaseNames, A extends BaseAccount> implements DataTable<T> {

    private static final PseudoClass STOPPED = PseudoClass.getPseudoClass("party-stopped");
    private static final double EMPTY_COLUMN_WIDTH = 10;
    private static final double MIN_CONTENT_WIDTH = 62;
    private static final double MAX_CONTENT_WIDTH = 260;
    private static final double CELL_PADDING = 28;
    private static final String VIEW_MODE = "party.list.view.mode";
    private static final String ACTIONS_COLUMN = "party-actions";
    private static final String SELECTION_COLUMN = "party-selection";

    private final NameAndAccountInterface<T, A> source;
    private final NameData<T> nameData;
    private final List<RowAction<T>> actions;
    private final Consumer<T> edit;
    private final Set<TableColumn<T, ?>> contentSizedColumns =
            Collections.newSetFromMap(new IdentityHashMap<>());

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

        RadioMenuItem compact = radio("party.list.view.compact");
        RadioMenuItem full = radio("party.list.view.full");
        ToggleGroup views = new ToggleGroup();
        compact.setToggleGroup(views);
        full.setToggleGroup(views);
        compact.setOnAction(event -> applyPreset(tableView, Preset.COMPACT, preferences));
        full.setOnAction(event -> applyPreset(tableView, Preset.FULL, preferences));

        Menu customize = new Menu(text("party.list.view.customize"));
        for (TableColumn<T, ?> column : configurableColumns(tableView)) {
            CheckMenuItem item = new CheckMenuItem(column.getText());
            item.selectedProperty().bindBidirectional(column.visibleProperty());
            item.setOnAction(event -> preferences.put(VIEW_MODE, Preset.CUSTOM.name()));
            customize.getItems().add(item);
        }

        RadioMenuItem selected = preferences.get(VIEW_MODE, Preset.COMPACT.name())
                .equals(Preset.FULL.name()) ? full : compact;
        selected.setSelected(true);
        applySavedView(tableView, preferences);

        var reset = new javafx.scene.control.MenuItem(text("party.list.view.reset"));
        reset.setOnAction(event -> {
            compact.setSelected(true);
            applyPreset(tableView, Preset.COMPACT, preferences);
        });
        viewMenu.getItems().setAll(compact, full, new SeparatorMenuItem(), customize,
                new SeparatorMenuItem(), reset);
    }

    /**
     * Keeps party columns together at their useful width. A contact field that is empty for the
     * entire loaded result deliberately becomes a narrow divider instead of receiving a share of
     * the unused screen width. Fixed action and selection columns already declare their widths.
     */
    @Override
    public void layoutColumns(TableView<T> tableView) {
        for (TableColumn<T, ?> column : tableView.getVisibleLeafColumns()) {
            if (!contentSizedColumns.contains(column) && isFixedColumn(column)) {
                continue;
            }
            contentSizedColumns.add(column);
            double width = contentWidth(tableView, column);
            column.setMinWidth(width);
            column.setPrefWidth(width);
            column.setMaxWidth(width);
        }
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

    private boolean isFixedColumn(TableColumn<T, ?> column) {
        return column.getMinWidth() == column.getMaxWidth();
    }

    private double contentWidth(TableView<T> tableView, TableColumn<T, ?> column) {
        double widest = 0;
        boolean hasContent = false;
        for (T row : tableView.getItems()) {
            var observable = column.getCellObservableValue(row);
            Object value = observable == null ? null : observable.getValue();
            if (value == null || value.toString().isBlank()) {
                continue;
            }
            hasContent = true;
            widest = Math.max(widest, textWidth(value.toString()));
        }
        if (!hasContent) {
            return EMPTY_COLUMN_WIDTH;
        }
        return Math.min(MAX_CONTENT_WIDTH,
                Math.max(MIN_CONTENT_WIDTH, Math.max(textWidth(column.getText()), widest) + CELL_PADDING));
    }

    private double textWidth(String value) {
        return new Text(value).getLayoutBounds().getWidth();
    }

    private void nameDetailColumns(TableView<T> tableView) {
        for (int index = 0; index < tableView.getColumns().size(); index++) {
            TableColumn<T, ?> column = tableView.getColumns().get(index);
            if (column.getId() == null || column.getId().isBlank()) {
                column.setId("party-detail-" + index);
            }
        }
    }

    private List<TableColumn<T, ?>> configurableColumns(TableView<T> tableView) {
        return tableView.getColumns().stream()
                .filter(column -> !SELECTION_COLUMN.equals(column.getId()))
                .filter(column -> !ACTIONS_COLUMN.equals(column.getId()))
                .toList();
    }

    private void applySavedView(TableView<T> tableView, Preferences preferences) {
        String value = preferences.get(VIEW_MODE, Preset.COMPACT.name());
        if (Preset.FULL.name().equals(value)) {
            applyVisibility(tableView, Preset.FULL);
        } else if (Preset.COMPACT.name().equals(value)) {
            applyVisibility(tableView, Preset.COMPACT);
        }
    }

    private void applyPreset(TableView<T> tableView, Preset preset, Preferences preferences) {
        applyVisibility(tableView, preset);
        preferences.put(VIEW_MODE, preset.name());
    }

    private void applyVisibility(TableView<T> tableView, Preset preset) {
        for (TableColumn<T, ?> column : configurableColumns(tableView)) {
            boolean visible = preset == Preset.FULL || isCompactColumn(column);
            column.setVisible(visible);
        }
    }

    private boolean isCompactColumn(TableColumn<T, ?> column) {
        return switch (column.getId()) {
            case "party-code", "party-name", "party-phone", "party-status" -> true;
            default -> false;
        };
    }

    private RadioMenuItem radio(String key) {
        return new RadioMenuItem(text(key));
    }

    private String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }

    private static <S, V> TableColumn<S, V> named(String id, TableColumn<S, V> column) {
        column.setId(id);
        return column;
    }

    private enum Preset {
        COMPACT,
        FULL,
        CUSTOM
    }
}
