package com.hamza.account.controller.dataByName;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.features.masterdata.*;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserFacingException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.animation.PauseTransition;
import javafx.css.PseudoClass;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/** One reusable editor. Database work is detached from JavaFX and stale searches are discarded. */
final class MasterDataPane extends VBox {
    private final MasterDataKind kind;
    private final MasterDataService service;
    private final Runnable changed;
    private final TableView<MasterDataEntry> table = new TableView<>();
    private final TextField search = new TextField();
    private final TextField name = new TextField();
    private final TextField factor = new TextField("1");
    private final Label context = new Label();
    private final Label mode = new Label();
    private final Label status = new Label();
    private final Label pageLabel = new Label();
    private final Button save = button("common.save", AppIcon.SAVE);
    private final Button add = button("masterdata.new", AppIcon.ADD);
    private final Button edit = button("masterdata.edit", AppIcon.EDIT);
    private final Button delete = button("masterdata.delete", AppIcon.DELETE);
    private final Button cancel = button("masterdata.cancel", AppIcon.CLOSE);
    private final Button previous = new Button(text("masterdata.previous"));
    private final Button next = new Button(text("masterdata.next"));
    private final ProgressIndicator progress = new ProgressIndicator();
    private final VBox editor = new VBox(8);
    private final MasterDataDrafts drafts = new MasterDataDrafts();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));
    private int parentId, editedId, page, generation;
    private boolean restoring, dirty, writing, loaded, disposed;
    private Consumer<MasterDataEntry> selected = row -> { };

    MasterDataPane(MasterDataKind kind, MasterDataService service, Runnable changed) {
        super(12);
        this.kind = kind;
        this.service = service;
        this.changed = changed;
        setId("masterdata-" + kind.name().toLowerCase(java.util.Locale.ROOT));
        search.setId("entry-search"); name.setId("entry-name"); factor.setId("entry-factor");
        save.setId("entry-save"); status.setId("entry-status"); table.setId("entry-table");
        getStyleClass().add("masterdata-card");
        setMinWidth(280);
        Label heading = new Label(text(kind.titleKey));
        heading.getStyleClass().add("app-section-title");
        context.getStyleClass().add("app-subtitle");
        context.setWrapText(true);
        context.setText(kind == MasterDataKind.SUB ? text("masterdata.choose.parent") : text("masterdata.list.hint"));
        search.setPromptText(text("masterdata.search"));
        search.setAccessibleText(text("masterdata.search") + " " + text(kind.titleKey));
        Button refresh = button("refresh", AppIcon.REFRESH);
        HBox searchBar = new HBox(8, search, refresh);
        HBox.setHgrow(search, Priority.ALWAYS);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().addAll(List.of(Columns.number("code", MasterDataEntry::id),
                Columns.text("name", MasterDataEntry::name)));
        table.getColumns().get(0).setMaxWidth(85);
        if (kind == MasterDataKind.UNIT)
            table.getColumns().add(Columns.text("unit.default.factor", MasterDataEntry::factorText));
        if (kind == MasterDataKind.MAIN || kind == MasterDataKind.SUB) {
            table.getColumns().add(Columns.number(kind == MasterDataKind.MAIN
                    ? "masterdata.count.subgroups" : "masterdata.count.items", MasterDataEntry::contentCount));
            PseudoClass emptyGroup = PseudoClass.getPseudoClass("group-without-contents");
            table.setRowFactory(view -> new TableRow<>() {
                @Override protected void updateItem(MasterDataEntry entry, boolean empty) {
                    super.updateItem(entry, empty);
                    boolean noContents = !empty && entry != null && entry.hasNoContents(kind);
                    pseudoClassStateChanged(emptyGroup, noContents);
                    setTooltip(noContents ? new Tooltip(text(kind == MasterDataKind.MAIN
                            ? "masterdata.empty.main.hint" : "masterdata.empty.sub.hint")) : null);
                }
            });
        }
        table.getColumns().forEach(column -> column.setSortable(false));
        table.setPlaceholder(new Label(text("masterdata.empty")));
        table.setMinHeight(130);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.getSelectionModel().selectedItemProperty().addListener((o, old, row) -> {
            updateActions();
            if (row != null) selected.accept(row);
        });
        table.setOnMouseClicked(event -> { if (event.getClickCount() == 2) edit.fire(); });
        table.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) edit.fire();
            else if (event.getCode() == KeyCode.DELETE) delete.fire();
        });
        HBox actions = new HBox(8, add, edit, delete);
        actions.setAlignment(Pos.CENTER_LEFT);
        previous.setOnAction(e -> { page--; reload(); });
        next.setOnAction(e -> { page++; reload(); });
        previous.getStyleClass().add("app-neutral-button");
        next.getStyleClass().add("app-neutral-button");
        pageLabel.getStyleClass().add("masterdata-caption");
        progress.setPrefSize(20, 20);
        progress.setMaxSize(20, 20);
        progress.setVisible(false);
        HBox paging = new HBox(10, previous, pageLabel, next, progress);
        paging.setAlignment(Pos.CENTER_LEFT);
        name.setPromptText(text("name"));
        name.setAccessibleText(text("name"));
        Label nameLabel = new Label(text("name"));
        nameLabel.setLabelFor(name);
        editor.getChildren().addAll(mode, nameLabel, name);
        if (kind == MasterDataKind.UNIT) {
            Label factorLabel = new Label(text("unit.default.factor"));
            factorLabel.setLabelFor(factor);
            factor.setTooltip(new Tooltip(text("unit.default.factor.hint")));
            setTextFormatter(factor);
            factor.setText("1");
            VBox nameField = new VBox(6, nameLabel, name);
            VBox factorField = new VBox(6, factorLabel, factor);
            factorField.setPrefWidth(220);
            HBox fields = new HBox(16, nameField, factorField);
            HBox.setHgrow(nameField, Priority.ALWAYS);
            editor.getChildren().setAll(mode, fields);
            whenEnterPressed(name, factor, save);
        } else {
            whenEnterPressed(name, save);
        }
        // The final field saves immediately; the shared helper still declares the form's focus order.
        (kind == MasterDataKind.UNIT ? factor : name).addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) { save.fire(); event.consume(); }
        });
        editor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && !writing) { reset(); event.consume(); }
        });
        save.getStyleClass().remove("app-neutral-button");
        save.getStyleClass().add("app-primary-button");
        editor.getChildren().add(new HBox(8, save, cancel));
        editor.getStyleClass().add("masterdata-editor");
        status.setWrapText(true);
        status.setMinHeight(24);
        getChildren().addAll(heading, context, searchBar, table, paging, actions, editor, status);
        name.textProperty().addListener((o, old, value) -> markDirty());
        factor.textProperty().addListener((o, old, value) -> markDirty());
        debounce.setOnFinished(e -> { page = 0; reload(); });
        search.textProperty().addListener((o, old, value) -> debounce.playFromStart());
        search.setOnAction(e -> { debounce.stop(); page = 0; reload(); });
        refresh.setOnAction(e -> reload());
        add.setOnAction(e -> { if (!dirty || confirmDiscard()) reset(); });
        cancel.setOnAction(e -> reset());
        edit.setOnAction(e -> editSelected());
        save.setOnAction(e -> save());
        delete.setOnAction(e -> deleteSelected());
        updateActions();
    }

    void onSelected(Consumer<MasterDataEntry> callback) { selected = callback; }
    void ensureLoaded() { if (!loaded) reload(); }
    boolean hasChanges() { return dirty || drafts.hasChanges(); }
    boolean isWriting() { return writing; }
    void dispose() { disposed = true; generation++; debounce.stop(); }

    void setParent(MasterDataEntry parent) {
        if (kind != MasterDataKind.SUB) return;
        context.setText(parent == null ? text("masterdata.choose.parent") : text("masterdata.parent") + " " + parent.name());
        int id = parent == null ? 0 : parent.id();
        if (id == parentId) return;
        remember();
        parentId = id;
        restore(drafts.get(parentId));
        page = 0;
        reload();
    }

    private void markDirty() {
        if (restoring) return;
        dirty = true;
        remember();
        updateActions();
    }
    private void remember() { drafts.put(parentId, new MasterDataDrafts.Draft(editedId, name.getText(), factor.getText(), dirty)); }
    private void restore(MasterDataDrafts.Draft draft) {
        restoring = true;
        editedId = draft.id(); name.setText(draft.name()); factor.setText(draft.factor()); dirty = draft.dirty();
        restoring = false;
        updateActions();
    }
    private void reset() {
        drafts.clear(parentId);
        restore(MasterDataDrafts.Draft.empty());
        name.requestFocus();
    }
    private void editSelected() {
        MasterDataEntry row = table.getSelectionModel().getSelectedItem();
        if (row == null || (dirty && !confirmDiscard())) return;
        restore(new MasterDataDrafts.Draft(row.id(), row.name(), row.factorText(), false));
        remember(); name.requestFocus(); name.selectAll();
    }

    private void updateActions() {
        boolean noParent = kind == MasterDataKind.SUB && parentId <= 0;
        boolean rowMissing = table.getSelectionModel().getSelectedItem() == null;
        add.setDisable(writing || noParent || !AuthorizationGuard.isGranted(kind.create));
        edit.setDisable(writing || rowMissing || !AuthorizationGuard.isGranted(kind.update));
        delete.setDisable(writing || rowMissing || !AuthorizationGuard.isGranted(kind.delete));
        editor.setDisable(writing || noParent || !AuthorizationGuard.isGranted(editedId > 0 ? kind.update : kind.create));
        save.setDisable(name.getText().isBlank());
        mode.setText(text(editedId > 0 ? "masterdata.edit" : "masterdata.new")
                + (editedId > 0 ? " #" + editedId : "") + (dirty ? " • " + text("masterdata.draft") : ""));
    }

    void reload() {
        if (disposed) return;
        int token = ++generation;
        if (kind == MasterDataKind.SUB && parentId <= 0) {
            table.getItems().clear(); previous.setDisable(true); next.setDisable(true);
            progress.setVisible(false); updateActions(); return;
        }
        String query = search.getText();
        int requestedParent = parentId, requestedPage = page;
        MasterDataEntry selection = table.getSelectionModel().getSelectedItem();
        progress.setVisible(true);
        previous.setDisable(true); next.setDisable(true);
        run(() -> service.search(kind, query, requestedParent, requestedPage), rows -> {
            if (token != generation) return;
            loaded = true;
            progress.setVisible(false);
            if (rows.isEmpty() && page > 0) { page--; reload(); return; }
            boolean more = rows.size() > MasterDataQuery.PAGE_SIZE;
            table.getItems().setAll(rows.subList(0, Math.min(rows.size(), MasterDataQuery.PAGE_SIZE)));
            if (selection != null) table.getItems().stream().filter(row -> row.id() == selection.id())
                    .findFirst().ifPresent(row -> table.getSelectionModel().select(row));
            previous.setDisable(page == 0); next.setDisable(!more);
            pageLabel.setText(text("masterdata.page") + " " + (page + 1));
            updateActions();
        }, error -> {
            if (token == generation) { progress.setVisible(false); report(error); }
        });
    }

    private void save() {
        if (writing) return;
        int id = editedId, scope = parentId;
        String enteredName = name.getText(), enteredFactor = factor.getText();
        setWriting(true);
        run(() -> service.save(kind, id, enteredName, scope, enteredFactor), result -> {
            setWriting(false);
            drafts.clear(scope);
            if (parentId == scope) reset();
            notifyChanged();
            status.getStyleClass().remove("masterdata-error");
            status.setText(text("masterdata.saved"));
            reload();
        }, error -> { setWriting(false); report(error); });
    }

    private void deleteSelected() {
        MasterDataEntry row = table.getSelectionModel().getSelectedItem();
        if (row == null || writing || !AllAlerts.confirmDelete()) return;
        setWriting(true);
        run(() -> service.delete(kind, row.id()), result -> {
            setWriting(false);
            if (editedId == row.id()) reset();
            if (kind == MasterDataKind.MAIN) selected.accept(null);
            notifyChanged();
            status.getStyleClass().remove("masterdata-error");
            status.setText(text("masterdata.deleted"));
            reload();
        }, error -> { setWriting(false); report(error); });
    }

    private void notifyChanged() {
        try { changed.run(); }
        catch (RuntimeException e) { AllAlerts.handleError(text("masterdata.refresh.failed"), e); }
    }

    private void setWriting(boolean value) { writing = value; updateActions(); }
    private void report(Throwable error) {
        status.getStyleClass().remove("masterdata-error");
        status.getStyleClass().add("masterdata-error");
        if (error instanceof UserFacingException expected) {
            String message = expected.userMessage();
            status.setText(message.startsWith("masterdata.") ? text(message) : message);
        } else {
            status.setText(text("masterdata.failed"));
            AllAlerts.handleError(text(kind.titleKey), error);
        }
    }

    private <T> void run(Callable<T> operation, Consumer<T> success, Consumer<Throwable> failure) {
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return operation.call(); } };
        task.setOnSucceeded(e -> { if (!disposed) success.accept(task.getValue()); });
        task.setOnFailed(e -> { if (!disposed) failure.accept(task.getException()); });
        Thread.ofVirtual().name("master-data-" + kind.name()).start(task);
    }

    static String text(String key) { return LanguageManager.getInstance().getString(key); }
    static Button button(String key, AppIcon icon) {
        Button button = new Button(text(key), icon.graphic());
        button.getStyleClass().add("app-neutral-button");
        return button;
    }
    private boolean confirmDiscard() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text("masterdata.discard"), ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(text("masterdata.title")); alert.setHeaderText(null);
        alert.initOwner(getScene().getWindow());
        com.hamza.account.config.ThemeManager.apply(alert.getDialogPane().getScene());
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
}
