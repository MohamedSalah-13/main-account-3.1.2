package com.hamza.controlsfx.table.colorRow;

import javafx.css.PseudoClass;
import javafx.scene.control.TableCell;

public interface RowColorInterface<S, T> {

    /**
     * Checks whether a TableRow associated with the provided TableCell should be styled.
     *
     * @param tsTableCell The TableCell instance for which to check the row condition.
     * @return true if the row should be styled, otherwise false.
     */
    default boolean checkRow(TableCell<S, T> tsTableCell) {
        return false;
    }

    /**
     * Checks a specific condition on the given row parameter to determine its state.
     *
     * @param s the row item to be checked
     * @return true if the row meets the condition, false otherwise
     */
    default boolean checkRow(S s) {
        return false;
    }

    /**
     * Returns the specific PseudoClass associated with a row in a TableView or TreeTableView.
     *
     * @return the PseudoClass "aquaRow" used for styling rows.
     */
    default PseudoClass pseudoClass() {
        return PseudoClass.getPseudoClass("aquaRow");
    }

}
