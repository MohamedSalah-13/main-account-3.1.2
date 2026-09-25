package com.hamza.account.controller.items;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.search.ItemSuggestionField;
import com.hamza.account.features.offers.Offer;
import com.hamza.account.features.offers.OfferChoice;
import com.hamza.account.features.offers.OfferEngine;
import com.hamza.account.features.offers.OfferForm;
import com.hamza.account.features.offers.OfferKind;
import com.hamza.account.features.offers.OfferScope;
import com.hamza.account.features.offers.OfferService;
import com.hamza.account.features.offers.OfferStatus;
import com.hamza.account.features.offers.OfferTarget;
import com.hamza.account.features.offers.OfferTargetLabel;
import com.hamza.account.features.offers.Weekdays;
import com.hamza.account.features.pricing.PriceTier;
import com.hamza.account.features.pricing.PriceTiers;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.service.ItemUnits;
import com.hamza.account.service.ItemsService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.Utils;
import com.hamza.controlsfx.table.Columns;
import com.hamza.controlsfx.table.columnEdit.NumberTextConverter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The form an offer is written and edited in (V85, docs/pricing-and-offers-plan.md §6): its name and kind,
 * the value its kind uses, the unit an amount or a price is for, its days and its tiers, what it reaches -
 * and a "try it" box that says what the offer as typed would give one of an item, the commission rules'
 * box again. It holds no rule: {@link OfferForm} judges the form and {@link OfferService} the write.
 * <p>
 * <b>An offer a line names is history</b> (ق-ع٧): its terms are shown and not editable, and only its name,
 * its notes and its end remain - and a bundle's barcode. Before an offer is written switched on, or switched on,
 * the items it would sell below their cost are listed for a confirmation (ق-ع٩) - for a reader who may see a
 * cost.
 * <p>
 * A bundle (V87) is written as its price, its barcode and its components, each with a quantity - the targets
 * pane names items only then, with no exclusion; "try it" is one bundle at the first tier's prices. An invoice
 * offer is written as a threshold and a percentage or an amount, and "try it" says what an invoice of the item
 * tried would be given.
 */
final class OfferFormDialog {

    /** The shop's week starts on Saturday, and the days are ticked in that order. */
    private static final List<DayOfWeek> WEEK = List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private final OfferService service;
    private final ItemsService itemsService;
    private final List<PriceTier> activeTiers;
    private final Offer existing;
    private final boolean used;

    private final TextField txtName = new TextField();
    private final ComboBox<OfferKind> comboKind = new ComboBox<>();
    private final Label valueCaption = new Label();
    private final TextField txtValue = new TextField();
    private final ComboBox<OfferChoice> comboUnit = new ComboBox<>();
    private final DatePicker dateStarts = new DatePicker();
    private final DatePicker dateEnds = new DatePicker();
    private final Map<DayOfWeek, CheckBox> days = new EnumMap<>(DayOfWeek.class);
    private final TextField txtPriority = new TextField();
    private final List<CheckBox> tierBoxes = new ArrayList<>();
    private final CheckBox activateNow = new CheckBox(text("offer.form.activate"));
    private final TextField txtNotes = new TextField();

    /** A quantity offer's group, or a "buy and get"'s quantity bought (V86). */
    private final Label buyCaption = new Label();
    private final TextField txtBuy = new TextField();
    /** What a "buy and get" gives, and at what discount - a hundred is free. */
    private final Label getCaption = new Label(text("offer.form.get"));
    private final TextField txtGet = new TextField();
    private final Label getPercentCaption = new Label(text("offer.form.get.percent"));
    private final TextField txtGetPercent = new TextField();
    private final HBox getBox = new HBox(8);
    /** The two limits, counted in the offer's times: units, or groups for the quantity kinds. */
    private final TextField txtMaxPerInvoice = new TextField();
    private final TextField txtQuantityLimit = new TextField();
    private final CheckBox targetGift = new CheckBox(text("offer.form.target.gift"));

    /** The unit an amount or a price is for - none on a percentage, a bundle or an invoice offer. */
    private final Label unitCaption = caption("offer.form.unit");
    private final Label limitInvoiceCaption = caption("offer.form.limit.invoice");
    /** An invoice offer's threshold, and whether its value is an amount rather than a percentage (V87). */
    private final Label thresholdCaption = caption("offer.form.threshold");
    private final TextField txtThreshold = new TextField();
    private final Label invoiceModeCaption = caption("offer.form.invoice.mode");
    private final ComboBox<Boolean> comboAmountOff = new ComboBox<>();
    /** A bundle's barcode: scanned on an invoice, it puts the components on it (V87). */
    private final Label barcodeCaption = caption("offer.form.barcode");
    private final TextField txtBarcode = new TextField();
    /** How many of the item one bundle holds, beside the item picked for it. */
    private final Label componentQuantityCaption = caption("offer.form.component.quantity");
    private final TextField txtComponentQuantity = new TextField();
    /** The items a bundle's components name, kept for "try it" at the first tier's prices. */
    private final Map<Integer, ItemsModel> componentItems = new java.util.HashMap<>();

