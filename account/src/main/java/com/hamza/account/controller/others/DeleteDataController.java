package com.hamza.account.controller.others;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.SaveDatabaseFile;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadDataAndList;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.account.wipe.WipeCatalog;
import com.hamza.account.wipe.WipePlan;
import com.hamza.account.wipe.WipeService;
import com.hamza.account.wipe.WipeTarget;
import com.hamza.controlsfx.util.ImageChoose;
import javafx.beans.binding.BooleanExpression;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hamza.controlsfx.language.Setting_Language.CANCEL_SELECT_ALL;
import static com.hamza.controlsfx.language.Setting_Language.SELECT_ALL;

@Log4j2
@FxmlPath(pathFile = "delete-data.fxml")
public class DeleteDataController implements AppSettingInterface {

    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;
    /** Which box stands for which target, in the catalog's own order. */
    private final Map<WipeTarget, CheckBox> boxesByTarget = new LinkedHashMap<>();
    @FXML
    private CheckBox deleteSales, deleteSalesReturn, deleteCustomersAccount, deleteCustomers;
    @FXML
    private CheckBox deletePurchase, deletePurchaseReturn, deleteSuppliersAccount, deleteSuppliers;
    @FXML
    private CheckBox deleteItems, deleteSubGroup, deleteMainGroup;
    @FXML
    private CheckBox deleteExpenses, deleteEmployees, deleteProcesses, deleteUsers;
    @FXML
    private Button btnSave, btnClose;
    @FXML
    private StackPane stackPane;
    @FXML
    private ToggleButton btnSelected;
    private MaskerPaneSetting maskerPaneSetting;

    public DeleteDataController(DaoFactory daoFactory, DataPublisher dataPublisher) {
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
    }

    @FXML
    public void initialize() {
        otherSetting();
        getData();
    }

    private void otherSetting() {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        btnClose.setText(Setting_Language.WORD_CLOSE);
        btnSave.setText(Setting_Language.WORD_SAVE);
        btnSelected.setText(SELECT_ALL);

        var images = new Image_Setting();
        btnClose.setGraphic(ImageChoose.createIcon(images.cancel));
        btnSave.setGraphic(ImageChoose.createIcon(images.save));
        btnSelected.setGraphic(ImageChoose.createIcon(images.select));
    }

    private void getData() {
        bindToCatalog();

        btnSelected.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            btnSelected.setText(t1 ? CANCEL_SELECT_ALL : SELECT_ALL);
            boxesByTarget.values().forEach(box -> box.setSelected(t1));
        });
        btnSave.setOnAction(actionEvent -> {
            if (selectedTargets().isEmpty()) {
                deleteSalesReturn.requestFocus();
                AllAlerts.alertError(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
                return;
            }
            if (AllAlerts.confirmDelete()) {
                maskerPaneSetting.showMaskerPane(() -> delete());

                maskerPaneSetting.getVoidTask().setOnSucceeded(workerStateEvent -> {
                    AllAlerts.alertSave();
                    LoadDataAndList.updateData();
                    btnSelected.setSelected(false);
                });
                maskerPaneSetting.getVoidTask().setOnFailed(workerStateEvent -> {
                    var error = workerStateEvent.getSource().getException();
                    log.error("The wipe did not complete", error);
                    AllAlerts.alertError(error == null ? Error_Text_Show.CANT_DELETE : error.getMessage());
                });
            }
        });

        btnClose.setOnAction(actionEvent -> ((Stage) btnClose.getScene().getWindow()).close());

    }

    /**
     * Ties each box to its target and lets the declarations draw the tree.
     * <p>
     * The seven {@code addActionForCheckBox} calls that used to be written out here
     * were a dependency graph maintained by hand, next to a second one written in
     * SQL inside the truncate procedures, with nothing keeping the two agreed. Some
     * of what it enforced was not a dependency at all, and some of what the foreign
     * keys require was missing from it: you could erase the items while keeping the
     * purchase returns that point at them, and the procedures got away with it only
     * by turning off the checking. Both graphs are now the {@code requires} field in
     * {@link WipeCatalog}, and this reads it.
     */
    private void bindToCatalog() {
        boxesByTarget.put(WipeCatalog.SALES_RETURNS, deleteSalesReturn);
        boxesByTarget.put(WipeCatalog.SALES, deleteSales);
        boxesByTarget.put(WipeCatalog.PURCHASE_RETURNS, deletePurchaseReturn);
        boxesByTarget.put(WipeCatalog.PURCHASES, deletePurchase);
        boxesByTarget.put(WipeCatalog.CUSTOMER_ACCOUNTS, deleteCustomersAccount);
        boxesByTarget.put(WipeCatalog.SUPPLIER_ACCOUNTS, deleteSuppliersAccount);
        boxesByTarget.put(WipeCatalog.CUSTOMERS, deleteCustomers);
        boxesByTarget.put(WipeCatalog.SUPPLIERS, deleteSuppliers);
        boxesByTarget.put(WipeCatalog.ITEMS, deleteItems);
        boxesByTarget.put(WipeCatalog.SUB_GROUPS, deleteSubGroup);
        boxesByTarget.put(WipeCatalog.MAIN_GROUPS, deleteMainGroup);
        boxesByTarget.put(WipeCatalog.EXPENSES, deleteExpenses);
        boxesByTarget.put(WipeCatalog.EMPLOYEES, deleteEmployees);
        boxesByTarget.put(WipeCatalog.PROCESSES, deleteProcesses);
        boxesByTarget.put(WipeCatalog.USERS, deleteUsers);

        boxesByTarget.forEach((target, box) -> {
            var prerequisites = target.requires().stream()
                    .map(id -> WipeCatalog.byId(id).orElseThrow())
                    .map(boxesByTarget::get)
                    .filter(Objects::nonNull)
                    .toList();
            if (prerequisites.isEmpty()) {
                return;
            }

            var allSelected = prerequisites.stream()
                    .map(CheckBox::selectedProperty)
                    .map(BooleanExpression::booleanExpression)
                    .reduce(BooleanExpression::and)
                    .orElseThrow();

            box.disableProperty().bind(allSelected.not());
            // Unticking a prerequisite unticks whatever depended on it, so the screen
            // cannot be left holding a selection it would refuse to run.
            allSelected.addListener((observable, was, isSelected) -> {
                if (!isSelected) box.setSelected(false);
            });
        });
    }

    private Set<WipeTarget> selectedTargets() {
        return boxesByTarget.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private void delete() {
        try {
            // TRUNCATE commits implicitly, so a wipe that fails halfway cannot be
            // rolled back. Deleting a single invoice already takes a backup first
            // (TotalsController); the operation that empties the whole database was
            // the one doing it without. A backup that fails aborts the wipe - there
            // would be nothing to go back to.
            SaveDatabaseFile.saveBeforeClose(false);

            // The plan takes the closure of what was ticked, so a selection the
            // screen somehow allowed that leaves out something it depends on is
            // completed here rather than failing halfway through the wipe.
            new WipeService().run(WipePlan.of(selectedTargets()));

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            // Rethrown rather than only reported: the task's onSucceeded announces a
            // successful wipe and reloads every screen, and swallowing the failure
            // here is what let it run after a wipe that did not happen.
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public Pane pane() throws Exception {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return "Delete Tables";
    }
}
