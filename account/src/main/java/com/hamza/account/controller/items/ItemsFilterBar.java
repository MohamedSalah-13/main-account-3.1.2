package com.hamza.account.controller.items;

import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.features.items.ItemCatalogFilter.BalanceRule;
import com.hamza.account.features.items.ItemCatalogFilter.MatchMode;
import com.hamza.account.features.items.ItemCatalogFilter.SearchScope;
import com.hamza.account.features.items.ItemCatalogFilter.Tristate;
import com.hamza.account.features.items.ItemCatalogFilter.UsageRule;
import com.hamza.account.features.items.SavedItemFilters;
import com.hamza.account.model.domain.MainGroups;
import com.hamza.account.model.domain.SubGroups;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.input.KeyCode;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;

import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.prefs.Preferences;

/**
 * Every way the items screen can be narrowed, in one place.
 * <p>
 * There are four of them and they are deliberately not four features: the search box, the
 * quick chips, the filter panel and a saved filter all produce the same
 * {@link ItemCatalogFilter}, and this class holds exactly one of those as the single source
 * of truth. The controls are drawn <em>from</em> it by {@link #syncControls()} and never
 * hold state of their own - which is what stops a chip and the panel from disagreeing, the
 * failure that makes a filter panel worse than none at all.
 * <p>
 * The search text is the one thing not owned here: it is debounced by
 * {@link PaginationTableSetting}, which already knows how to drop a superseded read, and
 * duplicating that here would run two queries per keystroke.
 */
public final class ItemsFilterBar {

    /** Where a user's saved filters live. Per user and per machine, never in the database. */
    private static final String PREFERENCES_NODE = "items/filters";

    private final ComboBox<SearchScope> comboScope;
    private final ComboBox<MatchMode> comboMatch;
    private final ComboBox<GroupChoice> comboGroup;
    private final ComboBox<Tristate> comboActive;
    private final ComboBox<Tristate> comboBarcode;
    private final ComboBox<Tristate> comboExpiry;
    private final ComboBox<BalanceRule> comboBalance;
    private final ComboBox<UsageRule> comboUsage;
    private final TextField txtMinPrice;
    private final TextField txtMaxPrice;
    private final ComboBox<String> comboSaved;
    private final FlowPane chipBar;
    private final Region filterPane;
    private final ToggleButton btnFilters;
    private final Label labelFiltered;

    private final SavedItemFilters savedFilters;
    private final Consumer<ItemCatalogFilter> onChanged;
    private final List<Chip> chips = new ArrayList<>();

    /**
     * What the whole screen is filtered by right now. Replaced wholesale on every change,
     * so a listener can compare the old and the new rather than guessing what moved.
     */
    private ItemCatalogFilter filter = ItemCatalogFilter.EMPTY;
    /** True while the controls are being drawn from {@link #filter}, so their own listeners stay quiet. */
    private boolean syncing;

    public ItemsFilterBar(ComboBox<SearchScope> comboScope, ComboBox<MatchMode> comboMatch,
                          ComboBox<GroupChoice> comboGroup,
                          ComboBox<Tristate> comboActive, ComboBox<Tristate> comboBarcode,
                          ComboBox<Tristate> comboExpiry, ComboBox<BalanceRule> comboBalance,
                          ComboBox<UsageRule> comboUsage, TextField txtMinPrice, TextField txtMaxPrice,
                          ComboBox<String> comboSaved, FlowPane chipBar, Region filterPane,
                          ToggleButton btnFilters, Label labelFiltered,
                          Consumer<ItemCatalogFilter> onChanged) {
        this.comboScope = comboScope;
        this.comboMatch = comboMatch;
        this.comboGroup = comboGroup;
        this.comboActive = comboActive;
        this.comboBarcode = comboBarcode;
        this.comboExpiry = comboExpiry;
        this.comboBalance = comboBalance;
        this.comboUsage = comboUsage;
        this.txtMinPrice = txtMinPrice;
        this.txtMaxPrice = txtMaxPrice;
        this.comboSaved = comboSaved;
        this.chipBar = chipBar;
        this.filterPane = filterPane;
        this.btnFilters = btnFilters;
        this.labelFiltered = labelFiltered;
        this.onChanged = onChanged;
        this.savedFilters = new SavedItemFilters(
                Preferences.userNodeForPackage(ItemsController.class).node(PREFERENCES_NODE));
    }

