package com.hamza.account.controller.items;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.GroupsChanged;
import com.hamza.account.features.events.ItemsChanged;
import com.hamza.account.features.itemgroups.ItemGroupChange;
import com.hamza.account.features.itemgroups.ItemGroupItem;
import com.hamza.account.features.itemgroups.ItemGroupMoveCommand;
import com.hamza.account.features.itemgroups.ItemGroupMoveResult;
import com.hamza.account.features.itemgroups.ItemGroupMoveService;
import com.hamza.account.features.itemgroups.ItemGroupSummary;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/** A lazy group → subgroup → item tree with safe multi-item reassignment. */
@FxmlPath(pathFile = "items/item-group-manager.fxml")
public final class ItemGroupManagerController {

    private static final int PAGE_SIZE = 200;
    private static final DataFormat ITEM_SELECTION =
            new DataFormat("application/x-account-item-group-selection");
    private static final ExecutorService READER = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "item-group-manager-reader");
        thread.setDaemon(true);
        return thread;
    });

    private final ItemGroupMoveService service;
    private final EventBus eventBus;
    private final Subscriptions subscriptions = new Subscriptions();
    private final Set<Integer> initialSelection;
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(350));
    private final Map<Integer, ItemGroupSummary> subGroups = new LinkedHashMap<>();
    private final Map<Integer, ItemGroupSummary> allTargets = new LinkedHashMap<>();

    @FXML private StackPane root;
    @FXML private TextField txtSearch;
    @FXML private Button btnRefresh, btnExpandAll, btnCollapseAll, btnMove, btnUndo;
    @FXML private Label labelSelected, labelStatus;
    @FXML private ProgressIndicator progress;
    @FXML private TreeTableView<NodeModel> tree;

    private ItemGroupMoveCommand undoCommand;
    private String operationStatus;
    private boolean busy;
    private boolean synchronizingSelection;

    public ItemGroupManagerController() {
        this(ServiceRegistry.get(ItemGroupMoveService.class), ServiceRegistry.get(EventBus.class), List.of());
    }

    public ItemGroupManagerController(List<Integer> initiallySelectedItemIds) {
        this(ServiceRegistry.get(ItemGroupMoveService.class), ServiceRegistry.get(EventBus.class),
                initiallySelectedItemIds);
    }

    ItemGroupManagerController(ItemGroupMoveService service, EventBus eventBus,
                               List<Integer> initiallySelectedItemIds) {
        this.service = service;
        this.eventBus = eventBus;
        this.initialSelection = new LinkedHashSet<>(initiallySelectedItemIds == null
                ? List.of() : initiallySelectedItemIds);
    }

    @FXML
    public void initialize() {
        configureColumns();
        configureRows();
        configureSelection();
        configureActions();
        configureEvents();
        tree.setShowRoot(false);
        tree.setPlaceholder(new Label(text("item.group.manager.empty")));
        root.setNodeOrientation(LanguageManager.getInstance().getNodeOrientation());
        reload();
        subscriptions.disposeWith(root);
    }

    private void configureColumns() {
        TreeTableColumn<NodeModel, Boolean> selected = new TreeTableColumn<>();
        selected.setId("item_group_selected");
        selected.setPrefWidth(48);
        selected.setMinWidth(48);
        selected.setMaxWidth(48);
        selected.setSortable(false);
        selected.setCellValueFactory(cell -> cell.getValue().getValue().selectedProperty());
        selected.setCellFactory(column -> new TreeTableCell<>() {
            private final CheckBox checkBox = new CheckBox();
            {
                checkBox.setFocusTraversable(false);
                checkBox.setOnAction(event -> {
                    NodeModel row = getTreeTableRow() == null ? null : getTreeTableRow().getItem();
                    if (row == null || !row.isItem()) return;
                    if (checkBox.isSelected()) tree.getSelectionModel().select(getIndex());
                    else tree.getSelectionModel().clearSelection(getIndex());
                    syncChecksFromRowSelection();
                    event.consume();
                });
            }
            @Override protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                NodeModel row = empty || getTreeTableRow() == null ? null : getTreeTableRow().getItem();
                if (row == null || !row.isItem()) {
                    setGraphic(null);
                    return;
                }
                checkBox.setSelected(row.selectedProperty().get());
                checkBox.setDisable(!canMove());
                setGraphic(checkBox);
            }
        });

        TreeTableColumn<NodeModel, String> name = textColumn(
                "item.group.manager.column.name", node -> node.displayName());
        name.setCellFactory(column -> new NameCell());
        name.setId("item_group_name");
        name.setPrefWidth(430);
        name.getStyleClass().add("item-group-name-column");
        TreeTableColumn<NodeModel, String> barcode = textColumn(
                "item.group.manager.column.barcode", NodeModel::barcode);
        barcode.setId("item_group_barcode");
        barcode.setPrefWidth(180);
        TreeTableColumn<NodeModel, String> status = textColumn(
                "item.group.manager.column.status", node -> node.statusText());
        status.setId("item_group_status");
        status.setPrefWidth(130);
        tree.getColumns().setAll(selected, name, barcode, status);
        tree.setTreeColumn(name);
        tree.setFixedCellSize(38);
        tree.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void configureSelection() {
        tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tree.getSelectionModel().getSelectedItems().addListener(
                (ListChangeListener<TreeItem<NodeModel>>) change -> syncChecksFromRowSelection());
        tree.setTooltip(new Tooltip(text("item.group.manager.selection.hint")));
    }

    private TreeTableColumn<NodeModel, String> textColumn(String titleKey,
                                                           java.util.function.Function<NodeModel, String> value) {
        TreeTableColumn<NodeModel, String> column = new TreeTableColumn<>(text(titleKey));
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue().getValue())));
        return column;
    }

    private void configureRows() {
        tree.setRowFactory(view -> {
            TreeTableRow<NodeModel> row = new TreeTableRow<>() {
                @Override protected void updateItem(NodeModel node, boolean empty) {
                    super.updateItem(node, empty);
                    getStyleClass().removeAll("item-group-main-row", "item-group-sub-row",
                            "item-group-item-row", "item-group-more-row", "drop-valid");
                    setContextMenu(null);
                    if (empty || node == null) return;
                    getStyleClass().add(switch (node.kind()) {
                        case MAIN -> "item-group-main-row";
                        case SUB -> "item-group-sub-row";
                        case ITEM -> "item-group-item-row";
                        case MORE, LOADING -> "item-group-more-row";
                        case ROOT -> "item-group-main-row";
                    });
                    if (node.kind() == Kind.SUB) setContextMenu(moveHereMenu(node));
                }
            };
            installDragAndDrop(row);
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && row.getItem() != null
                        && row.getItem().kind() == Kind.MORE) {
                    loadMore(row.getTreeItem());
                }
            });
            return row;
        });
    }

    private ContextMenu moveHereMenu(NodeModel target) {
        MenuItem move = new MenuItem(text("item.group.manager.move.here"));
        move.setGraphic(AppIcon.ITEM.graphic(14));
        move.setDisable(!canMove());
        move.setOnAction(event -> requestMove(target.subGroupId()));
        return new ContextMenu(move);
    }

    private void installDragAndDrop(TreeTableRow<NodeModel> row) {
        row.setOnDragDetected(event -> {
            NodeModel node = row.getItem();
            if (busy || !canMove() || node == null || !node.isItem()) return;
            if (!node.selectedProperty().get()) {
                clearSelection();
                tree.getSelectionModel().select(row.getIndex());
                syncChecksFromRowSelection();
            }
            ClipboardContent content = new ClipboardContent();
            content.put(ITEM_SELECTION, selectedItems().size());
            row.startDragAndDrop(TransferMode.MOVE).setContent(content);
            event.consume();
        });
        row.setOnDragOver(event -> {
            if (!event.getDragboard().hasContent(ITEM_SELECTION) || row.getItem() == null) return;
            NodeModel node = row.getItem();
            if (node.kind() == Kind.MAIN) {
                row.getTreeItem().setExpanded(true);
            } else if (node.kind() == Kind.SUB && canMoveTo(node.subGroupId())) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            autoScroll(row.getIndex());
            event.consume();
        });
        row.setOnDragEntered(event -> {
            NodeModel node = row.getItem();
            if (node != null && node.kind() == Kind.SUB && canMoveTo(node.subGroupId())) {
                row.getStyleClass().add("drop-valid");
            }
        });
        row.setOnDragExited(event -> row.getStyleClass().remove("drop-valid"));
        row.setOnDragDropped(event -> {
            NodeModel node = row.getItem();
            boolean accepted = node != null && node.kind() == Kind.SUB && canMoveTo(node.subGroupId());
            if (accepted) requestMove(node.subGroupId());
            event.setDropCompleted(accepted);
            event.consume();
        });
    }

    private void autoScroll(int rowIndex) {
        if (rowIndex <= 2) tree.scrollTo(Math.max(0, rowIndex - 3));
        else if (rowIndex >= tree.getExpandedItemCount() - 3) {
            tree.scrollTo(Math.min(tree.getExpandedItemCount() - 1, rowIndex + 3));
        }
    }

    private void configureActions() {
        btnRefresh.setGraphic(AppIcon.REFRESH.graphic());
        btnExpandAll.setGraphic(AppIcon.TREE.graphic());
        btnCollapseAll.setGraphic(AppIcon.CLEAR.graphic());
        btnMove.setGraphic(AppIcon.ITEM.graphic());
        btnUndo.setGraphic(AppIcon.CLEAR.graphic());
        btnUndo.setDisable(true);

        btnRefresh.setOnAction(event -> reload());
        btnExpandAll.setOnAction(event -> setMainGroupsExpanded(true));
        btnCollapseAll.setOnAction(event -> collapseAllGroups());
        btnMove.setOnAction(event -> chooseTarget());
        btnUndo.setOnAction(event -> undo());
        txtSearch.textProperty().addListener((observable, old, value) -> {
            searchDelay.setOnFinished(event -> reload());
            searchDelay.playFromStart();
        });
        whenEnterPressed(txtSearch, tree);
        updateSelectedCount();
    }

    private void configureEvents() {
        if (eventBus == null) return;
        subscriptions.add(eventBus.subscribe(ItemsChanged.class, event -> reload()));
        subscriptions.add(eventBus.subscribe(GroupsChanged.class, event -> reload()));
    }

    private void reload() {
        if (busy || service == null) return;
        Set<Integer> keepSelected = selectedItemIds();
        keepSelected.addAll(initialSelection);
        String search = txtSearch.getText() == null ? "" : txtSearch.getText().trim();
        Task<CatalogLoad> task = new Task<>() {
            @Override protected CatalogLoad call() throws Exception {
                return new CatalogLoad(service.groups(search), service.itemsByIds(keepSelected));
            }
        };
        setBusy(true, "item.group.manager.status.loading");
        task.setOnSucceeded(event -> {
            setBusy(false, null);
            buildTree(task.getValue().groups(), task.getValue().selectedItems(), search, keepSelected);
        });
        task.setOnFailed(event -> {
            setBusy(false, null);
            report(task.getException());
        });
        READER.execute(task);
    }

    private void buildTree(List<ItemGroupSummary> summaries, List<ItemGroupItem> selectedItems,
                           String search, Set<Integer> keepSelected) {
        subGroups.clear();
        TreeItem<NodeModel> rootItem = new TreeItem<>(NodeModel.root());
        Map<Integer, TreeItem<NodeModel>> mainItems = new LinkedHashMap<>();
        Map<Integer, Integer> mainCounts = new LinkedHashMap<>();
        for (ItemGroupSummary summary : summaries) {
            mainCounts.merge(summary.mainGroupId(), summary.itemCount(), Integer::sum);
        }
        Map<Integer, TreeItem<NodeModel>> subItems = new LinkedHashMap<>();
        for (ItemGroupSummary summary : summaries) {
            subGroups.put(summary.subGroupId(), summary);
            TreeItem<NodeModel> main = mainItems.computeIfAbsent(summary.mainGroupId(), id -> {
                TreeItem<NodeModel> created = new TreeItem<>(NodeModel.main(
                        id, summary.mainGroupName(), mainCounts.getOrDefault(id, 0)));
                created.expandedProperty().addListener((observable, was, expanded) -> {
                    if (was && !expanded) refreshAfterCollapse();
                });
                rootItem.getChildren().add(created);
                return created;
            });
            TreeItem<NodeModel> sub = new TreeItem<>(NodeModel.sub(summary));
            if (summary.itemCount() > 0) sub.getChildren().add(new TreeItem<>(NodeModel.loadingPlaceholder()));
            sub.expandedProperty().addListener((observable, was, expanded) -> {
                if (expanded) loadFirstPage(sub, search, keepSelected);
                else if (was) refreshAfterCollapse();
            });
            main.getChildren().add(sub);
            subItems.put(summary.subGroupId(), sub);
        }
        if (search.isBlank()) {
            allTargets.clear();
            allTargets.putAll(subGroups);
        }
        for (ItemGroupItem item : selectedItems) {
            TreeItem<NodeModel> sub = subItems.get(item.subGroupId());
            if (sub == null) continue;
            NodeModel node = NodeModel.item(item);
            node.selectedProperty().set(true);
            TreeItem<NodeModel> child = new TreeItem<>(node);
            sub.getChildren().add(0, child);
            sub.getParent().setExpanded(true);
            sub.setExpanded(true);
        }
        tree.setRoot(rootItem);
        rootItem.setExpanded(true);
        mainItems.values().forEach(item -> item.setExpanded(!search.isBlank()));
        if (!search.isBlank()) {
            for (TreeItem<NodeModel> main : mainItems.values()) {
                main.setExpanded(true);
            }
        }
        Platform.runLater(this::restoreRowSelectionFromChecks);
        updateSelectedCount();
        if (operationStatus == null) {
            labelStatus.setText(text("item.group.manager.status.groups", summaries.size()));
        } else {
            labelStatus.setText(operationStatus);
            operationStatus = null;
        }
    }

    private void loadFirstPage(TreeItem<NodeModel> sub, String search, Set<Integer> keepSelected) {
        if (sub.getValue().loaded() || sub.getValue().loading()) return;
        sub.getValue().setLoading(true);
        loadItems(sub, search, 0, keepSelected);
    }

    private void loadMore(TreeItem<NodeModel> moreItem) {
        TreeItem<NodeModel> sub = moreItem.getParent();
        if (sub == null || sub.getValue().loading()) return;
        sub.getValue().setLoading(true);
        loadItems(sub, currentSearch(), moreItem.getValue().offset(), selectedItemIds());
    }

    private void loadItems(TreeItem<NodeModel> sub, String search, int offset, Set<Integer> keepSelected) {
        int subGroupId = sub.getValue().subGroupId();
        Task<List<ItemGroupItem>> task = new Task<>() {
            @Override protected List<ItemGroupItem> call() throws Exception {
                return service.items(subGroupId, search, PAGE_SIZE, offset);
            }
        };
        task.setOnSucceeded(event -> {
            sub.getValue().setLoading(false);
            sub.getChildren().removeIf(item -> item.getValue().kind() == Kind.LOADING
                    || item.getValue().kind() == Kind.MORE);
            Set<Integer> present = new HashSet<>();
            for (TreeItem<NodeModel> child : sub.getChildren()) {
                if (child.getValue().isItem()) present.add(child.getValue().itemId());
            }
            for (ItemGroupItem item : task.getValue()) {
                if (!present.add(item.id())) continue;
                NodeModel node = NodeModel.item(item);
                node.selectedProperty().set(keepSelected.contains(item.id()));
                TreeItem<NodeModel> child = new TreeItem<>(node);
                sub.getChildren().add(child);
            }
            int loaded = offset + task.getValue().size();
            if (loaded < sub.getValue().totalCount()) {
                sub.getChildren().add(new TreeItem<>(NodeModel.more(loaded)));
            }
            sub.getValue().setLoaded(true);
            Platform.runLater(this::restoreRowSelectionFromChecks);
            updateSelectedCount();
        });
        task.setOnFailed(event -> {
            sub.getValue().setLoading(false);
            report(task.getException());
        });
        READER.execute(task);
    }

    private void chooseTarget() {
        if (selectedItems().isEmpty()) {
            validation("item.group.manager.error.select.items");
            return;
        }
        Map<Integer, ItemGroupSummary> availableTargets = allTargets.isEmpty() ? subGroups : allTargets;
        List<TargetChoice> choices = availableTargets.values().stream()
                .map(TargetChoice::new)
                .sorted(Comparator.comparing(TargetChoice::toString))
                .toList();
        if (choices.isEmpty()) {
            validation("item.group.manager.error.target.missing");
            return;
        }
        ChoiceDialog<TargetChoice> dialog = new ChoiceDialog<>(choices.getFirst(), choices);
        dialog.setTitle(text("item.group.manager.choose.title"));
        dialog.setHeaderText(text("item.group.manager.choose.header"));
        dialog.setContentText(text("item.group.manager.choose.label"));
        dialog.getDialogPane().setNodeOrientation(LanguageManager.getInstance().getNodeOrientation());
        localizeDialogButtons(dialog);
        ThemeManager.apply(dialog.getDialogPane().getScene());
        dialog.showAndWait().ifPresent(choice -> requestMove(choice.summary().subGroupId()));
    }

    private void requestMove(int targetSubGroupId) {
        List<NodeModel> selected = selectedItems();
        if (selected.isEmpty()) {
            validation("item.group.manager.error.select.items");
            return;
        }
        ItemGroupSummary target = subGroups.get(targetSubGroupId);
        if (target == null) target = allTargets.get(targetSubGroupId);
        if (target == null) {
            validation("item.group.manager.error.target.missing");
            return;
        }
        List<ItemGroupChange> changes = selected.stream()
                .filter(node -> node.subGroupId() != targetSubGroupId)
                .map(node -> new ItemGroupChange(node.itemId(), node.subGroupId(), targetSubGroupId))
                .toList();
        if (changes.isEmpty()) {
            labelStatus.setText(text("item.group.manager.status.already.there"));
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                text("item.group.manager.confirm.message", changes.size(), target.subGroupName()),
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle(text("item.group.manager.confirm.title"));
        confirm.setHeaderText(text("item.group.manager.confirm.header"));
        confirm.getDialogPane().setNodeOrientation(LanguageManager.getInstance().getNodeOrientation());
        localizeDialogButtons(confirm);
        ThemeManager.apply(confirm.getDialogPane().getScene());
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        runMove(new ItemGroupMoveCommand(changes, CurrentUser.get().getId()), false);
    }

    private void undo() {
        if (undoCommand != null) runMove(undoCommand, true);
    }

    private void runMove(ItemGroupMoveCommand command, boolean undoing) {
        Task<ItemGroupMoveResult> task = new Task<>() {
            @Override protected ItemGroupMoveResult call() throws Exception {
                return service.move(command);
            }
        };
        setBusy(true, undoing ? "item.group.manager.status.undoing" : "item.group.manager.status.moving");
        task.setOnSucceeded(event -> {
            setBusy(false, null);
            ItemGroupMoveResult result = task.getValue();
            undoCommand = undoing || result.movedCount() == 0
                    ? null : result.undoCommand(CurrentUser.get().getId());
            clearSelection();
            initialSelection.clear();
            updateSelectedCount();
            btnUndo.setDisable(undoCommand == null);
            operationStatus = text(undoing ? "item.group.manager.status.undo.done"
                    : "item.group.manager.status.done", result.movedCount());
            labelStatus.setText(operationStatus);
            if (eventBus != null) eventBus.publish(new ItemsChanged());
            else reload();
        });
        task.setOnFailed(event -> {
            setBusy(false, null);
            report(task.getException());
            if (task.getException() != null
                    && "item.group.manager.error.concurrent".equals(task.getException().getMessage())) reload();
        });
        READER.execute(task);
    }

    private boolean canMoveTo(int targetSubGroupId) {
        return canMove() && selectedItems().stream().anyMatch(node -> node.subGroupId() != targetSubGroupId);
    }

    private boolean canMove() {
        return !busy && AuthorizationGuard.isGranted(AppPermissions.ITEMS_GROUP_MOVE);
    }

    private void setBusy(boolean value, String statusKey) {
        busy = value;
        progress.setVisible(value);
        txtSearch.setDisable(value);
        btnRefresh.setDisable(value);
        btnMove.setDisable(value || !AuthorizationGuard.isGranted(AppPermissions.ITEMS_GROUP_MOVE));
        btnUndo.setDisable(value || undoCommand == null);
        if (statusKey != null) labelStatus.setText(text(statusKey));
        tree.refresh();
    }

    private List<NodeModel> selectedItems() {
        List<NodeModel> result = new ArrayList<>();
        collect(tree.getRoot(), result, true);
        return result;
    }

    private Set<Integer> selectedItemIds() {
        Set<Integer> result = new HashSet<>();
        for (NodeModel node : selectedItems()) result.add(node.itemId());
        return result;
    }

    private void clearSelection() {
        synchronizingSelection = true;
        tree.getSelectionModel().clearSelection();
        List<NodeModel> checked = new ArrayList<>();
        collect(tree.getRoot(), checked, true);
        checked.forEach(node -> node.selectedProperty().set(false));
        synchronizingSelection = false;
        tree.refresh();
        updateSelectedCount();
    }

    private void syncChecksFromRowSelection() {
        if (synchronizingSelection) return;
        Set<NodeModel> selectedRows = new HashSet<>();
        for (TreeItem<NodeModel> item : tree.getSelectionModel().getSelectedItems()) {
            if (item != null && item.getValue() != null && item.getValue().isItem()) {
                selectedRows.add(item.getValue());
            }
        }
        List<NodeModel> loadedItems = new ArrayList<>();
        collect(tree.getRoot(), loadedItems, false);
        loadedItems.forEach(node -> node.selectedProperty().set(selectedRows.contains(node)));
        tree.refresh();
        updateSelectedCount();
    }

    private void restoreRowSelectionFromChecks() {
        if (tree.getRoot() == null) return;
        List<TreeItem<NodeModel>> checked = new ArrayList<>();
        collectCheckedTreeItems(tree.getRoot(), checked);
        synchronizingSelection = true;
        tree.getSelectionModel().clearSelection();
        for (TreeItem<NodeModel> item : checked) {
            int row = tree.getRow(item);
            if (row >= 0) tree.getSelectionModel().select(row);
        }
        synchronizingSelection = false;
        tree.refresh();
        updateSelectedCount();
    }

    private static void collect(TreeItem<NodeModel> parent, List<NodeModel> result, boolean selectedOnly) {
        if (parent == null) return;
        NodeModel value = parent.getValue();
        if (value != null && value.isItem() && (!selectedOnly || value.selectedProperty().get())) result.add(value);
        for (TreeItem<NodeModel> child : parent.getChildren()) collect(child, result, selectedOnly);
    }

    private static void collectCheckedTreeItems(TreeItem<NodeModel> parent,
                                                List<TreeItem<NodeModel>> result) {
        if (parent == null) return;
        NodeModel value = parent.getValue();
        if (value != null && value.isItem() && value.selectedProperty().get()) result.add(parent);
        for (TreeItem<NodeModel> child : parent.getChildren()) collectCheckedTreeItems(child, result);
    }

    private void updateSelectedCount() {
        int count = selectedItems().size();
        labelSelected.setText(text("item.group.manager.selected", count));
        btnMove.setDisable(busy || count == 0 || !AuthorizationGuard.isGranted(AppPermissions.ITEMS_GROUP_MOVE));
    }

    private static void setExpanded(TreeItem<NodeModel> item, boolean expanded) {
        if (item == null) return;
        item.setExpanded(expanded);
        for (TreeItem<NodeModel> child : item.getChildren()) setExpanded(child, expanded);
    }

    private void setMainGroupsExpanded(boolean expanded) {
        TreeItem<NodeModel> rootItem = tree.getRoot();
        if (rootItem == null) return;
        rootItem.setExpanded(true);
        for (TreeItem<NodeModel> main : rootItem.getChildren()) main.setExpanded(expanded);
    }

    private void collapseAllGroups() {
        TreeItem<NodeModel> rootItem = tree.getRoot();
        if (rootItem == null) return;
        for (TreeItem<NodeModel> main : rootItem.getChildren()) setExpanded(main, false);
        rootItem.setExpanded(true);
        tree.refresh();
        tree.requestLayout();
    }

    private void refreshAfterCollapse() {
        clearSelection();
        Platform.runLater(() -> {
            tree.getFocusModel().focus(-1);
            tree.refresh();
            tree.requestLayout();
        });
    }

    /**
     * The name cell, which owns its own icon.
     * <p>
     * The icons used to be set on the {@code TreeItem}s. A {@code Node} can only be in the scene
     * graph once, and a {@code TreeTableView} moves an item's graphic between the cells it reuses
     * as rows scroll and shift - so expanding a group made the folder icon disappear from two
     * unrelated rows further down, which is what running the screen showed. Rendering the icon
     * here instead means a cell's icon belongs to the cell: nothing is moved, and the disclosure
     * arrow the skin draws no longer lands on top of it.
     * <p>
     * Three nodes per cell, made once and swapped, because {@code updateItem} runs on every
     * scroll and a new {@code FontIcon} per call is a new node per row per frame.
     */
    private static final class NameCell extends TreeTableCell<NodeModel, String> {
        private final Map<Kind, javafx.scene.Node> icons = new java.util.EnumMap<>(Kind.class);

        @Override protected void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);
            NodeModel row = empty || getTreeTableRow() == null ? null : getTreeTableRow().getItem();
            setText(row == null ? null : value);
            setGraphic(row == null ? null : icons.computeIfAbsent(row.kind(), kind -> switch (kind) {
                case MAIN -> styledIcon(AppIcon.MAIN_GROUP, 17, "item-group-main-icon");
                case SUB -> styledIcon(AppIcon.SUB_GROUP, 16, "item-group-sub-icon");
                case ITEM -> styledIcon(AppIcon.ITEM, 15, "item-group-item-icon");
                case ROOT, MORE, LOADING -> null;
            }));
        }
    }

    private static javafx.scene.Node styledIcon(AppIcon icon, int size, String styleClass) {
        var graphic = icon.graphic(size);
        graphic.getStyleClass().add(styleClass);
        return graphic;
    }

    private String currentSearch() {
        return txtSearch.getText() == null ? "" : txtSearch.getText().trim();
    }

    private void validation(String key) {
        AllAlerts.handleError(text("item.group.manager.title"), new UserValidationException(text(key)));
    }

    private void report(Throwable failure) {
        Throwable actual = failure == null ? new DaoException(text("item.group.manager.error.load")) : failure;
        String key = actual.getMessage();
        if (key != null && key.startsWith("item.group.manager.")) {
            actual = new UserValidationException(text(key));
        }
        AllAlerts.handleError(text("item.group.manager.title"), actual);
    }

    private static String text(String key, Object... args) {
        return args.length == 0 ? LanguageManager.getInstance().getString(key)
                : LanguageManager.getInstance().getString(key, args);
    }

    private static void localizeDialogButtons(Dialog<?> dialog) {
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        ok.setText(text("ok"));
        cancel.setText(text("cancel"));
    }

    private record TargetChoice(ItemGroupSummary summary) {
        @Override public String toString() {
            return summary.mainGroupName() + " / " + summary.subGroupName();
        }
    }

    private enum Kind { ROOT, MAIN, SUB, ITEM, MORE, LOADING }

    /** JavaFX state belongs to the screen model, while the feature records remain plain Java. */
    static final class NodeModel {
        private final Kind kind;
        private final int itemId;
        private final int subGroupId;
        private final String name;
        private final String barcode;
        private final boolean active;
        private final int totalCount;
        private final int offset;
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        private boolean loaded;
        private boolean loading;

        private NodeModel(Kind kind, int itemId, int subGroupId, String name, String barcode,
                          boolean active, int totalCount, int offset) {
            this.kind = kind;
            this.itemId = itemId;
            this.subGroupId = subGroupId;
            this.name = name == null ? "" : name;
            this.barcode = barcode == null ? "" : barcode;
            this.active = active;
            this.totalCount = totalCount;
            this.offset = offset;
        }

        static NodeModel root() { return new NodeModel(Kind.ROOT, 0, 0, "", "", true, 0, 0); }
        static NodeModel main(int id, String name, int count) {
            return new NodeModel(Kind.MAIN, 0, 0, name, "", true, count, id);
        }
        static NodeModel sub(ItemGroupSummary value) {
            return new NodeModel(Kind.SUB, 0, value.subGroupId(), value.subGroupName(), "", true,
                    value.itemCount(), 0);
        }
        static NodeModel item(ItemGroupItem value) {
            return new NodeModel(Kind.ITEM, value.id(), value.subGroupId(), value.name(), value.barcode(),
                    value.active(), 0, 0);
        }
        static NodeModel more(int offset) {
            return new NodeModel(Kind.MORE, 0, 0, text("item.group.manager.load.more"), "", true, 0, offset);
        }
        static NodeModel loadingPlaceholder() {
            return new NodeModel(Kind.LOADING, 0, 0, text("item.group.manager.loading"), "", true, 0, 0);
        }

        Kind kind() { return kind; }
        int itemId() { return itemId; }
        int subGroupId() { return subGroupId; }
        String barcode() { return kind == Kind.ITEM ? barcode : ""; }
        int totalCount() { return totalCount; }
        int offset() { return offset; }
        boolean loaded() { return loaded; }
        void setLoaded(boolean value) { loaded = value; }
        boolean loading() { return loading; }
        void setLoading(boolean value) { loading = value; }
        boolean isItem() { return kind == Kind.ITEM; }
        BooleanProperty selectedProperty() { return selected; }
        String displayName() {
            return kind == Kind.SUB || kind == Kind.MAIN
                    ? text("item.group.manager.group.with.count", name, totalCount) : name;
        }
        String statusText() {
            if (kind != Kind.ITEM) return "";
            return text(active ? "activated" : "in_active");
        }
    }

    private record CatalogLoad(List<ItemGroupSummary> groups, List<ItemGroupItem> selectedItems) { }
}
