package com.hamza.account.controller.convert_treasury;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.features.currency.CurrencyFormat;
import com.hamza.account.features.currency.RateInForce;
import com.hamza.account.features.currency.online.OnlineRateLine;
import com.hamza.account.features.currency.online.OnlineRatePreview;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The rates found on the internet, each beside the rate the shop already has, to tick and record
 * (docs/currency-plan.md ق-٩, §12).
 * <p>
 * It decides nothing. Which lines may be ticked and which are ticked when it opens are
 * {@link OnlineRatePreview}'s answers, tested there; this draws them and hands back the ticks, and
 * {@code OnlineRatePreview.drafts} ignores a tick on a line that may not be recorded whatever a screen
 * sends. A line that cannot be ticked is shown anyway, muted, with the reason in its last column: a
 * currency missing from the list would read as a currency the fetch forgot.
 */
final class OnlineRatesDialog {

    private static final PseudoClass UNAVAILABLE = PseudoClass.getPseudoClass("online-unavailable");
    private static final PseudoClass LARGE_MOVE = PseudoClass.getPseudoClass("online-large-move");
    /** Keeps a date reading 2026-09-23 after an Arabic word; see StockTransferHistoryView. */
    private static final String LEFT_TO_RIGHT_MARK = "\u200E";

    private OnlineRatesDialog() {
    }

    /** One currency's line and its tick. */
    private static final class Row {
        final OnlineRateLine line;
        final BooleanProperty chosen = new SimpleBooleanProperty();

        Row(OnlineRateLine line, boolean chosen) {
            this.line = line;
            this.chosen.set(chosen && line.recordable());
        }
    }

