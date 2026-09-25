package com.hamza.account.controller.items;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.OffersChanged;
import com.hamza.account.features.offers.Offer;
import com.hamza.account.features.offers.OfferCostCheck;
import com.hamza.account.features.offers.OfferFilter;
import com.hamza.account.features.offers.OfferKind;
import com.hamza.account.features.offers.OfferRow;
import com.hamza.account.features.offers.OfferScope;
import com.hamza.account.features.offers.OfferService;
import com.hamza.account.features.offers.OfferStatus;
import com.hamza.account.features.offers.OfferTargetLabel;
import com.hamza.account.features.offers.OfferUsage;
import com.hamza.account.features.offers.Weekdays;
import com.hamza.account.features.pricing.PriceTier;
import com.hamza.account.features.pricing.PriceTierService;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.ItemsService;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.RowDetailDrawer;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.table.Columns;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The offers (V85, docs/pricing-and-offers-plan.md §6): a list narrowed by a text, a status, a kind and the
 * days an offer runs, an offer's targets and what it gave in a drawer over the list, and the form it is
 * written in ({@link OfferFormDialog}). It holds no rule - {@link OfferService} decides and asks each
 * permission; the ones here are hints. An offer a line names is stopped, never deleted, and its terms are
 * not edited: its delete button is off, and its form opens with its terms shown and locked.
 */
@FxmlPath(pathFile = "items/offers.fxml")
public class OffersController {

    /** Set on a stopped offer's row. Styled in {@code app-theme.css}. */
    private static final PseudoClass STOPPED = PseudoClass.getPseudoClass("stopped");

