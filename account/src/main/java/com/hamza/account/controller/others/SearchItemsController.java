package com.hamza.account.controller.others;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.NamesTables;
import com.hamza.account.config.UiScale;
import com.hamza.account.controller.items.ColumnImage;
import com.hamza.account.controller.items.PaginationTableSetting;
import com.hamza.account.document.InvoiceBuy;
import com.hamza.account.features.invoice.ItemPickRequest;
import com.hamza.account.features.invoice.ItemPickerSelection;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.ItemsService;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/** Item catalog picker used by all four invoice document families. */
@FxmlPath(pathFile = "search-view.fxml")
public class SearchItemsController implements Initializable {

    private final DataInterface<?, ?, ?, ?> dataInterface;
    private final InvoiceBuy<?, ?, ?, ?> invoiceBuy;
    private final int priceTier;
    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final TableView<ItemsModel> tableItems = new TableView<>();
    private final ItemPickerSelection selection = new ItemPickerSelection();
    private final ObservableList<ItemPickRequest> selectedRows = FXCollections.observableArrayList();
    private List<ItemPickRequest> result;

    @FXML
    private StackPane stackPane;
    @FXML
    private Label headerIcon;
    @FXML
    private Label searchIcon;
    @FXML
    private Label labelCount;
    @FXML
    private Label labelResultsCount;
    @FXML
    private TextField txtSearch;
    @FXML
    private Pagination pagination;
    @FXML
    private TitledPane selectedPane;
    @FXML
    private TableView<ItemPickRequest> tableSelected;
    @FXML
    private Button btnClearSelection;
    @FXML
    private Button btnClose;
    @FXML
    private Button btnSave;

    public SearchItemsController(DataInterface<?, ?, ?, ?> dataInterface, int priceTier) {
        this.dataInterface = dataInterface;
        this.invoiceBuy = dataInterface.invoiceBuy();
        this.priceTier = Math.max(1, priceTier);
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        configureIcons();
        configureResultsTable();
        configureSelectedTable();
        configureActions();

        PaginationTableSetting paging = new PaginationTableSetting(
                tableItems, itemsService, txtSearch, pagination);
        paging.totalRowsProperty().addListener((observable, oldCount, newCount) ->
                updateResultsCount(newCount.intValue()));
        paging.initializePagination();

        updateResultsCount(paging.totalRowsProperty().get());
        updateSelectionUi();
        javafx.application.Platform.runLater(txtSearch::requestFocus);
    }

    private void configureIcons() {
        int headerSize = (int) Math.round(20 * UiScale.factor());
        headerIcon.setGraphic(AppIcon.SEARCH.graphic(headerSize));
        searchIcon.setGraphic(AppIcon.SEARCH.graphic());
        selectedPane.setGraphic(AppIcon.SELECT_ALL.graphic());
        btnClearSelection.setGraphic(AppIcon.CLEAR.graphic());
        btnClose.setGraphic(AppIcon.CLOSE.graphic());
        btnSave.setGraphic(AppIcon.ADD.graphic());
    }

