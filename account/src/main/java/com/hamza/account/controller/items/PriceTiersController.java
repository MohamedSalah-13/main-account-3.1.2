package com.hamza.account.controller.items;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.ItemsChanged;
import com.hamza.account.features.events.PriceTiersChanged;
import com.hamza.account.features.party.statement.StatementPeriod;
import com.hamza.account.features.pricing.MissingPriceScope;
import com.hamza.account.features.pricing.PriceTier;
import com.hamza.account.features.pricing.PriceTierCatalog;
import com.hamza.account.features.pricing.PriceTierForm;
import com.hamza.account.features.pricing.PriceTierService;
import com.hamza.account.features.pricing.PriceTiers;
import com.hamza.account.features.pricing.TierFillRule;
import com.hamza.account.features.pricing.TierReportService;
import com.hamza.account.features.pricing.TierReports;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.PeriodPicker;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.table.Columns;
import com.hamza.controlsfx.table.columnEdit.NumberTextConverter;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The price tiers (V84, docs/pricing-and-offers-plan.md phase A): their names, which are in use, and how
 * each is filled - and the two reports that come with them, the items missing a price on a tier in use
 * and the sales priced below their list.
 * <p>
 * It holds no rule: what a tier may be is {@link PriceTierService}'s and {@link PriceTierForm}'s, and
 * what a report may show is {@link TierReportService}'s, each asking its permission. The permissions here
 * are hints in the {@code isGranted} sense.
 * <p>
 * <b>Saving a fill rule moves no price.</b> Its row's "apply" button does, through
 * {@link com.hamza.account.features.pricing.TierFillService}: a preview of every figure it would write,
 * and then exactly that or nothing. Not the unit prices screen, where the plan put it - that screen lists
 * only items sold in more than one unit.
 */
@FxmlPath(pathFile = "items/price-tiers.fxml")
public class PriceTiersController {

    private static final int PAGE_SIZE = 100;

    private final PriceTierService tierService = ServiceRegistry.get(PriceTierService.class);
    private final TierReportService reportService = ServiceRegistry.get(TierReportService.class);
    private final com.hamza.account.features.pricing.TierFillService fillService =
            ServiceRegistry.get(com.hamza.account.features.pricing.TierFillService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);

    @FXML
    private StackPane root;

    private PriceTierCatalog catalog = new PriceTierCatalog(List.of());
    private final List<TierRow> rows = new ArrayList<>();
    private final Button btnSave = new Button(text("save"));

    private final TextField missingSearch = new TextField();
    private final TableView<TierReports.MissingPrice> missingTable = new TableView<>();
    private final Pagination missingPages = new Pagination(1, 0);
    private final Label missingCount = new Label();
    private final CheckBox missingIncludeIdle = new CheckBox(text("pricing.tiers.missing.include.idle"));
    private final Label missingIdleNote = new Label();

    private final PeriodPicker belowPeriod = new PeriodPicker("pt");
    private final TextField belowSearch = new TextField();
    private final TableView<TierReports.BelowList> belowTable = new TableView<>();
    private final Pagination belowPages = new Pagination(1, 0);
    private final Label belowSummary = new Label();

    private static String text(String key, Object... args) {
        return LanguageManager.getInstance().getString(key, args);
    }

