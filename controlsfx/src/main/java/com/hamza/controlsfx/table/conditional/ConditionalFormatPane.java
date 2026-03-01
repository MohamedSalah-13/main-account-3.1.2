package com.hamza.controlsfx.table.conditional;

import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * A reusable pane that allows users to add/remove conditional formatting rules
 * for a TableView, similar to Excel.
 */
public class ConditionalFormatPane<S> extends VBox {

    private final TableView<S> tableView;
    private final ObservableList<ConditionalRule<S>> rules = FXCollections.observableArrayList(param -> new Observable[]{});

    // UI controls
    private final ComboBox<TableColumn<S, ?>> columnBox = new ComboBox<>();
    private final ComboBox<Operator> operatorBox = new ComboBox<>();
    private final TextField valueField = new TextField();
    private final ToggleGroup scopeGroup = new ToggleGroup();
    private final RadioButton scopeCell = new RadioButton("خلية");
    private final RadioButton scopeRow = new RadioButton("صف");
    private final ColorPicker colorPicker = new ColorPicker(Color.web("#FAD7A0"));
    private final Button addButton = new Button("إضافة شرط");

    private final ListView<ConditionalRule<S>> rulesView = new ListView<>(rules);
    private final Map<TableColumn<S, ?>, javafx.util.Callback> previousCellFactories = new HashMap<>();
    // Keep previous factories to not lose existing behavior
    private javafx.util.Callback<TableView<S>, TableRow<S>> previousRowFactory;

    public ConditionalFormatPane(TableView<S> tableView) {
        this.tableView = tableView;
        setPadding(new Insets(6));
        getChildren().addAll(buildInputPane(), buildRulesView());

        // init data
        List<String> columnNames = tableView.getColumns()
                .stream()
                .map(TableColumn::getText)
                .toList();
        columnBox.getItems().setAll(tableView.getColumns());
        operatorBox.getItems().setAll(Operator.values());
        scopeCell.setToggleGroup(scopeGroup);
        scopeRow.setToggleGroup(scopeGroup);
        scopeRow.setSelected(true);

        addButton.setOnAction(e -> onAddRule());

        // When rules change, re-apply factories
        rules.addListener((ListChangeListener<ConditionalRule<S>>) change -> applyRules());

        // initialize factories holders
        previousRowFactory = tableView.getRowFactory();
        for (TableColumn<S, ?> col : tableView.getColumns()) {
            previousCellFactories.put(col, col.getCellFactory());
        }
    }

    private Node buildInputPane() {
        columnBox.setPromptText("اختر العمود");
        operatorBox.setPromptText("الشرط");
        valueField.setPromptText("القيمة");
//        colorPicker.setStyle("-fx-color-label-visible: false;");

        HBox scopeCellRow = new HBox(scopeCell, scopeRow);
        scopeCellRow.setSpacing(8);
        scopeCellRow.setAlignment(Pos.CENTER_LEFT);
//        colorPicker.setPrefWidth(Double.MAX_VALUE);
        colorPicker.setMinHeight(30);

        VBox box1 = new VBox(8, new Label("العمود:"), columnBox,
                new Label("الشرط:"), operatorBox,
                new Label("القيمة:"), valueField,
                new Label("التطبيق:"), scopeCellRow,
                new Label("اللون:"), colorPicker,
                addButton);
//        VBox.setVgrow(columnBox, Priority.ALWAYS);
//        VBox.setVgrow(valueField, Priority.ALWAYS);
//        VBox.setVgrow(colorPicker, Priority.ALWAYS);
//        VBox.setVgrow(scopeCellRow, Priority.ALWAYS);
        box1.setPadding(new Insets(4));
        return box1;
    }

