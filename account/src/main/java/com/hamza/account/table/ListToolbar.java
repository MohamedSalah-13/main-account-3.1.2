package com.hamza.account.table;

import com.hamza.account.config.AppIcon;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The bar a list screen puts above its table, in the one order every such screen uses:
 * <b>search, filters, clear all | refresh, print, export, view | anything else</b>.
 * <p>
 * <b>The order is decided here and nowhere else.</b> A screen names what each of its controls
 * <em>is</em> - this is the refresh, that is the print - and this class places them. The screens
 * that had been arranged by hand had drifted apart: the totals screen put the view menu before
 * refresh and print last, the parties list put its reports first, and the employees screen put
 * its search after two rows of fields. Nobody decided those differences; they are what a
 * hand-written row turns into, one screen at a time. {@code ListToolbarArchitectureTest} is
 * what keeps a new list screen from writing its own.
 * <p>
 * <b>The filters panel is closed until somebody opens it</b>, and the button says how many
 * filters are narrowing the list whether it is open or not - a closed panel must never hide the
 * fact that the list is filtered. The panel is placed by the screen, since it sits under the bar
 * rather than in it; this class only owns its visibility, which is {@code visible} and
 * {@code managed} together, or a hidden panel keeps its height as an empty band.
 * <p>
 * It works for a bar declared in FXML as much as for one built in code: {@link #installIn}
 * replaces the row's children with the same nodes in their order, so an {@code fx:id} keeps
 * pointing at the control it named.
 */
public final class ListToolbar {

    /** What a control in the bar is. The declaration order is the order on screen. */
    public enum Slot {
        SEARCH_FIELD(Group.FIND),
        SEARCH(Group.FIND),
        FILTERS(Group.FIND),
        CLEAR(Group.FIND),
        REFRESH(Group.LIST),
        PRINT(Group.LIST),
        EXPORT(Group.LIST),
        VIEW(Group.LIST),
        EXTRA(Group.OTHER);

        private final Group group;

        Slot(Group group) {
            this.group = group;
        }
    }

    /** Finding rows, acting on the list found, and whatever the screen adds. A separator between each. */
    enum Group {FIND, LIST, OTHER}

    private final Map<Slot, List<Node>> nodes = new EnumMap<>(Slot.class);
    private ToggleButton filtersToggle;
    private Node filtersPanel;

    public ListToolbar searchField(Node... field) {
        return put(Slot.SEARCH_FIELD, field);
    }

    public ListToolbar search(Node button) {
        return put(Slot.SEARCH, button);
    }

    /**
     * The toggle and the panel it opens. The panel starts closed, and the toggle's own action is
     * replaced - a screen that needs to react as well listens to {@link ToggleButton#selectedProperty()}.
     */
    public ListToolbar filters(ToggleButton toggle, Node panel) {
        this.filtersToggle = toggle;
        this.filtersPanel = panel;
        toggle.setOnAction(event -> setFiltersVisible(toggle.isSelected()));
        setFiltersVisible(false);
        return put(Slot.FILTERS, toggle);
    }

    public ListToolbar clear(Node button) {
        return put(Slot.CLEAR, button);
    }

    public ListToolbar refresh(Node button) {
        return put(Slot.REFRESH, button);
    }

    public ListToolbar print(Node button) {
        return put(Slot.PRINT, button);
    }

    public ListToolbar export(Node... buttons) {
        return put(Slot.EXPORT, buttons);
    }

    public ListToolbar view(Node menu) {
        return put(Slot.VIEW, menu);
    }

    /** Everything that is not one of the named controls: adding a record, opening a report, bulk actions. */
    public ListToolbar extra(Node... controls) {
        return put(Slot.EXTRA, controls);
    }

    /** Puts the controls into {@code row}, replacing what it held, and answers the row. */
    public <P extends Pane> P installIn(P row) {
        List<Node> arranged = new ArrayList<>();
        for (List<Slot> group : arrange(nodes.keySet())) {
            if (!arranged.isEmpty()) {
                Separator divider = new Separator(Orientation.VERTICAL);
                divider.getStyleClass().add("modern-separator");
                arranged.add(divider);
            }
            group.forEach(slot -> arranged.addAll(nodes.get(slot)));
        }
        row.getChildren().setAll(arranged);
        return row;
    }

    public void setFiltersVisible(boolean visible) {
        if (filtersPanel == null) {
            return;
        }
        filtersPanel.setVisible(visible);
        filtersPanel.setManaged(visible);
        filtersToggle.setSelected(visible);
    }

    /** How many filters are narrowing the list - shown on the toggle, whether the panel is open or not. */
    public void showActiveFilters(int count) {
        if (filtersToggle != null) {
            filtersToggle.setText(filtersCaption(count));
        }
    }

    /**
     * The slots present, in screen order, split into the groups a separator goes between.
     * An empty group leaves no separator behind.
     */
    static List<List<Slot>> arrange(Collection<Slot> present) {
        Set<Slot> wanted = present.isEmpty() ? EnumSet.noneOf(Slot.class) : EnumSet.copyOf(present);
        List<List<Slot>> groups = new ArrayList<>();
        for (Group group : Group.values()) {
            List<Slot> slots = new ArrayList<>();
            for (Slot slot : wanted) {
                if (slot.group == group) {
                    slots.add(slot);
                }
            }
            if (!slots.isEmpty()) {
                groups.add(slots);
            }
        }
        return groups;
    }

    static String filtersCaption(int count) {
        LanguageManager language = LanguageManager.getInstance();
        return count > 0
                ? language.getString("invoice.search.filters.count", count)
                : language.getString("invoice.search.filters");
    }

    /** The filters toggle every list screen shows, with its caption and icon. */
    public static ToggleButton filtersToggle() {
        ToggleButton toggle = new ToggleButton(filtersCaption(0), AppIcon.FILTER.graphic());
        toggle.getStyleClass().add("app-neutral-button");
        toggle.setMinWidth(Region.USE_PREF_SIZE);
        return toggle;
    }

    /** "مسح الكل" - the same words on every screen, for the same act. */
    public static Button clearButton(Runnable action) {
        return button("invoice.search.clear", AppIcon.CLEAR, action);
    }

    public static Button refreshButton(Runnable action) {
        return button("refresh", AppIcon.REFRESH, action);
    }

    public static Button printButton(Runnable action) {
        return button("print", AppIcon.PRINT, action);
    }

    /** A neutral bar button whose minimum is its whole caption, so a narrow window wraps rather than cuts it. */
    public static Button button(String key, AppIcon icon, Runnable action) {
        Button button = new Button(LanguageManager.getInstance().getString(key), icon.graphic());
        button.getStyleClass().add("app-neutral-button");
        button.setContentDisplay(ContentDisplay.RIGHT);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(event -> action.run());
        return button;
    }

    private ListToolbar put(Slot slot, Node... controls) {
        List<Node> list = nodes.computeIfAbsent(slot, key -> new ArrayList<>());
        for (Node control : controls) {
            if (control != null) {
                list.add(control);
            }
        }
        if (list.isEmpty()) {
            nodes.remove(slot);
        }
        return this;
    }
}
