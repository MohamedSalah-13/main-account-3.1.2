package com.hamza.account.controller.others;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.SaveDatabaseFile;
import com.hamza.account.controller.main.LoadDataAndList;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.wipe.WipeCatalog;
import com.hamza.account.wipe.WipePlan;
import com.hamza.account.wipe.WipeService;
import com.hamza.account.wipe.WipeTarget;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.util.ImageChoose;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import java.util.*;
import java.util.stream.Collectors;

import static com.hamza.controlsfx.language.Setting_Language.CANCEL_SELECT_ALL;
import static com.hamza.controlsfx.language.Setting_Language.SELECT_ALL;

@Log4j2
@FxmlPath(pathFile = "delete-data.fxml")
public class DeleteDataController implements AppSettingInterface {

    /**
     * Which box stands for which target, in the catalog's own order.
     */
    private final Map<WipeTarget, CheckBox> boxesByTarget = new LinkedHashMap<>();
    @FXML
    private Button btnSave, btnClose;
    @FXML
    private StackPane stackPane;
    @FXML
    private ToggleButton btnSelected;
    @FXML
    private FlowPane targetsPane;
    @FXML
    private Label summaryLabel;
    private MaskerPaneSetting maskerPaneSetting;
    /**
     * Set while one tick is ticking others, so the cascade does not re-enter.
     */
    private boolean cascading;

    /**
     * The grouping, with anything the catalog has gained since put on the end.
     */
    private static List<Section> sections() {
        List<Section> sections = new ArrayList<>(List.of(
                new Section("المبيعات والعملاء", List.of(
                        WipeCatalog.SALES_RETURNS, WipeCatalog.SALES,
                        WipeCatalog.CUSTOMER_ACCOUNTS, WipeCatalog.CUSTOMERS)),
                new Section("المشتريات والموردين", List.of(
                        WipeCatalog.PURCHASE_RETURNS, WipeCatalog.PURCHASES,
                        WipeCatalog.SUPPLIER_ACCOUNTS, WipeCatalog.SUPPLIERS)),
                new Section("الأصناف والمجموعات", List.of(
                        WipeCatalog.ITEMS, WipeCatalog.SUB_GROUPS, WipeCatalog.MAIN_GROUPS)),
                new Section("أخرى", List.of(
                        WipeCatalog.EXPENSES, WipeCatalog.EMPLOYEES,
                        WipeCatalog.PROCESSES, WipeCatalog.USERS))));

        Set<WipeTarget> placed = sections.stream()
                .flatMap(section -> section.targets().stream())
                .collect(Collectors.toSet());
        List<WipeTarget> missing = WipeCatalog.TARGETS.stream()
                .filter(target -> !placed.contains(target))
                .toList();

        if (!missing.isEmpty()) {
            log.warn("Wipe targets with no section, shown under the last one: {}",
                    missing.stream().map(WipeTarget::id).toList());
            Section last = sections.removeLast();
            List<WipeTarget> merged = new ArrayList<>(last.targets());
            merged.addAll(missing);
            sections.add(new Section(last.title(), merged));
        }
        return List.copyOf(sections);
    }

    @FXML
    public void initialize() {
        otherSetting();
        getData();
    }

    private void otherSetting() {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        btnClose.setText(Setting_Language.WORD_CLOSE);
        // The button empties tables; calling it "save" was the old screen's word for
        // it, and the only one on the screen that did not say what it does.
        btnSave.setText(Setting_Language.WORD_DELETE);
        btnSelected.setText(SELECT_ALL);

        var images = new Image_Setting();
        btnClose.setGraphic(ImageChoose.createIcon(images.cancel));
        btnSave.setGraphic(ImageChoose.createIcon(images.delete));
        btnSelected.setGraphic(ImageChoose.createIcon(images.select));
    }

