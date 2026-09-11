package com.hamza.account.controller.name_account;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.ageing.AgeingBucket;
import com.hamza.account.features.party.ageing.PartyAgeingFilter;
import com.hamza.account.features.party.ageing.PartyAgeingPage;
import com.hamza.account.features.party.ageing.PartyAgeingRow;
import com.hamza.account.features.party.ageing.PartyAgeingService;
import com.hamza.account.features.party.ageing.PartyAgeingSummary;
import com.hamza.account.features.party.balances.PartyAreaOption;
import com.hamza.account.features.party.balances.PartyBalanceService;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.PageJumpBox;
import com.hamza.account.table.TableColumnViews;
import com.hamza.account.table.TableSetting;
import com.hamza.account.table.VisibleColumnsExcelWriter;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.Columns;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;

import static com.hamza.controlsfx.others.Utils.setOptionalNumberFormatter;

/**
 * The ageing report: what every party owes, split by how overdue it is.
 *
 * <p><b>The screen displays and does not decide.</b> Every band, every total and the
 * reconciliation are {@code features/party/ageing}'s, computed in SQL and checked in
 * {@code PartyAgeingRow}'s constructor. That is the rule phase D of {@code docs/party-plan.md}
 * sets for every report it adds, and it is why this class has no arithmetic in it at all.
 *
 * <p><b>The column a reader will not expect is "unallocated".</b> It is what the party owes
 * that sits on no open invoice - their opening balance, their returns, and above all payments
 * taken on account. It is usually negative, and a big negative number is not an error: it is
 * the report saying that allocating those payments to invoices would sharpen every other column
 * on the row. The five bands plus that column equal the balance exactly, which is what makes the
 * report checkable at a glance.
 *
 * <p><b>It wears what the accounts screen wears</b>, because it opens from it and reads the same
 * parties: the customer's or supplier's header and colours, its list actions after the filters
 * that decide the list rather than on a bar of their own, the "العرض" menu, columns as wide as
 * what they hold, and a PDF and a spreadsheet of the columns on screen - {@code TableColumnViews},
 * {@code ContentSizedColumns}, {@code PartyListPdfLayout} and {@code VisibleColumnsExcelWriter},
 * the same four pieces, not copies of them.
 *
 * <p>Loading is off the JavaFX thread with a {@code generation} token that discards the answer to
 * a search the user has already replaced - as {@code AccountController2} and {@code MasterDataPane}
 * do.
 */
@Log4j2
public class PartyAgeingController<T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T3, T4> implements AppSettingInterface {

    private static final String UNALLOCATED_COLUMN = "ageing-unallocated";
    private static final String BALANCE_COLUMN = "ageing-balance";

    private final PartyAgeingService ageingService = new PartyAgeingService();
    private final PartyBalanceService balanceService = new PartyBalanceService();

    private final TableView<PartyAgeingRow> table = new TableView<>();
    private final DatePicker asOf = new DatePicker(LocalDate.now());
    private final ComboBox<PartyAreaOption> comboArea = new ComboBox<>();
    private final CheckBox overdueOnly = new CheckBox(text("party.ageing.filter.overdue.only"));
    private final CheckBox includeSettled = new CheckBox(text("party.ageing.filter.include.settled"));
    private final TextField minimumBalance = new TextField();
    private final TextField search = new TextField();

    private final Label statParties = statValue("ageing-parties");
    private final Label statOverdue = statValue("ageing-overdue");
    private final Label statPercent = statValue("ageing-percent");
    private final Label statUnallocated = statValue("ageing-unallocated-total");

    private final Label countLabel = new Label();
    private final Button previous = new Button();
    private final Button next = new Button();
    private final PageJumpBox pageJump = new PageJumpBox(this::search);
    private final ProgressIndicator progress = new ProgressIndicator();
    private final StackPane content = new StackPane();

    private final ContentSizedColumns<PartyAgeingRow> columnSizing = new ContentSizedColumns<>();
    private final MenuButton viewMenu = TableColumnViews.menuButton();

    private PartyAgeingFilter filter;
    private PartyAgeingSummary summary = PartyAgeingSummary.EMPTY;
    private int generation;
    private boolean loading;

