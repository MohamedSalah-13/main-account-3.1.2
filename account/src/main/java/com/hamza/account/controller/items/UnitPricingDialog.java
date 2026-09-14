package com.hamza.account.controller.items;

import com.hamza.account.config.ThemeManager;
import com.hamza.account.features.unitprices.AutomaticPricing;
import com.hamza.account.features.unitprices.PriceField;
import com.hamza.account.features.unitprices.UnitPriceDraft;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The two questions "automatic prices" asks before it changes anything: which units, which prices,
 * and whether to clear them or fix them at today's figure - then a list of every figure that would
 * change, before and after, which the operator confirms or backs out of.
 */
final class UnitPricingDialog extends Dialog<UnitPricingDialog.Options> {

    /** Which units the operation covers. */
    enum Scope {
        /** The units ticked on the page. */
        TICKED,
        /** Every unit on the page. */
        PAGE,
        /** Every unit of every item the filter matches, on every page - saved on confirmation. */
        FILTER
    }

    record Options(Scope scope, AutomaticPricing.Mode mode, Set<PriceField> fields) {
    }

    private final ToggleGroup scopes = new ToggleGroup();
    private final ToggleGroup modes = new ToggleGroup();
    private final Map<PriceField, CheckBox> fieldBoxes = new EnumMap<>(PriceField.class);

    /**
     * @param filterAllowed whether the screen holds no unsaved edits - the filter-wide operation
     *                      saves at once, and would otherwise be saved beside edits nobody confirmed
     */
    UnitPricingDialog(Window owner, boolean costVisible, int tickedUnits, int pageUnits, int filterItems,
                      boolean filterAllowed) {
        LanguageManager lm = LanguageManager.getInstance();
        if (owner != null) initOwner(owner);
        setTitle(text("unit.prices.automatic.title"));
        setHeaderText(text("unit.prices.automatic.header"));

        RadioButton ticked = radio(scopes, lm.getString("unit.prices.automatic.scope.ticked", tickedUnits), Scope.TICKED);
        RadioButton page = radio(scopes, lm.getString("unit.prices.automatic.scope.page", pageUnits), Scope.PAGE);
        RadioButton wide = radio(scopes, lm.getString("unit.prices.automatic.scope.filter", filterItems), Scope.FILTER);
        ticked.setDisable(tickedUnits == 0);
        wide.setDisable(!filterAllowed || filterItems == 0);
        (tickedUnits > 0 ? ticked : page).setSelected(true);
        Label wideHint = hint(filterAllowed ? "unit.prices.automatic.scope.filter.hint"
                : "unit.prices.automatic.scope.filter.blocked");

        RadioButton automatic = radio(modes, text("unit.prices.automatic.mode.automatic"), AutomaticPricing.Mode.AUTOMATIC);
        RadioButton fixed = radio(modes, text("unit.prices.automatic.mode.fixed"), AutomaticPricing.Mode.FIXED);
        automatic.setSelected(true);

        HBox fields = new HBox(14);
        for (PriceField field : PriceField.values()) {
            if (field == PriceField.BUY && !costVisible) continue;
            CheckBox box = new CheckBox(fieldName(field));
            box.setSelected(true);
            box.selectedProperty().addListener((observable, old, on) -> refreshOk());
            fieldBoxes.put(field, box);
            fields.getChildren().add(box);
        }

        VBox content = new VBox(8,
                section("unit.prices.automatic.scope"), ticked, page, wide, wideHint,
                section("unit.prices.automatic.mode"), automatic, hint("unit.prices.automatic.mode.automatic.hint"),
                fixed, hint("unit.prices.automatic.mode.fixed.hint"),
                section("unit.prices.automatic.fields"), fields);
        content.setPadding(new Insets(12));
        getDialogPane().setContent(content);

        ButtonType next = new ButtonType(text("unit.prices.automatic.next"), ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().setAll(next, ButtonType.CANCEL);
        getDialogPane().setNodeOrientation(lm.getNodeOrientation());
        ThemeManager.apply(getDialogPane().getScene());
        setResultConverter(button -> button == next ? new Options(
                (Scope) scopes.getSelectedToggle().getUserData(),
                (AutomaticPricing.Mode) modes.getSelectedToggle().getUserData(),
                chosenFields()) : null);
        refreshOk();
    }

    private Set<PriceField> chosenFields() {
        Set<PriceField> chosen = EnumSet.noneOf(PriceField.class);
        fieldBoxes.forEach((field, box) -> {
            if (box.isSelected()) chosen.add(field);
        });
        return chosen;
    }

    private void refreshOk() {
        Node ok = getDialogPane().getButtonTypes().isEmpty() ? null
                : getDialogPane().lookupButton(getDialogPane().getButtonTypes().getFirst());
        if (ok != null) ok.setDisable(chosenFields().isEmpty());
    }

    /**
     * Every figure the operation would change, and a yes or a no.
     *
     * @param savesImmediately the filter-wide operation, which saves on "yes" rather than leaving the
     *                         change on screen for the Save button
     */
    static boolean confirmPreview(Window owner, List<UnitPriceDraft.PreviewRow> rows, boolean savesImmediately) {
        LanguageManager lm = LanguageManager.getInstance();
        long units = rows.stream().map(row -> row.itemId() + ":" + row.unitName()).distinct().count();

        TableView<UnitPriceDraft.PreviewRow> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.getColumns().setAll(List.of(
                Columns.text("unit.prices.preview.item", UnitPriceDraft.PreviewRow::itemName),
                Columns.text("unit.prices.preview.unit", UnitPriceDraft.PreviewRow::unitName),
                Columns.text("unit.prices.preview.field", row -> fieldName(row.field())),
                Columns.text("unit.prices.preview.before", row -> figure(row.before(), row.beforeAutomatic())),
                Columns.text("unit.prices.preview.after", row -> figure(row.after(), row.afterAutomatic()))));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefSize(760, 380);

        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle(text("unit.prices.preview.title"));
        dialog.setHeaderText(lm.getString("unit.prices.preview.header", rows.size(), units));
        VBox content = new VBox(8, table, hint(savesImmediately
                ? "unit.prices.preview.saves.now" : "unit.prices.preview.saves.later"));
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        ButtonType apply = new ButtonType(text(savesImmediately ? "unit.prices.preview.save" : "unit.prices.preview.apply"),
                ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(apply, ButtonType.CANCEL);
        dialog.getDialogPane().setNodeOrientation(lm.getNodeOrientation());
        dialog.setResizable(true);
        ThemeManager.apply(dialog.getDialogPane().getScene());
        return dialog.showAndWait().filter(button -> button == apply).isPresent();
    }

    private static String figure(double value, boolean automatic) {
        String amount = value > 0 ? Columns.money(BigDecimal.valueOf(value)) : "—";
        return automatic ? LanguageManager.getInstance().getString("unit.prices.preview.automatic", amount) : amount;
    }

    static String fieldName(PriceField field) {
        return switch (field) {
            case BUY -> text("unit.prices.column.buy");
            case SELL_1 -> text("unit.prices.column.sell1");
            case SELL_2 -> text("unit.prices.column.sell2");
            case SELL_3 -> text("unit.prices.column.sell3");
        };
    }

    private static RadioButton radio(ToggleGroup group, String label, Object value) {
        RadioButton button = new RadioButton(label);
        button.setToggleGroup(group);
        button.setUserData(value);
        return button;
    }

    private static Label section(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("section-title");
        return label;
    }

    private static Label hint(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("kpi-hint");
        label.setWrapText(true);
        return label;
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
