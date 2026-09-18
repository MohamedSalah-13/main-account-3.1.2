package com.hamza.account.controller.convert_treasury;

import com.hamza.account.config.AppIcon;
import com.hamza.account.features.treasury.CashDirection;
import com.hamza.account.features.treasury.TreasuryHistoryFilter;
import com.hamza.account.features.treasury.TreasuryHistoryPage;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The bar above a treasury history list, and the pager below it - the period, the treasury, the
 * page, print and export - shared by the transfers screen and the deposits screen.
 * <p>
 * Both used to show "the last fifty" with a delete button that acted on the selected row, so a
 * movement older than the fifty could be neither found nor corrected. The controls are placed by
 * {@link ListToolbar}, so their order is the one every other list in the program has.
 * <p>
 * It holds no rule: what a filter may be is {@link TreasuryHistoryFilter}'s constructor, and what a
 * page is comes back from the service. A period the constructor refuses leaves the list as it was.
 */
final class TreasuryHistoryBar {

    /** "Every treasury" - a row of its own rather than an empty combo, which reads as "nothing chosen". */
    private static final TreasuryBalanceSummary EVERY_TREASURY = null;

    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final ComboBox<TreasuryBalanceSummary> treasury = new ComboBox<>();
    private final ComboBox<CashDirection> direction;
    private final Button previous = new Button();
    private final Button next = new Button();
    private final Label pageLabel = new Label();
    private final Label totalsLabel = new Label();
    private final Consumer<TreasuryHistoryFilter> onLoad;
    private final Runnable onPrint;
    private final Runnable onExport;
    private int page;

    /**
     * @param withDirection the deposits screen narrows by direction; a transfer has none
     */
    TreasuryHistoryBar(boolean withDirection, Consumer<TreasuryHistoryFilter> onLoad, Runnable onPrint,
                       Runnable onExport) {
        this.onLoad = onLoad;
        this.onPrint = onPrint;
        this.onExport = onExport;
        this.direction = withDirection ? new ComboBox<>() : null;

        TreasuryHistoryFilter opening = TreasuryHistoryFilter.thisMonth(LocalDate.now());
        from.setValue(opening.from());
        to.setValue(opening.to());
        from.setPrefWidth(140);
        to.setPrefWidth(140);

        treasury.setPrefWidth(200);
        treasury.setButtonCell(treasuryCell());
        treasury.setCellFactory(list -> treasuryCell());
        if (direction != null) {
            List<CashDirection> choices = new ArrayList<>();
            choices.add(null);
            choices.addAll(List.of(CashDirection.values()));
            direction.setItems(FXCollections.observableArrayList(choices));
            direction.setPrefWidth(140);
            direction.setConverter(new StringConverter<>() {
                @Override
                public String toString(CashDirection value) {
                    return value == null ? text("treasury.history.direction.all") : text(value.labelKey());
                }

                @Override
                public CashDirection fromString(String value) {
                    return direction.getValue();
                }
            });
            direction.getSelectionModel().selectFirst();
        }
    }

    /** Places the controls: the filters and the list's actions in {@code bar}, the pager in {@code footer}. */
    void installIn(HBox bar, HBox footer) {
        List<javafx.scene.Node> fields = new ArrayList<>(List.of(
                caption("from"), from, caption("to"), to, treasury));
        if (direction != null) {
            fields.add(direction);
        }
        new ListToolbar()
                .searchField(fields.toArray(javafx.scene.Node[]::new))
                .search(ListToolbar.button("search", AppIcon.SEARCH, this::reload))
                .refresh(ListToolbar.refreshButton(this::load))
                .print(ListToolbar.printButton(onPrint))
                .export(ListToolbar.button("treasury.history.export.excel", AppIcon.SPREADSHEET, onExport))
                .installIn(bar);

        previous.setText(text("treasury.statement.previous"));
        next.setText(text("treasury.statement.next"));
        for (Button button : List.of(previous, next)) {
            button.getStyleClass().add("app-neutral-button");
            button.setMinWidth(Region.USE_PREF_SIZE);
        }
        previous.setOnAction(event -> { page = Math.max(0, page - 1); load(); });
        next.setOnAction(event -> { page++; load(); });
        pageLabel.getStyleClass().add("form-label");
        totalsLabel.getStyleClass().add("form-label");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        footer.getChildren().setAll(totalsLabel, gap, previous, pageLabel, next);
    }

    /** The pickers, keeping whichever treasury was chosen when the list is refilled after a save. */
    void setTreasuries(List<TreasuryBalanceSummary> treasuries) {
        Integer chosen = treasury.getValue() == null ? null : treasury.getValue().id();
        List<TreasuryBalanceSummary> choices = new ArrayList<>();
        choices.add(EVERY_TREASURY);
        choices.addAll(treasuries);
        treasury.setItems(FXCollections.observableArrayList(choices));
        treasury.getSelectionModel().select(choices.stream()
                .filter(row -> row != null && chosen != null && row.id() == chosen).findFirst().orElse(EVERY_TREASURY));
    }

    /** Back to the first page - a new filter describes another list, and its page three may not exist. */
    void reload() {
        page = 0;
        load();
    }

    /** The same page again, after a save or a delete changed what is on it. */
    void load() {
        TreasuryHistoryFilter filter = filter();
        if (filter != null) {
            onLoad.accept(filter);
        }
    }

    /** What the controls say now, or {@code null} when they say nothing a query can run. */
    TreasuryHistoryFilter filter() {
        try {
            return new TreasuryHistoryFilter(from.getValue(), to.getValue(),
                    treasury.getValue() == null ? null : treasury.getValue().id(),
                    direction == null ? null : direction.getValue(), page, TreasuryHistoryFilter.PAGE_SIZE);
        } catch (RuntimeException e) {
            com.hamza.controlsfx.alert.AllAlerts.alertError(text("treasury.statement.error.period.reversed"));
            return null;
        }
    }

    /** The period as the printed subtitle writes it. */
    String periodText() {
        return text("from") + ": " + from.getValue() + "  |  " + text("to") + ": " + to.getValue();
    }

    void showPage(TreasuryHistoryPage<?> shown, String totals) {
        page = shown.page();
        previous.setDisable(!shown.hasPrevious());
        next.setDisable(!shown.hasNext());
        pageLabel.setText(text("treasury.history.page", page + 1));
        totalsLabel.setText(totals);
    }

    private static Label caption(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    private static ListCell<TreasuryBalanceSummary> treasuryCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(TreasuryBalanceSummary row, boolean empty) {
                super.updateItem(row, empty);
                setText(empty ? null : row == null ? text("treasury.history.treasury.all") : row.name());
                setGraphic(empty || row == null ? null : row.type().icon().graphic());
            }
        };
    }

    private static String text(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }
}
