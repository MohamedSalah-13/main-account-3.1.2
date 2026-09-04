package com.hamza.account.controller.items;

import com.hamza.account.features.itemreports.ItemReport;
import com.hamza.account.features.itemreports.ItemReportCatalog;
import com.hamza.account.features.itemreports.ItemReportColumn;
import com.hamza.account.features.itemreports.ItemReportRequest;
import com.hamza.account.features.itemreports.ItemReportResult;
import com.hamza.account.features.itemreports.ItemReportRow;
import com.hamza.account.features.itemreports.JdbcCatalogFactRepository;
import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.model.domain.MainGroups;
import com.hamza.account.model.domain.SubGroups;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.MainGroupService;
import com.hamza.account.service.SupGroupService;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One screen for every item report there is.
 * <p>
 * It names no report. The list is {@link ItemReportCatalog}, the table is built from the
 * columns whichever report was chosen declares, and the strip underneath is whatever totals
 * it handed back - so a report added to the catalogue appears here complete, with its table,
 * its totals, its export and the filter row already working, and nothing in this file
 * changes. That is the point of the exercise: the reports are where the thinking is, and
 * this is the one place that draws them.
 * <p>
 * Everything is read on a background thread. A valuation over a large catalogue is a real
 * query, and a report screen that locks up while it runs is a report screen people stop
 * opening.
 */
@FxmlPath(pathFile = "items/item-reports-view.fxml")
public class ItemReportsController {

