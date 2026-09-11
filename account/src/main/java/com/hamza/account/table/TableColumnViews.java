package com.hamza.account.table;

import com.hamza.account.config.AppIcon;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Region;

import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * The "العرض" menu of a list: a compact view, a full view, a hand-picked set of columns,
 * and the way back to the default - remembered per table.
 *
 * <p>Written for the parties list and lifted out of it when the accounts screen wanted the
 * same menu, so the two cannot come to behave differently. Any table can take it; what it
 * needs is an id on every column it offers, since the id is what the compact set names and
 * what {@link TableSetting} keys a column's saved visibility by. CLAUDE.md, "Column views
 * and widths", has the adoption steps.</p>
 *
 * <p><b>Only the preset is stored here.</b> A custom choice is the columns' own visibility,
 * which {@link TableSetting#tableMenuSetting} already saves per column - so call that first,
 * and a custom view is restored by leaving those columns alone.</p>
 */
public final class TableColumnViews<T> {

    /** How a table's columns are shown. {@code CUSTOM} is whatever the user ticked. */
    public enum Preset {
        COMPACT,
        FULL,
        CUSTOM;

        static Preset fromStored(String value, Preset fallback) {
            for (Preset preset : values()) {
                if (preset.name().equals(value)) {
                    return preset;
                }
            }
            return fallback;
        }
    }

    private final Preferences preferences;
    private final String modeKey;
    private final Preset defaultPreset;
    private final Set<String> compactColumnIds;
    private final Set<String> fixedColumnIds;

    /**
     * @param preferences      where this table's preset lives - one node per table, and per
     *                         party kind where a screen serves both
     * @param modeKey          the key inside that node
     * @param defaultPreset    COMPACT or FULL: what the table opens as before anyone chooses,
     *                         and what "restore" goes back to
     * @param compactColumnIds the columns the compact view keeps
     * @param fixedColumnIds   columns never offered in the menu - the row actions and the
     *                         selection box, which are not data
     */
    public TableColumnViews(Preferences preferences, String modeKey, Preset defaultPreset,
                            Set<String> compactColumnIds, Set<String> fixedColumnIds) {
        if (defaultPreset == Preset.CUSTOM) {
            throw new IllegalArgumentException("A table's default view is compact or full");
        }
        this.preferences = preferences;
        this.modeKey = modeKey;
        this.defaultPreset = defaultPreset;
        this.compactColumnIds = Set.copyOf(compactColumnIds);
        this.fixedColumnIds = Set.copyOf(fixedColumnIds);
    }

    /**
     * The menu's button, for a screen that builds its own bar. The shared table shell declares
     * one in {@code main-tableview.fxml} and hands it to {@link #install} instead.
     */
    public static MenuButton menuButton() {
        MenuButton button = new MenuButton(text("party.list.view"), AppIcon.SETTINGS.graphic());
        button.getStyleClass().add("app-neutral-button");
        button.setContentDisplay(ContentDisplay.RIGHT);
        button.setMinWidth(Region.USE_PREF_SIZE);
        return button;
    }

    /** Fills the menu from the table's columns and applies the saved view. */
    public void install(MenuButton viewMenu, TableView<T> tableView) {
        RadioMenuItem compact = new RadioMenuItem(text("party.list.view.compact"));
        RadioMenuItem full = new RadioMenuItem(text("party.list.view.full"));
        ToggleGroup views = new ToggleGroup();
        compact.setToggleGroup(views);
        full.setToggleGroup(views);
        compact.setOnAction(event -> choose(tableView, Preset.COMPACT));
        full.setOnAction(event -> choose(tableView, Preset.FULL));

        Menu customize = new Menu(text("party.list.view.customize"));
        for (TableColumn<T, ?> column : configurableColumns(tableView)) {
            CheckMenuItem item = new CheckMenuItem(column.getText());
            item.selectedProperty().bindBidirectional(column.visibleProperty());
            item.setOnAction(event -> {
                preferences.put(modeKey, Preset.CUSTOM.name());
                // Neither named view describes the table any more, so neither stays ticked.
                views.selectToggle(null);
            });
            customize.getItems().add(item);
        }

        MenuItem reset = new MenuItem(text("party.list.view.reset"));
        reset.setOnAction(event -> {
            views.selectToggle(defaultPreset == Preset.FULL ? full : compact);
            choose(tableView, defaultPreset);
        });

        Preset saved = Preset.fromStored(preferences.get(modeKey, defaultPreset.name()), defaultPreset);
        if (saved != Preset.CUSTOM) {
            views.selectToggle(saved == Preset.FULL ? full : compact);
            applyVisibility(tableView, saved);
        }
        viewMenu.getItems().setAll(compact, full, new SeparatorMenuItem(), customize,
                new SeparatorMenuItem(), reset);
    }

    /** Whether a column shows under a named view. Only COMPACT and FULL are ever applied. */
    static boolean visibleUnder(Preset preset, String columnId, Set<String> compactColumnIds) {
        return preset != Preset.COMPACT || (columnId != null && compactColumnIds.contains(columnId));
    }

    private void choose(TableView<T> tableView, Preset preset) {
        applyVisibility(tableView, preset);
        preferences.put(modeKey, preset.name());
    }

    private void applyVisibility(TableView<T> tableView, Preset preset) {
        for (TableColumn<T, ?> column : configurableColumns(tableView)) {
            column.setVisible(visibleUnder(preset, column.getId(), compactColumnIds));
        }
    }

    private List<TableColumn<T, ?>> configurableColumns(TableView<T> tableView) {
        return tableView.getColumns().stream()
                .filter(column -> column.getId() == null || !fixedColumnIds.contains(column.getId()))
                .toList();
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
