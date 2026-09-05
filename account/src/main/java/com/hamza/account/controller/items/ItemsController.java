package com.hamza.account.controller.items;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.config.NamesTables;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.DisableButtons;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.others.SelectedButton;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.GroupsChanged;
import com.hamza.account.features.events.ItemSaved;
import com.hamza.account.features.events.ItemsChanged;
import com.hamza.account.features.events.SelPriceNamesChanged;
import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.features.items.ItemQuickEditField;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.MainGroups;
import com.hamza.account.model.domain.SubGroups;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.ItemsService;
import com.hamza.account.service.MainGroupService;
import com.hamza.account.service.SelPriceItemService;
import com.hamza.account.service.SupGroupService;
import com.hamza.account.table.EditCell;
import com.hamza.account.table.TableSetting;
import com.hamza.account.view.AddItemApplication;
import com.hamza.account.view.CardApplication;
import com.hamza.account.view.ItemReportsApplication;
import com.hamza.account.view.SceneAll;
import com.hamza.account.view.barcode.PrintBarcodeApp;
import com.hamza.account.view.barcode.PrintBarcodeModel;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.table.Columns;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import com.hamza.controlsfx.table.columnEdit.TableColumnEdite;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Pagination;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.TreeView;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * The items list.
 * <p>
 * A coordinator rather than a screen full of logic. What used to live here has been moved
 * to where it can be reasoned about and, in most cases, tested:
 * <ul>
 *   <li>what may narrow the list, and what that means in SQL - {@code ItemCatalogFilter} and
 *       {@code ItemCatalogSql}, both covered by {@code ItemCatalogSqlTest};</li>
 *   <li>what an in-table edit will accept - {@link ItemQuickEditField}, covered by its own
 *       test, and stated to agree with {@code ItemsService} rather than to almost agree;</li>
 *   <li>the four affordances that narrow the list - {@link ItemsFilterBar}, which holds one
 *       filter so a chip and the panel cannot show different answers;</li>
 *   <li>the group tree, which reads the database - {@link ItemsGroupTreePane}, off the
 *       JavaFX thread, which it was not;</li>
 *   <li>the spreadsheet export - {@link ItemsExcelExport}, which now writes each value under
 *       its own heading.</li>
 * </ul>
 * <p>
 * <b>Columns are held by reference, never by index.</b> Every index in the old version was
 * counted by hand against a list that four different calls insert into, and one of them was
 * wrong: the buy-price column's permission check hid the units column instead, so a user
 * without {@code SHOW_COLUMN_BUY_PRICE} saw every cost in the catalogue.
 */
@FxmlPath(pathFile = "items/items-view.fxml")
public class ItemsController extends LoadData {

    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final TableView<ItemsModel> tableView = new TableView<>();
    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final MainGroupService mainGroupService = ServiceRegistry.get(MainGroupService.class);
    private final SupGroupService supGroupService = ServiceRegistry.get(SupGroupService.class);
    private final SelPriceItemService selPriceService = ServiceRegistry.get(SelPriceItemService.class);

    @FXML
    private Button btnNew, btnUpdate, btnDelete, btnRefresh, btnReports;
    @FXML
    private Button btnApplyFilter, btnClearFilter, btnSaveFilter, btnDeleteFilter;
    @FXML
    private MenuItem menuPrint, menuPrintBarcode, menuPrintMenu, menuItemCard, menuItemConvertGroup, menuItemBulkEdit, menuExportExcel;
    @FXML
    private TextField txtSearch, txtMinPrice, txtMaxPrice;
    @FXML
    private StackPane stackPane;
    @FXML
    private ToggleButton btnSelected, btnQuickEdit, btnGroupTree, btnGroupedView, btnFilters;
    @FXML
    private MenuButton menuButtonOther, menuButtonPrint;
    @FXML
    private Pagination pagination;
    @FXML
    private TreeView<ItemsGroupTreePane.GroupNode> groupTree;
    @FXML
    private TreeTableView<GroupedItemRow> groupedTreeTable;
    @FXML
    private VBox groupTreePane, filterPane;
    @FXML
    private FlowPane chipBar;
    @FXML
    private Label labelCount, labelSelected, labelFiltered;
    @FXML
    private ComboBox<ItemCatalogFilter.SearchScope> comboSearchScope;
    @FXML
    private ComboBox<ItemCatalogFilter.MatchMode> comboMatchMode;
    @FXML
    private ComboBox<ItemsFilterBar.GroupChoice> comboFilterGroup;
    @FXML
    private ComboBox<ItemCatalogFilter.Tristate> comboFilterActive, comboFilterBarcode, comboFilterExpiry;
    @FXML
    private ComboBox<ItemCatalogFilter.BalanceRule> comboFilterBalance;
    @FXML
    private ComboBox<ItemCatalogFilter.UsageRule> comboFilterUsage;
    @FXML
    private ComboBox<String> comboSavedFilters;

