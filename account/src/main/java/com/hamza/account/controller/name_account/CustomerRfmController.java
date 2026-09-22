package com.hamza.account.controller.name_account;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.PropertiesName;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.rfm.CustomerRfmFilter;
import com.hamza.account.features.party.rfm.CustomerRfmOrder;
import com.hamza.account.features.party.rfm.CustomerRfmPage;
import com.hamza.account.features.party.rfm.CustomerRfmRow;
import com.hamza.account.features.party.rfm.CustomerRfmService;
import com.hamza.account.features.party.rfm.CustomerRfmSummary;
import com.hamza.account.perm.PermAccountAndNameInt;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.PageJumpBox;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.TableColumnViews;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.table.TableSetting;
import com.hamza.account.table.VisibleColumnsExcelWriter;
import com.hamza.account.view.OpenApplication;
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
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * Every customer's recency, frequency and value over a period, and the fifth each falls in
 * ({@code features/party/rfm}). It opens from the customer balances screen beside the ageing report,
 * with that report's permissions, and from the reports hub.
 *
 * <p>The period and the order stay in the bar: they say which list this is. A row opens the customer's
 * profile, which explains the row - the figures are that profile's for the same dates, held to it on
 * MySQL. Loading is off the JavaFX thread with a {@code generation} token, as on the ageing screen.</p>
 */
public class CustomerRfmController implements AppSettingInterface {

    private static final String ACTIONS_COLUMN = "rfm-actions";
    /** The money columns the printed totals line sums. Not the invoices: the layout sums amounts, and
     *  a count printed there came out as 0.00 - seen on the rendered page. The count is on the card. */
    private static final Set<String> TOTALLED = Set.of("rfm-sold", "rfm-returned", "rfm-net");

    private final CustomerRfmService service = new CustomerRfmService();

    private final TableView<CustomerRfmRow> table = new TableView<>();
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final ComboBox<CustomerRfmOrder> comboOrder = new ComboBox<>();
    private final TextField search = new TextField();

    private final Label statParties = statValue("rfm-parties");
    private final Label statBuying = statValue("rfm-buying");
    private final Label statIdle = statValue("rfm-idle");
    private final Label statNet = statValue("rfm-net-total");

    private final Label countLabel = new Label();
    /** Who is left out as the cash-sales customer, by name, or that nobody is - never left to be guessed. */
    private final Label excludedLabel = new Label();
    private final Button previous = new Button();
    private final Button next = new Button();
    private final PageJumpBox pageJump = new PageJumpBox(this::search);
    private final ProgressIndicator progress = new ProgressIndicator();
    private final StackPane content = new StackPane();

    private final ContentSizedColumns<CustomerRfmRow> columnSizing = new ContentSizedColumns<>();
    private final MenuButton viewMenu = TableColumnViews.menuButton();
    private final ListToolbar toolbar = new ListToolbar();

    private CustomerRfmFilter filter = CustomerRfmFilter.lastTwelveMonths(LocalDate.now(), cashCustomer());
    private int generation;
    private boolean loading;
    private volatile Optional<String> excluded = Optional.empty();

    @Override
    public Pane pane() {
        PartyScreenIdentity identity = PartyScreenIdentity.forKind(PartyKind.CUSTOMER);
        buildTable();
        columnViews().install(viewMenu, table);

        Label explanation = new Label(text("party.rfm.explanation"));
        explanation.getStyleClass().add("form-hint");
        explanation.setWrapText(true);
        excludedLabel.getStyleClass().add("form-hint");
        excludedLabel.setWrapText(true);
        excludedLabel.setId("rfm-excluded");

        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("app-container");
        layout.setTop(new VBox(8, PartyIdentityHeader.of(identity.rfmProfile()), statCards(), filterBar(),
                new VBox(2, explanation, excludedLabel)));
        layout.setCenter(content);
        layout.setBottom(footer());
        BorderPane.setMargin(content, new Insets(8, 0, 8, 0));

        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        content.getChildren().addAll(table, progress);

        StackPane screen = new StackPane(layout);
        screen.getStyleClass().addAll("app-root", identity.styleClass());
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("customer-rfm");

        Platform.runLater(() -> search(0));
        return screen;
    }