    /** A main group, a sub group, or "all of them" - one entry type for one combo. */
    public record GroupChoice(Integer mainGroupId, Integer subGroupId, String label, boolean sub) {
        static GroupChoice all() {
            return new GroupChoice(null, null,
                    LanguageManager.getInstance().getString("item.filter.group.all"), false);
        }
    }

    public ItemCatalogFilter filter() {
        return filter;
    }

    public void initialize() {
        fillCombo(comboScope, SearchScope.values(), scope -> switch (scope) {
            case ANY -> "item.filter.scope.any";
            case CODE -> "item.filter.scope.code";
            case BARCODE -> "item.filter.scope.barcode";
            case NAME -> "item.filter.scope.name";
        });
        fillCombo(comboMatch, MatchMode.values(), mode -> switch (mode) {
            case AUTO -> "item.filter.match.auto";
            case EXACT -> "item.filter.match.exact";
            case CONTAINS -> "item.filter.match.contains";
            case STARTS_WITH -> "item.filter.match.starts";
            case ENDS_WITH -> "item.filter.match.ends";
        });
        fillCombo(comboActive, Tristate.values(), tristate -> switch (tristate) {
            case ANY -> "item.filter.active.any";
            case YES -> "item.filter.active.yes";
            case NO -> "item.filter.active.no";
        });
        fillCombo(comboBarcode, Tristate.values(), tristate -> switch (tristate) {
            case ANY -> "item.filter.barcode.any";
            case YES -> "item.filter.barcode.yes";
            case NO -> "item.filter.barcode.no";
        });
        fillCombo(comboExpiry, Tristate.values(), tristate -> switch (tristate) {
            case ANY -> "item.filter.expiry.any";
            case YES -> "item.filter.expiry.yes";
            case NO -> "item.filter.expiry.no";
        });
        fillCombo(comboBalance, BalanceRule.values(), rule -> switch (rule) {
            case ANY -> "item.filter.balance.any";
            case BELOW_MINIMUM -> "item.filter.balance.below";
            case OUT_OF_STOCK -> "item.filter.balance.out";
            case NEGATIVE -> "item.filter.balance.negative";
            case IN_STOCK -> "item.filter.balance.in";
        });
        fillCombo(comboUsage, UsageRule.values(), rule -> switch (rule) {
            case ANY -> "item.filter.usage.any";
            case NEVER_MOVED -> "item.filter.usage.never.moved";
            case NEVER_SOLD -> "item.filter.usage.never.sold";
        });

        comboGroup.setConverter(new StringConverter<>() {
            @Override
            public String toString(GroupChoice choice) {
                if (choice == null) return "";
                // A sub group is indented under its main one; the combo is flat, and a
                // list of names with no shape reads as one level.
                return choice.sub() ? "    " + choice.label() : choice.label();
            }

            @Override
            public GroupChoice fromString(String text) {
                return null;
            }
        });
        comboGroup.setItems(FXCollections.observableArrayList(GroupChoice.all()));
        comboGroup.getSelectionModel().selectFirst();

        buildChips();
        reloadSavedFilters();

        // The scope belongs to the search box rather than to the panel, so it applies the
        // moment it changes - there is nothing else on that row to press.
        comboScope.valueProperty().addListener((observable, old, scope) -> {
            if (syncing || scope == null) return;
            apply(filter.withSearchScope(scope));
        });
        // Both of these belong to the search box rather than to the panel, so they apply the
        // moment they change - there is no Apply button on that row to press.
        comboMatch.valueProperty().addListener((observable, old, mode) -> {
            if (syncing || mode == null) return;
            apply(filter.withMatchMode(mode));
        });
        comboSaved.valueProperty().addListener((observable, old, name) -> {
            if (syncing || name == null) return;
            ItemCatalogFilter saved = savedFilters.get(name);
            // The typed text is kept: a saved filter is a standing question, and what the
            // operator is looking for inside it right now is their own business.
            if (saved != null) apply(saved.withSearch(filter.searchText()));
        });
        btnFilters.setOnAction(event -> setPanelVisible(btnFilters.isSelected()));
        syncControls();
    }