    private void getData() {
        buildCards();
        bindToCatalog();
        updateSummary();

        btnSelected.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            btnSelected.setText(t1 ? CANCEL_SELECT_ALL : SELECT_ALL);
            // All of them at once is already closed under requires, either way, so the
            // cascade has nothing to add and is held off rather than run per box.
            cascading = true;
            try {
                boxesByTarget.values().forEach(box -> box.setSelected(t1));
            } finally {
                cascading = false;
            }
            updateSummary();
        });
        // Nothing ticked used to be answered with an error alert after the fact; the
        // button now says so by being unavailable.
        btnSave.setOnAction(actionEvent -> {
            if (AllAlerts.confirmDelete()) {
                maskerPaneSetting.showMaskerPane("حذف البيانات", this::delete);

                maskerPaneSetting.getVoidTask().setOnSucceeded(workerStateEvent -> {
                    AllAlerts.alertSave();
                    LoadDataAndList.updateData();
                    // Clearing the toggle only cleared the boxes when the toggle was what
                    // ticked them; the screen kept its ticks after a wipe that had already
                    // run, and offering to run them again is the last thing it should do.
                    btnSelected.setSelected(false);
                    boxesByTarget.values().forEach(box -> box.setSelected(false));
                });
            }
        });

        btnClose.setOnAction(actionEvent -> ((Stage) btnClose.getScene().getWindow()).close());

    }

    /**
     * Draws one card per section and one box per target, and fills
     * {@code boxesByTarget} on the way.
     * <p>
     * The boxes were fifteen {@code fx:id}s in the FXML and fifteen fields here,
     * which is fifteen places to edit for a target the catalog gains - and the
     * labels were written twice, once in each. They are built from the catalog now,
     * and the cards wrap to the width of the window rather than sitting in two
     * fixed columns.
     */
    private void buildCards() {
        for (Section section : sections()) {
            VBox card = new VBox(8.0);
            card.getStyleClass().addAll("app-card", "items-section-card");
            card.setMinWidth(240.0);
            card.setPrefWidth(260.0);

            Label title = new Label(section.title());
            title.getStyleClass().add("app-section-title");
            card.getChildren().add(title);

            for (WipeTarget target : section.targets()) {
                CheckBox box = new CheckBox(target.label());
                box.getStyleClass().add("modern-check-box");
                box.setMaxWidth(Double.MAX_VALUE);
                boxesByTarget.put(target, box);
                card.getChildren().add(box);
            }

            targetsPane.getChildren().add(card);
        }
    }

    /**
     * What the wipe would actually erase, said before it is run.
     * <p>
     * The closure is not what was ticked: ticking the main groups takes the sub
     * groups, the items and all four invoice targets with it. The screen used to
     * leave the user to work that out from which boxes had gone grey.
     */
    private void updateSummary() {
        Set<WipeTarget> selected = selectedTargets();
        btnSave.setDisable(selected.isEmpty());

        if (selected.isEmpty()) {
            summaryLabel.setText("لم يتم اختيار أي بيانات");
            summaryLabel.setTooltip(null);
            return;
        }

        List<WipeTarget> closure = WipeCatalog.closureOf(selected);
        String names = closure.stream().map(WipeTarget::label).collect(Collectors.joining("، "));
        summaryLabel.setText("سيتم مسح (%d): %s".formatted(closure.size(), names));
        // The label is one line and ends in an ellipsis when everything is ticked.
        summaryLabel.setTooltip(new Tooltip(names));
    }

    /**
     * Ties each box to its target and lets the declarations drive the ticking.
     * <p>
     * The seven {@code addActionForCheckBox} calls that used to be written out here
     * were a dependency graph maintained by hand, next to a second one written in
     * SQL inside the truncate procedures, with nothing keeping the two agreed. Some
     * of what it enforced was not a dependency at all, and some of what the foreign
     * keys require was missing from it: you could erase the items while keeping the
     * purchase returns that point at them, and the procedures got away with it only
     * by turning off the checking. Both graphs are now the {@code requires} field in
     * {@link WipeCatalog}, and this reads it.
     * <p>
     * It reads it forwards now. Disabling a box until its prerequisites were ticked
     * stated the same rule from the wrong end: it asked the user to discover, by
     * ticking things, that erasing the main groups needs six other boxes - and a
     * disabled control shows no tooltip, so there was nowhere to write down why.
     * Ticking a box now ticks its closure, and unticking one unticks whatever needed
     * it. The invariant is the same: what is ticked is always closed under
     * {@code requires}, so the plan can never be missing something it depends on.
     */
    private void bindToCatalog() {
        boxesByTarget.forEach((target, box) -> {
            List<WipeTarget> alsoErased = WipeCatalog.closureOf(List.of(target)).stream()
                    .filter(other -> !other.equals(target))
                    .toList();
            if (!alsoErased.isEmpty()) {
                box.setTooltip(new Tooltip("يُمسح معه: " + alsoErased.stream()
                        .map(WipeTarget::label)
                        .collect(Collectors.joining("، "))));
            }

            box.selectedProperty().addListener((observable, was, isSelected) -> {
                if (!cascading) {
                    cascading = true;
                    try {
                        if (isSelected) {
                            select(alsoErased);
                        } else {
                            deselectDependentsOf(target);
                        }
                    } finally {
                        cascading = false;
                    }
                }
                updateSummary();
            });
        });
    }

    private void select(Collection<WipeTarget> targets) {
        targets.stream()
                .map(boxesByTarget::get)
                .filter(Objects::nonNull)
                .forEach(box -> box.setSelected(true));
    }

    /**
     * Everything whose closure contains this target loses its tick with it.
     */
    private void deselectDependentsOf(WipeTarget target) {
        boxesByTarget.forEach((other, box) -> {
            if (box.isSelected() && WipeCatalog.closureOf(List.of(other)).contains(target)) {
                box.setSelected(false);
            }
        });
    }

    private Set<WipeTarget> selectedTargets() {
        return boxesByTarget.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private void delete() throws Exception {
        // A backup that fails aborts the wipe: there would be nothing to go back to.
        SaveDatabaseFile.saveBeforeClose(false);

        // The plan takes the closure of what was ticked, so a selection the screen
        // somehow allowed that leaves out a dependency is completed here.
        // Let failures reach the Task boundary; swallowing one would fire the
        // onSucceeded callback and announce a wipe that did not happen.
        new WipeService().run(WipePlan.of(selectedTargets()));
    }

    @Override
    public Pane pane() throws Exception {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        // The window title is the one string on the screen the user cannot read:
        // everything else is Arabic and this said "Delete Tables".
        return Setting_Language.WORD_DELETE + " " + Setting_Language.DATA;
    }

    /**
     * How the targets are grouped on the screen, and nothing more.
     * <p>
     * Which targets exist and what has to go with what is {@link WipeCatalog}'s to
     * say; how they are arranged into cards is this screen's, and belongs nowhere
     * near the wipe. A target the catalog gains and this list forgets is not lost -
     * {@link #sections()} appends it to the last card - so adding one still means
     * editing one file.
     */
    private record Section(String title, List<WipeTarget> targets) {
    }
}
