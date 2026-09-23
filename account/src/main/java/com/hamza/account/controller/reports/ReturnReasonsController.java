package com.hamza.account.controller.reports;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.features.export.PdfExportService;
import com.hamza.account.features.export.StatementPdfLayout;
import com.hamza.account.features.party.statement.StatementPeriod;
import com.hamza.account.features.returns.reasons.ReasonTotal;
import com.hamza.account.features.returns.reasons.ReturnDocument;
import com.hamza.account.features.returns.reasons.ReturnReasonsReport;
import com.hamza.account.features.returns.reasons.ReturnReasonsService;
import com.hamza.account.features.returns.reasons.ReturnSide;
import com.hamza.account.features.returns.reasons.ReturnedItem;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.PeriodPicker;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.RowDetailDrawer;
import com.hamza.account.table.RowsExcelWriter;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Why goods come back ({@code features/returns/reasons}): a period's returns of one side by the reason
 * given, and the items that came back most.
 *
 * <p>It replaced a modal window of three unformatted columns whose total was the returns before their own
 * discounts - more than was refunded - and whose service asked no permission and threw Arabic sentences.
 * The screen draws and does not decide: what a return is worth, what a share or a rate is when there is
 * nothing to divide by, and which reason leads are the package's, with a test each.</p>
 *
 * <p>A reason's row opens its returns in a {@link RowDetailDrawer} - which invoice each reversed, for
 * whom, for how much - so "damaged: 14" leads straight to the fourteen. The items beside it are the
 * period's, not the selected reason's: two tables that do not depend on each other's selection.</p>
 */
public class ReturnReasonsController {

    private static final String ACTIONS = "rr-actions";
    private static final String VALUE = "rr-value";
    private static final String COUNT = "rr-count";
    private static final String ITEM_VALUE = "rr-item-value";
    private static final String ITEM_QUANTITY = "rr-item-quantity";
    private static final String NOTHING = "—";
    /** What the row of cards is measured as wide as before it has been laid out. */
    private static final double WRAP_LENGTH = 1300;

    private final ReturnReasonsService service;

    private final ComboBox<ReturnSide> comboSide = new ComboBox<>();
    private final PeriodPicker period = new PeriodPicker("rr");
    private final Label subtitle = new Label();

    private final Label statCount = statValue("rr-stat-count");
    private final Label statValue = statValue("rr-stat-value");
    private final FigureLine statRate = new FigureLine();
    private final Label statWithout = statValue("rr-stat-without");
    private final FigureLine statWithoutValue = new FigureLine();
    private final Label statLeading = new Label();
    private final FigureLine statLeadingValue = new FigureLine();

    private final TableView<ReasonTotal> reasonsTable = new TableView<>();
    private final TableView<ReturnedItem> itemsTable = new TableView<>();
    private final TableView<ReturnDocument> documentsTable = new TableView<>();
    private final ContentSizedColumns<ReturnedItem> itemSizing = new ContentSizedColumns<>();
    private final ContentSizedColumns<ReturnDocument> documentSizing = new ContentSizedColumns<>();
    private final ListToolbar toolbar = new ListToolbar();
    private final ProgressIndicator progress = new ProgressIndicator();

    private RowDetailDrawer drawer;
    private ReturnReasonsReport shown;
    private int generation;
    private int documentsGeneration;

    public ReturnReasonsController(ReturnReasonsService service) {
        this.service = service;
    }

    public static ReturnReasonsController standard() {
        return new ReturnReasonsController(new ReturnReasonsService());
    }

    // ---- the screen ------------------------------------------------------------------

    public Pane pane() {
        buildReasons();
        buildItems();

        SplitPane body = new SplitPane(reasonsCard(), itemsCard());
        body.setDividerPositions(0.5);

        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("app-container");
        layout.setPadding(new Insets(8));
        layout.setTop(new VBox(8, header(), statCards()));
        layout.setCenter(body);
        BorderPane.setMargin(body, new Insets(8, 0, 0, 0));

        AnchorPane host = new AnchorPane(layout);
        AnchorPane.setTopAnchor(layout, 0.0);
        AnchorPane.setRightAnchor(layout, 0.0);
        AnchorPane.setBottomAnchor(layout, 0.0);
        AnchorPane.setLeftAnchor(layout, 0.0);
        drawer = RowDetailDrawer.installIn(host);
        drawer.setContent(documentsPane());
        drawer.setPreferredWidth(720);

        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        StackPane screen = new StackPane(host, progress);
        screen.getStyleClass().addAll("app-root", "return-reasons");
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("return-reasons");
        screen.setPrefSize(1280, 800);

        period.choose(StatementPeriod.THIS_MONTH);
        return screen;
    }

