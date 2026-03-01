package com.hamza.controlsfx.table.conditional;

import javafx.scene.control.TableColumn;
import javafx.scene.paint.Color;

/**
 * A rule that can be applied to a TableView to color rows or cells
 * depending on the operator evaluation result.
 * @param value  kept as string, parsed on demand
 */
public record ConditionalRule<S>(TableColumn<S, ?> column, Operator operator, String value, ApplyScope scope,
                                 Color color) {

    @Override
    public String toString() {
        return column.getText() + " " + operator + " " + value + " [" + scope + "]";
    }
}
