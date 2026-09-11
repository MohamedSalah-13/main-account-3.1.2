package com.hamza.account.controller.name_account;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.controller.model.AccountCard;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.AccountChanged;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.statement.PartyMovementKind;
import com.hamza.account.features.party.statement.PartyStatementFilter;
import com.hamza.account.features.party.statement.PartyStatementPrintData;
import com.hamza.account.features.party.statement.PartyStatementRow;
import com.hamza.account.features.party.statement.PartyStatementService;
import com.hamza.account.features.party.statement.PartyStatementSummary;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.dao.DaoFactory;
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
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
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

/**
 * A party's account statement.
 * <p>
 * <b>Built in code, not from FXML, and every reason is a defect the old screen had.</b>
 * {@code accountDetailsTreeTableView.fxml} carried English captions the controller overwrote at
 * runtime - so three {@code CheckMenuItem}s shipped reading {@code "Unspecified Action"} and every
 * open flashed English first; its columns were bound by field-name strings through
 * {@code PropertyValueFactory}, which answers a renamed field with a silently empty column; its
 * {@code TreeTableView} sat inside a {@code ScrollPane} with a fixed preferred height, so the table
 * never grew and the page had two scrollbars; and its header showed two figures, one labelled as
 * something else and one that nothing ever wrote. A screen assembled here cannot have a caption
 * nobody set or a field nobody fills: there is no second file to fall out of step with.
 * <p>
 * <b>Where the numbers come from.</b> {@link PartyStatementService}, and nowhere else. This screen
 * used to assemble the statement itself from four queries and got the deferred return wrong - see
 * that service for what that cost. It reads through {@code forPrint} rather than {@code search}
 * because it shows the whole filtered statement rather than a page of it, which is also what makes
 * the table, the print and the export one set of rows by construction.
 * <p>
 * What is kept from the old screen is the part that was good: expanding a document row to show its
 * lines, loaded only when the row is opened. A party with two thousand invoices must not read two
 * thousand line tables to render.
 */
@Log4j2
public class AccountDetailsWithItemsController<T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T3, T4> implements AppSettingInterface {

    /** Set on a document row, which is the one kind that can be expanded. Styled in the theme. */
    private static final PseudoClass DOCUMENT_ROW = PseudoClass.getPseudoClass("document-row");

