package com.hamza.account.controller.others;

import com.hamza.account.model.base.BaseGroups;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.SubGroups;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.Disable;
import com.hamza.controlsfx.interfaceData.TableViewShowDataInt;
import com.hamza.controlsfx.interfaceData.ToolbarAccountInt;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.others.Utils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Log4j2
@FxmlPath(pathFile = "addSubGroup.fxml")
public class AddSubGroupController extends ServiceData {

    private final Publisher<String> publisherAddGroup;
    private final ObservableList<SubGroups> subGroupsObservableList = FXCollections.observableArrayList(supGroupService.getSubGroupsList());
    @FXML
    private Label labelCode, labelName, labelMainGroup;
    @FXML
    private TextField txtCode, txtName;
    @FXML
    private ComboBox<String> comboMainGroup;
    @FXML
    private VBox box;

    public AddSubGroupController(Publisher<String> publisherAddGroup, DaoFactory daoFactory) throws Exception {
        super(daoFactory);
        this.publisherAddGroup = publisherAddGroup;
    }

    @FXML
    public void initialize() {
        otherSetting();
        resetData();
    }

    public TableViewShowDataInt<SubGroups> createAreaTableView() {
        return new TableViewShowDataInt<>() {
            @Override
            public List<SubGroups> dataList() {
                return subGroupsObservableList;
            }

            @Override
            public Class<? super SubGroups> classForColumn() {
                return BaseGroups.class;
            }
        };
    }

    public ToolbarAccountInt<SubGroups> getToolbarAccountActionInterface() {
        return new ToolbarAccountInt<>() {
            @Override
            public void addNewAccount() {
                resetData();
            }

            @Override
            public int deleteAccount() throws Exception {
                if (!txtCode.getText().equals(Setting_Language.generate) || !txtCode.getText().isEmpty()) {
                    var text = Integer.parseInt(txtCode.getText());
                    if (text > 0) {
                        return supGroupService.deleteSubGroup(text);
                    }
                }
                return 0;
            }

            @Disable
            @Override
            public void printAccount() {

            }

            @Override
            public SubGroups saveAccount() throws Exception {
                return insertData();
            }

            @Override
            public void firstPage(SubGroups subGroups) {
                selectData(subGroups);
            }

            @Override
            public void previousPage(SubGroups subGroups) {
                selectData(subGroups);
            }

            @Override
            public void nextPage(SubGroups subGroups) {
                selectData(subGroups);
            }

            @Override
            public void lastPage(SubGroups subGroups) {
                selectData(subGroups);
            }

            @Override
            public ObservableList<SubGroups> observableList() {
                return subGroupsObservableList;
            }

            @Override
            public void afterSaveOrDelete() {
                try {
                    publisherAddGroup.setAvailability(Setting_Language.WORD_SUB_G);
                    resetData();
                    subGroupsObservableList.clear();
                    subGroupsObservableList.setAll(supGroupService.getSubGroupsList());
                } catch (Exception e) {
                    log.error(e.getMessage());
                    AllAlerts.alertError(e.getMessage());
                }
            }

            @Override
            public Publisher<String> publisherTable() {
                return publisherAddGroup;
            }
        };
    }

    private void otherSetting() {
        comboMainSetting();
        labelCode.setText(Setting_Language.WORD_CODE);
        labelName.setText(Setting_Language.WORD_NAME);
        labelMainGroup.setText(Setting_Language.WORD_MAIN_G);

        txtCode.setPromptText(Setting_Language.WORD_CODE);
        txtName.setPromptText(Setting_Language.WORD_NAME);
        comboMainGroup.setPromptText(Setting_Language.WORD_MAIN_G);
    }


    private SubGroups insertData() throws Exception {

        if (txtName.getText().isEmpty()) {
            txtName.requestFocus();
            throw new Exception("من فضلك أدخل الاسم");
        }
        if (comboMainGroup.getSelectionModel().isEmpty()) {
            comboMainGroup.requestFocus();
            throw new Exception("من فضلك حدد المجموعة");
        }
        SubGroups subGroups = new SubGroups();
        subGroups.setName(txtName.getText());
        subGroups.setMainGroups(mainGroupService.getMainGroupsByName(comboMainGroup.getSelectionModel().getSelectedItem()));
        var codeId = 0;

        if (txtCode.getText().isEmpty() || !txtCode.getText().equals(Setting_Language.generate)) {
            codeId = Integer.parseInt(txtCode.getText());
        }

        if (codeId > 0) {
            subGroups.setId(codeId);
            return supGroupService.update(subGroups) > 0 ? subGroups : null;
        } else {
            return supGroupService.insert(subGroups) > 0 ? subGroups : null;
        }
    }

    private void selectData(SubGroups subGroups) {
        txtCode.setText(String.valueOf(subGroups.getId()));
        txtName.setText(subGroups.getName());
        comboMainGroup.getSelectionModel().select(subGroups.getMainGroups().getName());
    }

    private void resetData() {
        txtCode.setText(Setting_Language.generate);
        txtName.clear();
        comboMainGroup.getSelectionModel().clearSelection();
        Utils.clearAll(txtName);
        txtName.requestFocus();

    }

    private void comboMainSetting() {
        comboMainGroup.setItems(FXCollections.observableArrayList(getMainGroupsNames()));
    }

    @NotNull
    private List<String> getMainGroupsNames() {
        try {
            return mainGroupService.getMainGroupsNames();
        } catch (DaoException e) {
            log.error(e.getMessage());
            return List.of();
        }
    }
}
