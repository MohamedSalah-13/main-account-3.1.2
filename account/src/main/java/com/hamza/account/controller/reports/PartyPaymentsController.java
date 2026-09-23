package com.hamza.account.controller.reports;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.payment.PartyPaymentRow;
import com.hamza.account.features.party.payment.PartyPaymentsFilter;
import com.hamza.account.features.party.payment.PartyPaymentsRepository.TreasuryOption;
import com.hamza.account.features.party.payment.PartyPaymentsService;
import com.hamza.account.features.party.payment.PartyPaymentsSummary;
import com.hamza.account.features.party.statement.StatementPeriod;
import com.hamza.account.features.productprofile.ProductFeatureAccess;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.PeriodPicker;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.table.TableSetting;
import com.hamza.account.table.VisibleColumnsExcelWriter;
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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Customers' and suppliers' payments, one screen ({@code features/party/payment}) - the two sidebar
 * entries it replaced were one class opened twice, each in a window of its own, empty until «بحث» was
 * pressed, over today alone.
 *
 * <p><b>Which side is chosen in the bar, among the sides this reader may read</b>
 * ({@link PartyPaymentsService#offeredSides}): the customers' payments are a sales report and the
 * suppliers' a purchases report, each with its own key and its own feature in the edition, and the
 * service asks the side's key again on every read. A reader who may read one side sees no choice.</p>
 *
 * <p>The cards split what came in from what went back the other way - a collection from a refund to a
 * customer - where the old screen showed one net figure. The text finds a party by name, or its code or
 * an allocated invoice by number; the treasury is the one filter beside it.</p>
 */
public class PartyPaymentsController {

    private static final String AMOUNT = "pp-amount";
    /** What the row of cards is measured as wide as before it has been laid out. */
    private static final double WRAP_LENGTH = 1300;

    private final PartyPaymentsService service;
    private final List<PartyKind> sides;
    private final PartyKind preferred;

    private final ComboBox<PartyKind> comboSide = new ComboBox<>();
    private final PeriodPicker period = new PeriodPicker("pp");
    private final TextField search = new TextField();
    private final ComboBox<TreasuryOption> comboTreasury = new ComboBox<>();
    private final Label subtitle = new Label();

    private final Label statCount = statValue("pp-stat-count");
    private final Label statReceived = statValue("pp-stat-received");
    private final Label statReturned = statValue("pp-stat-returned");
    private final Label statNet = statValue("pp-stat-net");
    private final Label titleReceived = statTitle();
    private final Label titleReturned = statTitle();

    private final TableView<PartyPaymentRow> table = new TableView<>();
    private final ContentSizedColumns<PartyPaymentRow> widths = new ContentSizedColumns<>();
    private final ListToolbar toolbar = new ListToolbar();
    private final ProgressIndicator progress = new ProgressIndicator();

    private List<PartyPaymentRow> shown = List.of();
    private PartyPaymentsFilter shownFilter;
    private int generation;
    private boolean arranging;

    /**
     * @param sides     the sides offered, customers first; the screen opens on {@code preferred} when it is
     *                  one of them and on the first otherwise
     */
    public PartyPaymentsController(PartyPaymentsService service, List<PartyKind> sides, PartyKind preferred) {
        this.service = service;
        this.sides = List.copyOf(sides);
        this.preferred = preferred;
    }

    /**
     * The sides this edition carries and this reader may read. With no profile registered - a build that
     * never loaded one - the edition is taken to carry both, as a database without a profile row is.
     */
    public static PartyPaymentsController standard(PartyKind preferred) {
        ProductFeatureAccess features = ServiceRegistry.get(ProductFeatureAccess.class);
        List<PartyKind> sides = PartyPaymentsService.offeredSides(AuthorizationGuard::isGranted,
                feature -> features == null || features.isEnabled(feature));
        return new PartyPaymentsController(new PartyPaymentsService(), sides, preferred);
    }

    // ---- the screen ------------------------------------------------------------------

    public Pane pane() {
        buildTable();

        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        StackPane tableArea = new StackPane(table, progress);
        VBox tableCard = new VBox(tableArea);
        VBox.setVgrow(tableArea, Priority.ALWAYS);
        tableCard.getStyleClass().add("app-card");

        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("app-container");
        layout.setPadding(new Insets(8));
        layout.setTop(new VBox(8, header(), statCards()));
        layout.setCenter(tableCard);
        BorderPane.setMargin(tableCard, new Insets(8, 0, 0, 0));

        StackPane screen = new StackPane(layout);
        screen.getStyleClass().addAll("app-root", "party-payments");
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("party-payments");
        screen.setPrefSize(1280, 800);

        loadTreasuries();
        period.choose(StatementPeriod.THIS_MONTH);
        return screen;
    }

    /** The title on one row and the bar under it: the side and the period say which list this is. */
    private VBox header() {
        Label title = new Label(text("report.party.payments.title"));
        title.getStyleClass().add("report-title");
        subtitle.getStyleClass().add("report-subtitle");
        subtitle.setWrapText(true);
        subtitle.setId("pp-subtitle");
        VBox titles = new VBox(4, title, subtitle);

        comboSide.setId("pp-side");
        comboSide.getItems().setAll(sides);
        comboSide.setConverter(converter(kind -> text(kind == PartyKind.CUSTOMER
                ? "report.party.payments.side.customers" : "report.party.payments.side.suppliers")));
        comboSide.setValue(sides.contains(preferred) ? preferred : sides.isEmpty() ? null : sides.getFirst());
        // A reader who may read one side is not offered a choice of one.
        comboSide.setVisible(sides.size() > 1);
        comboSide.setManaged(sides.size() > 1);
        comboSide.setOnAction(event -> {
            if (!arranging) {
                reload();
            }
        });
        period.setOnChange(this::reload);

        search.setId("pp-search");
        search.setPromptText(text("report.party.payments.search"));
        search.setPrefWidth(220);
        search.setOnAction(event -> reload());
        comboTreasury.setId("pp-treasury");
        comboTreasury.setConverter(converter(treasury -> treasury.id() == 0
                ? text("report.party.payments.all.treasuries") : treasury.name()));
        comboTreasury.setOnAction(event -> {
            if (!arranging) {
                reload();
            }
        });

        HBox which = new HBox(12, comboSide, period.node());
        which.setAlignment(Pos.CENTER_LEFT);
        toolbar.searchField(which, search, comboTreasury)
                .search(ListToolbar.button("search", AppIcon.SEARCH, this::reload))
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
        cards.setId("pp-stats");
        cards.setPrefWrapLength(WRAP_LENGTH);
        cards.getChildren().addAll(
                card(titled("report.party.payments.stat.count"), statCount),
                card(titleReceived, statReceived),
                card(titleReturned, statReturned),
                card(titled("report.party.payments.stat.net"), statNet));
        return cards;
    }

    private static VBox card(Label title, Label value) {
        VBox card = new VBox(4, title, value);
        card.getStyleClass().addAll("dashboard-tile", "party-stat-card");
        card.setMinWidth(190);
        return card;
    }

    /**
     * Built in code, each title a whole key. An id of its own: an id-less table shares its saved widths
     * with every other id-less table in the package.
     */
    private void buildTable() {
        table.setId("partyPaymentsTable");
        table.setPlaceholder(new Label(text("report.party.payments.empty")));
        table.getColumns().setAll(List.of(
                named("partyPaymentDate", Columns.date("report.party.payments.column.date", PartyPaymentRow::date)),
                named("partyPaymentCode", Columns.number("report.party.payments.column.code", PartyPaymentRow::partyId)),
                named("partyPaymentName", Columns.text("report.party.payments.column.name", PartyPaymentRow::partyName)),
                named(AMOUNT, Columns.money("report.party.payments.column.amount", PartyPaymentRow::paid)),
                named("partyPaymentTreasury", Columns.text("report.party.payments.column.treasury",
                        PartyPaymentRow::treasuryName)),
                named("partyPaymentInvoice", Columns.text("report.party.payments.column.invoice",
                        row -> row.invoiceNumber() > 0 ? String.valueOf(row.invoiceNumber())
                                : text("report.party.payments.on.account"))),
                named("partyPaymentUser", Columns.text("report.party.payments.column.user", PartyPaymentRow::userName)),
                named("partyPaymentNotes", Columns.text("report.party.payments.column.notes", PartyPaymentRow::notes))));
        TableSetting.tableMenuSetting(getClass(), table);
        table.setTableMenuButtonVisible(false);
        widths.install(table);
    }

    // ---- loading ---------------------------------------------------------------------

    /** Every treasury, "all" first. A failure leaves the filter at "all" rather than stopping the screen. */
    private void loadTreasuries() {
        PartyKind side = comboSide.getValue();
        TreasuryOption all = new TreasuryOption(0, "");
        arranging = true;
        try {
            comboTreasury.getItems().setAll(all);
            comboTreasury.setValue(all);
        } finally {
            arranging = false;
        }
        if (side == null) {
            return;
        }
        Task<List<TreasuryOption>> task = new Task<>() {
            @Override
            protected List<TreasuryOption> call() throws Exception {
                return service.treasuries(side);
            }
        };
        task.setOnSucceeded(event -> {
            arranging = true;
            try {
                comboTreasury.getItems().addAll(task.getValue());
            } finally {
                arranging = false;
            }
        });
        run(task, "party-payments-treasuries");
    }

    private void reload() {
        PartyKind side = comboSide.getValue();
        if (side == null) {
            return;
        }
        TreasuryOption treasury = comboTreasury.getValue();
        PartyPaymentsFilter filter = new PartyPaymentsFilter(side, period.from(), period.to(), search.getText(),
                treasury == null ? 0 : treasury.id());
        int mine = ++generation;
        progress.setVisible(true);
        Task<List<PartyPaymentRow>> task = new Task<>() {
            @Override
            protected List<PartyPaymentRow> call() throws Exception {
                return service.payments(filter);
            }
        };
        task.setOnSucceeded(event -> {
            if (mine == generation) {
                progress.setVisible(false);
                show(filter, task.getValue());
            }
        });
        task.setOnFailed(event -> {
            if (mine == generation) {
                progress.setVisible(false);
                Throwable error = task.getException();
                AllAlerts.handleError(text("report.error.search.statement.title"),
                        error instanceof Exception exception ? exception : new Exception(error));
            }
        });
        run(task, "party-payments-load");
    }

    private static void run(Task<?> task, String name) {
        Thread worker = new Thread(task, name);
        worker.setDaemon(true);
        worker.start();
    }

    private void show(PartyPaymentsFilter filter, List<PartyPaymentRow> rows) {
        shownFilter = filter;
        shown = rows;
        table.getItems().setAll(rows);
        widths.layout(table);
        subtitle.setText(subtitleOf(filter));

        boolean customers = filter.kind() == PartyKind.CUSTOMER;
        titleReceived.setText(text(customers ? "report.party.payments.stat.received.customers"
                : "report.party.payments.stat.received.suppliers"));
        titleReturned.setText(text(customers ? "report.party.payments.stat.returned.customers"
                : "report.party.payments.stat.returned.suppliers"));
        PartyPaymentsSummary summary = PartyPaymentsSummary.of(rows);
        statCount.setText(String.valueOf(summary.movements()));
        money(statReceived, summary.received());
        money(statReturned, summary.returned());
        money(statNet, summary.total());
    }

    // ---- printing and export ---------------------------------------------------------

    private void print() {
        if (shown.isEmpty()) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        String title = text(shownFilter.kind() == PartyKind.CUSTOMER ? "report.customer.payments.title"
                : "report.supplier.payments.title");
        File target = TablePdfReport.chooseTarget(table.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        TablePdfLayout layout = TablePdfLayout.from(table, shown, Set.of(), Set.of(AMOUNT), text("total"));
        TablePdfReport.write(target, title, subtitleOf(shownFilter), layout, () -> { });
    }

    private void exportExcel() {
        try {
            if (shown.isEmpty()) {
                throw new UserValidationException(text("party.error.no.data.export"));
            }
            String title = text(shownFilter.kind() == PartyKind.CUSTOMER ? "report.customer.payments.title"
                    : "report.supplier.payments.title");
            int written = ExportData.exportDataToExcel(shown,
                    VisibleColumnsExcelWriter.of(title, table, Set.of(), shown));
            if (written >= 1) {
                AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            }
        } catch (Exception e) {
            AllAlerts.handleError(text("report.export.excel"), e);
        }
    }

    // ---- words -----------------------------------------------------------------------

    /** Which side, which period, and what narrows it - the paper carries it as its subtitle. */
    private String subtitleOf(PartyPaymentsFilter filter) {
        StringBuilder sentence = new StringBuilder(text(filter.kind() == PartyKind.CUSTOMER
                ? "report.party.payments.side.customers" : "report.party.payments.side.suppliers"));
        sentence.append("  |  ").append(LanguageManager.getInstance().getString("report.party.payments.period",
                dayName(filter.from()), dayName(filter.to())));
        TreasuryOption treasury = comboTreasury.getValue();
        if (filter.hasTreasury() && treasury != null) {
            sentence.append("  |  ").append(treasury.name());
        }
        if (filter.hasText()) {
            sentence.append("  |  ").append(filter.text());
        }
        return sentence.toString();
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

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : label.apply(value);
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        };
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

    private static Label statTitle() {
        Label label = new Label();
        label.getStyleClass().add("stat-title");
        return label;
    }

    private static Label titled(String key) {
        Label label = statTitle();
        label.setText(text(key));
        return label;
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