    private FlowPane statCards() {
        FlowPane cards = new FlowPane(12, 10);
        cards.setId("rfm-stats");
        cards.getChildren().addAll(
                card("party.rfm.stat.parties", statParties),
                card("party.rfm.stat.buying", statBuying),
                card("party.rfm.stat.idle", statIdle),
                card("party.rfm.stat.net", statNet));
        return cards;
    }

    private VBox card(String titleKey, Label value) {
        Label title = new Label(text(titleKey));
        title.getStyleClass().add("stat-title");
        VBox box = new VBox(4, title, value);
        box.getStyleClass().addAll("dashboard-tile", "party-stat-card");
        box.setMinWidth(150);
        return box;
    }

    /** The period and the order say which list this is, so they stay in the bar; there is no panel. */
    private VBox filterBar() {
        DateSetting.dateAction(from);
        DateSetting.dateAction(to);
        from.setValue(filter.from());
        to.setValue(filter.to());
        from.setId("rfm-from");
        to.setId("rfm-to");
        from.setOnAction(event -> reload());
        to.setOnAction(event -> reload());

        comboOrder.getItems().setAll(CustomerRfmOrder.values());
        comboOrder.setValue(CustomerRfmOrder.SCORE);
        comboOrder.setId("rfm-order");
        comboOrder.setConverter(new StringConverter<>() {
            @Override
            public String toString(CustomerRfmOrder order) {
                return order == null ? "" : text(order.messageKey());
            }

            @Override
            public CustomerRfmOrder fromString(String string) {
                return null;
            }
        });
        comboOrder.setOnAction(event -> reload());

        search.setPromptText(text("party.balances.filter.text"));
        search.setId("rfm-search");
        search.setPrefWidth(220);

        Button apply = new Button(text("search"), AppIcon.SEARCH.graphic());
        apply.getStyleClass().addAll("app-primary-button", "party-primary-button");
        apply.setMinWidth(Region.USE_PREF_SIZE);
        apply.setId("rfm-apply");
        apply.setOnAction(event -> reload());
        com.hamza.controlsfx.others.Utils.whenEnterPressed(search, apply);

        HBox period = new HBox(8, caption("party.trend.from"), from,
                caption("party.trend.to"), to, caption("party.rfm.order"), comboOrder);
        period.setAlignment(Pos.CENTER_LEFT);

        toolbar.searchField(period, search)
                .search(apply)
                .clear(ListToolbar.clearButton(this::reset))
                .refresh(ListToolbar.refreshButton(this::reload))
                .print(ListToolbar.printButton(this::print))
                .export(button("party.statement.export.excel", AppIcon.SPREADSHEET, this::exportExcel))
                .view(viewMenu);
        FlowPane row = toolbar.installIn(new FlowPane(8, 8));
        row.setAlignment(Pos.CENTER_LEFT);

        VBox bar = new VBox(8, row);
        bar.getStyleClass().addAll("app-card", "party-form-card");
        return bar;
    }