    private PaginationTableSetting paginationTableSetting;
    private ItemsFilterBar filterBar;
    private ItemsGroupTreePane groupTreeLoader;

    /**
     * The columns anything else refers to.
     * <p>
     * Held rather than looked up by position: the units column is inserted at 3, the picture
     * column is appended, and the selection column is prepended - so the index of any given
     * column depends on the order three unrelated calls happen to run in.
     */
    private TableColumn<ItemsModel, String> colBarcode;
    private TableColumn<ItemsModel, String> colName;
    /**
     * {@code Number}, not {@code Double}: {@code Columns.number} builds the column over
     * {@code Number} and the editor casts to {@code Double} where it is wired. Declaring
     * these as {@code Double} would only move the same unchecked cast into this file.
     */
    private TableColumn<ItemsModel, Number> colBuyPrice;
    private TableColumn<ItemsModel, Number> colSelPrice1;
    private TableColumn<ItemsModel, Number> colSelPrice2;
    private TableColumn<ItemsModel, Number> colSelPrice3;

    public ItemsController(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
    }

    public void initialize() {
        buildTable();
        configureGroupedView();
        // Constructed first, and started last. Everything below can hand it a filter the
        // moment it is wired - a combo selecting its first entry is enough - and a callback
        // reaching a field that is still null is a screen that does not open.
        paginationTableSetting = new PaginationTableSetting(tableView, itemsService, txtSearch, pagination);

        setUpFilterBar();
        setUpGroupTree();
        setUpButtons();
        setUpEvents();
        bindStatusLabels();

        paginationTableSetting.initializePagination();
        // The groups are read here rather than when the tree is first shown: the filter
        // panel's group combo needs the same two lists, and the tree is hidden by default,
        // so waiting for it would leave that combo empty for anyone who never opens it.
        groupTreeLoader.loadOnce();

        // The tab can be opened again and again; the publishers live as long as the
        // main screen, so what this instance registered has to go with its tab.
        subscriptions.disposeWith(stackPane);
    }

    // ---------------------------------------------------------------------------
    // The table
    // ---------------------------------------------------------------------------

    private void buildTable() {
        colBarcode = Columns.text(NamesTables.STRING, ItemsModel::getBarcode);
        colName = Columns.text(NamesTables.NAME_ITEM, ItemsModel::getNameItem);
        colBuyPrice = Columns.number(NamesTables.BUY_PRICE, ItemsModel::getBuyPrice);
        colSelPrice1 = Columns.number(NamesTables.SEL_PRICE, ItemsModel::getSelPrice1);
        colSelPrice2 = Columns.number(NamesTables.SEL_PRICE + "2", ItemsModel::getSelPrice2);
        colSelPrice3 = Columns.number(NamesTables.SEL_PRICE + "3", ItemsModel::getSelPrice3);

        tableView.getColumns().addAll(
                Columns.number(NamesTables.CODE, ItemsModel::getId),
                colBarcode,
                colName,
                colBuyPrice,
                colSelPrice1,
                colSelPrice2,
                colSelPrice3,
                Columns.number(NamesTables.MINI_QUANTITY, ItemsModel::getMini_quantity),
                Columns.number(NamesTables.FIRST_BALANCE, ItemsModel::getFirstBalanceForStock),
                Columns.number(NamesTables.SUM_ALL_BALANCE, ItemsModel::getSumAllBalance)
        );
        tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tableView.setPlaceholder(new Label(LanguageManager.getInstance().getString("item.table.empty")));

        new ColumnImage(tableView, itemsService).addColumnImage();
        tableView.getColumns().add(3, unitColumn());

        // The cost of goods is a figure a business hides from most of its staff, so the
        // column is REMOVED rather than made invisible. Invisible is not hidden here: the
        // table's own menu button offers every column back, and TableSetting persists that
        // choice - so a user without the permission could restore the column and keep it.
        if (!AuthorizationGuard.isGranted(AppPermissions.SHOW_COLUMN_BUY_PRICE)) {
            tableView.getColumns().remove(colBuyPrice);
        }

        setUpQuickEdit();
        tableView.setEditable(false);

        ColumnSetting.addSelectedColumn(tableView);
        applyColumnWidths();
        // An id per column, so the remembered widths and visibility survive a column being
        // added, removed or moved - the preferences are keyed by index when there is no id,
        // and removing the buy-price column would otherwise shift every remembered setting.
        nameColumnsForPreferences();
        TableSetting.tableMenuSetting(getClass(), tableView);

        subscribePriceNames();
    }