    /** The two buttons inside the panel, and the two beside the saved-filter combo. */
    public void wireButtons(Button apply, Button clear, Button save, Button delete) {
        // The price bounds are a small form and are typed in order, so Enter walks it:
        // from, to, apply. The button is not a default button on purpose - Enter would
        // then fire it from the search box, three controls away, while the panel is shut.
        whenEnterPressed(txtMinPrice, txtMaxPrice, apply);
        apply.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) apply.fire();
        });
        apply.setOnAction(event -> apply(readControls()));
        clear.setOnAction(event -> apply(ItemCatalogFilter.EMPTY.withSearch(filter.searchText())));
        save.setOnAction(event -> saveCurrent());
        delete.setOnAction(event -> deleteSelected());
    }

    /**
     * The groups, flattened for the panel's combo.
     * <p>
     * Handed in rather than read here: the tree pane already loads them off the JavaFX
     * thread, and a second read on this thread would be the slow query the paging was
     * written to avoid, run every time the screen opens.
     */
    public void setGroups(List<MainGroups> mainGroups, List<SubGroups> subGroups) {
        List<GroupChoice> choices = new ArrayList<>();
        choices.add(GroupChoice.all());
        for (MainGroups main : mainGroups) {
            choices.add(new GroupChoice(main.getId(), null, main.getName(), false));
            for (SubGroups sub : subGroups) {
                if (sub.getMainGroups() != null && sub.getMainGroups().getId() == main.getId()) {
                    choices.add(new GroupChoice(main.getId(), sub.getId(), sub.getName(), true));
                }
            }
        }
        comboGroup.setItems(FXCollections.observableArrayList(choices));
        syncControls();
    }

    /** Sets the group from outside - the tree on the left selects through here, not around it. */
    public void selectGroup(Integer mainGroupId, Integer subGroupId) {
        apply(filter.withGroup(mainGroupId, subGroupId));
    }

    /** Records what was typed, without re-running anything: the debounce owns that. */
    public void noteSearchText(String text) {
        filter = filter.withSearch(text);
        updateBadges();
    }

    private void setPanelVisible(boolean visible) {
        filterPane.setVisible(visible);
        filterPane.setManaged(visible);
        btnFilters.setSelected(visible);
    }

    /**
     * Adopts a new filter, redraws every control from it and tells the screen once.
     * <p>
     * One path in and one notification out, whichever affordance was used. A chip that
     * updated the query directly would leave the panel showing the previous answer.
     */
    private void apply(ItemCatalogFilter next) {
        filter = next == null ? ItemCatalogFilter.EMPTY : next;
        syncControls();
        onChanged.accept(filter);
    }

    /** Reads the panel's controls into a filter. Only the panel's own Apply button calls it. */
    private ItemCatalogFilter readControls() {
        GroupChoice group = comboGroup.getValue();
        return new ItemCatalogFilter(
                filter.searchText(),
                value(comboScope, SearchScope.ANY),
                value(comboMatch, MatchMode.AUTO),
                group == null ? null : group.mainGroupId(),
                group == null ? null : group.subGroupId(),
                value(comboActive, Tristate.ANY),
                value(comboBarcode, Tristate.ANY),
                value(comboExpiry, Tristate.ANY),
                value(comboBalance, BalanceRule.ANY),
                price(txtMinPrice),
                price(txtMaxPrice),
                value(comboUsage, UsageRule.ANY));
    }

    /**
     * Draws every control from {@link #filter}.
     * <p>
     * {@link #syncing} is what keeps this from being a loop: setting a combo's value fires
     * its listener, which would apply a filter, which would sync again.
     */
    private void syncControls() {
        syncing = true;
        try {
            comboScope.setValue(filter.searchScope());
            comboMatch.setValue(filter.matchMode());
            comboActive.setValue(filter.active());
            comboBarcode.setValue(filter.hasBarcode());
            comboExpiry.setValue(filter.tracksExpiry());
            comboBalance.setValue(filter.balance());
            comboUsage.setValue(filter.usage());
            txtMinPrice.setText(filter.minSellPrice() == null ? "" : String.valueOf(filter.minSellPrice()));
            txtMaxPrice.setText(filter.maxSellPrice() == null ? "" : String.valueOf(filter.maxSellPrice()));
            selectGroupChoice();
            for (Chip chip : chips) {
                chip.button().setSelected(chip.isOn(filter));
            }
            updateBadges();
        } finally {
            syncing = false;
        }
    }

    private void selectGroupChoice() {
        for (GroupChoice choice : comboGroup.getItems()) {
            if (java.util.Objects.equals(choice.mainGroupId(), filter.mainGroupId())
                    && java.util.Objects.equals(choice.subGroupId(), filter.subGroupId())) {
                comboGroup.setValue(choice);
                return;
            }
        }
        comboGroup.getSelectionModel().selectFirst();
    }

    /**
     * The filter button says how many conditions are on, and a badge beside the title says
     * the list is narrowed at all.
     * <p>
     * This is the whole reason a collapsed panel is safe. Without it, a condition set
     * yesterday and forgotten shows as an empty table, and the operator concludes the items
     * are gone.
     */
    private void updateBadges() {
        int count = filter.activeConditionCount();
        LanguageManager language = LanguageManager.getInstance();
        btnFilters.setText(count == 0
                ? language.getString("item.filter.button")
                : language.getString("item.filter.button.count", count));
        boolean narrowed = !filter.isEmpty();
        labelFiltered.setVisible(narrowed);
        labelFiltered.setManaged(narrowed);
    }

    // ---------------------------------------------------------------------------
    // Quick chips
    // ---------------------------------------------------------------------------

    /**
     * One condition, one click.
     * <p>
     * A chip is nothing but a named pair of functions over the filter, so a chip cannot
     * mean anything the panel cannot also express - which is what keeps the two able to
     * show each other's state.
     */
    private record Chip(ToggleButton button, Function<ItemCatalogFilter, ItemCatalogFilter> on,
                        Function<ItemCatalogFilter, ItemCatalogFilter> off,
                        java.util.function.Predicate<ItemCatalogFilter> test) {
        boolean isOn(ItemCatalogFilter filter) {
            return test.test(filter);
        }
    }

    private void buildChips() {
        addChip("item.chip.below.minimum",
                current -> current.withBalance(BalanceRule.BELOW_MINIMUM),
                current -> current.withBalance(BalanceRule.ANY),
                current -> current.balance() == BalanceRule.BELOW_MINIMUM);
        addChip("item.chip.out.of.stock",
                current -> current.withBalance(BalanceRule.OUT_OF_STOCK),
                current -> current.withBalance(BalanceRule.ANY),
                current -> current.balance() == BalanceRule.OUT_OF_STOCK);
        addChip("item.chip.negative",
                current -> current.withBalance(BalanceRule.NEGATIVE),
                current -> current.withBalance(BalanceRule.ANY),
                current -> current.balance() == BalanceRule.NEGATIVE);
        addChip("item.chip.no.barcode",
                current -> current.withHasBarcode(Tristate.NO),
                current -> current.withHasBarcode(Tristate.ANY),
                current -> current.hasBarcode() == Tristate.NO);
        addChip("item.chip.inactive",
                current -> current.withActive(Tristate.NO),
                current -> current.withActive(Tristate.ANY),
                current -> current.active() == Tristate.NO);
        addChip("item.chip.never.moved",
                current -> current.withUsage(UsageRule.NEVER_MOVED),
                current -> current.withUsage(UsageRule.ANY),
                current -> current.usage() == UsageRule.NEVER_MOVED);
    }

    private void addChip(String labelKey, Function<ItemCatalogFilter, ItemCatalogFilter> on,
                         Function<ItemCatalogFilter, ItemCatalogFilter> off,
                         java.util.function.Predicate<ItemCatalogFilter> test) {
        ToggleButton button = new ToggleButton(LanguageManager.getInstance().getString(labelKey));
        button.getStyleClass().add("filter-chip");
        Chip chip = new Chip(button, on, off, test);
        chips.add(chip);
        button.setOnAction(event -> {
            if (syncing) return;
            // Read the intent off the button, then rebuild from the filter: the three
            // balance chips write the same field, so turning one on turns the other two
            // off without any of them knowing the others exist.
            apply(button.isSelected() ? chip.on().apply(filter) : chip.off().apply(filter));
        });
        chipBar.getChildren().add(button);
    }

    // ---------------------------------------------------------------------------
    // Saved filters
    // ---------------------------------------------------------------------------

    private void reloadSavedFilters() {
        syncing = true;
        try {
            String selected = comboSaved.getValue();
            comboSaved.setItems(FXCollections.observableArrayList(savedFilters.names()));
            if (selected != null && comboSaved.getItems().contains(selected)) {
                comboSaved.setValue(selected);
            }
        } finally {
            syncing = false;
        }
    }

    private void saveCurrent() {
        LanguageManager language = LanguageManager.getInstance();
        if (filter.activeConditionCount() == 0) {
            com.hamza.controlsfx.alert.AllAlerts.handleError(
                    language.getString("item.filter.save.title"),
                    new com.hamza.controlsfx.error.UserValidationException(
                            language.getString("item.filter.save.empty")));
            return;
        }
        TextInputDialog dialog = new TextInputDialog(comboSaved.getValue());
        dialog.setTitle(language.getString("item.filter.save.title"));
        dialog.setHeaderText(null);
        dialog.setContentText(language.getString("item.filter.save.prompt"));
        Optional<String> name = dialog.showAndWait();
        name.filter(text -> !text.isBlank()).ifPresent(text -> {
            savedFilters.save(text, filter);
            reloadSavedFilters();
            comboSaved.setValue(text.trim());
        });
    }

    private void deleteSelected() {
        String name = comboSaved.getValue();
        if (name == null) return;
        savedFilters.delete(name);
        syncing = true;
        try {
            comboSaved.setValue(null);
        } finally {
            syncing = false;
        }
        reloadSavedFilters();
    }

    // ---------------------------------------------------------------------------
    // Small helpers
    // ---------------------------------------------------------------------------

    private static <E> void fillCombo(ComboBox<E> combo, E[] values, Function<E, String> labelKey) {
        combo.setItems(FXCollections.observableArrayList(values));
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(E value) {
                return value == null ? "" : LanguageManager.getInstance().getString(labelKey.apply(value));
            }

            @Override
            public E fromString(String text) {
                return null;
            }
        });
        combo.getSelectionModel().selectFirst();
    }

    private static <E> E value(ComboBox<E> combo, E fallback) {
        return combo.getValue() == null ? fallback : combo.getValue();
    }

    /**
     * A price bound, or {@code null} where the field is blank or holds something that is
     * not a number. Refusing to parse is not an error worth interrupting anyone over - the
     * bound simply is not applied, and the field shows what was typed.
     */
    private static Double price(TextField field) {
        String text = field.getText();
        if (text == null || text.isBlank()) return null;
        try {
            return Double.valueOf(text.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** Adds the same explanation to a control and to its label, where one is given. */
    static void tooltip(javafx.scene.control.Control control, String key) {
        control.setTooltip(new Tooltip(LanguageManager.getInstance().getString(key)));
    }
}
