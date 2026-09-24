package com.hamza.account.controller.others;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.SaveDatabaseFile;
import com.hamza.account.controller.main.LoadDataAndList;
import com.hamza.account.features.backup.BackupKind;
import com.hamza.account.features.wipe.WipeSection;
import com.hamza.account.features.wipe.WipeSections;
import com.hamza.account.features.wipe.WipeSelection;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.wipe.WipeCatalog;
import com.hamza.account.wipe.WipePlan;
import com.hamza.account.wipe.WipeService;
import com.hamza.account.wipe.WipeTarget;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The "delete data" screen: which parts of the database to empty, what else goes with them, and
 * the wipe itself.
 * <p>
 * It is built here rather than in an FXML file. The FXML was a frame of title, warning and
 * buttons whose sentences were written into it in Arabic, so the English screen said them in
 * Arabic, and the cards were built in code anyway. The rules of ticking - a target takes what
 * depends on it, unticking one unticks what needed it - are {@link WipeSelection}'s, tested
 * without a toolkit; the boxes here only mirror it. Which card a target sits on is
 * {@link WipeSections}'. Which tables a target empties and in what order is
 * {@link WipeCatalog}'s, and running them is {@link WipeService}'s, which asks the permission.
 * <p>
 * Laid out for 1366x768: four cards side by side, so no card scrolls and none leaves a column
 * empty, and a summary of what will be erased whose height does not move the buttons.
 */
public class DeleteDataController implements AppSettingInterface {

    /** Wide enough for four cards of the longest English title, narrow enough for the smallest screen. */
    private static final double PREF_WIDTH = 940;
    private static final PseudoClass HAS_SELECTION = PseudoClass.getPseudoClass("has-selection");

    private final WipeSelection selection = new WipeSelection(WipeCatalog.TARGETS);
    private final List<WipeSection> sections = WipeSections.of(WipeCatalog.TARGETS);
    private final Map<WipeTarget, CheckBox> boxes = new LinkedHashMap<>();
    private final Map<WipeSection, Label> counts = new LinkedHashMap<>();
    private final Map<WipeSection, VBox> cards = new LinkedHashMap<>();

    private final FlowPane chips = new FlowPane(6, 6);
    private final Label summaryEmpty = new Label(text("wipe.summary.none"));
    private final Label summaryCount = new Label();
    private final ToggleButton btnSelectAll = new ToggleButton();
    private final Button btnDelete = new Button(text("wipe.button.delete"), AppIcon.DELETE.graphic());
    private final Button btnClose = new Button(text("common.close"), AppIcon.CLOSE.graphic());

    private MaskerPaneSetting masker;
    /** Set while the boxes are being made to match the selection, so their listeners stay out. */
    private boolean syncing;