    private final String partyName;
    private final int partyId;
    private final AccountDetailsInterface documentLines;
    private final PartyStatementService statementService = new PartyStatementService();
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);

    /** Rows already expanded, by identity: a second expand must not read the lines again. */
    private final Set<TreeItem<AccountCard>> expanded =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private final TreeTableView<AccountCard> treeView = new TreeTableView<>();
    private final TreeItem<AccountCard> root = new TreeItem<>(new AccountCard());
    private final PartyStatementHeader header = new PartyStatementHeader();
    private final PartyStatementFilterBar filters;
    private final Label status = new Label();
    private final Label narrowed = new Label(text("party.statement.narrowed"));
    private final CheckBox expandAll = new CheckBox(text("party.check.show.all"));
    private final CheckBox printDetails = new CheckBox(text("party.check.show.print.details"));
    private final ProgressIndicator progress = new ProgressIndicator();
    private final StackPane content = new StackPane();

    /** The statement on screen, so the print and both exports describe exactly what is shown. */
    private PartyStatementPrintData statement =
            new PartyStatementPrintData(List.of(), PartyStatementSummary.EMPTY, false);

    /** Discards the answer to a search the user has already replaced. */
    private int generation;

    /**
     * Opens the statement of one party.
     * <p>
     * It takes the party's id and name rather than a {@code T4} movement to read them out of.
     * The caller had to fabricate a movement to satisfy the old signature - one carrying an id
     * and nothing else - so {@code accountData.getName(...)} answered null and the window opened
     * titled "كشف حساب - null". A screen that needs a party should ask for a party.
     */
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

    // ---- the screen ------------------------------------------------------------------

    @Override
    public Pane pane() {
        buildTree();
        filters.setOnSearch(this::load);

        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("app-container");
        layout.setTop(new VBox(8, toolbar(), header, filters));
        layout.setCenter(content);
        layout.setBottom(footer());
        BorderPane.setMargin(content, new Insets(8, 0, 8, 0));

        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        content.getChildren().addAll(treeView, progress);

        StackPane screen = new StackPane(layout);
        screen.getStyleClass().add("app-root");
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("party-statement");

        // Subscribed here, where the node exists: a movement saved or deleted anywhere must reach
        // an open statement. The old screen published AccountChanged on delete and subscribed to
        // nothing, so a deleted row stayed on the page until it was closed and reopened.
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(AccountChanged.class, event -> {
                if (event.kind() == partyKind()) {
                    reload();
                }
            }));
            subscriptions.disposeWith(screen);
        }
        Platform.runLater(this::openOnTheWholeHistory);
        return screen;
    }

    private FlowPane toolbar() {
        Button refresh = button("refresh", AppIcon.REFRESH, this::reload);
        Button print = button("print", AppIcon.PRINT, this::print);
        Button pdf = button("party.btn.export.pdf", AppIcon.EXPORT, this::exportPdf);
        pdf.getStyleClass().setAll("pdf-button");
        Button excel = button("party.statement.export.excel", AppIcon.SPREADSHEET, this::exportExcel);
        excel.getStyleClass().setAll("excel-button");

        Label name = new Label(partyName);
        name.getStyleClass().add("app-section-title");
        name.setId("statement-party-name");

        expandAll.setOnAction(event -> {
            if (expandAll.isSelected()) {
                root.getChildren().forEach(child -> child.setExpanded(true));
            }
        });

        // A FlowPane for the same reason as the filter row: in a dialog narrower than the main
        // window an HBox squeezed the two checkboxes into each other, so they overlapped and read
        // as one unintelligible caption. Wrapping keeps every control whole at any width.
        FlowPane bar = new FlowPane(8, 8, name, expandAll, printDetails, refresh, print, pdf, excel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("app-card");
        return bar;
    }

    private HBox footer() {
        status.setId("statement-status");
        status.getStyleClass().add("status-label");
        narrowed.getStyleClass().add("form-label");
        narrowed.setVisible(false);
        narrowed.setManaged(false);
        HBox bar = new HBox(14, status, narrowed);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("summary-card");
        return bar;
    }

    /**
     * The columns, built in code.
     * <p>
     * Each one names a method rather than a field, so the compiler checks it - rule ق-ل1 of
     * {@code docs/new-code-rules.md}. The amounts are {@code Columns.money}, which is the one
     * definition of how a figure is written here: two decimals, thousands separated, right-aligned,
     * negatives red. The old columns printed whatever {@code Double.toString} produced.
     */
    private void buildTree() {
        treeView.setId("statement-table");
        treeView.setShowRoot(false);
        treeView.setRoot(root);
        treeView.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        treeView.setPlaceholder(new Label(text("party.statement.empty")));
        root.setExpanded(true);
        VBox.setVgrow(treeView, Priority.ALWAYS);

        treeView.getColumns().setAll(List.of(
                tree("date", AccountCard::getDate),
                kindColumn(),
                tree("party.statement.column.reference", card -> card.getId() == 0
                        ? "" : String.valueOf(card.getId())),
                money("common.debtor", AccountCard::getPurchase),
                money("common.creditor", AccountCard::getPaid),
                money("party.statement.column.running", AccountCard::getDetails),
                tree("party.statement.column.treasury", AccountCard::getName),
                tree("column.notes", AccountCard::getNotes)));

        treeView.setRowFactory(view -> new TreeTableRow<>() {
            @Override
            protected void updateItem(AccountCard card, boolean empty) {
                super.updateItem(card, empty);
                pseudoClassStateChanged(DOCUMENT_ROW,
                        !empty && card != null && card.hasDocumentLines());
            }
        });
        TableSetting.tableMenuSetting(getClass(), treeView);
    }

    /**
     * The movement's kind, as a translated label from the enum.
     * <p>
     * The old screen decided what to colour and what could be expanded by comparing this column's
     * text against the Arabic literals {@code "المبيعات"} and {@code "مرتجع المبيعات"} - so a user
     * on the English bundle got neither. The text is for reading; {@link AccountCard#getKind()} is
     * what anything decides on.
     */
    private TreeTableColumn<AccountCard, String> kindColumn() {
        return tree("party.statement.column.kind", card -> card.getKind() == null
                ? "" : text(card.getKind().messageKey()));
    }

    // ---- loading ---------------------------------------------------------------------

    private void openOnTheWholeHistory() {
        try {
            filters.initialise(statementService.options(partyKind()),
                    statementService.earliestMovement(partyKind(), partyId));
        } catch (Exception e) {
            report(e);
        }
    }

    private void reload() {
        load(filters.filter(0, PartyStatementFilter.DEFAULT_PAGE_SIZE));
    }

    /**
     * Reads the statement for a filter, off the JavaFX thread.
     * <p>
     * The old screen read it inline, so a party with a long history froze the window while the four
     * queries ran. A {@code generation} token discards the answer to a search the user has already
     * replaced - the same guard {@code MasterDataPane} and {@code ItemSuggestionField} use, and
     * without it a slow query can overwrite the result of a faster, later one.
     */
    private void load(PartyStatementFilter filter) {
        int token = ++generation;
        progress.setVisible(true);
        Task<PartyStatementPrintData> task = new Task<>() {
            @Override
            protected PartyStatementPrintData call() throws Exception {
                return statementService.forPrint(filter);
            }
        };
        task.setOnSucceeded(event -> {
            if (token == generation) {
                progress.setVisible(false);
                show(task.getValue(), filter);
            }
        });
        task.setOnFailed(event -> {
            if (token == generation) {
                progress.setVisible(false);
                report(task.getException());
            }
        });
        Thread thread = new Thread(task, "party-statement-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void show(PartyStatementPrintData loaded, PartyStatementFilter filter) {
        statement = loaded;
        header.show(loaded.summary());
        showCreditLimit(loaded.summary());
        buildRows(loaded.rowsOldestFirst());

        status.setText(LanguageManager.getInstance()
                .getString("party.statement.rows", loaded.rows().size()));
        boolean hidden = filter.narrowsRows() && !loaded.summary().rowsExplainTheBalance();
        narrowed.setVisible(hidden);
        narrowed.setManaged(hidden);

        if (loaded.truncated()) {
            AllAlerts.handleError(text("party.statement.title"), new UserValidationException(
                    LanguageManager.getInstance().getString("party.statement.truncated",
                            PartyStatementService.PRINT_LIMIT)));
        }
    }

    private void showCreditLimit(PartyStatementSummary summary) {
        if (partyKind() != PartyKind.CUSTOMER) {
            header.showCreditLimit(null, null);
            return;
        }
        try {
            T3 party = nameAndAccountInterface.getNameById(partyId);
            header.showCreditLimit(
                    BigDecimal.valueOf(nameService.getCredit(List.of(party), partyId)),
                    summary.closingBalance());
        } catch (Exception e) {
            report(e);
        }
    }

    /**
     * One tree row per statement row, oldest first.
     * <p>
     * Oldest first because that is the order the running balance was accumulated in, so each row's
     * balance follows from the one above rather than contradicting it.
     */
    private void buildRows(List<PartyStatementRow> rows) {
        expanded.clear();
        root.getChildren().clear();
        for (PartyStatementRow row : rows) {
            TreeItem<AccountCard> item = new TreeItem<>(toCard(row));
            root.getChildren().add(item);
            if (row.hasDocumentLines()) {
                // A placeholder gives the row its disclosure arrow; the lines are read when it opens.
                item.getChildren().add(new TreeItem<>(new AccountCard()));
                item.expandedProperty().addListener((observable, was, open) -> {
                    if (open) {
                        loadDocumentLines(item);
                    }
                });
            }
        }
        if (expandAll.isSelected()) {
            root.getChildren().forEach(child -> child.setExpanded(true));
        }
    }

    private void loadDocumentLines(TreeItem<AccountCard> item) {
        if (!expanded.add(item)) {
            return;
        }
        item.getChildren().clear();
        try {
            documentLines.addTreeItemTotals(item.getValue(), item);
        } catch (Exception e) {
            expanded.remove(item);
            report(e);
        }
    }

    /** A statement row as a tree row: the label for the eye, the kind for every decision. */
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
        card.setNotes(row.notes());
        return card;
    }

    // ---- printing and exporting -------------------------------------------------------

    /**
     * Prints the statement on screen.
     * <p>
     * Every row of it: {@link #statement} is the whole filtered extract rather than a page, so the
     * printed page and the screen cannot differ. Ticking "show details" includes the expanded
     * document lines, which are the rows with no movement kind of their own.
     */
    private void print() {
        List<AccountCard> rows = printDetails.isSelected()
                ? allRows(root)
                : root.getChildren().stream().map(TreeItem::getValue).toList();
        if (rows.isEmpty()) {
            report(new UserValidationException(text("party.error.no.data.export")));
            return;
        }
        printReports.printAccountStatement(rows, true, text("party.statement.title"), partyName, null);
    }

    private void exportExcel() {
        try {
            requireRows();
            int written = ExportData.exportDataToExcel(statement.rows(),
                    new PartyStatementExcelWriter(statement.rows(), statement.summary()));
            if (written < 1) {
                throw new BusinessRuleException(text("party.error.cannot.save"));
            }
            AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
        } catch (Exception e) {
            report(e);
        }
    }

    private void exportPdf() {
        try {
            requireRows();
            FileChooser chooser = new FileChooser();
            chooser.setTitle(text("party.dialog.save.report"));
            chooser.setInitialFileName(partyName + "_" + LocalDate.now() + ".pdf");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            File file = chooser.showSaveDialog(null);
            if (file == null) {
                return;
            }
            if (!new PartyStatementPdfExporter().export(statement, partyName, file.getAbsolutePath())) {
                throw new BusinessRuleException(text("party.error.export.generic"));
            }
            AllAlerts.alertSaveWithMessage(LanguageManager.getInstance()
                    .getString("party.export.success.saved.at", file.getAbsolutePath()));
        } catch (Exception e) {
            report(e);
        }
    }

    /** Refuses an export of nothing out loud, rather than writing an empty file and saying it saved. */
    private void requireRows() throws UserValidationException {
        if (statement.rows().isEmpty()) {
            throw new UserValidationException(text("party.error.no.data.export"));
        }
    }

    /** Every row under the root, the root itself excluded - it stands for the tree, not a movement. */
    private List<AccountCard> allRows(TreeItem<AccountCard> item) {
        List<AccountCard> rows = new java.util.ArrayList<>();
        if (item != root && item.getValue() != null) {
            rows.add(item.getValue());
        }
        item.getChildren().forEach(child -> rows.addAll(allRows(child)));
        return rows;
    }

    // ---- plumbing --------------------------------------------------------------------

    private PartyKind partyKind() {
        return nameAndAccountInterface.partyKind();
    }

    private Button button(String key, AppIcon icon, Runnable action) {
        Button button = new Button(text(key), icon.graphic());
        button.getStyleClass().add("app-neutral-button");
        button.setId("statement-" + key.replace('.', '-'));
        button.setOnAction(event -> action.run());
        return button;
    }

    private <T> TreeTableColumn<AccountCard, String> tree(
            String titleKey, java.util.function.Function<AccountCard, String> value) {
        TreeTableColumn<AccountCard, String> column = new TreeTableColumn<>(text(titleKey));
        column.setCellValueFactory(features -> new javafx.beans.property.ReadOnlyObjectWrapper<>(
                value.apply(features.getValue().getValue())));
        return column;
    }

    private TreeTableColumn<AccountCard, BigDecimal> money(
            String titleKey, java.util.function.Function<AccountCard, Double> value) {
        TreeTableColumn<AccountCard, BigDecimal> column = new TreeTableColumn<>(text(titleKey));
        column.setStyle("-fx-alignment: CENTER-RIGHT;");
        column.setCellValueFactory(features -> {
            Double amount = value.apply(features.getValue().getValue());
            return new javafx.beans.property.ReadOnlyObjectWrapper<>(
                    amount == null || amount == 0 ? null : BigDecimal.valueOf(amount));
        });
        column.setCellFactory(ignored -> new javafx.scene.control.TreeTableCell<>() {
            @Override
            protected void updateItem(BigDecimal amount, boolean empty) {
                super.updateItem(amount, empty);
                setText(empty || amount == null ? null : Columns.money(amount));
                pseudoClassStateChanged(Columns.NEGATIVE, amount != null && amount.signum() < 0);
            }
        });
        return column;
    }

    private void report(Throwable e) {
        AllAlerts.handleError(text("party.error.load.account.details.items"),
                e instanceof Exception exception ? exception : new RuntimeException(e));
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }

    @Override
    public String title() {
        return LanguageManager.getInstance().getString("party.account.card.title", partyName);
    }

    @Override
    public boolean resize() {
        return true;
    }
}