    private static final List<DayOfWeek> WEEK = List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private final OfferService service = ServiceRegistry.get(OfferService.class);
    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final PriceTierService tierService = ServiceRegistry.get(PriceTierService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final Subscriptions subscriptions = new Subscriptions();

    private final TableView<OfferRow> table = new TableView<>();
    private final ContentSizedColumns<OfferRow> columnSizing = new ContentSizedColumns<>();
    private final ListToolbar toolbar = new ListToolbar();
    private final TextField search = new TextField();
    private final ComboBox<OfferStatus> comboStatus = new ComboBox<>();
    private final ComboBox<OfferKind> comboKind = new ComboBox<>();
    private final DatePicker runningFrom = new DatePicker();
    private final DatePicker runningTo = new DatePicker();
    private final Label countLabel = new Label();

    private final GridPane detailGrid = new GridPane();
    private final VBox detailTargets = new VBox(4);
    private RowDetailDrawer detail;

    @FXML
    private StackPane stackPane;
    @FXML
    private AnchorPane root;

    private static String text(String key, Object... args) {
        return LanguageManager.getInstance().getString(key, args);
    }

    @FXML
    private void initialize() {
        stackPane.getStyleClass().add("screen-offers");
        buildTable();
        VBox box = new VBox(8, header(), filterBar(), table, countLabel);
        box.setPadding(new Insets(8));
        VBox.setVgrow(table, Priority.ALWAYS);
        AnchorPane.setTopAnchor(box, 0.0);
        AnchorPane.setRightAnchor(box, 0.0);
        AnchorPane.setBottomAnchor(box, 0.0);
        AnchorPane.setLeftAnchor(box, 0.0);
        root.getChildren().setAll(box);
        buildDetail();
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(OffersChanged.class, event -> reload()));
        }
        subscriptions.disposeWith(stackPane);
        Platform.runLater(this::reload);
    }

    // ---- the screen ------------------------------------------------------------------------

    private HBox header() {
        HBox iconBox = new HBox(AppIcon.PERCENT.graphic(32));
        iconBox.setAlignment(Pos.CENTER);
        iconBox.getStyleClass().add("party-screen-icon-box");
        Label title = new Label(text("offers.title"));
        title.getStyleClass().add("party-screen-title");
        Label subtitle = new Label(text("offers.subtitle"));
        subtitle.getStyleClass().add("party-screen-subtitle");
        subtitle.setWrapText(true);
        VBox textBox = new VBox(3, title, subtitle);
        textBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textBox, Priority.ALWAYS);
        HBox bar = new HBox(14, iconBox, textBox);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("party-screen-header");
        return bar;
    }

    private VBox filterBar() {
        search.setPromptText(text("offers.search.prompt"));
        search.setPrefColumnCount(18);
        search.setOnAction(event -> reload());
        List<OfferStatus> statuses = new ArrayList<>();
        statuses.add(null);
        statuses.addAll(List.of(OfferStatus.values()));
        comboStatus.setItems(FXCollections.observableArrayList(statuses));
        comboStatus.setConverter(converter(status -> status == null ? text("offers.filter.all.statuses")
                : statusName(status)));
        comboStatus.getSelectionModel().selectFirst();
        comboStatus.setOnAction(event -> reload());
        List<OfferKind> kinds = new ArrayList<>();
        kinds.add(null);
        kinds.addAll(List.of(OfferKind.values()));
        comboKind.setItems(FXCollections.observableArrayList(kinds));
        comboKind.setConverter(converter(kind -> kind == null ? text("offers.filter.all.kinds") : kindName(kind)));
        comboKind.getSelectionModel().selectFirst();
        comboKind.setOnAction(event -> reload());
        runningFrom.setPromptText(text("offers.filter.from"));
        runningTo.setPromptText(text("offers.filter.to"));
        runningFrom.valueProperty().addListener(observable -> reload());
        runningTo.valueProperty().addListener(observable -> reload());

        HBox panel = new HBox(8, caption("offers.filter.kind"), comboKind, caption("offers.filter.running"),
                runningFrom, runningTo);
        panel.setAlignment(Pos.CENTER_LEFT);
        toolbar.searchField(search, comboStatus)
                .search(ListToolbar.button("search", AppIcon.SEARCH, this::reload))
                .filters(ListToolbar.filtersToggle(), panel)
                .clear(ListToolbar.clearButton(this::clear))
                .refresh(ListToolbar.refreshButton(this::reload));
        if (service.canCreate()) {
            Button add = ListToolbar.button("offers.new", AppIcon.ADD, () -> openForm(null));
            add.getStyleClass().add("app-primary-button");
            toolbar.extra(add);
        }
        FlowPane row = toolbar.installIn(new FlowPane(8, 8));
        row.setAlignment(Pos.CENTER_LEFT);
        return new VBox(8, row, panel);
    }

    private void buildTable() {
        table.setId("offersTable");
        table.setPlaceholder(new Label(text("offers.empty")));
        List<RowAction<OfferRow>> actions = List.of(
                RowAction.of("offers.action.details", AppIcon.SHOW, "app-neutral-button",
                        AppPermissions.OFFER_SHOW, this::showDetails),
                RowAction.of("offers.action.edit", AppIcon.EDIT, "app-primary-button",
                        AppPermissions.OFFER_UPDATE, row -> openForm(row)),
                // Two buttons, each on only when it applies: one that did both read as a tick on a live offer
                // and said nothing of whether pressing it would stop it.
                new RowAction<>("offers.action.activate", AppIcon.CONFIRM, "app-neutral-button",
                        AppPermissions.OFFER_UPDATE, row -> row.offer().status() != OfferStatus.ACTIVE, this::activate),
                new RowAction<>("offers.action.stop", AppIcon.CLOSE, "app-neutral-button",
                        AppPermissions.OFFER_UPDATE, row -> row.offer().status() == OfferStatus.ACTIVE, this::stop),
                new RowAction<>("offers.action.delete", AppIcon.DELETE, "app-neutral-button",
                        AppPermissions.OFFER_DELETE, row -> !row.used(), this::delete));
        TableColumn<OfferRow, Void> actionsColumn = RowActionsColumn.of("offers.column.actions",
                RowAction.permitted(actions));
        actionsColumn.setId("offer-actions");
        List<TableColumn<OfferRow, ?>> columns = new ArrayList<>();
        columns.add(actionsColumn);
        columns.add(id(Columns.text("offers.column.name", row -> row.offer().name()), "offer-name"));
        columns.add(id(Columns.text("offers.column.kind", row -> kindName(row.offer().kind())), "offer-kind"));
        columns.add(id(Columns.text("offers.column.value", OffersController::valueText), "offer-value"));
        columns.add(id(Columns.text("offers.column.unit", row -> row.unitName() == null ? "" : row.unitName()),
                "offer-unit"));
        columns.add(id(Columns.text("offers.column.starts", row -> row.offer().startsOn().toString()), "offer-starts"));
        columns.add(id(Columns.text("offers.column.ends",
                row -> row.offer().endsOn() == null ? "" : row.offer().endsOn().toString()), "offer-ends"));
        columns.add(id(Columns.text("offers.column.days", row -> daysText(row.offer().weekdays())), "offer-days"));
        columns.add(id(Columns.text("offers.column.status", row -> statusName(row.offer().status())), "offer-status"));
        columns.add(id(Columns.text("offers.column.used", row -> String.valueOf(row.usedLines())), "offer-used"));
        columns.add(id(Columns.text("offers.column.given", row -> Columns.money(row.given())), "offer-given"));
        table.getColumns().setAll(columns);
        table.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(OfferRow row, boolean empty) {
                super.updateItem(row, empty);
                pseudoClassStateChanged(STOPPED, !empty && row != null
                        && row.offer().status() == OfferStatus.STOPPED);
            }
        });
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                showDetails(table.getSelectionModel().getSelectedItem());
            }
        });
        columnSizing.install(table);
    }

    private static <S, T> TableColumn<S, T> id(TableColumn<S, T> column, String id) {
        column.setId(id);
        return column;
    }

    private void buildDetail() {
        detail = RowDetailDrawer.installIn(root);
        detailGrid.setHgap(10);
        detailGrid.setVgap(6);
        Label targetsTitle = new Label(text("offers.detail.targets"));
        targetsTitle.getStyleClass().add("section-title");
        VBox content = new VBox(10, detailGrid, targetsTitle, detailTargets);
        content.setPadding(new Insets(4));
        detail.setContent(content);
        detail.setPreferredWidth(440);
    }

    // ---- loading ---------------------------------------------------------------------------

    private void clear() {
        search.clear();
        comboStatus.getSelectionModel().selectFirst();
        comboKind.getSelectionModel().selectFirst();
        runningFrom.setValue(null);
        runningTo.setValue(null);
        reload();
    }

    private void reload() {
        try {
            OfferFilter filter = OfferFilter.of(search.getText(), comboStatus.getValue(), comboKind.getValue(),
                    runningFrom.getValue(), runningTo.getValue());
            List<OfferRow> rows = service.list(filter);
            table.setItems(FXCollections.observableArrayList(rows));
            table.refresh();
            columnSizing.layout(table);
            toolbar.showActiveFilters(filter.panelConditionCount());
            countLabel.setText(text("offers.count", rows.size()));
        } catch (Exception e) {
            AllAlerts.handleError(text("offers.title"), e);
        }
    }

    // ---- the row's actions -----------------------------------------------------------------

    private void showDetails(OfferRow row) {
        try {
            OfferUsage usage = service.usage(row.offer().id());
            List<OfferTargetLabel> targets = service.targets(row.offer().id());
            detailGrid.getChildren().clear();
            int line = 0;
            line = detailLine(line, "offers.column.kind", kindName(row.offer().kind()));
            // The dates on lines of their own: inside an Arabic subtitle they were drawn backwards.
            line = detailLine(line, "offers.column.starts", row.offer().startsOn().toString());
            line = detailLine(line, "offers.column.ends",
                    row.offer().endsOn() == null ? text("offers.detail.open.ended") : row.offer().endsOn().toString());
            line = detailLine(line, "offers.column.value", valueText(row));
            line = detailLine(line, "offers.column.days", daysText(row.offer().weekdays()));
            line = detailLine(line, "offers.detail.tiers", tiersText(row.offer()));
            line = detailLine(line, "offers.detail.priority", String.valueOf(row.offer().priority()));
            if (row.offer().maxPerInvoice() != null) {
                line = detailLine(line, "offers.detail.limit.invoice", plain(row.offer().maxPerInvoice()));
            }
            if (row.offer().quantityLimit() != null) {
                line = detailLine(line, "offers.detail.limit.total", text("offers.detail.limit.used",
                        plain(usage.times(row.offer())), plain(row.offer().quantityLimit())));
            }
            line = detailLine(line, "offers.detail.invoices", String.valueOf(usage.invoices()));
            line = detailLine(line, "offers.column.used", String.valueOf(usage.lines()));
            line = detailLine(line, "offers.column.given", Columns.money(usage.given()));
            line = detailLine(line, "offers.detail.returned", Columns.money(usage.returned()));
            line = detailLine(line, "offers.detail.net", Columns.money(usage.net()));
            line = detailLine(line, "offers.detail.first", usage.firstUsed() == null ? "" : usage.firstUsed().toString());
            line = detailLine(line, "offers.detail.last", usage.lastUsed() == null ? "" : usage.lastUsed().toString());
            if (row.offer().notes() != null) {
                detailLine(line, "offers.detail.notes", row.offer().notes());
            }
            detailTargets.getChildren().setAll(targets.stream().map(target -> {
                Label label = new Label(targetText(target));
                label.getStyleClass().add("detail-drawer-value");
                label.setWrapText(true);
                return label;
            }).toList());
            detail.show(row.offer().name(), statusName(row.offer().status()));
        } catch (Exception e) {
            AllAlerts.handleError(text("offers.title"), e);
        }
    }

    private int detailLine(int line, String captionKey, String value) {
        Label caption = caption(captionKey);
        Label figure = new Label(value);
        figure.getStyleClass().add("detail-drawer-value");
        figure.setWrapText(true);
        detailGrid.add(caption, 0, line);
        detailGrid.add(figure, 1, line);
        return line + 1;
    }

    private void openForm(OfferRow row) {
        try {
            Offer offer = row == null ? null : service.find(row.offer().id()).orElse(null);
            List<OfferTargetLabel> targets = row == null ? List.of() : service.targets(row.offer().id());
            if (OfferFormDialog.open(window(), service, itemsService, activeTiers(), offer, targets,
                    row != null && row.used())) {
                changedHere();
            }
        } catch (Exception e) {
            AllAlerts.handleError(text("offers.title"), e);
        }
    }

    private void stop(OfferRow row) {
        try {
            Offer offer = row.offer();
            if (AllAlerts.confirm_all(text("offers.action.stop"), text("offers.confirm.stop", offer.name()))) {
                service.stop(offer.id(), offer.version());
                changedHere();
            }
        } catch (Exception e) {
            AllAlerts.handleError(text("offers.title"), e);
        }
    }

    /** Switches an offer on, after the items it would sell below their cost, if any, are confirmed (ق-ع٩). */
    private void activate(OfferRow row) {
        try {
            Offer offer = row.offer();
            Offer full = service.find(offer.id()).orElse(offer);
            if (confirmBelowCost(window(), service, full, activeTiers())) {
                service.activate(offer.id(), offer.version());
                changedHere();
            }
        } catch (Exception e) {
            AllAlerts.handleError(text("offers.title"), e);
        }
    }

    private void delete(OfferRow row) {
        try {
            if (AllAlerts.confirm_all(text("offers.action.delete"), text("offers.confirm.delete", row.offer().name()))) {
                service.delete(row.offer().id());
                changedHere();
            }
        } catch (Exception e) {
            AllAlerts.handleError(text("offers.title"), e);
        }
    }

    /**
     * The service tells the other tills through {@code data_change}; this one hears it here. The relay passes
     * over the rows its own machine wrote, so without this the list - and every invoice open on this till -
     * went on showing the offers as they were until reopened.
     */
    private void changedHere() {
        eventBus.publish(new OffersChanged());
    }

    private List<PriceTier> activeTiers() throws DaoException {
        return tierService == null ? List.of() : tierService.catalog().active();
    }

    private Window window() {
        return stackPane.getScene() == null ? null : stackPane.getScene().getWindow();
    }

    /**
     * The items the offer would sell below their cost, for a yes or a no before it goes live (ق-ع٩) - a
     * decision taken once, knowingly, never a refusal. Asked only of a reader who may see a cost; for anybody
     * else the answer is yes, as it was before the offers existed for a price typed by hand.
     * <p>
     * An item is listed at the tier where it fares worst, so the tier is named on its row: without it the
     * dialog priced a 42.00 item at 40.00 - its third tier's - with nothing saying why.
     */
    static boolean confirmBelowCost(Window owner, OfferService service, Offer offer, List<PriceTier> activeTiers)
            throws DaoException {
        if (!service.canSeeCost()) {
            return true;
        }
        Map<Integer, String> tierNames = activeTiers.stream()
                .collect(Collectors.toMap(PriceTier::id, PriceTier::name, (first, second) -> first));
        List<OfferCostCheck.BelowCost> below = service.belowCost(offer, tierNames.keySet());
        if (below.isEmpty()) {
            return true;
        }
        TableView<OfferCostCheck.BelowCost> list = new TableView<>(FXCollections.observableArrayList(below));
        list.getColumns().setAll(List.of(
                Columns.text("offers.below.item", OfferCostCheck.BelowCost::name),
                Columns.text("offers.below.unit", OfferCostCheck.BelowCost::unitName),
                Columns.text("offers.below.tier",
                        row -> tierNames.getOrDefault(row.tierId(), String.valueOf(row.tierId()))),
                Columns.text("offers.below.price", row -> Columns.money(row.price())),
                Columns.text("offers.below.net", row -> Columns.money(row.net())),
                Columns.text("offers.below.cost", row -> Columns.money(row.cost()))));
        list.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        list.setPrefSize(720, 320);
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(text("offers.below.title"));
        dialog.setHeaderText(text("offers.below.header", offer.name(), below.size()));
        dialog.getDialogPane().setContent(list);
        ButtonType go = new ButtonType(text("offers.below.confirm"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(go, ButtonType.CANCEL);
        dialog.getDialogPane().setNodeOrientation(LanguageManager.getInstance().getNodeOrientation());
        dialog.setResizable(true);
        ThemeManager.apply(dialog.getDialogPane().getScene());
        return dialog.showAndWait().filter(button -> button == go).isPresent();
    }

    // ---- words -------------------------------------------------------------------------------

    static String kindName(OfferKind kind) {
        return switch (kind) {
            case PERCENT -> text("offer.kind.percent");
            case AMOUNT -> text("offer.kind.amount");
            case PRICE -> text("offer.kind.price");
            case QUANTITY_PRICE -> text("offer.kind.quantity.price");
            case BUY_GET -> text("offer.kind.buy.get");
        };
    }

    static String statusName(OfferStatus status) {
        return switch (status) {
            case DRAFT -> text("offer.status.draft");
            case ACTIVE -> text("offer.status.active");
            case STOPPED -> text("offer.status.stopped");
        };
    }

    static String scopeName(OfferScope scope) {
        return switch (scope) {
            case ITEM -> text("offer.scope.item");
            case SUB_GROUP -> text("offer.scope.sub.group");
            case MAIN_GROUP -> text("offer.scope.main.group");
            case ALL -> text("offer.scope.all");
        };
    }

    static String dayName(DayOfWeek day) {
        return switch (day) {
            case SATURDAY -> text("offer.day.saturday");
            case SUNDAY -> text("offer.day.sunday");
            case MONDAY -> text("offer.day.monday");
            case TUESDAY -> text("offer.day.tuesday");
            case WEDNESDAY -> text("offer.day.wednesday");
            case THURSDAY -> text("offer.day.thursday");
            case FRIDAY -> text("offer.day.friday");
        };
    }

    static String daysText(Integer mask) {
        if (mask == null) {
            return text("offer.days.every");
        }
        Set<DayOfWeek> days = Weekdays.days(mask);
        return WEEK.stream().filter(days::contains).map(OffersController::dayName).collect(Collectors.joining("، "));
    }

    /**
     * What the offer gives, in a few words. Every figure stands between words: "2 + 1" in a right-to-left
     * cell reads "1 + 2", which is the opposite offer.
     */
    static String valueText(OfferRow row) {
        Offer offer = row.offer();
        return switch (offer.kind()) {
            case PERCENT -> plain(offer.percent()) + "%";
            case AMOUNT -> Columns.money(offer.amount());
            case PRICE -> Columns.money(offer.offerPrice());
            case QUANTITY_PRICE -> text("offer.value.quantity.price", plain(offer.buyQuantity()),
                    Columns.money(offer.offerPrice()));
            case BUY_GET -> offer.getPercent().compareTo(BigDecimal.valueOf(100)) == 0
                    ? text("offer.value.buy.get.free", plain(offer.buyQuantity()), plain(offer.getQuantity()))
                    : text("offer.value.buy.get.percent", plain(offer.buyQuantity()), plain(offer.getQuantity()),
                            plain(offer.getPercent()));
        };
    }

    static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String tiersText(Offer offer) {
        if (offer.priceTierIds().isEmpty()) {
            return text("offer.tiers.all");
        }
        try {
            var catalog = tierService.catalog();
            return offer.priceTierIds().stream().sorted().map(catalog::name).collect(Collectors.joining("، "));
        } catch (DaoException e) {
            return offer.priceTierIds().toString();
        }
    }

    /** A target as a sentence: "item: juice (carton)", "sub group: detergents", "everything", "except: ...". */
    static String targetText(OfferTargetLabel label) {
        String what = switch (label.target().scope()) {
            case ITEM -> text("offer.scope.item") + ": " + label.itemName()
                    + (label.unitName() == null ? "" : " (" + label.unitName() + ")");
            case SUB_GROUP -> text("offer.scope.sub.group") + ": " + label.subGroupName();
            case MAIN_GROUP -> text("offer.scope.main.group") + ": " + label.mainGroupName();
            case ALL -> text("offer.scope.all");
        };
        if (label.target().reward()) {
            return text("offer.target.gift") + " " + what;
        }
        return label.target().excluded() ? text("offer.target.except") + " " + what : what;
    }

    private static Label caption(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        return label;
    }

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> name) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return name.apply(value);
            }

            @Override
            public T fromString(String value) {
                return null;
            }
        };
    }
}
