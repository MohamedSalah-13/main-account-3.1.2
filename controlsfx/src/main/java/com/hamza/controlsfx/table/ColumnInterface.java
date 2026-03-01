package com.hamza.controlsfx.table;

import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;

import java.util.HashMap;

public interface ColumnInterface<T, T1> {

    HashMap<String, Callback<TableColumn.CellDataFeatures<T, T1>, ObservableValue<T1>>> STRING_CALLBACK_HASH_MAP();
}