    @Override
    public Pane pane() {
        VBox content = new VBox(12, titleBar(), warning(), cardsRow(), summary(), footer());
        content.setPadding(new Insets(16));

        StackPane root = new StackPane(content);
        root.getStyleClass().addAll("app-root", "wipe-screen");
        root.setPrefWidth(PREF_WIDTH);
        masker = new MaskerPaneSetting(root);

        // The dialog has no button of its own, so the window's X and Escape answer nothing unless
        // told to; a screen that erases the database is one to be able to leave with a key.
        root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                close();
            }
        });
        refresh();
        return root;
    }

    @Override
    public String title() {
        return text("wipe.screen.title");
    }

    // ---- the layout -----------------------------------------------------------------------------

    private HBox titleBar() {
        HBox iconBox = new HBox(AppIcon.DELETE.graphic(28));
        iconBox.setAlignment(Pos.CENTER);
        iconBox.getStyleClass().add("party-screen-icon-box");

        Label title = new Label(text("wipe.screen.title"));
        title.getStyleClass().add("party-screen-title");
        Label subtitle = new Label(text("wipe.screen.subtitle"));
        subtitle.getStyleClass().add("party-screen-subtitle");
        subtitle.setWrapText(true);
        VBox titles = new VBox(2, title, subtitle);
        titles.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titles, Priority.ALWAYS);

        HBox header = new HBox(12, iconBox, titles);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("party-screen-header");
        return header;
    }

    private HBox warning() {
        Label text = new Label(text("wipe.screen.warning"));
        text.setWrapText(true);
        text.getStyleClass().add("wipe-warning-text");
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox warning = new HBox(10, AppIcon.WARNING.graphic(18), text);
        warning.setAlignment(Pos.CENTER_LEFT);
        warning.getStyleClass().add("wipe-warning");
        return warning;
    }

    /** One card per section, four equal columns - a flow of fixed-width cards left a third of the window empty. */
    private GridPane cardsRow() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        for (int column = 0; column < sections.size(); column++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / sections.size());
            constraints.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(constraints);
            grid.add(card(sections.get(column)), column, 0);
        }
        return grid;
    }

    private VBox card(WipeSection section) {
        Label title = new Label(text(section.titleKey()));
        title.getStyleClass().add("wipe-card-title");
        title.setMinWidth(0);
        HBox.setHgrow(title, Priority.ALWAYS);

        Label count = new Label();
        count.getStyleClass().add("wipe-card-count");
        count.setMinWidth(Region.USE_PREF_SIZE);
        // "2 / 4" has no letter to give it a direction, and a right-to-left card draws it "4 / 2".
        count.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        counts.put(section, count);

        HBox head = new HBox(6, sectionIcon(section), title, count);
        head.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, head);
        card.getStyleClass().addAll("app-card", "wipe-card");
        card.setMaxHeight(Double.MAX_VALUE);
        for (WipeTarget target : section.targets()) {
            card.getChildren().add(box(target));
        }
        cards.put(section, card);
        return card;
    }

    private CheckBox box(WipeTarget target) {
        CheckBox box = new CheckBox(target.label());
        box.getStyleClass().add("wipe-check");
        box.setMaxWidth(Double.MAX_VALUE);
        box.setWrapText(true);
        List<WipeTarget> alsoErased = WipeSelection.alsoErasedWith(target);
        if (!alsoErased.isEmpty()) {
            box.setTooltip(new Tooltip(text("wipe.also.erased", labels(alsoErased))));
        }
        box.selectedProperty().addListener((observable, was, ticked) -> {
            if (!syncing) {
                selection.set(target, ticked);
                refresh();
            }
        });
        boxes.put(target, box);
        return box;
    }

    private Node sectionIcon(WipeSection section) {
        AppIcon icon = switch (section.id()) {
            case WipeSections.SALES -> AppIcon.SALES;
            case WipeSections.PURCHASES -> AppIcon.PURCHASE;
            case WipeSections.ITEMS -> AppIcon.ITEM;
            default -> AppIcon.SETTINGS;
        };
        Node graphic = icon.graphic(16);
        graphic.getStyleClass().add("wipe-card-icon");
        return graphic;
    }

    /**
     * What will be erased, one chip per target, closure included. It was one line in a navy strip,
     * written in the dialog's dark label colour, and ended in an ellipsis once a few were ticked.
     * The height is fixed so a wrap does not push the buttons off the window.
     */
    private VBox summary() {
        Label caption = new Label(text("wipe.summary.title"));
        caption.getStyleClass().add("wipe-summary-caption");
        summaryCount.getStyleClass().add("wipe-summary-count");
        summaryCount.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        HBox head = new HBox(8, caption, summaryCount);
        head.setAlignment(Pos.CENTER_LEFT);

        summaryEmpty.getStyleClass().add("wipe-summary-empty");
        chips.setMinHeight(0);
        chips.setPrefWrapLength(PREF_WIDTH - 64);
        StackPane body = new StackPane(summaryEmpty, chips);
        StackPane.setAlignment(summaryEmpty, Pos.TOP_LEFT);
        StackPane.setAlignment(chips, Pos.TOP_LEFT);
        body.setMinHeight(58);
        body.setPrefHeight(58);

        VBox summary = new VBox(6, head, body);
        summary.getStyleClass().add("wipe-summary");
        return summary;
    }

    private HBox footer() {
        btnSelectAll.setGraphic(AppIcon.SELECT_ALL.graphic());
        btnSelectAll.getStyleClass().addAll("app-neutral-button", "select-action-toggle");
        btnSelectAll.selectedProperty().addListener((observable, was, all) -> {
            if (!syncing) {
                if (all) {
                    selection.selectAll();
                } else {
                    selection.clear();
                }
                refresh();
            }
        });

        btnDelete.getStyleClass().add("wipe-delete-button");
        btnDelete.setOnAction(event -> delete());
        btnClose.getStyleClass().add("wipe-close-button");
        btnClose.setOnAction(event -> close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(10, btnSelectAll, spacer, btnDelete, btnClose);
        footer.setAlignment(Pos.CENTER_LEFT);
        return footer;
    }

    // ---- the state ------------------------------------------------------------------------------

    /** Makes every control say what the selection says. */
    private void refresh() {
        syncing = true;
        try {
            boxes.forEach((target, box) -> box.setSelected(selection.isSelected(target)));
            btnSelectAll.setSelected(selection.isAll());
        } finally {
            syncing = false;
        }
        btnSelectAll.setText(text(selection.isAll() ? "setting.cancel.select.all" : "setting.select.all"));
        btnDelete.setDisable(selection.isEmpty());

        cards.forEach((section, card) -> {
            long ticked = selection.countIn(section.targets());
            counts.get(section).setText(ticked + " / " + section.targets().size());
            card.pseudoClassStateChanged(HAS_SELECTION, ticked > 0);
        });

        List<WipeTarget> erased = selection.selected();
        chips.getChildren().setAll(erased.stream().map(this::chip).toList());
        chips.setVisible(!erased.isEmpty());
        summaryEmpty.setVisible(erased.isEmpty());
        summaryCount.setText(erased.size() + " / " + WipeCatalog.TARGETS.size());
        summaryCount.setVisible(!erased.isEmpty());
    }

    private Label chip(WipeTarget target) {
        Label chip = new Label(target.label());
        chip.getStyleClass().add("wipe-chip");
        return chip;
    }

    // ---- the actions ----------------------------------------------------------------------------

    /**
     * Confirms, backs up, erases. The plan is built here, on the JavaFX thread, from the selection;
     * the worker thread used to read the check boxes itself.
     */
    private void delete() {
        WipePlan plan = selection.plan();
        if (plan.isEmpty()) {
            return;
        }
        int kinds = plan.targets().size();
        if (!AllAlerts.confirm_all(text("wipe.confirm.title"), text("wipe.confirm.message", kinds))) {
            return;
        }
        masker.showMaskerPane(text("wipe.running"), () -> {
            // A backup that fails aborts the wipe: there would be nothing to go back to.
            SaveDatabaseFile.save(BackupKind.BEFORE_DELETE, false);
            // Failures reach the task's boundary, which reports them; swallowing one would run
            // onSucceeded and announce a wipe that did not happen.
            new WipeService().run(plan);
        });
        masker.getVoidTask().setOnSucceeded(event -> {
            // Offering to run the same wipe again is the last thing the screen should do.
            selection.clear();
            refresh();
            LoadDataAndList.updateData();
            AllAlerts.alertInformation(text("wipe.screen.title"), text("wipe.done", kinds));
        });
    }

    private void close() {
        ((Stage) btnClose.getScene().getWindow()).close();
    }

    private static String labels(List<WipeTarget> targets) {
        return targets.stream().map(WipeTarget::label).collect(Collectors.joining(text("wipe.list.separator")));
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }

    private static String text(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }
}
