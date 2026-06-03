package com.hamza.controlsfx.table.columnEdit;

import javafx.scene.control.TableColumn;

@FunctionalInterface
public interface TableColumnEdite<T, S> {

    void updateColumn(TableColumn.CellEditEvent<T, S> t) throws Exception;
}
