package com.hamza.controlsfx.table.colorRow;

import javafx.scene.control.*;

public class RowColor {

    /**
     * Customizes the appearance of rows by adjusting their cells based on the provided criteria.
     *
     * @param <S>               The type of the items contained within the TableView.
     * @param <T>               The type of the items contained within the TableColumn.
     * @param callType          The TableColumn to customize.
     * @param rowColorInterface The interface containing the logic for determining the row's appearance.
     */
    public <S, T> void customiseRowByCell(TableColumn<S, T> callType, RowColorInterface<S, T> rowColorInterface) {
        callType.setCellFactory(column -> new TableCell<>() {

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? "" : String.valueOf(getItem()));
                setGraphic(null);
                TableRow<S> currentRow = getTableRow();
                if (!isEmpty()) {
                    currentRow.pseudoClassStateChanged(rowColorInterface.pseudoClass(), rowColorInterface.checkRow(this));
                }
            }
        });

    }

    /**
     * Customizes the appearance of each row in a TableView based on a specified RowColorInterface.
     * This method allows dynamic styling of rows in a TableView by applying pseudo-classes.
     *
     * @param tableView         the TableView whose rows are to be customized
     * @param rowColorInterface the RowColorInterface used to determine the styling of each row
     */
    public <S, T> void customiseRowByRow(TableView<S> tableView, RowColorInterface<S, T> rowColorInterface) {
        tableView.setRowFactory(sTableView -> {
            TableRow<S> row = new TableRow<>();
            row.itemProperty().addListener((observableValue, s, t1) -> {
                if (t1 != null)
                    row.pseudoClassStateChanged(rowColorInterface.pseudoClass(), rowColorInterface.checkRow(t1));
                tableView.refresh();
            });

            return row;
        });
    }

    /**
     * Customises the row appearance of a TreeTableView based on the conditions defined by the provided RowColorInterface.
     *
     * @param <S>               the type of the items contained within the TreeTableView.
     * @param <T>               the type of the column in the TreeTableView.
     * @param treeTableView     the TreeTableView where rows will be customised.
     * @param rowColorInterface the interface that defines the conditions and pseudo-class for row customization.
     */
    public <S, T> void customiseRowInTree(TreeTableView<S> treeTableView, RowColorInterface<S, T> rowColorInterface) {
        treeTableView.setRowFactory(sTableView -> {
            TreeTableRow<S> row = new TreeTableRow<>();
            row.itemProperty().addListener((observableValue, s, t1) -> {
                if (t1 != null)
                    row.pseudoClassStateChanged(rowColorInterface.pseudoClass(), rowColorInterface.checkRow(t1));
                treeTableView.refresh();
            });

            return row;
        });
    }
}