    private final ObservableList<OfferTargetLabel> targets = FXCollections.observableArrayList();
    private final ComboBox<OfferScope> comboScope = new ComboBox<>();
    private final ItemSuggestionField targetItem;
    private final ComboBox<UnitsModel> targetUnit = new ComboBox<>();
    private final ComboBox<OfferChoice> targetSubGroup = new ComboBox<>();
    private final ComboBox<OfferChoice> targetMainGroup = new ComboBox<>();
    private final CheckBox targetExcluded = new CheckBox(text("offer.form.target.excluded"));

    private final ItemSuggestionField tryItem;
    private final ComboBox<UnitsModel> tryUnit = new ComboBox<>();
    private final TextField tryQuantity = new TextField();
    private final Label tryResult = new Label();
    /** The item, unit and quantity boxes of "try it" - which a bundle does without: it tries itself. */
    private final List<Node> tryInputs = new ArrayList<>();

    private OfferFormDialog(OfferService service, ItemsService itemsService, List<PriceTier> activeTiers,
                            Offer existing, boolean used) {
        this.service = service;
        this.itemsService = itemsService;
        this.activeTiers = activeTiers;
        this.existing = existing;
        this.used = used;
        this.targetItem = new ItemSuggestionField(itemsService::getFilterItems);
        this.tryItem = new ItemSuggestionField(itemsService::getFilterItems);
    }

    /**
     * Opens the form over {@code existing}, or empty for a new offer, and answers once the offer has been
     * written - the form stays open on a refusal, with the refusal said.
     */
    static boolean open(Window owner, OfferService service, ItemsService itemsService, List<PriceTier> activeTiers,
                        Offer existing, List<OfferTargetLabel> existingTargets, boolean used) {
        OfferFormDialog form = new OfferFormDialog(service, itemsService, activeTiers, existing, used);
        return form.show(owner, existingTargets);
    }

    private static String text(String key, Object... args) {
        return LanguageManager.getInstance().getString(key, args);
    }