    /**
     * The title on one row and the bar under it, in one card: which side and which period both say which
     * report this is, and beside the title they would not fit a 1366 screen.
     */
    private VBox header() {
        Label title = new Label(text("report.returns.reasons.title"));
        title.getStyleClass().add("report-title");
        subtitle.getStyleClass().add("report-subtitle");
        subtitle.setWrapText(true);
        subtitle.setId("rr-subtitle");
        VBox titles = new VBox(4, title, subtitle);

        comboSide.setId("rr-side");
        comboSide.getItems().setAll(ReturnSide.values());
        comboSide.setConverter(new StringConverter<>() {
            @Override
            public String toString(ReturnSide side) {
                return side == null ? "" : text(side.messageKey());
            }

            @Override
            public ReturnSide fromString(String string) {
                return null;
            }
        });
        comboSide.setValue(ReturnSide.SALES);
        comboSide.setOnAction(event -> reload());
        period.setOnChange(this::reload);
        HBox which = new HBox(12, comboSide, period.node());
        which.setAlignment(Pos.CENTER_LEFT);

        toolbar.searchField(which)
                .refresh(ListToolbar.refreshButton(this::reload))
                .print(ListToolbar.printButton(this::print))
                .export(ListToolbar.button("party.statement.export.excel", AppIcon.SPREADSHEET, this::exportExcel));
        HBox bar = toolbar.installIn(new HBox(8));
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, titles, bar);
        card.getStyleClass().add("app-card");
        return card;
    }

    private FlowPane statCards() {
        FlowPane cards = new FlowPane(12, 10);
        cards.setId("rr-stats");
        cards.setPrefWrapLength(WRAP_LENGTH);
        statLeading.getStyleClass().add("stat-value");
        statLeading.setId("rr-stat-leading");
        cards.getChildren().addAll(
                card("report.returns.reasons.stat.count", statCount),
                card("report.returns.reasons.stat.value", statValue, statRate),
                card("report.returns.reasons.stat.without", statWithout, statWithoutValue),
                card("report.returns.reasons.stat.leading", statLeading, statLeadingValue));
        return cards;
    }

    private static VBox card(String titleKey, Label value, FigureLine... lines) {
        Label title = new Label(text(titleKey));
        title.getStyleClass().add("stat-title");
        VBox card = new VBox(4, title, value);
        for (FigureLine line : lines) {
            card.getChildren().add(line.box);
        }
        card.getStyleClass().addAll("dashboard-tile", "party-stat-card");
        card.setMinWidth(190);
        return card;
    }

    private VBox reasonsCard() {
        Label title = new Label(text("report.returns.reasons.by.reason"));
        title.getStyleClass().add("section-title");
        VBox card = new VBox(8, title, reasonsTable);
        VBox.setVgrow(reasonsTable, Priority.ALWAYS);
        card.getStyleClass().add("app-card");
        card.setMinWidth(420);
        return card;
    }

    private VBox itemsCard() {
        Label title = new Label(text("report.returns.reasons.items.title"));
        title.getStyleClass().add("section-title");
        Label note = new Label(text("report.returns.reasons.items.note"));
        note.getStyleClass().add("form-hint");
        note.setWrapText(true);
        // A wrapping label in a VBox is offered one line's height and cut with an ellipsis otherwise.
        note.setMinHeight(Region.USE_PREF_SIZE);
        VBox card = new VBox(8, title, itemsTable, note);
        VBox.setVgrow(itemsTable, Priority.ALWAYS);
        card.getStyleClass().add("app-card");
        card.setMinWidth(380);
        return card;
    }

    /** A reason, its count, what it came to, its share and its average - the actions first, as on every list. */
    private void buildReasons() {
        reasonsTable.setId("rr-reasons-table");
        reasonsTable.setPlaceholder(new Label(text("report.returns.reasons.empty")));
        reasonsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        List<RowAction<ReasonTotal>> actions = List.of(new RowAction<>("report.returns.reasons.action.documents",
                AppIcon.SHOW, "app-neutral-button", null, reason -> reason.count() > 0, this::openDocuments));
        reasonsTable.getColumns().setAll(List.of(
                named(ACTIONS, RowActionsColumn.of("party.balances.column.actions", actions)),
                Columns.text("report.returns.reasons.column.reason", ReturnReasonsController::label),
                named(COUNT, Columns.number("report.returns.reasons.column.count", ReasonTotal::count)),
                named(VALUE, Columns.money("report.returns.reasons.column.value", ReasonTotal::value)),
                Columns.text("report.returns.reasons.column.share", reason -> percent(shown == null
                        ? Optional.empty() : shown.share(reason))),
                Columns.text("report.returns.reasons.column.average",
                        reason -> reason.average().map(Columns::money).orElse(NOTHING))));
        reasonsTable.setOnMouseClicked(event -> {
            ReasonTotal selected = reasonsTable.getSelectionModel().getSelectedItem();
            if (event.getClickCount() == 2 && selected != null) {
                openDocuments(selected);
            }
        });
        reasonsTable.getSelectionModel().selectedItemProperty().addListener((observable, was, reason) -> {
            if (reason != null && drawer != null && drawer.isShowing()) {
                openDocuments(reason);
            }
        });
        TableSetting.tableMenuSetting(getClass(), reasonsTable);
        reasonsTable.setTableMenuButtonVisible(false);
    }

    private void buildItems() {
        itemsTable.setId("rr-items-table");
        itemsTable.setPlaceholder(new Label(text("report.returns.reasons.empty")));
        itemsTable.getColumns().setAll(List.of(
                Columns.text("report.returns.reasons.column.item", ReturnedItem::name),
                named(ITEM_QUANTITY, Columns.asQuantity(Columns.<ReturnedItem, Double>column(
                        "report.returns.reasons.column.quantity", item -> item.baseQuantity().doubleValue()))),
                named(ITEM_VALUE, Columns.money("report.returns.reasons.column.item.value", ReturnedItem::value)),
                Columns.number("report.returns.reasons.column.returns", ReturnedItem::returns)));
        itemSizing.install(itemsTable);
        TableSetting.tableMenuSetting(getClass(), itemsTable);
        itemsTable.setTableMenuButtonVisible(false);
    }

    private VBox documentsPane() {
        documentsTable.setId("rr-documents-table");
        documentsTable.setPlaceholder(new Label(text("report.returns.reasons.empty")));
        documentsTable.getColumns().setAll(List.of(
                Columns.number("report.returns.reasons.column.number", ReturnDocument::number),
                Columns.date("report.returns.reasons.column.date", ReturnDocument::date),
                Columns.text("report.returns.reasons.column.party", ReturnDocument::party),
                Columns.text("report.returns.reasons.column.source",
                        document -> document.sourceInvoice() > 0 ? String.valueOf(document.sourceInvoice()) : NOTHING),
                Columns.money("report.returns.reasons.column.value", ReturnDocument::value),
                Columns.text("report.returns.reasons.column.notes", ReturnDocument::notes)));
        documentSizing.install(documentsTable);
        VBox pane = new VBox(documentsTable);
        VBox.setVgrow(documentsTable, Priority.ALWAYS);
        return pane;
    }

    // ---- loading ---------------------------------------------------------------------

    private void reload() {
        ReturnSide side = comboSide.getValue();
        LocalDate from = period.from();
        LocalDate to = period.to();
        int mine = ++generation;
        progress.setVisible(true);
        Task<ReturnReasonsReport> task = new Task<>() {
            @Override
            protected ReturnReasonsReport call() throws Exception {
                return service.report(side, from, to);
            }
        };
        task.setOnSucceeded(event -> {
            if (mine == generation) {
                progress.setVisible(false);
                show(task.getValue());
            }
        });
        task.setOnFailed(event -> {
            if (mine == generation) {
                progress.setVisible(false);
                report("report.returns.reasons.error.load", task.getException());
            }
        });
        run(task, "return-reasons-load");
    }

    private static void run(Task<?> task, String name) {
        Thread worker = new Thread(task, name);
        worker.setDaemon(true);
        worker.start();
    }

    private void show(ReturnReasonsReport report) {
        shown = report;
        if (drawer.isShowing()) {
            drawer.hide();
        }
        subtitle.setText(periodSentence(report));
        reasonsTable.setItems(FXCollections.observableArrayList(report.reasons()));
        itemsTable.setItems(FXCollections.observableArrayList(report.items()));
        itemSizing.layout(itemsTable);

        statCount.setText(String.valueOf(report.count()));
        money(statValue, report.value());
        statRate.show(report.side() == ReturnSide.SALES ? "report.returns.reasons.stat.rate.sales"
                : "report.returns.reasons.stat.rate.purchases", percent(report.returnRate()), null);
        ReasonTotal without = report.withoutReason();
        statWithout.setText(String.valueOf(without.count()));
        statWithoutValue.show("report.returns.reasons.stat.worth", Columns.money(without.value()), null);
        Optional<ReasonTotal> leading = report.leadingReason();
        statLeading.setText(leading.map(ReturnReasonsController::label)
                .orElse(text("report.returns.reasons.stat.none")));
        if (leading.isPresent()) {
            statLeadingValue.show("report.returns.reasons.stat.worth", Columns.money(leading.get().value()),
                    percent(report.share(leading.get())));
        } else {
            statLeadingValue.hide();
        }
    }

    private void openDocuments(ReasonTotal reason) {
        if (shown == null || reason.count() == 0) {
            if (drawer.isShowing()) {
                drawer.hide();
            }
            return;
        }
        ReturnReasonsReport report = shown;
        int mine = ++documentsGeneration;
        Task<List<ReturnDocument>> task = new Task<>() {
            @Override
            protected List<ReturnDocument> call() throws Exception {
                return service.documents(report.side(), report.from(), report.to(), reason);
            }
        };
        task.setOnSucceeded(event -> {
            if (mine != documentsGeneration) {
                return;
            }
            List<ReturnDocument> documents = task.getValue();
            documentsTable.setItems(FXCollections.observableArrayList(documents));
            documentSizing.layout(documentsTable);
            drawer.show(label(reason), LanguageManager.getInstance()
                    .getString("report.returns.reasons.documents.subtitle", documents.size()));
        });
        task.setOnFailed(event -> {
            if (mine == documentsGeneration) {
                report("report.returns.reasons.error.load", task.getException());
            }
        });
        run(task, "return-reasons-documents");
    }

    // ---- printing and export ---------------------------------------------------------

    /** The reasons with their total on the band, then the items - what is loaded, not read again. */
    private void print() {
        if (shown == null || shown.isEmpty()) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        File target = TablePdfReport.chooseTarget(reasonsTable.getScene().getWindow(),
                text("report.returns.reasons.title"));
        if (target != null) {
            TablePdfReport.write(target, paper());
        }
    }

    /**
     * The paper, its layouts read from the tables now, on the JavaFX thread; the file is written later,
     * off it, by whoever calls the writer.
     */
    private TablePdfReport.PdfFileWriter paper() {
        String title = text("report.returns.reasons.title");
        List<StatementPdfLayout.Line> lines = new ArrayList<>();
        for (ReasonTotal reason : shown.reasons()) {
            lines.add(new StatementPdfLayout.Line(StatementPdfLayout.Style.ROW, new String[]{label(reason),
                    String.valueOf(reason.count()), Columns.money(reason.value()), percent(shown.share(reason))}));
        }
        lines.add(new StatementPdfLayout.Line(StatementPdfLayout.Style.RESULT, new String[]{text("total"),
                String.valueOf(shown.count()), Columns.money(shown.value()), ""}));
        StatementPdfLayout reasons = new StatementPdfLayout(new String[]{
                text("report.returns.reasons.column.reason"), text("report.returns.reasons.column.count"),
                text("report.returns.reasons.column.value"), text("report.returns.reasons.column.share")},
                new float[]{3, 1, 1.5f, 1}, lines);
        TablePdfLayout items = TablePdfLayout.from(itemsTable, shown.items(), Set.of(), Set.of(ITEM_VALUE),
                text("total"), new TablePdfLayout.NumberFormats(Set.of(), Set.of(ITEM_QUANTITY)));
        String subtitleText = periodSentence(shown) + "\n" + text(shown.side() == ReturnSide.SALES
                ? "report.returns.reasons.stat.rate.sales" : "report.returns.reasons.stat.rate.purchases")
                + ": " + percent(shown.returnRate());
        return file -> new PdfExportService().exportStatementReport(file.getAbsolutePath(), title, subtitleText,
                reasons, items.headers(), items.columnWidths(), items.rows(), items.totals(),
                TablePdfReport.uprightPageSize());
    }

    /** One sheet: the reasons, a blank row, then the items. */
    private void exportExcel() {
        try {
            if (shown == null || shown.isEmpty()) {
                throw new UserValidationException(text("party.error.no.data.export"));
            }
            List<Object[]> rows = RowsExcelWriter.tableRows(reasonsTable, Set.of(ACTIONS), shown.reasons());
            Object[] headers = rows.remove(0);
            rows.add(new Object[]{text("total"), shown.count(), shown.value(), ""});
            rows.add(new Object[]{""});
            rows.addAll(RowsExcelWriter.tableRows(itemsTable, Set.of(), shown.items()));
            int written = ExportData.exportDataToExcel(rows,
                    new RowsExcelWriter(text("report.returns.reasons.title"), headers, rows));
            if (written >= 1) {
                AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            }
        } catch (Exception e) {
            AllAlerts.handleError(text("report.returns.reasons.error.export"), e);
        }
    }

    // ---- words -----------------------------------------------------------------------

    /** The reason in this language, "none given" for none, and a stored value this build does not know as written. */
    private static String label(ReasonTotal reason) {
        if (reason.isWithoutReason()) {
            return text("report.returns.reasons.no.reason");
        }
        return reason.reason().map(known -> known.label()).orElse(reason.storedValue());
    }

    private static String periodSentence(ReturnReasonsReport report) {
        return text(report.side().messageKey()) + "  |  " + LanguageManager.getInstance()
                .getString("report.returns.reasons.period", dayName(report.from()), dayName(report.to()));
    }

    /** "23 سبتمبر 2026": never a yyyy-MM-dd after an Arabic word, which is drawn back to front. */
    private static String dayName(LocalDate day) {
        return DateTimeFormatter.ofPattern("d MMMM yyyy", LanguageManager.getInstance().getCurrentLocale())
                .format(day);
    }

    private static String percent(Optional<BigDecimal> value) {
        return value.map(amount -> amount.toPlainString() + "%").orElse(NOTHING);
    }

    private static void money(Label label, BigDecimal value) {
        label.setText(Columns.money(value));
        label.pseudoClassStateChanged(Columns.NEGATIVE, value.signum() < 0);
    }

    /** A caption and its figures, each a left-to-right label of its own, never inside an Arabic sentence. */
    private static final class FigureLine {
        private final Label caption = subtitleLabel();
        private final Label value = subtitleLabel();
        private final Label extra = subtitleLabel();
        private final HBox box = new HBox(6, caption, value, extra);

        FigureLine() {
            value.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
            extra.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
            box.setAlignment(Pos.CENTER_LEFT);
            hide();
        }

        void show(String captionKey, String valueText, String extraText) {
            caption.setText(text(captionKey));
            value.setText(valueText);
            extra.setText(extraText == null ? "" : extraText);
            extra.setVisible(extraText != null);
            extra.setManaged(extraText != null);
            box.setVisible(true);
            box.setManaged(true);
        }

        void hide() {
            box.setVisible(false);
            box.setManaged(false);
        }

        private static Label subtitleLabel() {
            Label label = new Label();
            label.getStyleClass().add("stat-subtitle");
            return label;
        }
    }

    private static void report(String titleKey, Throwable error) {
        AllAlerts.handleError(text(titleKey), error instanceof Exception exception ? exception : new Exception(error));
    }

    private static <S, V> TableColumn<S, V> named(String id, TableColumn<S, V> column) {
        column.setId(id);
        return column;
    }

    private static Label statValue(String id) {
        Label label = new Label("0");
        label.getStyleClass().add("stat-value");
        label.setId(id);
        label.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        return label;
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