    /**
     * How wide each column starts out, with the item name by far the widest.
     * <p>
     * A code is six digits, a price is eight characters and a unit is one word - but a name
     * is however long the business made it, and it is the column the operator is actually
     * reading. Left to itself the table divides its width equally, which truncates the one
     * column that identifies the row while leaving whitespace beside the numbers.
     * <p>
     * <b>Set before {@code TableSetting.tableMenuSetting}, deliberately.</b> That method
     * restores a saved width per column and falls back to {@code getPrefWidth()} when there
     * is none - so these are the defaults a first run gets, and an operator who drags a
     * column still keeps their own choice afterwards.
     */
    private void applyColumnWidths() {
        for (TableColumn<ItemsModel, ?> column : tableView.getColumns()) {
            column.setMinWidth(60);
        }
        colName.setPrefWidth(300);
        colName.setMinWidth(180);
        colBarcode.setPrefWidth(140);
        colBuyPrice.setPrefWidth(95);
        colSelPrice1.setPrefWidth(95);
        colSelPrice2.setPrefWidth(95);
        colSelPrice3.setPrefWidth(95);
        // Unconstrained, deliberately. A constrained policy recomputes every column's width on
        // each layout, which would both override the widths above and make TableSetting's saved
        // widths meaningless - the operator drags a column, it is stored, and the policy
        // overwrites it on the next layout. Thirteen columns are wider than most windows, so a
        // horizontal scrollbar is the honest outcome rather than thirteen equal slivers.
        tableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    }

    private void nameColumnsForPreferences() {
        for (TableColumn<ItemsModel, ?> column : tableView.getColumns()) {
            if (column.getId() == null || column.getId().isBlank()) {
                column.setId("items_" + (column.getText() == null ? "col" : column.getText()));
            }
        }
    }