    private void buildTable() {
        table.setId("rfm-table");
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setPlaceholder(new Label(text("party.rfm.empty")));
        // The actions first, as on the balances screen: a column of buttons appended to the end of a
        // table wider than the window lands behind the horizontal scroll bar.
        table.getColumns().setAll(List.of(
                named(ACTIONS_COLUMN, actionsColumn()),
                named("rfm-code", Columns.number("code", CustomerRfmRow::partyId)),
                named("rfm-name", Columns.text("name", CustomerRfmRow::name)),
                // Three columns rather than one "5-3-4": digits run left to right inside a right-to-left
                // heading, so which figure came first was a question the column could not answer.
                named("rfm-r", Columns.number("party.rfm.column.score.recency", CustomerRfmRow::recencyScore)),
                named("rfm-f", Columns.number("party.rfm.column.score.frequency", CustomerRfmRow::frequencyScore)),
                named("rfm-m", Columns.number("party.rfm.column.score.value", CustomerRfmRow::valueScore)),
                named("rfm-total", Columns.number("party.rfm.column.score.total", CustomerRfmRow::totalScore)),
                named("rfm-last", Columns.date("party.rfm.column.last", CustomerRfmRow::lastDay)),
                named("rfm-recency", Columns.number("party.rfm.column.recency", CustomerRfmRow::recencyDays)),
                named("rfm-documents", Columns.number("party.rfm.column.documents", CustomerRfmRow::documents)),
                named("rfm-sold", Columns.money("party.rfm.column.sold", CustomerRfmRow::sold)),
                named("rfm-returned", Columns.money("party.rfm.column.returned", CustomerRfmRow::returned)),
                named("rfm-net", Columns.money("party.rfm.column.net", CustomerRfmRow::net))));
        table.setOnMouseClicked(event -> {
            CustomerRfmRow selected = table.getSelectionModel().getSelectedItem();
            if (event.getClickCount() == 2 && selected != null) {
                openProfile(selected);
            }
        });
        columnSizing.install(table);
        TableSetting.tableMenuSetting(getClass(), table);
        table.setTableMenuButtonVisible(false);
    }

    /** The customer's profile explains the row: the same figures for the same dates, and what lies behind them. */
    private TableColumn<CustomerRfmRow, Void> actionsColumn() {
        List<RowAction<CustomerRfmRow>> actions = List.of(
                RowAction.of("party.profile.open", AppIcon.PROFILE, "app-neutral-button",
                        PermAccountAndNameInt.forParty(PartyKind.CUSTOMER).showNames(), this::openProfile));
        return RowActionsColumn.of("party.balances.column.actions", RowAction.permitted(actions));
    }