    private static final ExecutorService RUNNER =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "item-report-runner");
                thread.setDaemon(true);
                return thread;
            });

    @FXML
    private StackPane stackPane;
    @FXML
    private ListView<ItemReport> reportList;
    @FXML
    private Label labelDescription, labelFrom;
    @FXML
    private ComboBox<ItemsFilterBar.GroupChoice> comboGroup;
    @FXML
    private ComboBox<ItemCatalogFilter.Tristate> comboActive;
    @FXML
    private DatePicker dateFrom;
    @FXML
    private Button btnRun, btnExport;
    @FXML
    private TableView<ItemReportRow> resultTable;
    @FXML
    private FlowPane totalsBar;
    @FXML
    private ProgressIndicator progress;

    private final ItemReportCatalog catalog = new ItemReportCatalog(new JdbcCatalogFactRepository());
    private final MainGroupService mainGroupService = ServiceRegistry.get(MainGroupService.class);
    private final SupGroupService supGroupService = ServiceRegistry.get(SupGroupService.class);

    /**
     * The filter the screen was opened with, so a report run from the items list answers
     * about the same rows the operator was already looking at rather than about the whole
     * catalogue.
     */
    private ItemCatalogFilter openingFilter = ItemCatalogFilter.EMPTY;

    public void setOpeningFilter(ItemCatalogFilter filter) {
        this.openingFilter = filter == null ? ItemCatalogFilter.EMPTY : filter;
    }

    @FXML
    public void initialize() {
        setUpReportList();
        setUpFilters();
        resultTable.setPlaceholder(new Label(LanguageManager.getInstance().getString("itemreport.empty")));
        // Unconstrained, because the widths are bound above and already add up to the table.
        // A constrained policy would fight the bindings for control of the same numbers.
        resultTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        btnRun.setOnAction(event -> run());
        btnExport.setOnAction(event -> ItemsExcelExport.export(resultTable, "Report", "item-report.xlsx"));
        reportList.getSelectionModel().selectFirst();
        loadGroups();
    }

    private void setUpReportList() {
        reportList.setItems(FXCollections.observableArrayList(catalog.all()));
        reportList.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(ItemReport report, boolean empty) {
                super.updateItem(report, empty);
                if (empty || report == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                LanguageManager language = LanguageManager.getInstance();
                Label title = new Label(language.getString(report.titleKey()));
                title.getStyleClass().add("item-report-name");
                Label description = new Label(language.getString(report.descriptionKey()));
                description.getStyleClass().add("item-report-hint");
                description.setWrapText(true);
                description.setMaxWidth(210);
                VBox box = new VBox(2, title, description);
                setText(null);
                setGraphic(box);
            }
        });
        reportList.getSelectionModel().selectedItemProperty().addListener((observable, old, report) -> {
            if (report == null) return;
            labelDescription.setText(LanguageManager.getInstance().getString(report.descriptionKey()));
            // A date box on a report that ignores dates is worse than no date box: it is set,
            // and the answer is then believed to be about that period.
            boolean dated = report.usesDateRange();
            dateFrom.setVisible(dated);
            dateFrom.setManaged(dated);
            labelFrom.setVisible(dated);
            labelFrom.setManaged(dated);
            run();
        });
    }

    private void setUpFilters() {
        comboActive.setItems(FXCollections.observableArrayList(ItemCatalogFilter.Tristate.values()));
        comboActive.setConverter(new StringConverter<>() {
            @Override
            public String toString(ItemCatalogFilter.Tristate value) {
                if (value == null) return "";
                return LanguageManager.getInstance().getString(switch (value) {
                    case ANY -> "item.filter.active.any";
                    case YES -> "item.filter.active.yes";
                    case NO -> "item.filter.active.no";
                });
            }

            @Override
            public ItemCatalogFilter.Tristate fromString(String text) {
                return null;
            }
        });
        comboActive.setValue(openingFilter.active());

        comboGroup.setConverter(new StringConverter<>() {
            @Override
            public String toString(ItemsFilterBar.GroupChoice choice) {
                if (choice == null) return "";
                return choice.sub() ? "    " + choice.label() : choice.label();
            }

            @Override
            public ItemsFilterBar.GroupChoice fromString(String text) {
                return null;
            }
        });
    }

    /** Groups are read off the JavaFX thread, exactly as the items screen reads them. */
    private void loadGroups() {
        Task<List<ItemsFilterBar.GroupChoice>> task = new Task<>() {
            @Override
            protected List<ItemsFilterBar.GroupChoice> call() throws Exception {
                List<MainGroups> mainGroups = mainGroupService.getMainGroupList();
                List<SubGroups> subGroups = supGroupService.getSubGroupsList();
                List<ItemsFilterBar.GroupChoice> choices = new ArrayList<>();
                choices.add(new ItemsFilterBar.GroupChoice(null, null,
                        LanguageManager.getInstance().getString("item.filter.group.all"), false));
                for (MainGroups main : mainGroups) {
                    choices.add(new ItemsFilterBar.GroupChoice(main.getId(), null, main.getName(), false));
                    for (SubGroups sub : subGroups) {
                        if (sub.getMainGroups() != null && sub.getMainGroups().getId() == main.getId()) {
                            choices.add(new ItemsFilterBar.GroupChoice(main.getId(), sub.getId(),
                                    sub.getName(), true));
                        }
                    }
                }
                return choices;
            }
        };
        task.setOnSucceeded(event -> {
            comboGroup.setItems(FXCollections.observableArrayList(task.getValue()));
            selectOpeningGroup();
        });
        RUNNER.execute(task);
    }

    private void selectOpeningGroup() {
        for (ItemsFilterBar.GroupChoice choice : comboGroup.getItems()) {
            if (java.util.Objects.equals(choice.mainGroupId(), openingFilter.mainGroupId())
                    && java.util.Objects.equals(choice.subGroupId(), openingFilter.subGroupId())) {
                comboGroup.setValue(choice);
                return;
            }
        }
        comboGroup.getSelectionModel().selectFirst();
    }

    /** Runs the chosen report against the filter row, off the JavaFX thread. */
    private void run() {
        ItemReport report = reportList.getSelectionModel().getSelectedItem();
        if (report == null) return;

        // The combo is filled by a background read, and the first report runs before that
        // read lands. Falling back to the filter the screen was opened with is what stops
        // that first answer from silently widening to the whole catalogue.
        ItemsFilterBar.GroupChoice group = comboGroup.getValue();
        ItemCatalogFilter filter = openingFilter
                .withSearch("")
                .withGroup(group == null ? openingFilter.mainGroupId() : group.mainGroupId(),
                        group == null ? openingFilter.subGroupId() : group.subGroupId())
                .withActive(comboActive.getValue() == null
                        ? ItemCatalogFilter.Tristate.ANY : comboActive.getValue());
        ItemReportRequest request = new ItemReportRequest(filter,
                report.usesDateRange() ? dateFrom.getValue() : null, null);

        setBusy(true);
        Task<ItemReportResult> task = new Task<>() {
            @Override
            protected ItemReportResult call() throws Exception {
                return report.run(request);
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            show(task.getValue());
        });
        task.setOnFailed(event -> {
            setBusy(false);
            AllAlerts.handleError(LanguageManager.getInstance().getString("itemreport.error.title"),
                    new Exception(task.getException()));
        });
        RUNNER.execute(task);
    }

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        progress.setManaged(busy);
        btnRun.setDisable(busy);
    }

    /**
     * Draws whatever came back.
     * <p>
     * The columns are rebuilt on every run rather than reused, because two reports do not
     * share a shape - keeping a table's columns across reports is how a value ends up under
     * the previous report's heading.
     */
    private void show(ItemReportResult result) {
        resultTable.getColumns().clear();
        List<ItemReportColumn> columns = result.columns();
        // Each column gets a width in pixels, straight from the weight it declared.
        //
        // Explicit pixels rather than a share of the table, after two attempts at the latter
        // failed on screen: reading the table's width while building the columns returns zero,
        // because they are built before it has been laid out, and binding to that width did
        // not hold either - the resize policy and the binding were both writing the same
        // numbers. A fixed width per weight unit depends on neither, is the same on every
        // window size, and leaves any surplus as blank space rather than donating it to
        // whichever column the policy happened to pick - which is how a code column four
        // characters wide came to be the widest thing on the report.
        for (int index = 0; index < columns.size(); index++) {
            ItemReportColumn spec = columns.get(index);
            TableColumn<ItemReportRow, Object> column = column(spec, index);
            double width = spec.weight() * WIDTH_PER_WEIGHT;
            column.setMinWidth(MINIMUM_COLUMN_WIDTH);
            column.setPrefWidth(width);
            // Nothing may stretch past its declared share either, or one long name would
            // push every figure off the edge of the table.
            column.setMaxWidth(Math.max(width * 2, MINIMUM_COLUMN_WIDTH * 2));
            resultTable.getColumns().add(column);
        }
        resultTable.setItems(FXCollections.observableArrayList(result.rows()));
        // A group row is a HEADING only in a report that nests - one that has rows beneath it.
        // The valuation is entirely group rows, one per group, and they are its data: banding
        // every one of them would stripe the whole table and say nothing.
        boolean nested = result.rows().stream().anyMatch(row -> row.depth() > 0);
        resultTable.setRowFactory(view -> new javafx.scene.control.TableRow<>() {
            @Override
            protected void updateItem(ItemReportRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().removeAll("item-report-group-row", "item-report-sub-group-row",
                        "item-report-total-row");
                if (empty || row == null) return;
                switch (row.kind()) {
                    case GROUP -> {
                        if (!nested) break;
                        getStyleClass().add(row.depth() == 0
                                ? "item-report-group-row" : "item-report-sub-group-row");
                    }
                    case TOTAL -> getStyleClass().add("item-report-total-row");
                    case ITEM -> {
                    }
                }
            }
        });

        totalsBar.getChildren().clear();
        LanguageManager language = LanguageManager.getInstance();
        for (ItemReportResult.Total total : result.totals()) {
            Label label = new Label(language.getString(total.labelKey()) + ": " + total.value());
            label.getStyleClass().add("item-report-total");
            totalsBar.getChildren().add(label);
        }
    }

    /**
     * One column, drawn according to its declared kind.
     * <p>
     * The first column carries the row's depth as an indent, which is what makes a
     * three-level report readable in a flat table - and is why the reports hand back a depth
     * rather than a tree.
     */
    private TableColumn<ItemReportRow, Object> column(ItemReportColumn spec, int index) {
        TableColumn<ItemReportRow, Object> column =
                new TableColumn<>(LanguageManager.getInstance().getString(spec.titleKey()));
        column.setCellValueFactory(features -> new SimpleObjectProperty<>(features.getValue().value(index)));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(Object value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(null);
                if (empty || value == null) {
                    setText(null);
                    return;
                }
                setText(format(value, spec.kind()));
                setAlignment(spec.kind() == ItemReportColumn.Kind.TEXT
                        || spec.kind() == ItemReportColumn.Kind.DATE ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
                if (index == 0) {
                    ItemReportRow row = getTableRow() == null ? null : getTableRow().getItem();
                    // Only ever ADD an indent. Writing a padding of zero for a top-level row
                    // would override the cell padding the stylesheet gives every other column,
                    // so the first column alone would sit flush against the table edge.
                    setStyle(row == null || row.depth() == 0
                            ? "" : "-fx-padding: 0 0 0 " + (row.depth() * 18) + ";");
                }
            }
        });
        return column;
    }

    /** No column may shrink past this, whatever its weight - a heading has to stay readable. */
    private static final double MINIMUM_COLUMN_WIDTH = 60;
    /**
     * Pixels per unit of {@link ItemReportColumn#weight}. A name is weight 6 and so is 330
     * wide; a count is weight 1 and gets 55, which is what "1,083" needs and no more.
     */
    private static final double WIDTH_PER_WEIGHT = 55;

    private static String format(Object value, ItemReportColumn.Kind kind) {
        if (!(value instanceof Number number)) return String.valueOf(value);
        return switch (kind) {
            case COUNT -> String.format("%,d", number.longValue());
            case NUMBER -> String.format("%,.2f", number.doubleValue());
            default -> String.valueOf(value);
        };
    }
}