    @FXML
    private void initialize() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(new Tab(text("pricing.tiers.tab.tiers"), tiersTab()));
        tabs.getTabs().add(new Tab(text("pricing.tiers.tab.missing"), missingTab()));
        if (reportService.canReadSales()) {
            tabs.getTabs().add(new Tab(text("pricing.tiers.tab.below"), belowTab()));
        }
        tabs.getSelectionModel().selectedIndexProperty().addListener((observable, before, now) -> {
            if (now.intValue() == 1) loadMissing(0);
            if (now.intValue() == 2) loadBelow(0);
        });
        root.getStyleClass().add("price-tiers");
        root.getChildren().setAll(tabs);
        loadTiers();
    }

    // ---- the tiers --------------------------------------------------------------------------------

    /** One tier's controls on the form. */
    private final class TierRow {
        final int id;
        final TextField name = new TextField();
        final CheckBox active = new CheckBox();
        final ComboBox<PriceTierForm.RuleSource> source = new ComboBox<>();
        final TextField percent = new TextField();
        final ComboBox<BigDecimal> rounding = new ComboBox<>(FXCollections.observableArrayList(TierFillRule.ROUNDINGS));
        final Label customers = new Label();
        final Button apply = new Button(text("pricing.tiers.apply"));

        TierRow(int id) {
            this.id = id;
            name.setPrefColumnCount(14);
            source.setItems(FXCollections.observableArrayList(PriceTierForm.RuleSource.choicesFor(id)));
            source.setConverter(sourceConverter());
            percent.setPrefColumnCount(6);
            percent.setTextFormatter(new javafx.scene.control.TextFormatter<>(change ->
                    change.getControlNewText().matches("[-+]?[0-9٠-٩.,]*") ? change : null));
            rounding.setConverter(new StringConverter<>() {
                @Override
                public String toString(BigDecimal value) {
                    return value == null ? "" : value.toPlainString();
                }

                @Override
                public BigDecimal fromString(String string) {
                    return null;
                }
            });
            source.valueProperty().addListener((observable, before, now) -> showRuleFields());
            if (id == PriceTiers.FIRST) {
                // Tier 1 is the price every item has and the one a missing price falls back to.
                active.setDisable(true);
                active.setTooltip(new javafx.scene.control.Tooltip(text("pricing.tier.error.first.active")));
            }
        }

        void show(PriceTier tier, int customerCount) {
            name.setText(tier.name());
            active.setSelected(tier.active());
            PriceTierForm.RuleSource chosen = PriceTierForm.RuleSource.of(tier.rule());
            source.getSelectionModel().select(chosen);
            percent.setText(tier.rule() == null ? "" : tier.rule().percent().stripTrailingZeros().toPlainString());
            rounding.getSelectionModel().select(tier.rule() == null ? new BigDecimal("0.25") : tier.rule().rounding());
            customers.setText(String.valueOf(customerCount));
            // The saved rule, not the one on the form: save first, then apply.
            apply.setDisable(tier.rule() == null || !fillService.canApply());
            apply.setOnAction(event -> applyRule(tier));
            showRuleFields();
        }

        void showRuleFields() {
            boolean hasRule = source.getValue() != null && !source.getValue().none();
            percent.setDisable(!hasRule);
            rounding.setDisable(!hasRule);
        }

        PriceTier read() throws DaoException {
            BigDecimal typedPercent = percent.getText() == null || percent.getText().isBlank()
                    ? null : NumberTextConverter.parse(percent.getText());
            TierFillRule rule = PriceTierForm.rule(source.getValue(), typedPercent, rounding.getValue());
            return new PriceTier(id, name.getText(), active.isSelected(), rule);
        }
    }

    private StringConverter<PriceTierForm.RuleSource> sourceConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(PriceTierForm.RuleSource source) {
                if (source == null || source.none()) return text("pricing.tiers.rule.none");
                if (source.source() == TierFillRule.Source.COST) return text("pricing.tiers.rule.cost");
                return text("pricing.tiers.rule.tier", catalog.name(source.tierId()));
            }

            @Override
            public PriceTierForm.RuleSource fromString(String string) {
                return null;
            }
        };
    }

    private Node tiersTab() {
        Label title = new Label(text("pricing.tiers.title"));
        title.getStyleClass().add("page-title");
        Label note = new Label(text("pricing.tiers.note"));
        note.setWrapText(true);
        note.getStyleClass().add("page-subtitle");
        VBox heading = new VBox(4, title, note);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.setMaxWidth(Double.MAX_VALUE);
        double[] columnWidths = {5, 17, 8, 22, 11, 14, 10, 13};
        for (double width : columnWidths) {
            ColumnConstraints constraint = new ColumnConstraints();
            constraint.setPercentWidth(width);
            constraint.setHgrow(Priority.ALWAYS);
            constraint.setMinWidth(0);
            grid.getColumnConstraints().add(constraint);
        }
        grid.addRow(0, header("pricing.tiers.column.tier"), header("pricing.tiers.column.name"),
                header("pricing.tiers.column.active"), header("pricing.tiers.column.source"),
                header("pricing.tiers.column.percent"), header("pricing.tiers.column.rounding"),
                header("pricing.tiers.column.customers"), new Label());
        List<javafx.scene.control.Control> enterOrder = new ArrayList<>();
        for (int id : PriceTiers.IDS) {
            TierRow row = new TierRow(id);
            rows.add(row);
            row.name.setMaxWidth(Double.MAX_VALUE);
            row.source.setMaxWidth(Double.MAX_VALUE);
            row.percent.setMaxWidth(Double.MAX_VALUE);
            row.rounding.setMaxWidth(Double.MAX_VALUE);
            row.customers.setMaxWidth(Double.MAX_VALUE);
            row.customers.setAlignment(Pos.CENTER);
            row.apply.setMaxWidth(Double.MAX_VALUE);
            grid.addRow(id, new Label(String.valueOf(id)), row.name, row.active, row.source, row.percent,
                    row.rounding, row.customers, row.apply);
            enterOrder.addAll(List.of(row.name, row.source, row.percent, row.rounding));
        }
        // Filled a row at a time, and the last Enter lands on the save.
        enterOrder.add(btnSave);
        com.hamza.controlsfx.others.Utils.whenEnterPressed(enterOrder.toArray(javafx.scene.control.Control[]::new));

        VBox tiersCard = new VBox(grid);
        tiersCard.getStyleClass().add("app-card");
        VBox.setVgrow(tiersCard, Priority.NEVER);

        btnSave.getStyleClass().add("primary-button");
        btnSave.setDisable(!tierService.canEdit());
        btnSave.setOnAction(event -> saveTiers());
        Button btnReload = new Button(text("refresh"));
        btnReload.getStyleClass().add("neutral-button");
        btnReload.setOnAction(event -> loadTiers());
        HBox buttons = new HBox(10, btnSave, btnReload);
        buttons.setAlignment(Pos.CENTER_LEFT);
        HBox footer = new HBox(buttons);
        footer.getStyleClass().add("app-card");

        VBox box = new VBox(14, heading, tiersCard, footer);
        box.getStyleClass().add("page-container");
        box.setPadding(new Insets(18));
        return box;
    }

    private static Label header(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        return label;
    }

    private void loadTiers() {
        try {
            catalog = tierService.catalog();
            Map<Integer, Integer> customers = tierService.activeCustomersByTier();
            for (TierRow row : rows) {
                catalog.find(row.id).ifPresent(tier -> row.show(tier, customers.getOrDefault(row.id, 0)));
                row.source.setConverter(sourceConverter());
            }
            boolean editable = tierService.canEdit();
            for (TierRow row : rows) {
                row.name.setEditable(editable);
                row.source.setDisable(!editable);
                if (row.id != PriceTiers.FIRST) row.active.setDisable(!editable);
            }
        } catch (DaoException e) {
            AllAlerts.handleError(text("pricing.tiers.title"), e);
        }
    }

    private void saveTiers() {
        try {
            List<PriceTier> edited = new ArrayList<>();
            for (TierRow row : rows) {
                edited.add(row.read());
            }
            Map<PriceTier, Integer> affected = PriceTierForm.switchedOffUnderCustomers(catalog, edited,
                    tierService.activeCustomersByTier());
            if (!affected.isEmpty()) {
                String which = affected.entrySet().stream()
                        .map(entry -> text("pricing.tiers.off.customers", entry.getKey().name(), entry.getValue()))
                        .collect(Collectors.joining("\n"));
                if (!AllAlerts.confirm_all(text("pricing.tiers.title"),
                        text("pricing.tiers.off.confirm", which, catalog.name(PriceTiers.FIRST)))) {
                    return;
                }
            }
            int written = tierService.save(edited);
            // The service tells the other tills; the relay passes over this machine's own rows, so this one
            // hears it here - the items screen shows the tiers' names.
            eventBus.publish(new PriceTiersChanged());
            loadTiers();
            AllAlerts.alertSaveWithMessage(text("pricing.tiers.saved", written));
        } catch (Exception e) {
            AllAlerts.handleError(text("pricing.tiers.title"), e);
        }
    }

    /**
     * The tier's saved rule over the whole catalogue: every figure it would write, shown before anything
     * is written, and then exactly those or none (the service refuses if a price moved in between).
     */
    private void applyRule(PriceTier tier) {
        try {
            // The gaps only, to begin with: a price somebody typed on the tier is a decision.
            FillChoice choice = confirmFill(tier, new FillChoice(true, fillService.preview(tier.id(), true)));
            if (choice == null) {
                return;
            }
            if (choice.changes().isEmpty()) {
                AllAlerts.alertInformation(text("pricing.tiers.title"), text("pricing.fill.nothing", tier.name()));
                return;
            }
            int written = fillService.apply(tier.id(), choice.onlyMissing(), choice.changes());
            eventBus.publish(new ItemsChanged());
            AllAlerts.alertSaveWithMessage(text("pricing.fill.done", written, tier.name()));
        } catch (Exception e) {
            AllAlerts.handleError(text("pricing.tiers.apply"), e);
        }
    }

    /** What the preview dialog was showing when "write" was pressed. */
    private record FillChoice(boolean onlyMissing,
                              List<com.hamza.account.features.pricing.TierFill.Change> changes) {
    }

    /** @return the choice written, or null when the dialog was cancelled */
    private FillChoice confirmFill(PriceTier tier, FillChoice opening) {
        FillChoice[] shown = {opening};
        List<com.hamza.account.features.pricing.TierFill.Change> changes = opening.changes();
        TableView<com.hamza.account.features.pricing.TierFill.Change> table =
                new TableView<>(FXCollections.observableArrayList(changes));
        table.getColumns().add(Columns.text("unit.prices.preview.item",
                com.hamza.account.features.pricing.TierFill.Change::itemName));
        table.getColumns().add(Columns.text("unit.prices.preview.unit",
                com.hamza.account.features.pricing.TierFill.Change::unitName));
        table.getColumns().add(Columns.money("unit.prices.preview.before",
                com.hamza.account.features.pricing.TierFill.Change::before));
        table.getColumns().add(Columns.money("unit.prices.preview.after",
                com.hamza.account.features.pricing.TierFill.Change::after));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefSize(700, 360);
        table.setPlaceholder(new Label(text("pricing.fill.nothing", tier.name())));

        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        if (root.getScene() != null) dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle(text("pricing.tiers.apply"));
        dialog.setHeaderText(fillHeader(tier, changes));
        CheckBox onlyMissing = new CheckBox(text("pricing.fill.only.missing"));
        onlyMissing.setSelected(opening.onlyMissing());
        onlyMissing.selectedProperty().addListener((observable, before, now) -> {
            try {
                shown[0] = new FillChoice(now, fillService.preview(tier.id(), now));
                table.getItems().setAll(shown[0].changes());
                dialog.setHeaderText(fillHeader(tier, shown[0].changes()));
            } catch (Exception e) {
                AllAlerts.handleError(text("pricing.tiers.apply"), e);
            }
        });
        Label hint = new Label(text("pricing.fill.preview.hint"));
        hint.setWrapText(true);
        VBox content = new VBox(8, onlyMissing, table, hint);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        javafx.scene.control.ButtonType write = new javafx.scene.control.ButtonType(text("pricing.fill.preview.write"),
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(write, javafx.scene.control.ButtonType.CANCEL);
        dialog.getDialogPane().setNodeOrientation(LanguageManager.getInstance().getNodeOrientation());
        dialog.setResizable(true);
        com.hamza.account.config.ThemeManager.apply(dialog.getDialogPane().getScene());
        return dialog.showAndWait().filter(button -> button == write).isPresent() ? shown[0] : null;
    }

    private static String fillHeader(PriceTier tier, List<com.hamza.account.features.pricing.TierFill.Change> changes) {
        long items = changes.stream().map(com.hamza.account.features.pricing.TierFill.Change::itemId).distinct().count();
        return text("pricing.fill.preview.header", tier.name(), changes.size(), items);
    }

    // ---- items missing a price on a tier in use ---------------------------------------------------

    private Node missingTab() {
        missingSearch.setPromptText(text("pricing.tiers.missing.search"));
        missingSearch.setOnAction(event -> loadMissing(0));
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        // Shown only while a tier in use has nobody on it - there is nothing to include otherwise.
        missingIncludeIdle.setVisible(false);
        missingIncludeIdle.managedProperty().bind(missingIncludeIdle.visibleProperty());
        missingIncludeIdle.selectedProperty().addListener((observable, before, now) -> loadMissing(0));
        new ListToolbar().searchField(missingSearch, missingIncludeIdle)
                .refresh(ListToolbar.refreshButton(() -> loadMissing(0)))
                .extra(missingCount)
                .installIn(bar);
        missingIdleNote.setWrapText(true);
        missingIdleNote.getStyleClass().add("form-hint");
        missingIdleNote.setVisible(false);
        missingIdleNote.managedProperty().bind(missingIdleNote.visibleProperty());

        missingTable.getColumns().add(Columns.text("barcode", TierReports.MissingPrice::barcode));
        missingTable.getColumns().add(Columns.text("name", TierReports.MissingPrice::name));
        for (int tier : PriceTiers.IDS) {
            TableColumn<TierReports.MissingPrice, BigDecimal> price = Columns.moneyOfDouble(
                    "pricing.tiers.column.price", row -> row.price(tier));
            price.setUserData(tier);
            missingTable.getColumns().add(price);
        }
        missingTable.getColumns().add(Columns.text("pricing.tiers.column.missing", row -> row.missingTiers().stream()
                .map(catalog::name).collect(Collectors.joining("، "))));
        missingTable.setPlaceholder(new Label(text("pricing.tiers.missing.empty")));
        missingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        missingPages.setPageFactory(page -> {
            loadMissing(page);
            return missingTable;
        });
        VBox.setVgrow(missingPages, Priority.ALWAYS);
        VBox box = new VBox(10, bar, missingIdleNote, missingPages);
        box.setPadding(new Insets(12));
        return box;
    }

    private void loadMissing(int page) {
        try {
            // A heading is a tier's name, which the first tab may just have changed.
            for (TableColumn<TierReports.MissingPrice, ?> column : missingTable.getColumns()) {
                if (column.getUserData() instanceof Integer tier) column.setText(catalog.name(tier));
            }
            // Not every tier in use: one nobody is sold at would list the whole catalogue (§10.5).
            MissingPriceScope scope = MissingPriceScope.of(catalog, tierService.activeCustomersByTier(),
                    missingIncludeIdle.isSelected());
            missingIncludeIdle.setVisible(!scope.idle().isEmpty());
            missingIdleNote.setVisible(scope.leftOut());
            missingIdleNote.setText(text("pricing.tiers.missing.idle.note", scope.idle().stream()
                    .map(tier -> "«" + tier + "»").collect(Collectors.joining("، "))));
            TierReports.MissingPage found = reportService.missing(scope.counted(), missingSearch.getText(),
                    PAGE_SIZE, page * PAGE_SIZE);
            missingTable.getItems().setAll(found.rows());
            missingCount.setText(text("pricing.tiers.missing.count", found.total()));
            int pages = Math.max(1, (found.total() + PAGE_SIZE - 1) / PAGE_SIZE);
            if (missingPages.getPageCount() != pages) missingPages.setPageCount(pages);
        } catch (DaoException e) {
            AllAlerts.handleError(text("pricing.tiers.tab.missing"), e);
        }
    }

    // ---- sales below their list price -------------------------------------------------------------

    private Node belowTab() {
        belowPeriod.choose(StatementPeriod.THIS_MONTH);
        belowPeriod.setOnChange(() -> loadBelow(0));
        belowSearch.setPromptText(text("pricing.tiers.below.search"));
        belowSearch.setOnAction(event -> loadBelow(0));
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        new ListToolbar().searchField(belowSearch)
                .refresh(ListToolbar.refreshButton(() -> loadBelow(0)))
                .extra(belowPeriod.node())
                .installIn(bar);

        belowTable.getColumns().add(Columns.date("date", TierReports.BelowList::date));
        belowTable.getColumns().add(Columns.number("pricing.tiers.column.invoice", TierReports.BelowList::invoiceNumber));
        belowTable.getColumns().add(Columns.text("pricing.tiers.column.customer", TierReports.BelowList::customer));
        belowTable.getColumns().add(Columns.text("pricing.tiers.column.user", TierReports.BelowList::user));
        belowTable.getColumns().add(Columns.text("name", TierReports.BelowList::item));
        belowTable.getColumns().add(Columns.text("pricing.tiers.column.unit", TierReports.BelowList::unit));
        belowTable.getColumns().add(Columns.asQuantity(Columns.number("pricing.tiers.column.quantity",
                TierReports.BelowList::quantity)));
        belowTable.getColumns().add(Columns.money("pricing.tiers.column.list", TierReports.BelowList::listPrice));
        belowTable.getColumns().add(Columns.money("pricing.tiers.column.charged", TierReports.BelowList::price));
        belowTable.getColumns().add(Columns.money("pricing.tiers.column.given", TierReports.BelowList::given));
        belowTable.setPlaceholder(new Label(text("pricing.tiers.below.empty")));
        belowTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        belowPages.setPageFactory(page -> {
            loadBelow(page);
            return belowTable;
        });
        belowSummary.getStyleClass().add("form-label");
        VBox.setVgrow(belowPages, Priority.ALWAYS);
        VBox box = new VBox(10, bar, belowSummary, belowPages);
        box.setPadding(new Insets(12));
        return box;
    }

    private void loadBelow(int page) {
        try {
            TierReports.BelowListPage found = reportService.belowList(belowPeriod.from(), belowPeriod.to(),
                    belowSearch.getText(), PAGE_SIZE, page * PAGE_SIZE);
            belowTable.getItems().setAll(found.rows());
            TierReports.BelowListSummary summary = found.summary();
            belowSummary.setText(text("pricing.tiers.below.summary", summary.lines(), summary.invoices(),
                    Columns.money(summary.given())));
            int pages = Math.max(1, (summary.lines() + PAGE_SIZE - 1) / PAGE_SIZE);
            if (belowPages.getPageCount() != pages) belowPages.setPageCount(pages);
        } catch (DaoException e) {
            AllAlerts.handleError(text("pricing.tiers.tab.below"), e);
        }
    }
}
