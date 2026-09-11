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
import com.hamza.account.table.PageJumpBox;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.BusinessRuleException;
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
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
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
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
 * <p>Loading is off the JavaFX thread with a {@code generation} token that discards the answer to
 * a search the user has already replaced - as {@code AccountController2} and {@code MasterDataPane}
 * do.
 */
@Log4j2
public class PartyAgeingController<T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T3, T4> implements AppSettingInterface {

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
    private final Label statUnallocated = statValue("ageing-unallocated");

    private final Label countLabel = new Label();
    private final Button previous = new Button();
    private final Button next = new Button();
    private final PageJumpBox pageJump = new PageJumpBox(this::search);
    private final ProgressIndicator progress = new ProgressIndicator();
    private final StackPane content = new StackPane();

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
        buildTable();

        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("app-container");
        layout.setTop(new VBox(8, toolbar(), statCards(), filterBar()));
        layout.setCenter(content);
        layout.setBottom(footer());
        BorderPane.setMargin(content, new Insets(8, 0, 8, 0));

        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        content.getChildren().addAll(table, progress);

        StackPane screen = new StackPane(layout);
        screen.getStyleClass().add("app-root");
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("party-ageing");

        Platform.runLater(this::loadAreasAndRows);
        return screen;
    }

    private FlowPane toolbar() {
        Button refresh = button("refresh", AppIcon.REFRESH, this::reload);
        Button excel = button("party.statement.export.excel", AppIcon.SPREADSHEET, this::exportExcel);
        excel.getStyleClass().setAll("excel-button");

        FlowPane bar = new FlowPane(8, 8, refresh, excel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("app-card");
        return bar;
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

    private VBox card(String titleKey, Label value) {
        Label title = new Label(text(titleKey));
        title.getStyleClass().add("stat-title");
        VBox box = new VBox(2, title, value);
        box.getStyleClass().add("app-card");
        box.setPadding(new Insets(10, 16, 10, 16));
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
        apply.getStyleClass().add("app-primary-button");
        apply.setId("ageing-apply");
        apply.setOnAction(event -> reload());

        Button clear = new Button(text("party.statement.filter.reset"), AppIcon.CLEAR.graphic());
        clear.getStyleClass().add("app-neutral-button");
        clear.setOnAction(event -> reset());

        com.hamza.controlsfx.others.Utils.whenEnterPressed(minimumBalance, search, apply);

        FlowPane bar = new FlowPane(8, 8,
                new Label(text("party.ageing.filter.as.of")), asOf,
                new Label(text("party.column.area")), comboArea,
                minimumBalance, search, overdueOnly, includeSettled, apply, clear);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("app-card");
        search.setPrefWidth(220);
        return bar;
    }

    /**
     * The columns, with the five bands generated from the enum.
     * <p>
     * Written out by hand they would be five places to forget one, and the export builds its own
     * header the same way - so a band added to {@code AgeingBucket} appears on the screen and in
     * the file without either being edited.
     */
    private void buildTable() {
        table.setId("ageing-table");
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label(text("party.ageing.empty")));

        List<TableColumn<PartyAgeingRow, ?>> columns = new ArrayList<>(List.of(
                Columns.number("code", PartyAgeingRow::partyId),
                Columns.text("name", PartyAgeingRow::name),
                Columns.text("party.column.area", PartyAgeingRow::areaName),
                Columns.number("party.ageing.column.terms", PartyAgeingRow::paymentTerms)));
        for (AgeingBucket bucket : AgeingBucket.inReadingOrder()) {
            columns.add(Columns.money(bucket.messageKey(), row -> row.amount(bucket)));
        }
        columns.add(Columns.money("party.ageing.column.unallocated", PartyAgeingRow::unallocated));
        columns.add(Columns.money("party.ageing.column.balance", PartyAgeingRow::balance));
        table.getColumns().setAll(columns);

        TableSetting.tableMenuSetting(getClass(), table);
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
        bar.getStyleClass().add("summary-card");
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

    // ---- export ----------------------------------------------------------------------

    /**
     * The whole filtered set, read again rather than taken from the table.
     * <p>
     * Same rule as the statement and the balances list: the file and the page must describe one
     * set. {@code forExport} also requires the report permission on top of the screen's.
     */
    private void exportExcel() {
        try {
            PartyAgeingPage extract = ageingService.forExport(filter);
            if (extract.rows().isEmpty()) {
                throw new UserValidationException(text("party.error.no.data.export"));
            }
            int written = ExportData.exportDataToExcel(extract.rows(),
                    new PartyAgeingExcelWriter(extract.rows()));
            if (written < 1) {
                throw new BusinessRuleException(text("party.error.cannot.save"));
            }
            AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            if (extract.hasNext()) {
                report(new UserValidationException(LanguageManager.getInstance()
                        .getString("party.balances.truncated", PartyAgeingService.PRINT_LIMIT)));
            }
        } catch (Exception e) {
            report(e);
        }
    }

    // ---- plumbing --------------------------------------------------------------------

    private PartyKind partyKind() {
        return nameAndAccountInterface.partyKind();
    }

    @Override
    public String title() {
        return text("party.ageing.title");
    }

    @Override
    public boolean resize() {
        return true;
    }

    private void report(Throwable error) {
        AllAlerts.handleError(text("party.ageing.title"),
                error instanceof Exception ? (Exception) error : new Exception(error));
    }

    private Button button(String key, AppIcon icon, Runnable action) {
        Button button = new Button(text(key), icon.graphic());
        button.getStyleClass().add("app-neutral-button");
        button.setOnAction(event -> action.run());
        return button;
    }

    private static Label statValue(String id) {
        Label label = new Label("0");
        label.getStyleClass().add("stat-value");
        label.setId(id);
        return label;
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