    private boolean show(Window owner, List<OfferTargetLabel> existingTargets) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(text(existing == null ? "offer.form.title.new" : "offer.form.title.edit"));
        dialog.getDialogPane().setContent(content());
        ButtonType save = new ButtonType(text("save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(save, ButtonType.CANCEL);
        dialog.getDialogPane().setNodeOrientation(LanguageManager.getInstance().getNodeOrientation());
        dialog.getDialogPane().setPrefSize(980, 720);
        dialog.setResizable(true);
        load(existingTargets);
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (!write(owner)) {
                event.consume();
            }
        });
        Utils.whenEnterPressed(txtName, txtValue, txtPriority, txtNotes);
        Utils.replaceNonDigitChar(txtBarcode);
        ThemeManager.apply(dialog.getDialogPane().getScene());
        return dialog.showAndWait().filter(button -> button == save).isPresent();
    }

    // ---- the form ---------------------------------------------------------------------------

    private Node content() {
        comboKind.setItems(FXCollections.observableArrayList(OfferKind.values()));
        comboKind.setConverter(converter(OffersController::kindName));
        comboKind.valueProperty().addListener((observable, before, now) -> kindChanged());
        comboUnit.setConverter(converter(choice -> choice == null ? "" : choice.name()));
        txtPriority.setPrefColumnCount(5);
        Utils.setOptionalNumberFormatter(txtValue);
        // Blank means none - a limit left empty is no limit - so none of these seeds a zero.
        for (TextField quantity : List.of(txtBuy, txtGet, txtGetPercent, txtMaxPerInvoice, txtQuantityLimit,
                txtThreshold)) {
            Utils.setOptionalNumberFormatter(quantity);
            quantity.setPrefColumnCount(6);
            quantity.textProperty().addListener(observable -> showTryOut());
        }
        comboAmountOff.setItems(FXCollections.observableArrayList(false, true));
        comboAmountOff.setConverter(converter(amount -> text(amount ? "offer.form.invoice.amount"
                : "offer.form.invoice.percent")));
        comboAmountOff.getSelectionModel().selectFirst();
        comboAmountOff.valueProperty().addListener((observable, before, now) -> kindChanged());
        txtBarcode.setPrefColumnCount(14);
        for (Label label : List.of(buyCaption, getCaption, getPercentCaption)) {
            label.getStyleClass().add("form-label");
            label.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        }
        getBox.getChildren().setAll(getCaption, txtGet, getPercentCaption, txtGetPercent);
        getBox.setAlignment(Pos.CENTER_LEFT);
        Label limitsHint = new Label(text("offer.form.limits.hint"));
        limitsHint.getStyleClass().add("text-explain");
        limitsHint.setWrapText(true);
        limitsHint.setMaxWidth(Double.MAX_VALUE);
        limitsHint.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        ColumnConstraints captions = new ColumnConstraints();
        captions.setMinWidth(110);
        ColumnConstraints values = new ColumnConstraints();
        values.setPrefWidth(300);
        values.setHgrow(Priority.SOMETIMES);
        grid.getColumnConstraints().addAll(captions, values, captions, values);
        grid.add(caption("offer.form.name"), 0, 0);
        grid.add(txtName, 1, 0);
        grid.add(caption("offer.form.kind"), 2, 0);
        grid.add(comboKind, 3, 0);
        grid.add(valueCaption, 0, 1);
        grid.add(txtValue, 1, 1);
        // One cell each, whichever the kind uses: the unit, or an invoice offer's percentage-or-amount.
        grid.add(unitCaption, 2, 1);
        grid.add(comboUnit, 3, 1);
        grid.add(invoiceModeCaption, 2, 1);
        grid.add(comboAmountOff, 3, 1);
        // The quantity bought, an invoice offer's threshold, or a bundle's barcode.
        grid.add(buyCaption, 0, 2);
        grid.add(txtBuy, 1, 2);
        grid.add(thresholdCaption, 0, 2);
        grid.add(txtThreshold, 1, 2);
        grid.add(barcodeCaption, 0, 2);
        grid.add(txtBarcode, 1, 2);
        grid.add(getBox, 2, 2, 2, 1);
        grid.add(limitInvoiceCaption, 0, 3);
        grid.add(txtMaxPerInvoice, 1, 3);
        grid.add(caption("offer.form.limit.total"), 2, 3);
        grid.add(txtQuantityLimit, 3, 3);
        grid.add(limitsHint, 1, 4, 3, 1);
        grid.add(caption("offer.form.starts"), 0, 5);
        grid.add(dateStarts, 1, 5);
        grid.add(caption("offer.form.ends"), 2, 5);
        grid.add(dateEnds, 3, 5);
        grid.add(caption("offer.form.days"), 0, 6);
        grid.add(daysBox(), 1, 6, 3, 1);
        grid.add(caption("offer.form.tiers"), 0, 7);
        grid.add(tiersBox(), 1, 7, 3, 1);
        grid.add(caption("offer.form.priority"), 0, 8);
        grid.add(txtPriority, 1, 8);
        grid.add(activateNow, 3, 8);
        grid.add(caption("offer.form.notes"), 0, 9);
        grid.add(txtNotes, 1, 9, 3, 1);
        for (Node node : List.of(txtName, comboKind, txtValue, comboUnit, comboAmountOff, dateStarts, dateEnds,
                txtNotes)) {
            if (node instanceof javafx.scene.control.Control control) {
                control.setMaxWidth(Double.MAX_VALUE);
            }
        }

        VBox box = new VBox(10, grid, targetsPane(), tryPane());
        if (used) {
            Label note = new Label(text("offer.form.used.note"));
            note.setWrapText(true);
            note.getStyleClass().add("text-explain");
            box.getChildren().add(0, note);
        }
        box.setPadding(new Insets(10));
        return box;
    }

    private FlowPane daysBox() {
        FlowPane box = new FlowPane(10, 6);
        for (DayOfWeek day : WEEK) {
            CheckBox check = new CheckBox(OffersController.dayName(day));
            check.setSelected(true);
            check.selectedProperty().addListener((observable, before, now) -> showTryOut());
            days.put(day, check);
            box.getChildren().add(check);
        }
        return box;
    }

    private Node tiersBox() {
        FlowPane box = new FlowPane(10, 6);
        for (PriceTier tier : activeTiers) {
            CheckBox check = new CheckBox(tier.name());
            check.setUserData(tier.id());
            check.selectedProperty().addListener((observable, before, now) -> showTryOut());
            tierBoxes.add(check);
            box.getChildren().add(check);
        }
        Label hint = new Label(text("offer.form.tiers.hint"));
        hint.getStyleClass().add("text-explain");
        box.getChildren().add(hint);
        return box;
    }

    private TitledPane targetsPane() {
        comboScope.setItems(FXCollections.observableArrayList(OfferScope.values()));
        comboScope.setConverter(converter(OffersController::scopeName));
        comboScope.getSelectionModel().select(OfferScope.ITEM);
        comboScope.valueProperty().addListener((observable, before, now) -> scopeChanged());
        targetUnit.setConverter(unitConverter());
        targetItem.setPrefWidth(260);
        targetItem.chosenItemProperty().addListener((observable, before, item) -> {
            List<UnitsModel> units = new ArrayList<>();
            units.add(null);
            if (item != null) {
                units.addAll(ItemUnits.unitsFor(item));
            }
            targetUnit.setItems(FXCollections.observableArrayList(units));
            targetUnit.getSelectionModel().selectFirst();
        });
        targetSubGroup.setConverter(converter(choice -> choice == null ? "" : choice.name()));
        targetMainGroup.setConverter(converter(choice -> choice == null ? "" : choice.name()));
        Button add = new Button(text("offer.form.target.add"), AppIcon.ADD.graphic());
        add.getStyleClass().add("app-primary-button");
        add.setOnAction(event -> addTarget());

        ListView<OfferTargetLabel> list = new ListView<>(targets);
        list.setPrefHeight(120);
        list.setCellFactory(view -> new ListCell<>() {
            private final Label label = new Label();
            private final Button remove = new Button(null, AppIcon.DELETE.graphic());
            private final HBox row = new HBox(8, remove, label);

            {
                row.setAlignment(Pos.CENTER_LEFT);
                remove.getStyleClass().add("app-neutral-button");
                remove.setOnAction(event -> targets.remove(getItem()));
            }

            @Override
            protected void updateItem(OfferTargetLabel item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                label.setText(OffersController.targetText(item));
                remove.setDisable(used);
                setGraphic(row);
            }
        });

        // A "buy and get"'s gift: one item, never left out - the box is there only for that kind and an item.
        targetGift.selectedProperty().addListener((observable, before, gift) -> {
            if (gift) {
                targetExcluded.setSelected(false);
            }
            targetExcluded.setDisable(gift || comboScope.getValue() == OfferScope.ALL);
        });
        Utils.setOptionalNumberFormatter(txtComponentQuantity);
        txtComponentQuantity.setPrefColumnCount(5);
        txtComponentQuantity.setText("1");
        HBox picker = new HBox(8, comboScope, targetItem, targetUnit, componentQuantityCaption, txtComponentQuantity,
                targetSubGroup, targetMainGroup, targetExcluded, targetGift, add);
        picker.setAlignment(Pos.CENTER_LEFT);
        picker.setDisable(used);
        scopeChanged();
        VBox content = new VBox(8, picker, list);
        TitledPane pane = new TitledPane(text("offer.form.targets"), content);
        pane.setCollapsible(false);
        return pane;
    }

    private TitledPane tryPane() {
        tryUnit.setConverter(unitConverter());
        tryItem.setPrefWidth(260);
        tryItem.chosenItemProperty().addListener((observable, before, item) -> {
            tryUnit.setItems(item == null ? FXCollections.observableArrayList()
                    : FXCollections.observableArrayList(ItemUnits.unitsFor(item)));
            tryUnit.getSelectionModel().selectFirst();
            showTryOut();
        });
        tryUnit.valueProperty().addListener((observable, before, now) -> showTryOut());
        Utils.setOptionalNumberFormatter(tryQuantity);
        tryQuantity.setPrefColumnCount(6);
        tryQuantity.setText("1");
        for (var input : List.of(txtValue.textProperty(), tryQuantity.textProperty())) {
            input.addListener(observable -> showTryOut());
        }
        comboUnit.valueProperty().addListener(observable -> showTryOut());
        targets.addListener((javafx.collections.ListChangeListener<OfferTargetLabel>) change -> showTryOut());
        tryResult.getStyleClass().add("form-label");
        Label tryItemCaption = caption("offer.form.try.item");
        Label tryQuantityCaption = caption("offer.form.try.quantity");
        tryInputs.addAll(List.of(tryItemCaption, tryItem, tryUnit, tryQuantityCaption, tryQuantity));
        HBox row = new HBox(8, tryItemCaption, tryItem, tryUnit, tryQuantityCaption, tryQuantity, tryResult);
        row.setAlignment(Pos.CENTER_LEFT);
        TitledPane pane = new TitledPane(text("offer.form.try"), row);
        pane.setCollapsible(false);
        return pane;
    }

    private void load(List<OfferTargetLabel> existingTargets) {
        try {
            List<OfferChoice> units = new ArrayList<>();
            units.add(new OfferChoice(0, text("offer.form.unit.base")));
            units.addAll(service.units());
            comboUnit.setItems(FXCollections.observableArrayList(units));
            targetSubGroup.setItems(FXCollections.observableArrayList(service.subGroups()));
            targetMainGroup.setItems(FXCollections.observableArrayList(service.mainGroups()));
        } catch (DaoException e) {
            AllAlerts.handleError(text("offers.title"), e);
        }
        if (existing == null) {
            comboKind.getSelectionModel().select(OfferKind.PERCENT);
            comboUnit.getSelectionModel().selectFirst();
            dateStarts.setValue(LocalDate.now());
            txtPriority.setText("0");
            activateNow.setSelected(true);
        } else {
            txtName.setText(existing.name());
            comboKind.getSelectionModel().select(existing.kind());
            BigDecimal value = switch (existing.kind()) {
                case PERCENT -> existing.percent();
                case AMOUNT -> existing.amount();
                case PRICE, QUANTITY_PRICE, BUNDLE -> existing.offerPrice();
                case BUY_GET -> null;
                case INVOICE -> existing.percent() != null ? existing.percent() : existing.amount();
            };
            comboAmountOff.getSelectionModel().select(existing.kind() == OfferKind.INVOICE
                    && existing.amount() != null);
            txtThreshold.setText(OffersController.plain(existing.threshold()));
            txtBarcode.setText(existing.barcode() == null ? "" : existing.barcode());
            txtValue.setText(OffersController.plain(value));
            txtBuy.setText(OffersController.plain(existing.buyQuantity()));
            txtGet.setText(OffersController.plain(existing.getQuantity()));
            txtGetPercent.setText(OffersController.plain(existing.getPercent()));
            txtMaxPerInvoice.setText(OffersController.plain(existing.maxPerInvoice()));
            txtQuantityLimit.setText(OffersController.plain(existing.quantityLimit()));
            comboUnit.getItems().stream()
                    .filter(choice -> choice.id() == (existing.unitId() == null ? 0 : existing.unitId()))
                    .findFirst().ifPresent(comboUnit.getSelectionModel()::select);
            dateStarts.setValue(existing.startsOn());
            dateEnds.setValue(existing.endsOn());
            Set<DayOfWeek> on = Weekdays.days(existing.weekdays());
            days.forEach((day, check) -> check.setSelected(on.contains(day)));
            tierBoxes.forEach(check -> check.setSelected(existing.priceTierIds().contains((Integer) check.getUserData())));
            txtPriority.setText(String.valueOf(existing.priority()));
            txtNotes.setText(existing.notes() == null ? "" : existing.notes());
            targets.setAll(existingTargets);
            activateNow.setVisible(false);
            activateNow.setManaged(false);
        }
        if (existing == null) {
            txtGetPercent.setText("100");
        }
        // A bundle's barcode is not among them: it is how the bundle is scanned, not what it gives.
        for (Node term : List.of(comboKind, txtValue, comboUnit, dateStarts, txtPriority, txtBuy, txtGet,
                txtGetPercent, txtMaxPerInvoice, txtQuantityLimit, txtThreshold, comboAmountOff)) {
            term.setDisable(used);
        }
        days.values().forEach(check -> check.setDisable(used));
        tierBoxes.forEach(check -> check.setDisable(used));
        kindChanged();
    }

    /**
     * Shows the boxes the kind uses and names them - a quantity offer's value is its group's price, a bundle's
     * its own, an invoice offer's a percentage or an amount beside its threshold.
     */
    private void kindChanged() {
        OfferKind kind = comboKind.getValue();
        boolean bundle = kind == OfferKind.BUNDLE;
        boolean invoice = kind == OfferKind.INVOICE;
        boolean amountOff = Boolean.TRUE.equals(comboAmountOff.getValue());
        valueCaption.setText(text(kind == OfferKind.AMOUNT ? "offer.form.value.amount"
                : kind == OfferKind.PRICE ? "offer.form.value.price"
                : kind == OfferKind.QUANTITY_PRICE ? "offer.form.value.group.price"
                : bundle ? "offer.form.value.bundle.price"
                : invoice && amountOff ? "offer.form.value.invoice.amount"
                : invoice ? "offer.form.value.invoice.percent" : "offer.form.value.percent"));
        valueCaption.getStyleClass().setAll("form-label");
        boolean buyGet = kind == OfferKind.BUY_GET;
        boolean counts = kind != null && kind.countsAQuantity();
        show(valueCaption, !buyGet);
        show(txtValue, !buyGet);
        buyCaption.setText(text(buyGet ? "offer.form.buy" : "offer.form.buy.group"));
        show(buyCaption, counts);
        show(txtBuy, counts);
        show(getBox, buyGet);
        show(thresholdCaption, invoice);
        show(txtThreshold, invoice);
        show(barcodeCaption, bundle);
        show(txtBarcode, bundle);
        show(unitCaption, !bundle && !invoice);
        show(comboUnit, !bundle && !invoice);
        show(invoiceModeCaption, invoice);
        show(comboAmountOff, invoice);
        // An invoice offer is given once an invoice; a limit per invoice says nothing about it.
        show(limitInvoiceCaption, !invoice);
        show(txtMaxPerInvoice, !invoice);
        tryInputs.forEach(node -> show(node, !bundle));
        comboUnit.setDisable(used || kind == OfferKind.PERCENT);
        if (kind == OfferKind.PERCENT && !comboUnit.getItems().isEmpty()) {
            comboUnit.getSelectionModel().selectFirst();
        }
        scopeChanged();
        showTryOut();
    }

    private void scopeChanged() {
        boolean bundle = comboKind.getValue() == OfferKind.BUNDLE;
        // A bundle's components are items, each in a quantity, never left out (ق-ع١٢).
        if (bundle && comboScope.getValue() != OfferScope.ITEM) {
            comboScope.getSelectionModel().select(OfferScope.ITEM);
            return;
        }
        comboScope.setDisable(bundle);
        show(componentQuantityCaption, bundle);
        show(txtComponentQuantity, bundle);
        show(targetExcluded, !bundle);
        if (bundle) {
            targetExcluded.setSelected(false);
        }
        OfferScope scope = comboScope.getValue();
        show(targetItem, scope == OfferScope.ITEM);
        show(targetUnit, scope == OfferScope.ITEM);
        show(targetSubGroup, scope == OfferScope.SUB_GROUP);
        show(targetMainGroup, scope == OfferScope.MAIN_GROUP);
        boolean gift = comboKind.getValue() == OfferKind.BUY_GET && scope == OfferScope.ITEM;
        show(targetGift, gift);
        if (!gift) {
            targetGift.setSelected(false);
        }
        targetExcluded.setDisable(scope == OfferScope.ALL || targetGift.isSelected());
        if (scope == OfferScope.ALL) {
            targetExcluded.setSelected(false);
        }
    }

    private static void show(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    private void addTarget() {
        boolean excluded = targetExcluded.isSelected();
        OfferTargetLabel label = switch (comboScope.getValue()) {
            case ITEM -> {
                ItemsModel item = targetItem.chosenItemProperty().get();
                if (item == null) {
                    yield null;
                }
                UnitsModel unit = targetUnit.getValue();
                if (comboKind.getValue() == OfferKind.BUNDLE) {
                    BigDecimal quantity = componentQuantity();
                    if (quantity == null) {
                        yield null;
                    }
                    // One line an item: adding it again says how many, rather than a second component of it.
                    targets.removeIf(existingLabel -> existingLabel.target().component()
                            && existingLabel.target().itemId() == item.getId());
                    componentItems.put(item.getId(), item);
                    yield new OfferTargetLabel(OfferTarget.component(item.getId(),
                            unit == null ? null : unit.getUnit_id(), quantity), item.getNameItem(),
                            unit == null ? null : unit.getUnit_name(), null, null);
                }
                if (targetGift.isVisible() && targetGift.isSelected()) {
                    OfferTarget gift = unit == null ? OfferTarget.reward(item.getId())
                            : OfferTarget.rewardInUnit(item.getId(), unit.getUnit_id());
                    // One gift an offer: a second replaces the first rather than joining it.
                    targets.removeIf(existingLabel -> existingLabel.target().reward());
                    yield new OfferTargetLabel(gift, item.getNameItem(),
                            unit == null ? null : unit.getUnit_name(), null, null);
                }
                OfferTarget target = unit == null ? OfferTarget.item(item.getId())
                        : OfferTarget.itemInUnit(item.getId(), unit.getUnit_id());
                yield new OfferTargetLabel(excluded ? target.except() : target, item.getNameItem(),
                        unit == null ? null : unit.getUnit_name(), null, null);
            }
            case SUB_GROUP -> {
                OfferChoice group = targetSubGroup.getValue();
                yield group == null ? null : new OfferTargetLabel(excluded
                        ? OfferTarget.subGroup(group.id()).except() : OfferTarget.subGroup(group.id()),
                        null, null, group.name(), null);
            }
            case MAIN_GROUP -> {
                OfferChoice group = targetMainGroup.getValue();
                yield group == null ? null : new OfferTargetLabel(excluded
                        ? OfferTarget.mainGroup(group.id()).except() : OfferTarget.mainGroup(group.id()),
                        null, null, null, group.name());
            }
            case ALL -> new OfferTargetLabel(OfferTarget.everything(), null, null, null, null);
        };
        if (label != null && targets.stream().noneMatch(existingLabel -> existingLabel.target().equals(label.target()))) {
            targets.add(label);
            targetItem.clearChoice();
            targetExcluded.setSelected(false);
        }
    }

    /** The quantity typed beside a component - one when blank - or null, with the refusal said. */
    private BigDecimal componentQuantity() {
        try {
            BigDecimal quantity = NumberTextConverter.parse(txtComponentQuantity.getText());
            if (quantity == null) {
                return BigDecimal.ONE;
            }
            if (quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 3) {
                throw new com.hamza.controlsfx.error.UserValidationException("offer.error.bundle.quantity");
            }
            return quantity;
        } catch (Exception e) {
            AllAlerts.handleError(text("offers.title"), e instanceof NumberFormatException
                    ? new com.hamza.controlsfx.error.UserValidationException("offer.error.bundle.quantity") : e);
            return null;
        }
    }

    // ---- what the form says ------------------------------------------------------------------

    /** The offer as typed - refused with a message key when it is not one yet. */
    private Offer offer() throws DaoException {
        Set<DayOfWeek> ticked = days.entrySet().stream().filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        Set<Integer> tiers = tierBoxes.stream().filter(CheckBox::isSelected)
                .map(check -> (Integer) check.getUserData()).collect(Collectors.toCollection(LinkedHashSet::new));
        OfferChoice unit = comboUnit.getValue();
        OfferForm.Terms terms;
        int priority;
        try {
            terms = new OfferForm.Terms(NumberTextConverter.parse(txtValue.getText()),
                    NumberTextConverter.parse(txtBuy.getText()), NumberTextConverter.parse(txtGet.getText()),
                    NumberTextConverter.parse(txtGetPercent.getText()),
                    NumberTextConverter.parse(txtMaxPerInvoice.getText()),
                    NumberTextConverter.parse(txtQuantityLimit.getText()),
                    NumberTextConverter.parse(txtThreshold.getText()),
                    Boolean.TRUE.equals(comboAmountOff.getValue()), txtBarcode.getText());
            BigDecimal typedPriority = NumberTextConverter.parse(txtPriority.getText());
            priority = typedPriority == null ? 0 : typedPriority.intValueExact();
        } catch (NumberFormatException | ArithmeticException notANumber) {
            throw new com.hamza.controlsfx.error.UserValidationException("offer.error.number");
        }
        OfferStatus status = existing != null ? existing.status()
                : activateNow.isSelected() ? OfferStatus.ACTIVE : OfferStatus.DRAFT;
        LocalDateTime version = existing == null ? null : existing.version();
        return OfferForm.build(existing == null ? 0 : existing.id(), txtName.getText(), comboKind.getValue(), status,
                dateStarts.getValue(), dateEnds.getValue(), ticked, priority, terms,
                unit == null || unit.id() == 0 ? null : unit.id(), txtNotes.getText(),
                targets.stream().map(OfferTargetLabel::target).toList(), tiers, version);
    }

    /** Writes the offer; false keeps the form open, with what refused it said. */
    private boolean write(Window owner) {
        try {
            Offer offer = offer();
            boolean goesLive = offer.status() == OfferStatus.ACTIVE
                    && (existing == null || !OfferForm.sameTerms(existing, offer));
            if (goesLive && !OffersController.confirmBelowCost(owner, service, offer, activeTiers)) {
                return false;
            }
            if (existing == null) {
                service.create(offer);
            } else {
                service.update(offer);
            }
            return true;
        } catch (Exception e) {
            AllAlerts.handleError(text("offers.title"), e);
            return false;
        }
    }

    /** What the offer as typed would give the quantity of the item tried, at tier 1 - or why nothing. */
    private void showTryOut() {
        if (comboKind.getValue() == OfferKind.BUNDLE) {
            showBundleTryOut();
            return;
        }
        ItemsModel item = tryItem == null ? null : tryItem.chosenItemProperty().get();
        UnitsModel unit = tryUnit.getValue();
        if (item == null || unit == null) {
            tryResult.setText("");
            return;
        }
        try {
            Offer offer = offer();
            BigDecimal quantity = NumberTextConverter.parse(tryQuantity.getText());
            if (quantity == null || quantity.signum() <= 0) {
                tryResult.setText("");
                return;
            }
            double price = ItemUnits.sellPrice(item, unit, PriceTiers.FIRST, item.getSelPrice1());
            int sub = item.getSubGroups() == null ? 0 : item.getSubGroups().getId();
            int main = item.getSubGroups() == null || item.getSubGroups().getMainGroups() == null
                    ? 0 : item.getSubGroups().getMainGroups().getId();
            OfferEngine.Line line = new OfferEngine.Line(0, item.getId(), unit.getUnit_id(), sub, main,
                    BigDecimal.valueOf(ItemUnits.factor(unit)), quantity, BigDecimal.valueOf(price));
            if (offer.rewardTarget().isPresent()) {
                // A gift is earned by one item and given on another's line: one line cannot show it.
                tryResult.setText(text("offer.try.gift"));
                return;
            }
            Optional<BigDecimal> discount = OfferEngine.discountFor(offer, line);
            if (discount.isEmpty()) {
                tryResult.setText(text("offer.try.not.reached"));
            } else if (offer.kind() == OfferKind.INVOICE && line.value().compareTo(offer.threshold()) < 0) {
                tryResult.setText(text("offer.try.threshold", Columns.money(line.value()),
                        Columns.money(offer.threshold())));
            } else if (discount.get().signum() == 0) {
                tryResult.setText(text("offer.try.nothing"));
            } else {
                tryResult.setText(text("offer.try.result", Columns.money(line.value()),
                        Columns.money(discount.get()), Columns.money(line.value().subtract(discount.get()))));
            }
        } catch (Exception notAnOfferYet) {
            tryResult.setText("");
        }
    }

    /** One bundle at the first tier's prices: what its components come to, what it gives, and what it costs. */
    private void showBundleTryOut() {
        try {
            Offer offer = offer();
            List<OfferEngine.Line> lines = new ArrayList<>();
            for (OfferTarget component : offer.components()) {
                ItemsModel item = componentItem(component.itemId());
                if (item == null) {
                    tryResult.setText("");
                    return;
                }
                UnitsModel unit = component.unitId() == null ? ItemUnits.baseUnit(item)
                        : ItemUnits.unitsFor(item).stream().filter(u -> u.getUnit_id() == component.unitId())
                        .findFirst().orElse(null);
                if (unit == null) {
                    tryResult.setText("");
                    return;
                }
                int sub = item.getSubGroups() == null ? 0 : item.getSubGroups().getId();
                int main = item.getSubGroups() == null || item.getSubGroups().getMainGroups() == null
                        ? 0 : item.getSubGroups().getMainGroups().getId();
                lines.add(new OfferEngine.Line(lines.size(), item.getId(), unit.getUnit_id(), sub, main,
                        BigDecimal.valueOf(ItemUnits.factor(unit)), component.quantity(),
                        BigDecimal.valueOf(ItemUnits.sellPrice(item, unit, PriceTiers.FIRST, item.getSelPrice1()))));
            }
            BigDecimal value = lines.stream().map(OfferEngine.Line::value).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal given = OfferEngine.givenAlone(offer, lines).values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            tryResult.setText(given.signum() == 0
                    ? text("offer.try.bundle.nothing", Columns.money(value), Columns.money(offer.offerPrice()))
                    : text("offer.try.result", Columns.money(value), Columns.money(given),
                            Columns.money(value.subtract(given))));
        } catch (Exception notABundleYet) {
            tryResult.setText("");
        }
    }

    /** A component's item: the one picked for it, else read once and kept. */
    private ItemsModel componentItem(int itemId) throws DaoException {
        ItemsModel item = componentItems.get(itemId);
        if (item == null) {
            item = itemsService.findItemById(itemId);
            if (item != null) {
                componentItems.put(itemId, item);
            }
        }
        return item;
    }

    private static Label caption(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        label.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        return label;
    }

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> name) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : name.apply(value);
            }

            @Override
            public T fromString(String value) {
                return null;
            }
        };
    }

    private static StringConverter<UnitsModel> unitConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(UnitsModel unit) {
                return unit == null ? text("offer.form.target.any.unit") : unit.getUnit_name();
            }

            @Override
            public UnitsModel fromString(String value) {
                return null;
            }
        };
    }
}
