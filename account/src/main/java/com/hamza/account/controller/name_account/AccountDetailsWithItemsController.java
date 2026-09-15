package com.hamza.account.controller.name_account;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.controller.model.AccountCard;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.AccountChanged;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.statement.PartyStatementFilter;
import com.hamza.account.features.party.statement.PartyStatementOptions;
import com.hamza.account.features.party.statement.PartyStatementPage;
import com.hamza.account.features.party.statement.PartyStatementPrintData;
import com.hamza.account.features.party.statement.PartyStatementRow;
import com.hamza.account.features.party.statement.PartyStatementService;
import com.hamza.account.features.party.statement.PartyStatementSummary;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.table.Columns;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The responsive account statement shared by customers and suppliers.
 *
 * <p>The statement arithmetic stays in {@link PartyStatementService}. This controller owns only
 * presentation state: one page of rows, the active filter, asynchronous loading and the lazy
 * document-line tree. Printing and exports ask the service for the complete filtered extract, so
 * paging the screen never cuts the document handed to the user.</p>
 */
@Log4j2
public class AccountDetailsWithItemsController<T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T3, T4> implements AppSettingInterface {

    private static final PseudoClass DOCUMENT_ROW = PseudoClass.getPseudoClass("document-row");
    private static final PseudoClass DETAIL_ROW = PseudoClass.getPseudoClass("document-detail-row");

