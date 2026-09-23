package com.hamza.account.controller.reports;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.CurrencyFormat;
import com.hamza.account.features.currency.difference.ExchangeDifferenceReport;
import com.hamza.account.features.currency.difference.ExchangeDifferenceRow;
import com.hamza.account.features.currency.difference.ExchangeDifferenceService;
import com.hamza.account.features.currency.difference.ExchangeDifferenceSummary;
import com.hamza.account.features.currency.difference.ExchangeMovementLine;
import com.hamza.account.features.party.statement.StatementPeriod;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.PeriodPicker;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.RowDetailDrawer;
import com.hamza.account.table.RowsExcelWriter;
import com.hamza.account.table.TableColumnViews;
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
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressIndicator;
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

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * The exchange differences of every account held in a foreign currency over a period
 * ({@code features/currency/difference}, docs/currency-plan.md §16).
 *
 * <p><b>The screen draws and does not decide.</b> What the average rate is, what a movement realized, what a
 * balance is worth on a day and what a missing rate means are the package's, with a test each. The cards are
 * the report's own totals - the profit and loss statement's two lines under its net profit for the same
 * period - and a row opens its movements in a {@link RowDetailDrawer}, whose realized figures add up to the
 * row's, so "the dollar drawer lost 120 this month" leads straight to the sale that lost it.</p>
 *
 * <p>An amount in an account's own currency is written to that currency's places with its code; an amount in
 * the base with {@code Columns.money}. A figure that needs a rate nobody recorded is blank, and the rate's
 * column says why: never a zero (ق-هـ٥).</p>
 */
public class ExchangeDifferencesController {

    private static final String ACTIONS = "xd-actions";
    private static final String NAME = "xd-name";
    private static final String KIND = "xd-kind";
    private static final String OWN = "xd-own";
    private static final String AVERAGE = "xd-average";
    private static final String RATE = "xd-rate";
    private static final String VALUE = "xd-value";
    private static final String BOOK = "xd-book";
    private static final String REALIZED = "xd-realized";
    private static final String UNREALIZED = "xd-unrealized";
    private static final String CHANGE = "xd-change";
    private static final String RESULT = "xd-result";

    /** What the compact view keeps: the account, what it holds, and what the period made of it. */
    private static final Set<String> COMPACT = Set.of(NAME, KIND, OWN, RATE, REALIZED, CHANGE, RESULT);
    /** The differences the printed totals line sums - never a balance, which is an asset on one row and a debt on the next. */
    private static final Set<String> TOTALLED = Set.of(REALIZED, UNREALIZED, CHANGE, RESULT);
    /** What the row of cards is measured as wide as before it has been laid out. */
    private static final double WRAP_LENGTH = 1300;

    private final ExchangeDifferenceService service;

    private final PeriodPicker period = new PeriodPicker("xd");
    private final Label subtitle = new Label();
    private final Label note = new Label();

    private final Label statRealized = statValue("xd-stat-realized");
    private final Label statChange = statValue("xd-stat-change");
    private final FigureLine statUnrealizedEnd = new FigureLine();
    private final Label statResult = statValue("xd-stat-result");
    private final FigureLine statAccounts = new FigureLine();
    private final Label statTotal = statValue("xd-stat-total");

    private final TableView<ExchangeDifferenceRow> table = new TableView<>();
    private final TableView<ExchangeMovementLine> linesTable = new TableView<>();
    private final ContentSizedColumns<ExchangeDifferenceRow> columnSizing = new ContentSizedColumns<>();
    private final ContentSizedColumns<ExchangeMovementLine> linesSizing = new ContentSizedColumns<>();
    private final MenuButton viewMenu = TableColumnViews.menuButton();
    private final ListToolbar toolbar = new ListToolbar();
    private final ProgressIndicator progress = new ProgressIndicator();

    private RowDetailDrawer drawer;
    private ExchangeDifferenceReport shown;
    /** The currency of the row the drawer shows, which its amounts are written in. */
    private Currency drawerCurrency;
    private int generation;

    public ExchangeDifferencesController(ExchangeDifferenceService service) {
        this.service = service;
    }

    public static ExchangeDifferencesController standard() {
        return new ExchangeDifferencesController(new ExchangeDifferenceService());
    }

    // ---- the screen ------------------------------------------------------------------

