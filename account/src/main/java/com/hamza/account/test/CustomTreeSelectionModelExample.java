package com.hamza.account.test;

import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class CustomTreeSelectionModelExample extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        TreeItem<Object> root = new TreeItem<>("Root");
        for (int i = 1; i <= 5; i++) {
            TreeItem<Object> item = new TreeItem<>(new Tour("Tour " + i));
            for (int j = 1; j <= 5; j++) {
                Delivery delivery = new Delivery("Delivery " + j);
                item.getChildren().add(new TreeItem<>(delivery));
            }
            root.getChildren().add(item);
        }
        TreeView<Object> tree = new TreeView<>();
        tree.setSelectionModel(new TourSelectionModel(tree.getSelectionModel(), tree));
        tree.setRoot(root);

        primaryStage.setScene(new Scene(new BorderPane(tree), 400, 400));
        primaryStage.show();
    }

    public static class TourSelectionModel extends MultipleSelectionModel<TreeItem<Object>> {

        private final MultipleSelectionModel<TreeItem<Object>> selectionModel;
        private final TreeView<Object> tree;

        public TourSelectionModel(MultipleSelectionModel<TreeItem<Object>> selectionModel, TreeView<Object> tree) {
            this.selectionModel = selectionModel;
            this.tree = tree;
            selectionModeProperty().bindBidirectional(selectionModel.selectionModeProperty());
        }

        @Override
        public ObservableList<Integer> getSelectedIndices() {
            return selectionModel.getSelectedIndices();
        }

        @Override
        public ObservableList<TreeItem<Object>> getSelectedItems() {
            return selectionModel.getSelectedItems();
        }

        @Override
        public void selectIndices(int index, int... indices) {

            List<Integer> indicesToSelect = Stream.concat(Stream.of(index), IntStream.of(indices).boxed())
                    .filter(i -> tree.getTreeItem(i).getValue() instanceof Tour)
                    .collect(Collectors.toList());


            if (indicesToSelect.isEmpty()) {
                return;
            }
            selectionModel.selectIndices(indicesToSelect.get(0),
                    indicesToSelect.stream().skip(1).mapToInt(Integer::intValue).toArray());

        }

        @Override
        public void selectAll() {
            List<Integer> indicesToSelect = IntStream.range(0, tree.getExpandedItemCount())
                    .filter(i -> tree.getTreeItem(i).getValue() instanceof Tour)
                    .boxed()
                    .collect(Collectors.toList());
            if (indicesToSelect.isEmpty()) {
                return;
            }
            selectionModel.selectIndices(0,
                    indicesToSelect.stream().skip(1).mapToInt(Integer::intValue).toArray());
        }

        @Override
        public void selectFirst() {
            IntStream.range(0, tree.getExpandedItemCount())
                    .filter(i -> tree.getTreeItem(i).getValue() instanceof Tour)
                    .findFirst()
                    .ifPresent(selectionModel::select);
        }

        @Override
        public void selectLast() {
            IntStream.iterate(tree.getExpandedItemCount() - 1, i -> i - 1)
                    .limit(tree.getExpandedItemCount())
                    .filter(i -> tree.getTreeItem(i).getValue() instanceof Tour)
                    .findFirst()
                    .ifPresent(selectionModel::select);
        }

        @Override
        public void clearAndSelect(int index) {
            int toSelect = index;
            int direction = selectionModel.getSelectedIndex() < index ? 1 : -1;
            while (toSelect >= 0 && toSelect < tree.getExpandedItemCount() && !(tree.getTreeItem(toSelect).getValue() instanceof Tour)) {
                toSelect = toSelect + direction;
            }
            if (toSelect >= 0 && toSelect < tree.getExpandedItemCount()) {
                selectionModel.clearAndSelect(toSelect);
            }
        }

        @Override
        public void select(int index) {
            int toSelect = index;
            int direction = selectionModel.getSelectedIndex() < index ? 1 : -1;
            while (toSelect >= 0 && toSelect < tree.getExpandedItemCount() && !(tree.getTreeItem(toSelect).getValue() instanceof Tour)) {
                toSelect = toSelect + direction;
            }
            if (toSelect >= 0 && toSelect < tree.getExpandedItemCount()) {
                selectionModel.select(toSelect);
            }
        }

        @Override
        public void select(TreeItem<Object> obj) {
            if (obj.getValue() instanceof Tour) {
                selectionModel.select(obj);
            }
        }

        @Override
        public void clearSelection(int index) {
            selectionModel.clearSelection(index);
        }

        @Override
        public void clearSelection() {
            selectionModel.clearSelection();
        }

        @Override
        public boolean isSelected(int index) {
            return selectionModel.isSelected(index);
        }

        @Override
        public boolean isEmpty() {
            return selectionModel.isEmpty();
        }

        @Override
        public void selectPrevious() {
            int current = selectionModel.getSelectedIndex();
            if (current > 0) {
                IntStream.iterate(current - 1, i -> i - 1).limit(current)
                        .filter(i -> tree.getTreeItem(i).getValue() instanceof Tour)
                        .findFirst()
                        .ifPresent(selectionModel::select);
            }
        }

        @Override
        public void selectNext() {
            int current = selectionModel.getSelectedIndex();
            if (current < tree.getExpandedItemCount() - 1) {
                IntStream.range(current + 1, tree.getExpandedItemCount())
                        .filter(i -> tree.getTreeItem(i).getValue() instanceof Tour)
                        .findFirst()
                        .ifPresent(selectionModel::select);
            }
        }

    }

    public static class Tour {

        private final String name;

        public Tour(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return getName();
        }

    }

    public static class Delivery {
        private final String name;

        public Delivery(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return getName();
        }
    }
}