    /** Statement queries never wait behind a page whose invoice details are being expanded. */
    private static final ExecutorService STATEMENT_READER = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "party-statement-reader");
        thread.setDaemon(true);
        return thread;
    });
    private static final ExecutorService DETAIL_READER = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "party-statement-detail-reader");
        thread.setDaemon(true);
        return thread;
    });

    private final String partyName;
    private final int partyId;
    private final AccountDetailsInterface documentLines;
    private final PartyStatementService statementService = new PartyStatementService();
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);

    private final Set<TreeItem<AccountCard>> expanded =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<TreeItem<AccountCard>> loadingDetails =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private final TreeTableView<AccountCard> treeView = new TreeTableView<>();
    private final TreeItem<AccountCard> root = new TreeItem<>(new AccountCard());
    private final PartyStatementHeader header = new PartyStatementHeader();
    private final PartyStatementFilterBar filters;

    private final Label status = new Label();
    private final Label pageLabel = new Label();
    private final Label narrowed = new Label(text("party.statement.narrowed"));
    private final Label busyMessage = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final VBox busyPane = new VBox(10, progress, busyMessage);
    private final StackPane content = new StackPane();

    private final CheckBox showDetails = new CheckBox(text("party.statement.details.current.page"));
    private final Button previous = button("party.statement.previous", null, this::previousPage);
    private final Button next = button("party.statement.next", null, this::nextPage);
    private final java.util.ArrayList<Control> busySensitive = new java.util.ArrayList<>();

    private PartyStatementFilter currentFilter;
    private PartyStatementPage currentPage = new PartyStatementPage(
            List.of(), PartyStatementSummary.EMPTY, 0, false, false);
    private BigDecimal creditLimit;
    private Task<?> activeTask;
    private int generation;
    private boolean detailErrorReported;

    public AccountDetailsWithItemsController(DaoFactory daoFactory, DataPublisher dataPublisher,
                                             DataInterface<?, ?, T3, T4> dataInterface,
                                             int partyId, String partyName,
                                             AccountDetailsInterface documentLines)
            throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.partyId = partyId;
        this.partyName = partyName == null ? "" : partyName;
        this.documentLines = documentLines;
        this.filters = new PartyStatementFilterBar(partyKind(), partyId);
    }

    @Override
    public Pane pane() {
        buildTree();
        filters.setOnSearch(this::load);
        filters.setDisable(true);

        content.getStyleClass().add("party-statement-table-card");
        content.getChildren().addAll(treeView, busyPane);
        BorderPane.setMargin(content, new Insets(10, 0, 10, 0));

        busyPane.setAlignment(Pos.CENTER);
        busyPane.getStyleClass().add("party-statement-loading-overlay");
        busyPane.setVisible(false);
        busyPane.setManaged(false);
        progress.setMaxSize(46, 46);
        busyMessage.getStyleClass().add("party-statement-loading-text");

        BorderPane layout = new BorderPane();
        layout.getStyleClass().addAll("app-container", "party-statement-shell");
        layout.setTop(new VBox(10, hero(), header, filters));
        layout.setCenter(content);
        layout.setBottom(footer());

        StackPane screen = new StackPane(layout);
        screen.getStyleClass().addAll("app-root", "party-statement-root");
        screen.setMinSize(840, 560);
        screen.setPrefSize(880, 560);
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("party-statement");

        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(AccountChanged.class, event -> {
                if (event.kind() == partyKind()) {
                    reload();
                }
            }));
            subscriptions.disposeWith(screen);
        }
        Platform.runLater(this::initialise);
        return screen;
    }

    private VBox hero() {
        HBox iconBox = new HBox(AppIcon.REPORT.graphic(34));
        iconBox.setAlignment(Pos.CENTER);
        iconBox.getStyleClass().add("party-statement-icon-box");

        Label type = new Label(text(partyKind() == PartyKind.CUSTOMER ? "customers" : "suppliers"));
        type.getStyleClass().add("party-statement-party-type");
        Label title = new Label(text("party.statement.title") + " — " + partyName);
        title.getStyleClass().add("party-statement-title");
        Label subtitle = new Label(text("party.statement.subtitle"));
        subtitle.getStyleClass().add("party-statement-subtitle");
        subtitle.setWrapText(true);

        VBox identityText = new VBox(3, type, title, subtitle);
        HBox.setHgrow(identityText, Priority.ALWAYS);
        HBox identity = new HBox(14, iconBox, identityText);
        identity.setAlignment(Pos.CENTER_LEFT);

        Button refresh = button("refresh", AppIcon.REFRESH, this::reload);
        Button print = button("print", AppIcon.PRINT, this::print);
        print.getStyleClass().setAll("button", "app-primary-button", "party-statement-primary-action");
        Button pdf = button("party.btn.export.pdf", AppIcon.EXPORT, this::exportPdf);
        pdf.getStyleClass().setAll("button", "pdf-button");
        Button excel = button("party.statement.export.excel", AppIcon.SPREADSHEET, this::exportExcel);
        excel.getStyleClass().setAll("button", "excel-button");

        showDetails.setTooltip(new Tooltip(text("party.statement.details.hint")));
        showDetails.setOnAction(event -> setAllExpanded(showDetails.isSelected()));

        FlowPane actions = new FlowPane(8, 8, showDetails, refresh, print, pdf, excel);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getStyleClass().add("party-statement-actions");
        busySensitive.addAll(List.of(showDetails, refresh, print, pdf, excel));

        VBox hero = new VBox(10, identity, actions);
        hero.getStyleClass().add("party-statement-hero");
        return hero;
    }

    private HBox footer() {
        status.setId("statement-status");
        status.getStyleClass().add("party-statement-status");
        pageLabel.getStyleClass().add("party-statement-page-label");
        narrowed.getStyleClass().add("party-statement-narrowed");
        narrowed.setVisible(false);
        narrowed.setManaged(false);

        previous.setId("statement-previous");
        next.setId("statement-next");
        busySensitive.addAll(List.of(previous, next));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(10, status, narrowed, spacer, previous, pageLabel, next);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("party-statement-footer");
        updateNavigation();
        return bar;
    }

    private void buildTree() {
        treeView.setId("statement-table");
        treeView.getStyleClass().add("party-statement-table");
        treeView.setShowRoot(false);
        treeView.setRoot(root);
        treeView.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        treeView.setPlaceholder(new Label(text("party.statement.empty")));
        root.setExpanded(true);

        treeView.getColumns().setAll(List.of(
                named("statement-date", tree("date", AccountCard::getDate, 108)),
                named("statement-kind", kindColumn()),
                named("statement-reference", tree("party.statement.column.reference",
                        card -> card.getId() == 0 ? "" : String.valueOf(card.getId()), 92)),
                named("statement-debit", money("common.debtor", AccountCard::getPurchase,
                        "party-statement-debit-cell")),
                named("statement-credit", money("common.creditor", AccountCard::getPaid,
                        "party-statement-credit-cell")),
                named("statement-balance", money("party.statement.column.running", AccountCard::getDetails,
                        "party-statement-balance-cell")),
                named("statement-treasury", tree("party.statement.column.treasury", AccountCard::getName, 130)),
                named("statement-user", tree("party.statement.column.user", AccountCard::getUserName, 120)),
                named("statement-notes", tree("column.notes", AccountCard::getNotes, 260))));

        treeView.setRowFactory(view -> new TreeTableRow<>() {
            @Override
            protected void updateItem(AccountCard card, boolean empty) {
                super.updateItem(card, empty);
                TreeItem<AccountCard> item = getTreeItem();
                boolean detail = !empty && item != null && item.getParent() != null
                        && item.getParent() != root;
                pseudoClassStateChanged(DOCUMENT_ROW,
                        !empty && card != null && card.hasDocumentLines());
                pseudoClassStateChanged(DETAIL_ROW, detail);
            }
        });
        treeView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                toggleSelectedDocument();
            }
        });
        treeView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                toggleSelectedDocument();
                event.consume();
            }
        });
        TableSetting.tableMenuSetting(getClass(), treeView);
    }

    private void toggleSelectedDocument() {
        TreeItem<AccountCard> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getValue() != null
                && selected.getValue().hasDocumentLines()) {
            selected.setExpanded(!selected.isExpanded());
        }
    }

    // ---- loading -------------------------------------------------------------------------

    private void initialise() {
        Task<StatementSetup> setup = new Task<>() {
            @Override
            protected StatementSetup call() throws Exception {
                PartyStatementOptions options = statementService.options(partyKind());
                LocalDate earliest = statementService.earliestMovement(partyKind(), partyId);
                return new StatementSetup(options, earliest, readCreditLimit());
            }
        };
        startPrimary(setup, "party.statement.status.initialising", value -> {
            creditLimit = value.creditLimit();
            filters.setDisable(false);
            filters.initialise(value.options(), value.earliest());
        }, "party.error.load.account.details.items");
    }

    private BigDecimal readCreditLimit() throws Exception {
        if (partyKind() != PartyKind.CUSTOMER) {
            return null;
        }
        T3 party = nameAndAccountInterface.getNameById(partyId);
        return BigDecimal.valueOf(nameService.getCredit(List.of(party), partyId));
    }

    private void reload() {
        if (currentFilter != null) {
            load(currentFilter);
        }
    }

    private void previousPage() {
        if (currentFilter != null && currentPage.hasPrevious()) {
            load(currentFilter.onPage(currentFilter.page() - 1));
        }
    }

    private void nextPage() {
        if (currentFilter != null && currentPage.hasNext()) {
            load(currentFilter.onPage(currentFilter.page() + 1));
        }
    }

    private void load(PartyStatementFilter filter) {
        Task<PartyStatementPage> task = new Task<>() {
            @Override
            protected PartyStatementPage call() throws Exception {
                return statementService.search(filter);
            }
        };
        startPrimary(task, "party.statement.status.loading", page -> show(page, filter),
                "party.error.load.account.details.items");
    }

    private void show(PartyStatementPage page, PartyStatementFilter filter) {
        currentFilter = filter;
        currentPage = page;
        header.show(page.summary());
        header.showCreditLimit(creditLimit, page.summary().closingBalance());
        buildRows(page.rows());

        status.setText(text("party.statement.page.status", page.rows().size()));
        pageLabel.setText(text("party.statement.page.number", page.page() + 1));
        boolean hidden = filter.narrowsRows() && !page.summary().rowsExplainTheBalance();
        narrowed.setVisible(hidden);
        narrowed.setManaged(hidden);
        updateNavigation();
    }

    private <R> void startPrimary(Task<R> task, String statusKey, Consumer<R> onSuccess,
                                  String errorContextKey) {
        int token = ++generation;
        if (activeTask != null) {
            activeTask.cancel();
        }
        activeTask = task;
        setBusy(true, statusKey);
        task.setOnSucceeded(event -> {
            if (token != generation) {
                return;
            }
            activeTask = null;
            setBusy(false, null);
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            if (token != generation) {
                return;
            }
            activeTask = null;
            setBusy(false, null);
            report(errorContextKey, task.getException());
        });
        task.setOnCancelled(event -> {
            if (token == generation) {
                activeTask = null;
                setBusy(false, null);
            }
        });
        STATEMENT_READER.execute(task);
    }

    private void setBusy(boolean busy, String statusKey) {
        busyPane.setVisible(busy);
        busyPane.setManaged(busy);
        treeView.setDisable(busy);
        busySensitive.forEach(control -> control.setDisable(busy));
        if (busy && statusKey != null) {
            busyMessage.setText(text(statusKey));
        }
        if (!busy) {
            updateNavigation();
        }
    }

    private void updateNavigation() {
        previous.setDisable(activeTask != null || !currentPage.hasPrevious());
        next.setDisable(activeTask != null || !currentPage.hasNext());
    }

    private void buildRows(List<PartyStatementRow> rows) {
        expanded.clear();
        loadingDetails.clear();
        detailErrorReported = false;
        root.getChildren().clear();
        for (PartyStatementRow row : rows) {
            TreeItem<AccountCard> item = new TreeItem<>(toCard(row));
            root.getChildren().add(item);
            if (row.hasDocumentLines()) {
                item.getChildren().add(detailPlaceholder("party.statement.details.loading"));
                item.expandedProperty().addListener((observable, was, open) -> {
                    if (open) {
                        loadDocumentLines(item);
                    }
                });
            }
        }
        if (showDetails.isSelected()) {
            setAllExpanded(true);
        }
    }

    private void setAllExpanded(boolean expandedState) {
        root.getChildren().stream()
                .filter(item -> item.getValue() != null && item.getValue().hasDocumentLines())
                .forEach(item -> item.setExpanded(expandedState));
    }

    private void loadDocumentLines(TreeItem<AccountCard> item) {
        if (expanded.contains(item) || !loadingDetails.add(item)) {
            return;
        }
        Task<List<AccountCard>> task = new Task<>() {
            @Override
            protected List<AccountCard> call() throws Exception {
                return documentLines.documentLines(item.getValue());
            }
        };
        task.setOnSucceeded(event -> {
            loadingDetails.remove(item);
            if (item.getParent() != root) {
                return;
            }
            expanded.add(item);
            List<AccountCard> lines = task.getValue();
            if (lines.isEmpty()) {
                item.getChildren().setAll(detailPlaceholder("party.statement.details.empty"));
            } else {
                item.getChildren().setAll(lines.stream().map(TreeItem::new).toList());
            }
        });
        task.setOnFailed(event -> {
            loadingDetails.remove(item);
            if (item.getParent() != root) {
                return;
            }
            item.getChildren().setAll(detailPlaceholder("party.statement.details.failed"));
            if (!detailErrorReported) {
                detailErrorReported = true;
                report("party.statement.details.error", task.getException());
            }
        });
        DETAIL_READER.execute(task);
    }

    private static TreeItem<AccountCard> detailPlaceholder(String key) {
        AccountCard placeholder = new AccountCard();
        placeholder.setNotes(text(key));
        return new TreeItem<>(placeholder);
    }

    private AccountCard toCard(PartyStatementRow row) {
        AccountCard card = new AccountCard();
        card.setId((int) row.reference());
        card.setDate(row.date().toString());
        card.setKind(row.kind());
        card.setInformation(text(row.kind().messageKey()));
        card.setPurchase(row.debit().doubleValue());
        card.setPaid(row.credit().doubleValue());
        card.setDetails(row.runningBalance().doubleValue());
        card.setName(row.treasuryName());
        card.setUserName(row.userName());
        card.setNotes(row.notes());
        return card;
    }

    // ---- printing and exporting ----------------------------------------------------------

    private void print() {
        PartyStatementFilter filter = printableFilter();
        if (filter == null) {
            return;
        }
        withExtract(filter, extract -> {
            if (!requireRows(extract)) {
                return;
            }
            String title = text("party.account.card.title", partyName);
            File target = TablePdfReport.chooseTarget(treeView.getScene().getWindow(), title);
            if (target == null) {
                return;
            }
            warnIfTruncated(extract);
            TablePdfReport.write(target,
                    file -> new PartyStatementPdfExporter()
                            .export(extract, partyName, file.getAbsolutePath()));
        });
    }

    private void exportPdf() {
        PartyStatementFilter filter = printableFilter();
        if (filter == null) {
            return;
        }
        withExtract(filter, extract -> {
            if (!requireRows(extract)) {
                return;
            }
            FileChooser chooser = new FileChooser();
            chooser.setTitle(text("party.dialog.save.report"));
            chooser.setInitialFileName(partyName + "_" + LocalDate.now() + ".pdf");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            File target = chooser.showSaveDialog(treeView.getScene().getWindow());
            if (target == null) {
                return;
            }
            warnIfTruncated(extract);
            TablePdfReport.write(target,
                    file -> new PartyStatementPdfExporter()
                            .export(extract, partyName, file.getAbsolutePath()));
        });
    }

    private void exportExcel() {
        PartyStatementFilter filter = printableFilter();
        if (filter == null) {
            return;
        }
        withExtract(filter, extract -> {
            if (!requireRows(extract)) {
                return;
            }
            try {
                int written = ExportData.exportDataToExcel(extract.rows(),
                        new PartyStatementExcelWriter(extract.rows(), extract.summary()));
                if (written < 1) {
                    throw new BusinessRuleException(text("party.error.cannot.save"));
                }
                AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
                warnIfTruncated(extract);
            } catch (Exception e) {
                report("party.error.export.account.statement", e);
            }
        });
    }

    private PartyStatementFilter printableFilter() {
        if (currentFilter == null || currentPage.isEmpty()) {
            report("party.statement.title",
                    new UserValidationException(text("party.error.no.data.export")));
            return null;
        }
        return currentFilter;
    }

    private void withExtract(PartyStatementFilter filter, Consumer<PartyStatementPrintData> consumer) {
        Task<PartyStatementPrintData> task = new Task<>() {
            @Override
            protected PartyStatementPrintData call() throws Exception {
                return statementService.forPrint(filter);
            }
        };
        startPrimary(task, "party.statement.status.preparing.export", consumer,
                "party.error.export.account.statement");
    }

    private boolean requireRows(PartyStatementPrintData extract) {
        if (extract.rows().isEmpty()) {
            report("party.statement.title",
                    new UserValidationException(text("party.error.no.data.export")));
            return false;
        }
        return true;
    }

    private void warnIfTruncated(PartyStatementPrintData extract) {
        if (extract.truncated()) {
            report("party.statement.title", new UserValidationException(
                    text("party.statement.truncated", PartyStatementService.PRINT_LIMIT)));
        }
    }

    // ---- columns and plumbing -------------------------------------------------------------

    private TreeTableColumn<AccountCard, String> kindColumn() {
        return tree("party.statement.column.kind", card -> card.getKind() == null
                ? "" : text(card.getKind().messageKey()), 130);
    }

    private TreeTableColumn<AccountCard, String> tree(
            String titleKey, java.util.function.Function<AccountCard, String> value, double width) {
        TreeTableColumn<AccountCard, String> column = new TreeTableColumn<>(text(titleKey));
        column.setPrefWidth(width);
        column.setCellValueFactory(features -> new javafx.beans.property.ReadOnlyObjectWrapper<>(
                value.apply(features.getValue().getValue())));
        return column;
    }

    private TreeTableColumn<AccountCard, BigDecimal> money(
            String titleKey, java.util.function.Function<AccountCard, Double> value,
            String styleClass) {
        TreeTableColumn<AccountCard, BigDecimal> column = new TreeTableColumn<>(text(titleKey));
        column.setPrefWidth(110);
        column.setCellValueFactory(features -> {
            Double amount = value.apply(features.getValue().getValue());
            return new javafx.beans.property.ReadOnlyObjectWrapper<>(
                    amount == null || amount == 0 ? null : BigDecimal.valueOf(amount));
        });
        column.setCellFactory(ignored -> new TreeTableCell<>() {
            @Override
            protected void updateItem(BigDecimal amount, boolean empty) {
                super.updateItem(amount, empty);
                setText(empty || amount == null ? null : Columns.money(amount));
                pseudoClassStateChanged(Columns.NEGATIVE, amount != null && amount.signum() < 0);
                getStyleClass().removeAll("party-statement-debit-cell",
                        "party-statement-credit-cell", "party-statement-balance-cell");
                if (!empty) {
                    getStyleClass().add(styleClass);
                }
            }
        });
        return column;
    }

    private static <V> TreeTableColumn<AccountCard, V> named(
            String id, TreeTableColumn<AccountCard, V> column) {
        column.setId(id);
        return column;
    }

    private PartyKind partyKind() {
        return nameAndAccountInterface.partyKind();
    }

    private Button button(String key, AppIcon icon, Runnable action) {
        Button button = new Button(text(key));
        if (icon != null) {
            button.setGraphic(icon.graphic());
        }
        button.getStyleClass().add("app-neutral-button");
        button.setContentDisplay(ContentDisplay.RIGHT);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setId("statement-" + key.replace('.', '-'));
        button.setOnAction(event -> action.run());
        return button;
    }

    private void report(String contextKey, Throwable error) {
        AllAlerts.handleError(text(contextKey),
                error instanceof Exception exception ? exception : new RuntimeException(error));
    }

    private static String text(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }

    @Override
    public String title() {
        return text("party.account.card.title", partyName);
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public boolean addLastPane() {
        return false;
    }

    @Override
    public double minWidth() {
        return 840;
    }

    @Override
    public double minHeight() {
        return 560;
    }

    @Override
    public String dialogStyleClass() {
        return "party-statement-dialog";
    }

    private record StatementSetup(PartyStatementOptions options, LocalDate earliest,
                                  BigDecimal creditLimit) {
    }
}
