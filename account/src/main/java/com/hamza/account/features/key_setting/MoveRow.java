package com.hamza.account.features.key_setting;

import javafx.collections.ObservableList;
import javafx.scene.control.TableView;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
public class MoveRow<T1> {

    private final TableView<T1> table;
    private final ObservableList<T1> myObservableList;

    public void moveSelectedRowsUp() {
        ObservableList<Integer> selectedIndices = table.getSelectionModel().getSelectedIndices();

        // Convert to List to avoid concurrent modification
        List<Integer> indices = new ArrayList<>(selectedIndices);

        // Sort indices in ascending order to maintain proper order when moving up
        Collections.sort(indices);

        // Can't move up if the first selected item is at index 0
        if (indices.isEmpty() || indices.getFirst() <= 0) {
            return;
        }

        // Remember selected items to restore selection later
        List<T1> selectedItems = new ArrayList<>(table.getSelectionModel().getSelectedItems());

        // Move each selected item up one position
        for (int index : indices) {
            T1 item = myObservableList.remove(index);
            myObservableList.add(index - 1, item);
        }

        // Restore selection (shifted up by 1)
        table.getSelectionModel().clearSelection();
        for (T1 item : selectedItems) {
            int newIndex = myObservableList.indexOf(item);
            table.getSelectionModel().select(newIndex);
        }
    }

    public void moveSelectedRowsDown() {
        ObservableList<Integer> selectedIndices = table.getSelectionModel().getSelectedIndices();

        // Convert to List to avoid concurrent modification
        List<Integer> indices = new ArrayList<>(selectedIndices);

        // Sort indices in descending order to maintain proper order when moving down
        indices.sort(Collections.reverseOrder());

        // Can't move down if the last selected item is at the last index
        if (indices.isEmpty() || indices.getLast() >= myObservableList.size() - 1) {
            return;
        }

        // Remember selected items to restore selection later
        List<T1> selectedItems = new ArrayList<>(table.getSelectionModel().getSelectedItems());

        // Move each selected item down one position
        for (int index : indices) {
            T1 item = myObservableList.remove(index);
            myObservableList.add(index + 1, item);
        }

        // Restore selection (shifted down by 1)
        table.getSelectionModel().clearSelection();
        for (T1 item : selectedItems) {
            int newIndex = myObservableList.indexOf(item);
            table.getSelectionModel().select(newIndex);
        }
    }
}