    private Node buildRulesView() {
        rulesView.setCellFactory(lv -> new ListCell<>() {
            private final Button removeBtn = new Button("حذف");
            private final HBox h = new HBox(8);

            {
                removeBtn.setOnAction(e -> {
                    ConditionalRule<S> r = getItem();
                    if (r != null) rules.remove(r);
                });
                h.getChildren().addAll(removeBtn);
            }

            @Override
            protected void updateItem(ConditionalRule<S> item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.toString());
                    setGraphic(h);
                }
            }
        });

        VBox wrapper = new VBox(4, new Label("الشروط المطبقة"), rulesView);
        wrapper.setPadding(new Insets(4, 0, 0, 0));
        return wrapper;
    }

    private void onAddRule() {
        TableColumn<S, ?> col = columnBox.getValue();
        Operator op = operatorBox.getValue();
        String val = valueField.getText();
        Toggle sel = scopeGroup.getSelectedToggle();
        if (col == null || op == null || val == null || val.isEmpty() || sel == null) {
            showAlert("الرجاء إدخال جميع الحقول لإضافة شرط");
            return;
        }
        ApplyScope scope = sel == scopeCell ? ApplyScope.CELL : ApplyScope.ROW;
        ConditionalRule<S> rule = new ConditionalRule<>(col, op, val.trim(), scope, colorPicker.getValue());
        rules.add(rule);
    }

    private void applyRules() {
        // restore previous first
        restoreFactories();

        if (rules.isEmpty()) {
            return;
        }

        // Row scope rules
        List<ConditionalRule<S>> rowRules = rules.filtered(r -> r.scope() == ApplyScope.ROW);
        if (!rowRules.isEmpty()) {
            javafx.util.Callback<TableView<S>, TableRow<S>> base = previousRowFactory;
            tableView.setRowFactory(tv -> {
                TableRow<S> row = base != null ? base.call(tv) : new TableRow<>();
                row.itemProperty().addListener((obs, old, val) -> styleRow(row, val, rowRules));
                // also style current
                styleRow(row, row.getItem(), rowRules);
                return row;
            });
        }

        // Cell scope rules: group by column
        Map<TableColumn<S, ?>, List<ConditionalRule<S>>> byCol = new HashMap<>();
        for (ConditionalRule<S> r : rules) {
            if (r.scope() == ApplyScope.CELL) {
                byCol.computeIfAbsent(r.column(), k -> new ArrayList<>()).add(r);
            }
        }
        byCol.forEach((col, list) -> {
            javafx.util.Callback base = previousCellFactories.get(col);
            TableColumn<S, Object> tc = (TableColumn<S, Object>) (TableColumn<S, ?>) col;
            tc.setCellFactory((javafx.util.Callback<TableColumn<S, Object>, TableCell<S, Object>>) column -> {
                TableCell<S, Object> cell = base != null ? (TableCell<S, Object>) ((javafx.util.Callback<TableColumn<S, Object>, TableCell<S, Object>>) base).call(column) : new TableCell<>();
                cell.itemProperty().addListener((o, ov, nv) -> styleCell(cell, list));
                cell.emptyProperty().addListener((o, ov, nv) -> styleCell(cell, list));
                styleCell(cell, list);
                return cell;
            });
        });
        tableView.refresh();
    }

    private void restoreFactories() {
        // restore row factory
        tableView.setRowFactory(previousRowFactory);
        // restore cell factories for any modified columns
        for (Map.Entry<TableColumn<S, ?>, javafx.util.Callback> e : previousCellFactories.entrySet()) {
            //noinspection unchecked
            e.getKey().setCellFactory(e.getValue());
        }
    }

    private void styleRow(TableRow<S> row, S item, List<ConditionalRule<S>> rowRules) {
        if (row == null) return;
        if (item == null) {
            row.setStyle("");
            return;
        }
        // first match wins
        for (ConditionalRule<S> r : rowRules) {
            if (matches(item, r)) {
                row.setStyle("-fx-background-color: " + toRgba(r.color(), 0.6) + ";");
                return;
            }
        }
        row.setStyle("");
    }

    private void styleCell(TableCell<S, ?> cell, List<ConditionalRule<S>> list) {
        if (cell == null || cell.getTableRow() == null) return;
        if (cell.isEmpty()) {
            cell.setStyle("");
            return;
        }
        S item = cell.getTableRow().getItem();
        if (item == null) {
            cell.setStyle("");
            return;
        }
        // first match for that column wins
        for (ConditionalRule<S> r : list) {
            if (r.column() == cell.getTableColumn() && matches(item, r)) {
                cell.setStyle("-fx-background-color: " + toRgba(r.color(), 0.8) + ";");
                return;
            }
        }
        cell.setStyle("");
    }

    private boolean matches(S item, ConditionalRule<S> rule) {
        Object cellVal = rule.column().getCellObservableValue(item) != null
                ? rule.column().getCellObservableValue(item).getValue()
                : null;
        Operator op = rule.operator();
        String val = rule.value();
        if (cellVal == null) return false;

        // Try number
        Double numCell = asDouble(cellVal);
        Double numVal = asDouble(val);
        if (numCell != null && numVal != null) {
            return switch (op) {
                case GREATER_THAN -> numCell > numVal;
                case GREATER_OR_EQUAL -> numCell >= numVal;
                case LESS_THAN -> numCell < numVal;
                case LESS_OR_EQUAL -> numCell <= numVal;
                case EQUALS -> Objects.equals(numCell, numVal);
                case NOT_EQUALS -> !Objects.equals(numCell, numVal);
                default -> false;
            };
        }
        // Try LocalDate
        LocalDate dCell = asDate(cellVal);
        LocalDate dVal = asDate(val);
        if (dCell != null && dVal != null) {
            return switch (op) {
                case GREATER_THAN -> dCell.isAfter(dVal);
                case GREATER_OR_EQUAL -> dCell.isAfter(dVal) || dCell.isEqual(dVal);
                case LESS_THAN -> dCell.isBefore(dVal);
                case LESS_OR_EQUAL -> dCell.isBefore(dVal) || dCell.isEqual(dVal);
                case EQUALS -> dCell.isEqual(dVal);
                case NOT_EQUALS -> !dCell.isEqual(dVal);
                default -> false;
            };
        }
        // Treat as string
        String sCell = String.valueOf(cellVal);
        return switch (op) {
            case CONTAINS -> sCell.contains(val);
            case STARTS_WITH -> sCell.startsWith(val);
            case ENDS_WITH -> sCell.endsWith(val);
            case EQUALS -> Objects.equals(sCell, val);
            case NOT_EQUALS -> !Objects.equals(sCell, val);
            default -> false;
        };
    }

    private Double asDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate asDate(Object o) {
        if (o instanceof LocalDate d) return d;
        try {
            return LocalDate.parse(String.valueOf(o));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String toRgba(Color c, double opacity) {
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        double a = Math.max(0, Math.min(1, opacity));
        return String.format("rgba(%d,%d,%d,%.2f)", r, g, b, a);
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    /**
     * Expose the list of rules, for saving/loading if needed.
     */
    public ObservableList<ConditionalRule<S>> getRules() {
        return rules;
    }

}