    private void configureResultsTable() {
        TableColumn<ItemsModel, ItemsModel> selectColumn =
                Columns.column("search.items.column.select", item -> item);
        selectColumn.setSortable(false);
        selectColumn.setPrefWidth(78);
        selectColumn.setCellFactory(column -> new SelectionCell(selection, this::updateSelectionUi));

        tableItems.getColumns().setAll(
                selectColumn,
                Columns.number(NamesTables.CODE, ItemsModel::getId),
                Columns.text(NamesTables.STRING, ItemsModel::getBarcode),
                Columns.text(NamesTables.NAME_ITEM, ItemsModel::getNameItem),
                Columns.number("search.items.column.price", this::effectivePrice),
                Columns.number(NamesTables.SUM_ALL_BALANCE, ItemsModel::getSumAllBalance)
        );
        tableItems.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableItems.setPlaceholder(new Label(text("search.items.empty")));
        tableItems.setRowFactory(table -> new SelectionRow(selection));

        new ColumnImage(tableItems, itemsService).addColumnImage();
        TableSetting.tableMenuSetting(getClass(), tableItems);

        tableItems.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                toggle(itemFromRow(event.getTarget()));
            }
        });
        tableItems.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                toggle(tableItems.getSelectionModel().getSelectedItem());
                event.consume();
            }
        });
    }

    private void configureSelectedTable() {
        TableColumn<ItemPickRequest, ItemPickRequest> removeColumn =
                Columns.column("search.items.column.remove", request -> request);
        removeColumn.setSortable(false);
        removeColumn.setPrefWidth(72);
        removeColumn.setCellFactory(column -> new RemoveCell(this::remove));

        tableSelected.getColumns().setAll(
                Columns.number(NamesTables.CODE, ItemPickRequest::itemId),
                Columns.text(NamesTables.NAME_ITEM, ItemPickRequest::itemName),
                removeColumn
        );
        tableSelected.setItems(selectedRows);
        tableSelected.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableSelected.setPlaceholder(new Label(text("search.items.selected.empty")));
        TableSetting.tableMenuSetting(getClass(), tableSelected);
    }

    private void configureActions() {
        txtSearch.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN && !tableItems.getItems().isEmpty()) {
                tableItems.getSelectionModel().selectFirst();
                tableItems.requestFocus();
                event.consume();
            }
        });
        btnClearSelection.setOnAction(event -> {
            selection.clear();
            updateSelectionUi();
        });
        btnSave.setOnAction(event -> {
            result = selection.requests();
            close();
        });
        btnClose.setOnAction(event -> close());

        stackPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                btnClose.fire();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER && event.isControlDown()
                    && !selection.isEmpty()) {
                btnSave.fire();
                event.consume();
            }
        });
    }

    private double effectivePrice(ItemsModel item) {
        if (!dataInterface.designInterface().showDataForCustomer()) {
            return item.getBuyPrice();
        }
        try {
            return invoiceBuy.getItemsPrice(item, priceTier);
        } catch (RuntimeException exception) {
            return item.getSelPrice1();
        }
    }

    private void toggle(ItemsModel item) {
        if (item == null) {
            return;
        }
        selection.toggle(item.getId(), item.getNameItem());
        updateSelectionUi();
    }

    private static ItemsModel itemFromRow(Object target) {
        if (!(target instanceof Node node)) {
            return null;
        }
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof ButtonBase) {
                return null;
            }
            if (current instanceof TableRow<?> row) {
                return row.getItem() instanceof ItemsModel item ? item : null;
            }
        }
        return null;
    }

    private void remove(ItemPickRequest request) {
        if (request == null) {
            return;
        }
        selection.deselect(request.itemId());
        updateSelectionUi();
    }

    private void updateSelectionUi() {
        selectedRows.setAll(selection.requests());
        int count = selection.size();
        labelCount.setText(text("search.items.selected.count", count));
        selectedPane.setText(text("search.items.selected.title", count));
        btnSave.setText(text("search.items.confirm", count));
        btnSave.setDisable(selection.isEmpty());
        btnClearSelection.setDisable(selection.isEmpty());
        tableItems.refresh();
    }

    private void updateResultsCount(int count) {
        labelResultsCount.setText(text("search.items.results.count", count));
    }

    private String text(String key, Object... args) {
        return LanguageManager.getInstance().getString(key, args);
    }

    private void close() {
        if (btnClose.getScene() != null && btnClose.getScene().getWindow() != null) {
            btnClose.getScene().getWindow().hide();
        }
    }

    public Optional<List<ItemPickRequest>> result() {
        return Optional.ofNullable(result).map(List::copyOf);
    }

    private static final class SelectionRow extends TableRow<ItemsModel> {

        private static final PseudoClass PICKED = PseudoClass.getPseudoClass("picked");
        private final ItemPickerSelection selection;

        private SelectionRow(ItemPickerSelection selection) {
            this.selection = selection;
        }

        @Override
        protected void updateItem(ItemsModel item, boolean empty) {
            super.updateItem(item, empty);
            pseudoClassStateChanged(PICKED,
                    !empty && item != null && selection.contains(item.getId()));
        }
    }

    private static final class SelectionCell extends TableCell<ItemsModel, ItemsModel> {

        private final ItemPickerSelection selection;
        private final Runnable changed;
        private final CheckBox checkBox = new CheckBox();
        private ItemsModel row;

        private SelectionCell(ItemPickerSelection selection, Runnable changed) {
            this.selection = selection;
            this.changed = changed;
            checkBox.getStyleClass().add("item-picker-check");
            checkBox.setOnAction(event -> {
                if (row == null) {
                    return;
                }
                if (checkBox.isSelected()) {
                    selection.select(row.getId(), row.getNameItem());
                } else {
                    selection.deselect(row.getId());
                }
                changed.run();
            });
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(ItemsModel item, boolean empty) {
            super.updateItem(item, empty);
            row = empty ? null : item;
            if (row == null) {
                setGraphic(null);
                return;
            }
            checkBox.setSelected(selection.contains(row.getId()));
            setGraphic(checkBox);
        }
    }

    private static final class RemoveCell extends TableCell<ItemPickRequest, ItemPickRequest> {

        private final Button button = new Button();
        private ItemPickRequest request;

        private RemoveCell(Consumer<ItemPickRequest> remove) {
            button.getStyleClass().addAll("icon-button", "item-picker-remove");
            button.setGraphic(AppIcon.DELETE.graphic());
            button.setTooltip(new Tooltip(LanguageManager.getInstance()
                    .getString("search.items.remove")));
            button.setOnAction(event -> remove.accept(request));
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(ItemPickRequest item, boolean empty) {
            super.updateItem(item, empty);
            request = empty ? null : item;
            setGraphic(request == null ? null : button);
        }
    }
}