    private TableColumn<ItemsModel, String> unitColumn() {
        TableColumn<ItemsModel, String> column =
                new TableColumn<>(LanguageManager.getInstance().getString("item.column.unit"));
        column.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getUnitsType().getUnit_name()));
        return column;
    }

    /**
     * The three sale-price columns are named by the business, and renamed from the settings
     * screen while this one is open.
     * <p>
     * By reference, not by index. The old version wrote the three names into columns 6, 7
     * and 8 - which happened to be right, and stayed right only for as long as nothing else
     * inserted a column.
     */
    private void subscribePriceNames() {
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(SelPriceNamesChanged.class,
                    event -> Platform.runLater(() -> applyPriceNames(event.names()))));
        }
        try {
            applyPriceNames(selPriceService.getIntegerStringHashMap());
        } catch (DaoException e) {
            reportError(e);
        }
    }

    private void applyPriceNames(Map<Integer, String> names) {
        if (names == null) return;
        if (names.get(1) != null) colSelPrice1.setText(names.get(1));
        if (names.get(2) != null) colSelPrice2.setText(names.get(2));
        if (names.get(3) != null) colSelPrice3.setText(names.get(3));
    }

    // ---------------------------------------------------------------------------
    // Editing in the table
    // ---------------------------------------------------------------------------

    private void setUpQuickEdit() {
        tableView.getSelectionModel().setCellSelectionEnabled(true);
        editableText(colBarcode, ItemsModel::setBarcode, ItemsModel::getBarcode);
        editableText(colName, ItemsModel::setNameItem, ItemsModel::getNameItem);
        editableNumber(colBuyPrice, ItemQuickEditField.BUY_PRICE);
        editableNumber(colSelPrice1, ItemQuickEditField.SELL_PRICE_1);
        editableNumber(colSelPrice2, ItemQuickEditField.SELL_PRICE_2);
        editableNumber(colSelPrice3, ItemQuickEditField.SELL_PRICE_3);
    }

    /**
     * Wires one numeric column to one field of {@link ItemQuickEditField}.
     * <p>
     * This is what a sixty-line chain of {@code if ("buy_price".equals(fieldType))} became.
     * The field knows how to read itself, write itself and refuse a value; all that is left
     * here is putting the old value back and saying why, which is the same three lines for
     * every column.
     */
    private void editableNumber(TableColumn<ItemsModel, Number> column, ItemQuickEditField field) {
        int index = tableView.getColumns().indexOf(column);
        if (index < 0) return;   // the column was removed by a permission
        TableColumnEdite<ItemsModel, Double> handler = event -> {
            ItemsModel item = event.getTableView().getItems().get(event.getTablePosition().getRow());
            Double typed = event.getNewValue();
            if (typed == null) return;

            ItemQuickEditField.Rejection rejection = field.reject(item, typed);
            if (rejection != null) {
                refuse(rejection);
                event.getTableView().refresh();
                return;
            }
            double previous = field.read(item);
            field.write(item, typed);
            try {
                saveAndAdvance(item);
            } catch (DaoException e) {
                field.write(item, previous);
                event.getTableView().refresh();
                throw e;
            }
        };
        new ColumnSetting().enableDoubleEditing(index, handler, tableView);
    }

    private void refuse(ItemQuickEditField.Rejection rejection) {
        LanguageManager language = LanguageManager.getInstance();
        String message = switch (rejection) {
            case OUT_OF_RANGE -> language.getString("item.error.value.out.of.range");
            case SELL_NOT_ABOVE_BUY -> language.getString("item.error.sell.not.above.buy");
        };
        AllAlerts.handleError(language.getString("item.dialog.price.title"),
                new UserValidationException(message));
    }

    private void editableText(TableColumn<ItemsModel, String> column,
                              java.util.function.BiConsumer<ItemsModel, String> writer,
                              Function<ItemsModel, String> reader) {
        column.setCellFactory(ignored -> EditCell.createStringEditCell());
        column.setOnEditCommit(event -> {
            ItemsModel item = event.getTableView().getItems().get(event.getTablePosition().getRow());
            String typed = event.getNewValue();
            if (typed == null) return;
            String previous = reader.apply(item);
            writer.accept(item, typed);
            try {
                saveAndAdvance(item);
            } catch (DaoException e) {
                writer.accept(item, previous);
                event.getTableView().refresh();
                reportEditFailure(column, e);
            }
        });
    }

    private void reportEditFailure(TableColumn<ItemsModel, ?> column, DaoException e) {
        String title = LanguageManager.getInstance().getString("item.dialog.update.column", column.getText());
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (message.contains("duplicate")) {
            AllAlerts.handleError(title,
                    new UserValidationException(LanguageManager.getInstance().getString("msg.duplicate")));
        } else {
            AllAlerts.handleError(title, e);
        }
    }

    /**
     * Saves one in-table edit and steps down to the next row.
     * <p>
     * Through {@code quickUpdate}, never through the full item update: a row in this list
     * was mapped for display and carries neither the item's units nor its extra barcodes, and
     * the full update replaces both from the model - so saving a catalog row through it
     * deletes every unit the item had.
     */
    private void saveAndAdvance(ItemsModel item) throws DaoException {
        item.setUsers(CurrentUser.get());
        if (itemsService.quickUpdate(item) >= 0) {
            if (eventBus != null) eventBus.publish(new ItemSaved(item));
            tableView.refresh();
            tableView.requestFocus();
            tableView.getSelectionModel().selectNext();
        }
    }

    // ---------------------------------------------------------------------------
    // Filtering
    // ---------------------------------------------------------------------------

    private void setUpFilterBar() {
        filterBar = new ItemsFilterBar(comboSearchScope, comboMatchMode, comboFilterGroup, comboFilterActive,
                comboFilterBarcode, comboFilterExpiry, comboFilterBalance, comboFilterUsage,
                txtMinPrice, txtMaxPrice, comboSavedFilters, chipBar, filterPane, btnFilters,
                labelFiltered, filter -> paginationTableSetting.setFilter(filter));
        filterBar.initialize();
        filterBar.wireButtons(btnApplyFilter, btnClearFilter, btnSaveFilter, btnDeleteFilter);
        // The filter bar owns every condition, and the debounced search box owns the text -
        // so the bar is told what was typed without being asked to re-run anything.
        txtSearch.textProperty().addListener((observable, old, typed) ->
                filterBar.noteSearchText(typed == null ? "" : typed.trim()));
        // One-touch rule 9. A barcode scanner ends its read with an Enter, and on this
        // screen the read is a search: the Enter puts the operator on the row the scan
        // found, ready for F2 or a double click, instead of leaving the caret in the box.
        whenEnterPressed(txtSearch, tableView);
    }

    private void setUpGroupTree() {
        groupTreeLoader = new ItemsGroupTreePane(groupTree, mainGroupService, supGroupService,
                (mainId, subId) -> filterBar.selectGroup(mainId, subId),
                this::onGroupsLoaded);
        groupTreeLoader.initialize();
    }

    /**
     * The groups, read once by the tree and handed to the filter panel's combo.
     * <p>
     * Once. Both need the same two lists, and reading them twice is the query the paging was
     * written to avoid, run every time this screen opens.
     */
    private void onGroupsLoaded(List<MainGroups> mainGroups, List<SubGroups> subGroups) {
        filterBar.setGroups(mainGroups, subGroups);
    }

    // ---------------------------------------------------------------------------
    // The status line
    // ---------------------------------------------------------------------------

    /**
     * How many items the current filter matches, and how many rows are ticked.
     * <p>
     * The count is the total in the database, not the fifty on this page - the screen used
     * to say nothing at all, so an operator narrowing a list had no way to tell an empty
     * result from a slow one.
     */
    private void bindStatusLabels() {
        paginationTableSetting.totalRowsProperty().addListener((observable, old, total) ->
                labelCount.setText(LanguageManager.getInstance().getString("item.status.total", total)));
        labelCount.setText(LanguageManager.getInstance().getString("item.status.total", 0));
        updateSelectedLabel();
    }

    private void updateSelectedLabel() {
        long selected = tableView.getItems().stream().filter(ItemsModel::isSelectedRow).count();
        labelSelected.setText(selected == 0 ? ""
                : LanguageManager.getInstance().getString("item.status.selected", selected));
    }

    // ---------------------------------------------------------------------------
    // Buttons, keys and events
    // ---------------------------------------------------------------------------

    private void setUpButtons() {
        var permissions = new DisableButtons.PermissionDisableService();
        permissions.applyPermissionBasedDisable(btnNew::setDisable, AppPermissions.ITEMS_CREATE);
        permissions.applyPermissionBasedDisable(btnUpdate::setDisable, AppPermissions.ITEMS_UPDATE);
        permissions.applyPermissionBasedDisable(btnDelete::setDisable, AppPermissions.ITEMS_DELETE);
        permissions.applyPermissionBasedDisable(btnQuickEdit::setDisable, AppPermissions.ITEMS_UPDATE);

        btnNew.setGraphic(AppIcon.ADD.graphic(16));
        btnUpdate.setGraphic(AppIcon.EDIT.graphic(16));
        btnDelete.setGraphic(AppIcon.DELETE.graphic(16));
        btnRefresh.setGraphic(AppIcon.REFRESH.graphic(16));
        btnReports.setGraphic(AppIcon.REPORT.graphic(16));
        btnFilters.setGraphic(AppIcon.FILTER.graphic(16));
        btnGroupTree.setGraphic(AppIcon.TREE.graphic(14));
        btnGroupedView.setGraphic(AppIcon.MAIN_GROUP.graphic(14));
        btnQuickEdit.setGraphic(AppIcon.EDIT.graphic(14));
        btnSelected.setGraphic(AppIcon.SELECT_ALL.graphic(14));
        menuButtonPrint.setGraphic(AppIcon.PRINT.graphic(16));
        menuButtonOther.setGraphic(AppIcon.SETTINGS.graphic(16));

        tip(btnNew, "item.tooltip.new");
        tip(btnUpdate, "item.tooltip.update");
        tip(btnDelete, "item.tooltip.delete");
        tip(btnRefresh, "item.tooltip.refresh");
        tip(btnGroupTree, "item.tooltip.group.tree");
        tip(btnGroupedView, "item.tooltip.grouped.view");
        tip(btnQuickEdit, "item.tooltip.quick.edit");
        tip(btnSelected, "item.tooltip.select");
        tip(btnFilters, "item.tooltip.filters");
        tip(btnReports, "item.tooltip.reports");

        menuExportExcel.setOnAction(event -> exportToExcel());
        menuItemCard.setOnAction(event -> openCard());
        menuItemConvertGroup.setOnAction(event -> convertGroups());
        menuItemBulkEdit.setOnAction(event -> bulkEdit());
        menuPrint.setOnAction(event -> printReports.printItems(selectedItems()));
        menuPrintMenu.setOnAction(event -> printReports.printItemsBarcode(selectedItems()));
        menuPrintBarcode.setOnAction(event -> printBarcodes());

        btnNew.setOnAction(event -> openItemEditor(0));
        btnUpdate.setOnAction(event -> editSelected());
        btnDelete.setOnAction(event -> delete());
        btnRefresh.setOnAction(event -> paginationTableSetting.reload());
        btnReports.setOnAction(event -> openReports());
        btnQuickEdit.setOnAction(event -> tableView.setEditable(btnQuickEdit.isSelected()
                && AuthorizationGuard.isGranted(AppPermissions.ITEMS_UPDATE)));
        btnGroupTree.setOnAction(event -> setGroupTreeVisible(btnGroupTree.isSelected()));
        btnGroupedView.setOnAction(event -> setGroupedView(btnGroupedView.isSelected()));

        new SelectedButton(btnSelected) {
            @Override
            public void clearSelection(boolean selected) {
                for (ItemsModel item : tableView.getItems()) {
                    item.setSelectedRow(selected);
                }
                updateSelectedLabel();
            }
        };

        tableView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE) btnDelete.fire();
        });
        tableView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) menuItemCard.fire();
            updateSelectedLabel();
        });
        installAccelerators();
    }

    /**
     * The three commands that get used all day, on the keys every catalogue screen uses for
     * them. Installed on the scene once it exists - a control has no scene while the FXML is
     * still being loaded.
     */
    private void installAccelerators() {
        stackPane.sceneProperty().addListener((observable, old, scene) -> {
            if (scene == null) return;
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.INSERT), () -> {
                if (!btnNew.isDisabled()) btnNew.fire();
            });
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F2), () -> {
                if (!btnUpdate.isDisabled()) btnUpdate.fire();
            });
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F5), btnRefresh::fire);
        });
    }

    private static void tip(javafx.scene.control.Control control, String key) {
        control.setTooltip(new Tooltip(LanguageManager.getInstance().getString(key)));
    }

    private void setUpEvents() {
        if (eventBus == null) return;
        subscriptions.add(eventBus.subscribe(GroupsChanged.class, event -> {
            groupTreeLoader.reload();
            if (btnGroupedView.isSelected()) loadGroupedView();
        }));
        subscriptions.add(eventBus.subscribe(ItemSaved.class, event -> itemSaved(event.item())));
        subscriptions.add(eventBus.subscribe(ItemsChanged.class, event -> refreshCurrentView()));
    }

    private void setGroupTreeVisible(boolean visible) {
        groupTreePane.setVisible(visible);
        groupTreePane.setManaged(visible);
        groupTreeLoader.loadOnce();
    }

    private void setGroupedView(boolean grouped) {
        pagination.setVisible(!grouped);
        pagination.setManaged(!grouped);
        groupedTreeTable.setVisible(grouped);
        groupedTreeTable.setManaged(grouped);
        btnQuickEdit.setDisable(grouped || !AuthorizationGuard.isGranted(AppPermissions.ITEMS_UPDATE));
        tableView.setEditable(!grouped && btnQuickEdit.isSelected());
        if (grouped) loadGroupedView();
    }

    private void refreshCurrentView() {
        if (btnGroupedView.isSelected()) loadGroupedView();
        else paginationTableSetting.reload();
    }

    /**
     * One item was saved, so one row is re-read - the page, the filter, the scroll position
     * and the selection all stay where the operator left them.
     * <p>
     * This used to reload the whole view, which sent the table back to the first page:
     * editing the fifth item meant finding the sixth again from the top, and doing it
     * once per item. The grouped view has no page to lose and is rebuilt as before.
     */
    private void itemSaved(ItemsModel saved) {
        if (btnGroupedView.isSelected()) loadGroupedView();
        else if (saved != null) paginationTableSetting.refreshRow(saved.getId());
        else paginationTableSetting.reload();
    }

    // ---------------------------------------------------------------------------
    // The grouped view
    // ---------------------------------------------------------------------------

    private void configureGroupedView() {
        groupedTreeTable.setShowRoot(false);
        groupedTreeTable.setEditable(false);
        groupedTreeTable.setPlaceholder(new Label(LanguageManager.getInstance().getString("item.table.empty")));
        groupedTreeTable.setRowFactory(view -> new TreeTableRow<>() {
            @Override
            protected void updateItem(GroupedItemRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("items-grouped-main-row", "items-grouped-sub-row",
                        "items-grouped-item-row");
                if (empty || item == null) return;
                getStyleClass().add(switch (item.depth()) {
                    case 0 -> "items-grouped-main-row";
                    case 1 -> "items-grouped-sub-row";
                    default -> "items-grouped-item-row";
                });
            }
        });
        groupedTreeTable.getColumns().setAll(
                groupedColumn(NamesTables.CODE, GroupedItemRow::code),
                groupedColumn(NamesTables.STRING, GroupedItemRow::barcode),
                groupedColumn(NamesTables.NAME_ITEM, GroupedItemRow::name),
                groupedColumn(NamesTables.BUY_PRICE, GroupedItemRow::buyPrice),
                groupedColumn(NamesTables.SEL_PRICE, GroupedItemRow::sellPrice));
    }

    private TreeTableColumn<GroupedItemRow, String> groupedColumn(String title,
                                                                  Function<GroupedItemRow, String> value) {
        TreeTableColumn<GroupedItemRow, String> column = new TreeTableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue().getValue())));
        return column;
    }

    private void loadGroupedView() {
        Task<GroupedCatalog> task = new Task<>() {
            @Override
            protected GroupedCatalog call() throws Exception {
                return new GroupedCatalog(mainGroupService.getMainGroupList(),
                        supGroupService.getSubGroupsList(), itemsService.getAllCatalogProducts());
            }
        };
        task.setOnSucceeded(event -> {
            if (btnGroupedView.isSelected()) buildGroupedTree(task.getValue());
        });
        task.setOnFailed(event -> reportError(new Exception(task.getException())));
        Thread thread = new Thread(task, "items-grouped-catalog");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Builds the tree of groups and the items under them.
     * <p>
     * <b>An item whose group was deleted goes under a heading of its own rather than being
     * dropped.</b> It used to be skipped in silence - the item was in the database, absent
     * from this view, and nothing on the screen said why - which is the hardest kind of
     * missing row to notice, because the screen looks complete.
     */
    private void buildGroupedTree(GroupedCatalog catalog) {
        TreeItem<GroupedItemRow> root = new TreeItem<>(GroupedItemRow.group("", 0));
        Map<Integer, TreeItem<GroupedItemRow>> mainNodes = new LinkedHashMap<>();
        Map<Integer, TreeItem<GroupedItemRow>> subNodes = new LinkedHashMap<>();

        for (MainGroups main : catalog.mainGroups()) {
            TreeItem<GroupedItemRow> node = new TreeItem<>(GroupedItemRow.group(main.getName(), 0));
            node.setGraphic(ItemsGroupTreePane.icon(AppIcon.MAIN_GROUP, "items-main-group-icon"));
            node.setExpanded(true);
            root.getChildren().add(node);
            mainNodes.put(main.getId(), node);
        }
        for (SubGroups sub : catalog.subGroups()) {
            if (sub.getMainGroups() == null) continue;
            TreeItem<GroupedItemRow> mainNode = mainNodes.get(sub.getMainGroups().getId());
            if (mainNode == null) continue;
            TreeItem<GroupedItemRow> node = new TreeItem<>(GroupedItemRow.group(sub.getName(), 1));
            node.setGraphic(ItemsGroupTreePane.icon(AppIcon.SUB_GROUP, "items-sub-group-icon"));
            node.setExpanded(true);
            mainNode.getChildren().add(node);
            subNodes.put(sub.getId(), node);
        }

        TreeItem<GroupedItemRow> orphans = null;
        for (ItemsModel item : catalog.items()) {
            TreeItem<GroupedItemRow> parent = parentFor(item, mainNodes, subNodes);
            if (parent == null) {
                if (orphans == null) {
                    orphans = new TreeItem<>(GroupedItemRow.group(
                            LanguageManager.getInstance().getString("item.group.tree.none"), 0));
                    orphans.setGraphic(ItemsGroupTreePane.icon(AppIcon.WARNING, "items-main-group-icon"));
                    orphans.setExpanded(true);
                }
                parent = orphans;
            }
            TreeItem<GroupedItemRow> leaf = new TreeItem<>(GroupedItemRow.item(item));
            leaf.setGraphic(ItemsGroupTreePane.icon(AppIcon.ITEM, "items-leaf-icon"));
            parent.getChildren().add(leaf);
        }
        if (orphans != null) root.getChildren().add(orphans);

        groupedTreeTable.setRoot(root);
    }

    private static TreeItem<GroupedItemRow> parentFor(ItemsModel item,
                                                      Map<Integer, TreeItem<GroupedItemRow>> mainNodes,
                                                      Map<Integer, TreeItem<GroupedItemRow>> subNodes) {
        if (item.getSubGroups() == null) return null;
        TreeItem<GroupedItemRow> node = subNodes.get(item.getSubGroups().getId());
        if (node != null) return node;
        return item.getSubGroups().getMainGroups() == null
                ? null : mainNodes.get(item.getSubGroups().getMainGroups().getId());
    }

    private record GroupedCatalog(List<MainGroups> mainGroups, List<SubGroups> subGroups,
                                  List<ItemsModel> items) {
    }

    /** A row of the grouped view. {@code depth} is what the row factory styles it by. */
    private record GroupedItemRow(String code, String barcode, String name, String buyPrice,
                                  String sellPrice, int depth) {
        static GroupedItemRow group(String name, int depth) {
            return new GroupedItemRow("", "", name, "", "", depth);
        }

        static GroupedItemRow item(ItemsModel item) {
            return new GroupedItemRow(String.valueOf(item.getId()), item.getBarcode(), item.getNameItem(),
                    String.valueOf(item.getBuyPrice()), String.valueOf(item.getSelPrice1()), 2);
        }
    }

    // ---------------------------------------------------------------------------
    // Commands
    // ---------------------------------------------------------------------------

    private void editSelected() {
        ItemsModel selected = requireSelection();
        if (selected != null) openItemEditor(selected.getId());
    }

    private void openItemEditor(int itemId) {
        try {
            new AddItemApplication(itemId).start(new Stage());
        } catch (Exception e) {
            reportError(e);
        }
    }

    private void openCard() {
        ItemsModel selected = requireSelection();
        if (selected == null) return;
        try {
            new CardApplication(selected, daoFactory, dataPublisher).start(new Stage());
        } catch (Exception e) {
            reportError(e);
        }
    }

    /**
     * Opens the reports on the rows the operator is looking at, not on the whole catalogue.
     * A report that quietly widened the question would be answering about items the screen
     * behind it is not showing.
     */
    private void openReports() {
        try {
            new ItemReportsApplication(filterBar.filter()).start(new Stage());
        } catch (Exception e) {
            reportError(e);
        }
    }

    private void delete() {
        ItemsModel selected = requireSelection();
        if (selected == null) return;
        if (!AllAlerts.confirmDelete()) return;
        try {
            if (itemsService.deleteItem(selected.getId()) >= 1) {
                AllAlerts.alertDelete();
                paginationTableSetting.reload();
            }
        } catch (DaoException e) {
            reportError(e);
        }
    }

    private void convertGroups() {
        List<ItemsModel> selected = selectedItems();
        try {
            var controller = new ItemGroupManagerController(
                    selected.stream().map(ItemsModel::getId).toList());
            Scene scene = new Scene(new com.hamza.account.openFxml.OpenFxmlApplication(controller).getPane(),
                    1050, 720);
            ThemeManager.apply(scene);
            Stage stage = new Stage();
            stage.setTitle(LanguageManager.getInstance().getString("item.group.manager.title"));
            stage.setScene(scene);
            stage.setMinWidth(820);
            stage.setMinHeight(600);
            stage.show();
        } catch (Exception e) {
            reportError(e);
        }
    }

    /**
     * The bulk editor is a menu entry of its own, not the group screen. It changes prices,
     * the active flag, the picture, the minimum and the opening balance as well as the group,
     * and it lost its only way in when "تحويل المجموعات" was repointed at
     * {@link ItemGroupManagerController} - which moves items between groups and nothing else.
     */
    private void bulkEdit() {
        List<ItemsModel> selected = selectedItems();
        if (selected.isEmpty()) {
            requireTickedRows("item.menu.bulk.edit");
            return;
        }
        try {
            Stage stage = new Stage();
            stage.setScene(new SceneAll(new com.hamza.account.openFxml.OpenFxmlApplication(
                    new UpdateSomeItems(selected)).getPane()));
            stage.setTitle(LanguageManager.getInstance().getString("item.menu.bulk.edit"));
            stage.setResizable(false);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();
        } catch (Exception e) {
            reportError(e);
        }
    }

    private void printBarcodes() {
        List<ItemsModel> selected = selectedItems();
        if (selected.isEmpty()) {
            requireTickedRows("item.dialog.barcode.title");
            return;
        }
        try {
            ObservableList<PrintBarcodeModel> models = FXCollections.observableArrayList();
            for (ItemsModel item : selected) {
                models.add(new PrintBarcodeModel(item.getBarcode(), item.getNameItem(), item.getSelPrice1()));
            }
            new PrintBarcodeApp(models);
        } catch (Exception e) {
            reportError(e);
        }
    }

    private void exportToExcel() {
        ItemsExcelExport.export(tableView, "Items", "Items.xlsx",
                ItemsModel::getItem_image, LanguageManager.getInstance().getString("item.image"));
    }

    /** The rows the operator ticked. One definition, used by print, barcodes and grouping. */
    private List<ItemsModel> selectedItems() {
        List<ItemsModel> selected = new ArrayList<>();
        for (ItemsModel item : tableView.getItems()) {
            if (item.isSelectedRow()) selected.add(item);
        }
        return selected;
    }

    private ItemsModel requireSelection() {
        ItemsModel selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            LanguageManager language = LanguageManager.getInstance();
            AllAlerts.handleError(language.getString("item.dialog.action.title"),
                    new UserValidationException(language.getString("msg.select.row")));
        }
        return selected;
    }

    private void requireTickedRows(String titleKey) {
        LanguageManager language = LanguageManager.getInstance();
        AllAlerts.handleError(language.getString(titleKey),
                new UserValidationException(language.getString("msg.insert.all")));
    }

    /** Shows a failure to the operator. It does not log - {@code ErrorReporter} does that. */
    private void reportError(Exception e) {
        AllAlerts.handleError(LanguageManager.getInstance().getString("item.dialog.manage.title"), e);
    }
}
