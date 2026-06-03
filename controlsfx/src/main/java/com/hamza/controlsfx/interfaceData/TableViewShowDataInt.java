package com.hamza.controlsfx.interfaceData;

import javafx.scene.control.TableView;

import java.util.ArrayList;
import java.util.List;

public interface TableViewShowDataInt<T> {

    default void getTable(TableView<T> tableView) {
    }

    default List<T> dataList() throws Exception {
        return new ArrayList<>();
    }


    Class<? super T> classForColumn();

}