    public Pane pane() {
        buildTable();
        columnViews().install(viewMenu, table);

        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("app-container");
        layout.setPadding(new Insets(8));
        layout.setTop(new VBox(8, header(), statCards()));
        layout.setCenter(tableCard());
        BorderPane.setMargin(layout.getCenter(), new Insets(8, 0, 0, 0));

        AnchorPane host = new AnchorPane(layout);
        AnchorPane.setTopAnchor(layout, 0.0);
        AnchorPane.setRightAnchor(layout, 0.0);
        AnchorPane.setBottomAnchor(layout, 0.0);
        AnchorPane.setLeftAnchor(layout, 0.0);
        drawer = RowDetailDrawer.installIn(host);
        drawer.setContent(linesPane());
        drawer.setPreferredWidth(820);

        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        StackPane screen = new StackPane(host, progress);
        screen.getStyleClass().addAll("app-root", "exchange-differences");
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("exchange-differences");
        screen.setPrefSize(1280, 760);

        period.choose(StatementPeriod.THIS_MONTH);
        return screen;
    }

    /** The title and the bar in one card: the period says which report this is, so it stays in the bar. */
    private VBox header() {
        Label title = new Label(text("currency.difference.title"));
        title.getStyleClass().add("report-title");
        subtitle.getStyleClass().add("report-subtitle");
        subtitle.setWrapText(true);
        subtitle.setId("xd-subtitle");
        VBox titles = new VBox(4, title, subtitle);

        period.setOnChange(this::reload);
        toolbar.searchField(period.node())
                .refresh(ListToolbar.refreshButton(this::reload))
                .print(ListToolbar.printButton(this::print))
                .export(ListToolbar.button("party.statement.export.excel", AppIcon.SPREADSHEET, this::exportExcel))
                .view(viewMenu);
        HBox bar = toolbar.installIn(new HBox(8));
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, titles, bar);
        card.getStyleClass().add("app-card");
        return card;
    }

    private FlowPane statCards() {
        FlowPane cards = new FlowPane(12, 10);
        cards.setId("xd-stats");
        cards.setPrefWrapLength(WRAP_LENGTH);
        cards.getChildren().addAll(
                card("currency.difference.stat.realized", statRealized),
                card("currency.difference.stat.change", statChange, statUnrealizedEnd),
                card("currency.difference.stat.result", statResult, statAccounts),
                card("currency.difference.stat.total", statTotal));
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

    private VBox tableCard() {
        note.getStyleClass().add("form-hint");
        note.setWrapText(true);
        note.setId("xd-note");
        // A wrapping label in a VBox is offered one line's height and cut with an ellipsis otherwise.
        note.setMinHeight(Region.USE_PREF_SIZE);
        VBox card = new VBox(8, table, note);
        VBox.setVgrow(table, Priority.ALWAYS);
        card.getStyleClass().add("app-card");
        return card;
    }

    /** Built in code, each title a whole key; the actions first, as on every list here. */
    private void buildTable() {
        table.setId("xd-table");
        table.setPlaceholder(new Label(text("currency.difference.empty")));
        table.setMinHeight(160);
        List<RowAction<ExchangeDifferenceRow>> actions = List.of(new RowAction<>(
                "currency.difference.action.movements", AppIcon.SHOW, "app-neutral-button", null,
                row -> true, this::openLines));
        table.getColumns().setAll(List.of(
                named(ACTIONS, RowActionsColumn.of("party.balances.column.actions", actions)),
                named(NAME, Columns.text("currency.difference.column.account", row -> row.account().name())),
                named(KIND, Columns.text("currency.difference.column.kind",
                        row -> text(row.account().kind().messageKey()))),
                named(OWN, Columns.text("currency.difference.column.own",
                        row -> ownAmount(row.ownEnd(), row.currency()))),
                named(AVERAGE, Columns.text("currency.difference.column.average",
                        row -> CurrencyFormat.indicative(row.averageEnd()))),
                named(RATE, Columns.text("currency.difference.column.rate", this::rateText)),
                named(VALUE, Columns.money("currency.difference.column.value", ExchangeDifferenceRow::valueEnd)),
                named(BOOK, Columns.money("currency.difference.column.book", ExchangeDifferenceRow::bookEnd)),
                named(REALIZED, Columns.money("currency.difference.column.realized",
                        ExchangeDifferenceRow::realizedPeriod)),
                named(UNREALIZED, Columns.money("currency.difference.column.unrealized",
                        ExchangeDifferenceRow::unrealizedEnd)),
                named(CHANGE, Columns.money("currency.difference.column.change",
                        ExchangeDifferenceRow::unrealizedChange)),
                named(RESULT, Columns.money("currency.difference.column.result", ExchangeDifferenceRow::result))));
        for (TableColumn<ExchangeDifferenceRow, ?> column : List.of(table.getColumns().get(3),
                table.getColumns().get(4), table.getColumns().get(5))) {
            column.setStyle(Columns.AMOUNT_ALIGNMENT);
        }
        table.setOnMouseClicked(event -> {
            ExchangeDifferenceRow selected = table.getSelectionModel().getSelectedItem();
            if (event.getClickCount() == 2 && selected != null) {
                openLines(selected);
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((observable, was, row) -> {
            if (row != null && drawer != null && drawer.isShowing()) {
                openLines(row);
            }
        });
        columnSizing.install(table);
        TableSetting.tableMenuSetting(getClass(), table);
        table.setTableMenuButtonVisible(false);
    }

    private TableColumnViews<ExchangeDifferenceRow> columnViews() {
        Preferences preferences = Preferences.userNodeForPackage(ExchangeDifferencesController.class)
                .node("exchange-differences");
        return new TableColumnViews<>(preferences, "view.mode", TableColumnViews.Preset.FULL, COMPACT,
                Set.of(ACTIONS));
    }

    /** One account's movements in the period, after the balance it was brought into it with. */
    private VBox linesPane() {
        linesTable.setId("xd-lines-table");
        linesTable.setPlaceholder(new Label(text("currency.difference.empty")));
        TableColumn<ExchangeMovementLine, String> own = Columns.text("currency.difference.column.movement.own",
                line -> line.own() == null ? "" : ownAmount(line.own(), drawerCurrency));
        TableColumn<ExchangeMovementLine, String> after = Columns.text("currency.difference.column.own.after",
                line -> ownAmount(line.ownAfter(), drawerCurrency));
        TableColumn<ExchangeMovementLine, String> rate = Columns.text("currency.difference.column.movement.rate",
                line -> CurrencyFormat.indicative(line.rate()));
        TableColumn<ExchangeMovementLine, String> average = Columns.text("currency.difference.column.average",
                line -> CurrencyFormat.indicative(line.averageAfter()));
        for (TableColumn<ExchangeMovementLine, String> column : List.of(own, after, rate, average)) {
            column.setStyle(Columns.AMOUNT_ALIGNMENT);
        }
        linesTable.getColumns().setAll(List.of(
                Columns.date("currency.difference.column.date", ExchangeMovementLine::date),
                Columns.text("currency.difference.column.movement", line -> text(line.labelKey())),
                Columns.text("currency.difference.column.number",
                        line -> line.reference() == 0 ? "" : String.valueOf(line.reference())),
                own,
                Columns.money("currency.difference.column.movement.book", ExchangeMovementLine::book),
                rate,
                after,
                average,
                Columns.money("currency.difference.column.movement.realized", ExchangeMovementLine::realized)));
        linesSizing.install(linesTable);
        VBox pane = new VBox(linesTable);
        VBox.setVgrow(linesTable, Priority.ALWAYS);
        return pane;
    }

    // ---- loading ---------------------------------------------------------------------

    private void reload() {
        LocalDate from = period.from();
        LocalDate to = period.to();
        int mine = ++generation;
        progress.setVisible(true);
        Task<ExchangeDifferenceReport> task = new Task<>() {
            @Override
            protected ExchangeDifferenceReport call() throws Exception {
                return service.report(from, to);
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
                report("currency.difference.error.load", task.getException());
            }
        });
        Thread worker = new Thread(task, "exchange-differences-load");
        worker.setDaemon(true);
        worker.start();
    }

    private void show(ExchangeDifferenceReport report) {
        shown = report;
        if (drawer.isShowing()) {
            drawer.hide();
        }
        subtitle.setText(periodSentence(report));
        table.setItems(FXCollections.observableArrayList(report.rows()));
        columnSizing.layout(table);

        ExchangeDifferenceSummary summary = report.summary();
        money(statRealized, summary.realized());
        money(statChange, summary.unrealizedChange());
        statUnrealizedEnd.show("currency.difference.stat.unrealized.end", Columns.money(summary.unrealizedEnd()),
                summary.unrealizedEnd().signum() < 0);
        money(statResult, summary.result());
        statAccounts.show("currency.difference.stat.accounts", String.valueOf(summary.accounts()), false);
        money(statTotal, summary.totalEnd());
        note.setText(noteText(summary));
    }

    private void openLines(ExchangeDifferenceRow row) {
        drawerCurrency = row.currency();
        List<ExchangeMovementLine> lines = new ArrayList<>();
        lines.add(ExchangeMovementLine.broughtForward(shown.from().minusDays(1), row.ownStart(), row.averageStart()));
        lines.addAll(row.lines());
        linesTable.setItems(FXCollections.observableArrayList(lines));
        linesTable.refresh();
        linesSizing.layout(linesTable);
        drawer.show(row.account().name() + " - " + row.currency().code(),
                LanguageManager.getInstance().getString("currency.difference.movements.subtitle", row.lines().size()));
    }

    // ---- printing and export ---------------------------------------------------------

    /** The columns on screen with the differences totalled - what is loaded, not read again. */
    private void print() {
        if (shown == null || shown.rows().isEmpty()) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        File target = TablePdfReport.chooseTarget(table.getScene().getWindow(), text("currency.difference.title"));
        if (target == null) {
            return;
        }
        TablePdfLayout layout = TablePdfLayout.from(table, shown.rows(), Set.of(ACTIONS), TOTALLED, text("total"));
        TablePdfReport.write(target, text("currency.difference.title"),
                periodSentence(shown) + "\n" + noteText(shown.summary()), layout, () -> { });
    }

    private void exportExcel() {
        try {
            if (shown == null || shown.rows().isEmpty()) {
                throw new UserValidationException(text("party.error.no.data.export"));
            }
            List<Object[]> rows = RowsExcelWriter.tableRows(table, Set.of(ACTIONS), shown.rows());
            Object[] headers = rows.remove(0);
            int written = ExportData.exportDataToExcel(rows,
                    new RowsExcelWriter(text("currency.difference.title"), headers, rows));
            if (written >= 1) {
                AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            }
        } catch (Exception e) {
            AllAlerts.handleError(text("currency.difference.error.export"), e);
        }
    }

    // ---- words -----------------------------------------------------------------------

    /** The rate the balance is valued at, or why it is not valued: nothing held needs none. */
    private String rateText(ExchangeDifferenceRow row) {
        if (row.rateEnd() != null) {
            return CurrencyFormat.rate(row.rateEnd());
        }
        return row.ownEnd().signum() == 0 ? "" : text("currency.difference.no.rate");
    }

    private static String ownAmount(BigDecimal amount, Currency currency) {
        if (amount == null || currency == null) {
            return "";
        }
        return CurrencyFormat.amount(amount, currency) + " " + currency.code();
    }

    /** What the figures are, and what they leave out for want of a rate - the codes last, after no full stop. */
    private static String noteText(ExchangeDifferenceSummary summary) {
        String note = text("currency.difference.note");
        if (summary.accountsWithoutRate() == 0) {
            return note;
        }
        return note + "\n" + LanguageManager.getInstance().getString("currency.difference.note.no.rate",
                summary.accountsWithoutRate(), String.join(", ", summary.currenciesWithoutRate()));
    }

    private static String periodSentence(ExchangeDifferenceReport report) {
        return LanguageManager.getInstance().getString("currency.difference.period", dayName(report.from()),
                dayName(report.to()));
    }

    /** "23 سبتمبر 2026": never a yyyy-MM-dd after an Arabic word, which is drawn back to front. */
    private static String dayName(LocalDate day) {
        return DateTimeFormatter.ofPattern("d MMMM yyyy", LanguageManager.getInstance().getCurrentLocale())
                .format(day);
    }

    private static void money(Label label, BigDecimal value) {
        label.setText(Columns.money(value));
        label.pseudoClassStateChanged(Columns.NEGATIVE, value.signum() < 0);
    }

    /** A caption and its figure, the figure a left-to-right label of its own, never inside an Arabic sentence. */
    private static final class FigureLine {
        private final Label caption = subtitleLabel();
        private final Label value = subtitleLabel();
        private final HBox box = new HBox(6, caption, value);

        FigureLine() {
            value.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setVisible(false);
            box.setManaged(false);
        }

        void show(String captionKey, String valueText, boolean negative) {
            caption.setText(text(captionKey));
            value.setText(valueText);
            value.pseudoClassStateChanged(Columns.NEGATIVE, negative);
            box.setVisible(true);
            box.setManaged(true);
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
        Label label = new Label(Columns.money(BigDecimal.ZERO));
        label.getStyleClass().add("stat-value");
        label.setId(id);
        // Left to right, so a loss keeps its minus sign on the side a reader looks for it.
        label.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        return label;
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