    /**
     * Shows {@code preview}. Empty when it was closed; otherwise the currencies ticked when record was
     * pressed, which is never an empty set - the button is disabled until something is ticked.
     */
    static Optional<Set<Integer>> ask(Window owner, OnlineRatePreview preview) {
        LanguageManager lang = LanguageManager.getInstance();
        Dialog<Set<Integer>> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(text("currency.online.title"));
        dialog.setHeaderText(null);
        DialogPane pane = dialog.getDialogPane();
        pane.setNodeOrientation(lang.getNodeOrientation());
        pane.getStylesheets().add(ThemeManager.getStylesheet());
        // A dialog takes its size from its root: without one the last column opens behind a scroll bar.
        // This fits 1366x768 with the window's own frame.
        pane.setPrefSize(900, 520);

        Set<Integer> ticked = preview.preselected();
        ObservableList<Row> rows = FXCollections.observableArrayList();
        preview.lines().forEach(line -> rows.add(new Row(line, ticked.contains(line.currency().id()))));

        TableView<Row> table = table(rows);
        Label source = new Label(text("currency.online.source", preview.source(), preview.site(),
                LEFT_TO_RIGHT_MARK + preview.published(), LEFT_TO_RIGHT_MARK + preview.day()));
        source.getStyleClass().add("form-label");
        source.setWrapText(true);
        Label rules = new Label(text("currency.online.rules"));
        rules.setWrapText(true);
        VBox content = new VBox(8, source, rules, table);
        if (preview.stale()) {
            Label stale = new Label(text("currency.online.stale", preview.publishedDaysAgo()));
            stale.getStyleClass().add("form-hint");
            stale.setWrapText(true);
            content.getChildren().add(1, stale);
        }
        VBox.setVgrow(table, Priority.ALWAYS);
        pane.setContent(content);

        ButtonType record = new ButtonType(text("currency.online.record.count", ticked.size()), ButtonBar.ButtonData.OK_DONE);
        ButtonType close = new ButtonType(text("close"), ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(record, close);
        Button recordButton = (Button) pane.lookupButton(record);
        recordButton.setGraphic(AppIcon.SAVE.graphic(16));
        recordButton.getStyleClass().add("app-primary-button");
        ((Button) pane.lookupButton(close)).setGraphic(AppIcon.CLOSE.graphic(16));
        IntegerBinding count = Bindings.createIntegerBinding(
                () -> (int) rows.stream().filter(row -> row.chosen.get()).count(),
                rows.stream().map(row -> row.chosen).toArray(BooleanProperty[]::new));
        recordButton.textProperty().bind(Bindings.createStringBinding(
                () -> text("currency.online.record.count", count.get()), count));
        recordButton.disableProperty().bind(count.isEqualTo(0));

        dialog.setResultConverter(button -> {
            if (button != record) {
                return null;
            }
            Set<Integer> chosen = new LinkedHashSet<>();
            rows.stream().filter(row -> row.chosen.get()).forEach(row -> chosen.add(row.line.currency().id()));
            return chosen.isEmpty() ? null : chosen;
        });
        return dialog.showAndWait();
    }

    private static TableView<Row> table(ObservableList<Row> rows) {
        TableView<Row> table = new TableView<>(rows);
        table.setId("onlineRates");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<Row, Boolean> tick = new TableColumn<>();
        tick.setId("onlineRateTick");
        tick.setSortable(false);
        tick.setMinWidth(44);
        tick.setMaxWidth(44);
        tick.setCellValueFactory(data -> data.getValue().chosen);
        tick.setCellFactory(column -> new TickCell());
        table.getColumns().setAll(List.of(tick,
                column("onlineRateCurrency", "currency.online.column.currency", row -> row.line.currency().label()),
                figure("onlineRateRecorded", "currency.online.column.recorded", row -> recorded(row.line.inForce())),
                column("onlineRateRecordedOn", "currency.online.column.recorded.on", row -> row.line.inForce() == null
                        ? "" : LEFT_TO_RIGHT_MARK + row.line.inForce().effectiveDate()),
                figure("onlineRateFetched", "currency.online.column.fetched", row -> CurrencyFormat.rate(row.line.fetched())),
                figure("onlineRateChange", "currency.online.column.change",
                        row -> CurrencyFormat.change(row.line.changePercent())),
                column("onlineRateStatus", "currency.online.column.status", row -> status(row.line))));
        table.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(Row row, boolean empty) {
                super.updateItem(row, empty);
                boolean shown = !empty && row != null;
                pseudoClassStateChanged(UNAVAILABLE, shown && !row.line.recordable());
                pseudoClassStateChanged(LARGE_MOVE, shown && row.line.status() == OnlineRateLine.Status.LARGE_MOVE);
            }
        });
        return table;
    }

    /** The tick box, offered only on a line that may be recorded. */
    private static final class TickCell extends TableCell<Row, Boolean> {
        private final CheckBox box = new CheckBox();
        private Row bound;

        TickCell() {
            box.selectedProperty().addListener((observable, was, now) -> {
                if (bound != null) {
                    bound.chosen.set(now);
                }
            });
        }

        @Override
        protected void updateItem(Boolean chosen, boolean empty) {
            super.updateItem(chosen, empty);
            // The row's own answer, not getItems().get(getIndex()) - see RowActionsColumn.
            Row row = empty || getTableRow() == null ? null : getTableRow().getItem();
            bound = null;
            if (row == null) {
                setGraphic(null);
                return;
            }
            box.setSelected(row.chosen.get());
            box.setDisable(!row.line.recordable());
            bound = row;
            setGraphic(box);
        }
    }

    private static String recorded(RateInForce inForce) {
        return inForce == null ? text("currency.rate.none") : CurrencyFormat.rate(inForce.rate());
    }

    private static String status(OnlineRateLine line) {
        return switch (line.status()) {
            case READY -> line.inForce() == null ? text("currency.online.status.first") : text("currency.online.status.ready");
            case LARGE_MOVE -> text("currency.online.status.large");
            case RECORDED_TODAY -> text("currency.online.status.today");
            case NOT_OFFERED -> text("currency.online.status.none");
        };
    }

    private static TableColumn<Row, String> column(String id, String titleKey, Function<Row, String> extractor) {
        TableColumn<Row, String> column = Columns.text(titleKey, extractor);
        column.setId(id);
        return column;
    }

    /** A column of figures, aligned as every figure column is. */
    private static TableColumn<Row, String> figure(String id, String titleKey, Function<Row, String> extractor) {
        TableColumn<Row, String> column = column(id, titleKey, extractor);
        column.setStyle(Columns.AMOUNT_ALIGNMENT);
        return column;
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }

    private static String text(String key, Object... args) {
        return LanguageManager.getInstance().getString(key, args);
    }
}