    /** It opens on the full view; the compact one keeps the name, the scores and the three figures. */
    private TableColumnViews<CustomerRfmRow> columnViews() {
        Preferences preferences = Preferences.userNodeForPackage(CustomerRfmController.class).node("rfm");
        return new TableColumnViews<>(preferences, "view.mode", TableColumnViews.Preset.FULL,
                Set.of("rfm-name", "rfm-r", "rfm-f", "rfm-m", "rfm-total", "rfm-recency", "rfm-documents", "rfm-net"),
                Set.of(ACTIONS_COLUMN));
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

    private void reload() {
        if (!loading) {
            search(0);
        }
    }

    private void reset() {
        loading = true;
        CustomerRfmFilter fresh = CustomerRfmFilter.lastTwelveMonths(LocalDate.now(), cashCustomer());
        from.setValue(fresh.from());
        to.setValue(fresh.to());
        comboOrder.setValue(CustomerRfmOrder.SCORE);
        search.clear();
        loading = false;
        search(0);
    }

    private void search(int page) {
        CustomerRfmFilter.Problem problem = CustomerRfmFilter.problem(from.getValue(), to.getValue());
        if (problem != CustomerRfmFilter.Problem.NONE) {
            report(new UserValidationException(text(problem == CustomerRfmFilter.Problem.MISSING
                    ? "party.rfm.error.period" : "party.rfm.error.reversed")));
            return;
        }
        filter = new CustomerRfmFilter(from.getValue(), to.getValue(), search.getText(), cashCustomer(),
                comboOrder.getValue() == null ? CustomerRfmOrder.SCORE : comboOrder.getValue(),
                Math.max(0, page), CustomerRfmFilter.DEFAULT_PAGE_SIZE);
        CustomerRfmFilter asked = filter;
        int mine = ++generation;
        progress.setVisible(true);

        Task<CustomerRfmPage> task = new Task<>() {
            @Override
            protected CustomerRfmPage call() throws Exception {
                excluded = service.excludedName(asked);
                return service.search(asked);
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
        Thread worker = new Thread(task, "customer-rfm");
        worker.setDaemon(true);
        worker.start();
    }

    private void show(CustomerRfmPage loaded) {
        CustomerRfmSummary summary = loaded.summary();
        table.setItems(FXCollections.observableArrayList(loaded.rows()));
        columnSizing.layout(table);

        statParties.setText(String.valueOf(summary.parties()));
        statBuying.setText(String.valueOf(summary.buying()));
        statIdle.setText(String.valueOf(summary.idle()));
        statNet.setText(Columns.money(summary.net()));
        excludedLabel.setText(excludedSentence());

        countLabel.setText(LanguageManager.getInstance()
                .getString("party.balances.count", loaded.rows().size(), summary.parties()));
        pageJump.showing(loaded.page(), AccountController2.pageCount(summary.parties(), filter.pageSize()));
        previous.setDisable(!loaded.hasPrevious());
        next.setDisable(!loaded.hasNext());
    }

    private void openProfile(CustomerRfmRow row) {
        try {
            new OpenApplication<>(new PartyProfileController(PartyKind.CUSTOMER, row.partyId(), row.name()));
        } catch (Exception e) {
            report(e);
        }
    }

    // ---- print and export ------------------------------------------------------------

    /** The whole filtered set as a PDF of the columns on screen, read again through {@code forExport}. */
    private void print() {
        String title = title();
        File target = TablePdfReport.chooseTarget(table.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        CustomerRfmFilter printed = filter;
        Task<CustomerRfmPage> load = new Task<>() {
            @Override
            protected CustomerRfmPage call() throws Exception {
                return service.forExport(printed);
            }
        };
        load.setOnSucceeded(event -> {
            CustomerRfmPage extract = load.getValue();
            if (extract.rows().isEmpty()) {
                AllAlerts.alertError(text("party.error.no.data.print"));
                return;
            }
            TablePdfLayout layout = TablePdfLayout.from(table, extract.rows(), Set.of(ACTIONS_COLUMN),
                    TOTALLED, text("total"));
            TablePdfReport.write(target, title, subtitle(printed), layout, () -> warnIfTruncated(extract));
        });
        AllAlerts.handleTaskFailure(text("party.error.export.generic"), load);
        TablePdfReport.start(load, "customer-rfm-pdf-load");
    }

    private void exportExcel() {
        try {
            CustomerRfmPage extract = service.forExport(filter);
            if (extract.rows().isEmpty()) {
                throw new UserValidationException(text("party.error.no.data.export"));
            }
            int written = ExportData.exportDataToExcel(extract.rows(),
                    VisibleColumnsExcelWriter.of(text("party.rfm.export.sheet"), table,
                            Set.of(ACTIONS_COLUMN), extract.rows()));
            if (written < 1) {
                return;
            }
            AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            warnIfTruncated(extract);
        } catch (Exception e) {
            report(e);
        }
    }

    /** Which list the paper is: the period, the order, the text, and who was left out. */
    private String subtitle(CustomerRfmFilter printed) {
        String text = text("party.trend.from") + " " + printed.from() + "  "
                + text("party.trend.to") + " " + printed.to() + "  |  "
                + text(printed.order().messageKey());
        if (!printed.text().isBlank()) {
            text += "  |  " + printed.text();
        }
        return text + "  |  " + excludedSentence();
    }

    private String excludedSentence() {
        return excluded.map(name -> LanguageManager.getInstance().getString("party.rfm.excluded", name))
                .orElseGet(() -> text("party.rfm.excluded.none"));
    }

    private void warnIfTruncated(CustomerRfmPage extract) {
        if (extract.hasNext()) {
            report(new UserValidationException(LanguageManager.getInstance()
                    .getString("party.balances.truncated", CustomerRfmService.PRINT_LIMIT)));
        }
    }

    // ---- plumbing --------------------------------------------------------------------

    /**
     * The customer cash sales land on, only when the settings name one: the setting's own fallback is
     * {@code 1}, which on a real database was a named customer with two invoices while the cash bucket was
     * number 48 - excluded on a guess, a real customer would silently vanish from the table.
     */
    private static int cashCustomer() {
        return PropertiesName.getChosenDefaultCustomer().orElse(0);
    }

    @Override
    public String title() {
        return text("party.rfm.title");
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public String dialogStyleClass() {
        return PartyScreenIdentity.forKind(PartyKind.CUSTOMER).styleClass();
    }

    private void report(Throwable error) {
        AllAlerts.handleError(title(), error instanceof Exception exception ? exception : new Exception(error));
    }

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

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
