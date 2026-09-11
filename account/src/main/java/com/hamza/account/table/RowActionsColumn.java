package com.hamza.account.table;

import com.hamza.controlsfx.language.LanguageManager;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import java.util.List;

/**
 * The column of buttons that act on their own row.
 *
 * <p>It exists because there were already two hand-rolled versions of this cell in the tree
 * and a third was about to be written. Each got some of it right: this one collects what has
 * to be true of all of them.
 *
 * <p><b>Three things a button cell gets wrong, and all three have shipped here before.</b>
 *
 * <ul>
 *   <li><b>A reused cell keeps its graphic.</b> A {@code TableView} does not build a cell per
 *       row; it builds enough to fill the viewport and moves them. So {@code updateItem} must
 *       clear the graphic when the cell is empty, or buttons appear on the blank rows under a
 *       short list - and pressing one acts on whatever row the cell last held.</li>
 *   <li><b>{@code getTableView().getItems().get(getIndex())} throws.</b> During the churn of a
 *       reload a cell can be asked to update at an index the list no longer reaches, and an
 *       {@code IndexOutOfBoundsException} out of a cell factory reaches the user as a
 *       reference code with no screen in it. {@code getTableRow().getItem()} is the row's own
 *       answer and is null when there is none.</li>
 *   <li><b>The buttons are built once.</b> Building them inside {@code updateItem} makes a new
 *       {@code Button} on every scroll tick, and re-registers its handler each time.</li>
 * </ul>
 *
 * <p>The column does not sort - sorting by a column of buttons means nothing - and it is
 * given a fixed width, because the buttons do not grow with the window.
 */
public final class RowActionsColumn {

    /** Enough for three icon buttons; the caption lives in the tooltip, not on the button. */
    private static final double WIDTH_PER_ACTION = 44;

    private RowActionsColumn() {
    }

    /**
     * @param titleKey the column heading - a whole literal, as {@code MessageKeyArchitectureTest}
     *                 requires
     * @param actions  already filtered by {@link RowAction#permitted(List)}; an empty list
     *                 still produces a column, so the table's shape does not depend on who is
     *                 signed in
     */
    public static <S> TableColumn<S, Void> of(String titleKey, List<RowAction<S>> actions) {
        TableColumn<S, Void> column =
                new TableColumn<>(LanguageManager.getInstance().getString(titleKey));
        column.setSortable(false);
        column.setReorderable(false);
        double width = Math.max(WIDTH_PER_ACTION, actions.size() * WIDTH_PER_ACTION);
        column.setMinWidth(width);
        column.setPrefWidth(width);
        column.setMaxWidth(width);
        column.setCellFactory(ignored -> new ActionCell<>(actions));
        return column;
    }

    private static final class ActionCell<S> extends TableCell<S, Void> {

        private final List<RowAction<S>> actions;
        private final List<Button> buttons;
        private final HBox box;

        private ActionCell(List<RowAction<S>> actions) {
            this.actions = actions;
            this.buttons = actions.stream().map(ActionCell::button).toList();
            this.box = new HBox(4);
            box.setAlignment(Pos.CENTER);
            box.getChildren().setAll(buttons);
            for (int index = 0; index < actions.size(); index++) {
                RowAction<S> action = actions.get(index);
                // The row is read when the button is pressed, not when it is built: the cell
                // outlives the row it is showing.
                buttons.get(index).setOnAction(event -> {
                    S row = row();
                    if (row != null) {
                        action.action().accept(row);
                    }
                });
            }
        }

        private static <S> Button button(RowAction<S> action) {
            Button button = new Button();
            button.setGraphic(action.icon().graphic());
            button.getStyleClass().add(action.styleClass());
            button.getStyleClass().add("row-action-button");
            String name = LanguageManager.getInstance().getString(action.titleKey());
            button.setTooltip(new Tooltip(name));
            // A button with no caption is invisible to a screen reader and to anyone using the
            // keyboard, and a tooltip is not read out.
            button.setAccessibleText(name);
            return button;
        }

        /** The row this cell is showing, or null. Never through the items list - see the javadoc. */
        private S row() {
            return getTableRow() == null ? null : getTableRow().getItem();
        }

        @Override
        protected void updateItem(Void unused, boolean empty) {
            super.updateItem(unused, empty);
            S row = empty ? null : row();
            if (row == null) {
                setGraphic(null);
                return;
            }
            for (int index = 0; index < actions.size(); index++) {
                buttons.get(index).setDisable(!actions.get(index).enabled().test(row));
            }
            setGraphic(box);
        }
    }
}