    public PartyAgeingController(DaoFactory daoFactory, DataPublisher dataPublisher,
                                 DataInterface<?, ?, T3, T4> dataInterface) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.filter = PartyAgeingFilter.today(partyKind());
    }

    // ---- the screen ------------------------------------------------------------------

    @Override
    public Pane pane() {
        PartyScreenIdentity identity = identity();
        buildTable();
        columnViews().install(viewMenu, table);

        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("app-container");
        layout.setTop(new VBox(8, PartyIdentityHeader.of(identity.ageingProfile()), statCards(),
                filterBar()));
        layout.setCenter(content);
        layout.setBottom(footer());
        BorderPane.setMargin(content, new Insets(8, 0, 8, 0));

        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        content.getChildren().addAll(table, progress);

        StackPane screen = new StackPane(layout);
        screen.getStyleClass().addAll("app-root", identity.styleClass());
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("party-ageing");

        Platform.runLater(this::loadAreasAndRows);
        return screen;
    }

    /**
     * The four figures, and the one that answers the question the report is opened for.
     * <p>
     * "Overdue" is the four late bands, never the balance - a party can owe nothing on balance
     * and still have an invoice ninety days old, offset by a payment nobody allocated.
     */
    private FlowPane statCards() {
        FlowPane cards = new FlowPane(12, 10);
        cards.setId("ageing-stats");
        cards.getChildren().addAll(
                card("party.ageing.stat.parties", statParties),
                card("party.ageing.stat.overdue", statOverdue),
                card("party.ageing.stat.percent", statPercent),
                card("party.ageing.stat.unallocated", statUnallocated));
        return cards;
    }

    /** The accounts screen's figure card, without clickable-card: these narrow nothing. */
    private VBox card(String titleKey, Label value) {
        Label title = new Label(text(titleKey));
        title.getStyleClass().add("stat-title");
        VBox box = new VBox(4, title, value);
        box.getStyleClass().addAll("dashboard-tile", "party-stat-card");
        box.setMinWidth(150);
        return box;
    }

    private FlowPane filterBar() {
        DateSetting.dateAction(asOf);
        asOf.setId("ageing-as-of");
        asOf.setOnAction(event -> reload());

        comboArea.setId("ageing-area");
        comboArea.setConverter(converter(option -> option == null || option.id() == 0
                ? text("party.statement.filter.all") : option.name()));
        comboArea.setOnAction(event -> reload());

        overdueOnly.setOnAction(event -> reload());
        includeSettled.setOnAction(event -> reload());

        // A filter field, not an entry field: an untouched box means "no floor", not zero.
        setOptionalNumberFormatter(minimumBalance);
        minimumBalance.setPromptText(text("party.ageing.filter.minimum"));
        minimumBalance.setId("ageing-minimum");
        search.setPromptText(text("party.balances.filter.text"));
        search.setId("ageing-search");

        Button apply = new Button(text("search"), AppIcon.SEARCH.graphic());
        apply.getStyleClass().addAll("app-primary-button", "party-primary-button");
        apply.setMinWidth(Region.USE_PREF_SIZE);
        apply.setId("ageing-apply");
        apply.setOnAction(event -> reload());

        Button clear = new Button(text("party.statement.filter.reset"), AppIcon.CLEAR.graphic());
        clear.getStyleClass().add("app-neutral-button");
        clear.setMinWidth(Region.USE_PREF_SIZE);
        clear.setOnAction(event -> reset());

        com.hamza.controlsfx.others.Utils.whenEnterPressed(minimumBalance, search, apply);

        FlowPane bar = new FlowPane(8, 8,
                caption("party.ageing.filter.as.of"), asOf,
                caption("party.column.area"), comboArea,
                minimumBalance, search, overdueOnly, includeSettled, apply, clear);
        bar.getChildren().addAll(listActions());
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().addAll("app-card", "party-form-card");
        search.setPrefWidth(220);
        return bar;
    }

    /**
     * What acts on the list, after the filters that decide what the list is - where the accounts
     * screen puts its own. It used to be a bar of its own above the figures, holding two buttons.
     */
    private Node[] listActions() {
        Button refresh = button("refresh", AppIcon.REFRESH, this::reload);
        Button print = button("print", AppIcon.PRINT, this::print);
        Button excel = button("party.statement.export.excel", AppIcon.SPREADSHEET, this::exportExcel);

        Separator divider = new Separator(Orientation.VERTICAL);
        divider.getStyleClass().add("modern-separator");
        return new Node[]{divider, refresh, print, excel, viewMenu};
    }

    /**
     * The columns, with the five bands generated from the enum, each with an id - the view menu
     * names columns by it, the printed totals line sums by it, and TableSetting keys a column's
     * saved visibility by it.
     * <p>
     * Written out by hand the bands would be five places to forget one, and the print and the
     * export are read from these same columns - so a band added to {@code AgeingBucket} appears on
     * the screen, the paper and in the file without any of them being edited.
     */
    private void buildTable() {
        table.setId("ageing-table-" + partyKind().name().toLowerCase());
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setPlaceholder(new Label(text("party.ageing.empty")));

        List<TableColumn<PartyAgeingRow, ?>> columns = new ArrayList<>(List.of(
                named("ageing-code", Columns.number("code", PartyAgeingRow::partyId)),
                named("ageing-name", Columns.text("name", PartyAgeingRow::name)),
                named("ageing-area", Columns.text("party.column.area", PartyAgeingRow::areaName)),
                named("ageing-terms", Columns.number("party.ageing.column.terms", PartyAgeingRow::paymentTerms))));
        for (AgeingBucket bucket : AgeingBucket.inReadingOrder()) {
            columns.add(named(bucketColumn(bucket),
                    Columns.money(bucket.messageKey(), row -> row.amount(bucket))));
        }
        columns.add(named(UNALLOCATED_COLUMN,
                Columns.money("party.ageing.column.unallocated", PartyAgeingRow::unallocated)));
        columns.add(named(BALANCE_COLUMN,
                Columns.money("party.ageing.column.balance", PartyAgeingRow::balance)));
        table.getColumns().setAll(columns);

        columnSizing.install(table);
        TableSetting.tableMenuSetting(getClass(), table);
        // The view menu offers these choices by name; JavaFX's header menu would be a second copy.
        table.setTableMenuButtonVisible(false);
    }

    /**
     * The "العرض" menu. It opens on the full view - what the report showed before the menu - and
     * the compact one keeps the party, the bands and the balance: what the report is opened for.
     */
    private TableColumnViews<PartyAgeingRow> columnViews() {
        Preferences preferences = Preferences.userNodeForPackage(PartyAgeingController.class)
                .node("ageing-" + partyKind().name().toLowerCase());
        Set<String> compact = new HashSet<>(Set.of("ageing-code", "ageing-name", BALANCE_COLUMN));
        for (AgeingBucket bucket : AgeingBucket.values()) {
            compact.add(bucketColumn(bucket));
        }
        return new TableColumnViews<>(preferences, "view.mode", TableColumnViews.Preset.FULL,
                compact, Set.of());
    }

    /** The money columns - the bands, unallocated and the balance - which a printed total sums. */
    private static Set<String> totalledColumns() {
        Set<String> totalled = new HashSet<>(Set.of(UNALLOCATED_COLUMN, BALANCE_COLUMN));
        for (AgeingBucket bucket : AgeingBucket.values()) {
            totalled.add(bucketColumn(bucket));
        }
        return totalled;
    }

    private static String bucketColumn(AgeingBucket bucket) {
        return "ageing-" + bucket.name().toLowerCase(java.util.Locale.ROOT);
    }

    private HBox footer() {
        previous.setText(text("masterdata.previous"));
        next.setText(text("masterdata.next"));
        previous.getStyleClass().add("app-neutral-button");
        next.getStyleClass().add("app-neutral-button");
        previous.setOnAction(event -> search(filter.page() - 1));
        next.setOnAction(event -> search(filter.page() + 1));

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(12, countLabel, spacer, previous, pageJump, next);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().addAll("summary-card", "party-summary-bar");
        return bar;
    }

    // ---- loading ---------------------------------------------------------------------

    private void loadAreasAndRows() {
        try {
            List<PartyAreaOption> areas = new ArrayList<>();
            areas.add(new PartyAreaOption(0, ""));
            areas.addAll(balanceService.areas(partyKind()));
            loading = true;
            comboArea.setItems(FXCollections.observableArrayList(areas));
            comboArea.getSelectionModel().selectFirst();
            loading = false;
        } catch (Exception e) {
            report(e);
        }
        search(0);
    }

    private void reload() {
        if (!loading) {
            search(0);
        }
    }

    private void reset() {
        loading = true;
        asOf.setValue(LocalDate.now());
        comboArea.getSelectionModel().selectFirst();
        overdueOnly.setSelected(false);
        includeSettled.setSelected(false);
        minimumBalance.clear();
        search.clear();
        loading = false;
        search(0);
    }

    /**
     * Reads one page off the JavaFX thread.
     * <p>
     * The {@code generation} token is what makes a fast typist safe: a search the user has already
     * replaced still finishes, and its answer is thrown away rather than painted over the newer one.
     */
    private void search(int page) {
        filter = currentFilter(page);
        int mine = ++generation;
        progress.setVisible(true);

        Task<PartyAgeingPage> task = new Task<>() {
            @Override
            protected PartyAgeingPage call() throws Exception {
                return ageingService.search(filter);
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
                report(task.getException());
            }
        });
        Thread worker = new Thread(task, "party-ageing");
        worker.setDaemon(true);
        worker.start();
    }

    private PartyAgeingFilter currentFilter(int page) {
        PartyAreaOption area = comboArea.getValue();
        return new PartyAgeingFilter(
                partyKind(),
                asOf.getValue() == null ? LocalDate.now() : asOf.getValue(),
                area == null || area.id() == 0 ? null : area.id(),
                overdueOnly.isSelected(),
                includeSettled.isSelected(),
                amount(minimumBalance),
                search.getText(),
                Math.max(0, page),
                PartyAgeingFilter.DEFAULT_PAGE_SIZE);
    }

    private void show(PartyAgeingPage loaded) {
        summary = loaded.summary();
        table.setItems(FXCollections.observableArrayList(loaded.rows()));
        columnSizing.layout(table);

        statParties.setText(String.valueOf(summary.parties()));
        statOverdue.setText(Columns.money(summary.overdue()));
        statPercent.setText(summary.overduePercent() + "%");
        statUnallocated.setText(Columns.money(summary.unallocated()));

        countLabel.setText(LanguageManager.getInstance()
                .getString("party.balances.count", loaded.rows().size(), summary.parties()));
        pageJump.showing(loaded.page(),
                AccountController2.pageCount(summary.parties(), filter.pageSize()));
        previous.setDisable(!loaded.hasPrevious());
        next.setDisable(!loaded.hasNext());
    }

    // ---- print and export ------------------------------------------------------------

    /**
     * The whole filtered set as a PDF of the columns on screen, with a totals line under the bands,
     * unallocated and the balance - the same rule and the same pieces as the accounts screen's
     * print. Read again through {@code forExport}, which also requires the report permission: a
     * file leaves the building whether it is a spreadsheet or a page.
     */
    private void print() {
        String title = identity().ageingProfile().title();
        File target = PartyPdfReport.chooseTarget(table.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        PartyAgeingFilter printed = filter;
        Task<PartyAgeingPage> load = new Task<>() {
            @Override
            protected PartyAgeingPage call() throws Exception {
                return ageingService.forExport(printed);
            }
        };
        load.setOnSucceeded(event -> {
            PartyAgeingPage extract = load.getValue();
            if (extract.rows().isEmpty()) {
                AllAlerts.alertError(text("party.error.no.data.print"));
                return;
            }
            PartyListPdfLayout layout = PartyListPdfLayout.from(table, extract.rows(), Set.of(),
                    totalledColumns(), text("total"));
            PartyPdfReport.write(target, title,
                    text("party.ageing.filter.as.of") + ": " + printed.asOf(), layout,
                    () -> warnIfTruncated(extract));
        });
        AllAlerts.handleTaskFailure(text("party.error.export.generic"), load);
        PartyPdfReport.start(load, "party-ageing-pdf-load");
    }

    /**
     * The whole filtered set, read again rather than taken from the table, in the columns on
     * screen. {@code forExport} also requires the report permission on top of the screen's.
     */
    private void exportExcel() {
        try {
            PartyAgeingPage extract = ageingService.forExport(filter);
            if (extract.rows().isEmpty()) {
                throw new UserValidationException(text("party.error.no.data.export"));
            }
            int written = ExportData.exportDataToExcel(extract.rows(),
                    VisibleColumnsExcelWriter.of(text("party.ageing.export.sheet"), table,
                            Set.of(), extract.rows()));
            if (written < 1) {
                // Zero is the save dialog cancelled - SaveExcelFile throws for every real failure.
                return;
            }
            AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            warnIfTruncated(extract);
        } catch (Exception e) {
            report(e);
        }
    }

    /** The file stopped at PRINT_LIMIT and must not pass for the whole list. */
    private void warnIfTruncated(PartyAgeingPage extract) {
        if (extract.hasNext()) {
            report(new UserValidationException(LanguageManager.getInstance()
                    .getString("party.balances.truncated", PartyAgeingService.PRINT_LIMIT)));
        }
    }

    // ---- plumbing --------------------------------------------------------------------

    private PartyKind partyKind() {
        return nameAndAccountInterface.partyKind();
    }

    private PartyScreenIdentity identity() {
        return PartyScreenIdentity.forKind(partyKind());
    }

    @Override
    public String title() {
        return identity().ageingProfile().title();
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public String dialogStyleClass() {
        return identity().styleClass();
    }

    private void report(Throwable error) {
        AllAlerts.handleError(text("party.ageing.title"),
                error instanceof Exception ? (Exception) error : new Exception(error));
    }

    /** A list action, laid out the way the accounts screen lays out its own. */
    private Button button(String key, AppIcon icon, Runnable action) {
        Button button = new Button(text(key), icon.graphic());
        button.getStyleClass().add("app-neutral-button");
        button.setContentDisplay(ContentDisplay.RIGHT);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static Label caption(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        return label;
    }

    private static Label statValue(String id) {
        Label label = new Label("0");
        label.getStyleClass().add("stat-value");
        label.setId(id);
        return label;
    }

    private static <S, V> TableColumn<S, V> named(String id, TableColumn<S, V> column) {
        column.setId(id);
        return column;
    }

    private static BigDecimal amount(TextField field) {
        String value = field.getText();
        return value == null || value.isBlank()
                ? null : BigDecimal.valueOf(
                        com.hamza.controlsfx.others.DoubleSetting.parseDoubleOrDefault(value));
    }

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return label.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
