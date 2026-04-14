package com.hamza.account.controller.dataSetting;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import lombok.extern.log4j.Log4j2;
import org.controlsfx.control.CheckListView;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import static com.hamza.controlsfx.util.ImageChoose.createIcon;

@FxmlPath(pathFile = "add-data.fxml")
@Log4j2
public class AddDataController implements Initializable {

    private final AddDataInterface addDataInterface;
    @FXML
    private CheckListView<String> checkListView;
    @FXML
    private Button btnNew, btnUpdate, btnDelete;
    @FXML
    private Label labelTitle;


    public AddDataController(AddDataInterface addDataInterface) {
        this.addDataInterface = addDataInterface;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        checkListView.getItems().addAll(getList());
        checkListView.getCheckModel().checkAll();
        labelTitle.setText(addDataInterface.titlePane());
        otherSetting();
        actionButtons();
        buttonGraphic();
    }

    private List<String> getList() {
        try {
            return addDataInterface.listData();
        } catch (Exception e) {
            log(e);
        }
        return List.of();
    }

    private void buttonGraphic() {
        var images = new Image_Setting();
        btnNew.setGraphic(createIcon(images.add));
        btnUpdate.setGraphic(createIcon(images.update));
        btnDelete.setGraphic(createIcon(images.delete));
    }

    private void otherSetting() {
        btnNew.setText(Setting_Language.WORD_NEW);
        btnUpdate.setText(Setting_Language.WORD_UPDATE);
        btnDelete.setText(Setting_Language.WORD_DELETE);
    }

    private void actionButtons() {
        btnNew.setOnAction(event -> {
            try {
                addDataInterface.addData();
            } catch (Exception e) {
                log.error(e.getMessage(), e.getCause());
            }
        });
        btnUpdate.setOnAction(event -> {
            try {
                if (checkListView.getSelectionModel().isEmpty()) throw new Exception("Please select data to update.");
                addDataInterface.updateData(checkListView.getSelectionModel().getSelectedItem());
            } catch (Exception e) {
                log(e);
            }
        });
        btnDelete.setOnAction(event -> {
            try {
                if (checkListView.getSelectionModel().isEmpty()) throw new Exception("Please select data to delete.");
                if (!AllAlerts.confirmDelete()) return;
                var i = addDataInterface.deleteData(checkListView.getSelectionModel().getSelectedItem());
                if (i >= 1) {
                    AllAlerts.alertSaveWithMessage("Data has been successfully deleted.");
                    checkListView.getItems().removeAll(checkListView.getSelectionModel().getSelectedItem());
                } else {
                    AllAlerts.alertError("No data found to delete.");
                }
                checkListView.getCheckModel().clearChecks();
                checkListView.refresh();
                checkListView.getSelectionModel().clearSelection();
                checkListView.getFocusModel().focus(0);
            } catch (Exception e) {
                log(e);
            }
        });

        addDataInterface.publisher().addObserver(message -> {
            checkListView.refresh();
            checkListView.getItems().removeAll();
            checkListView.getItems().setAll(getList());
            checkListView.getCheckModel().clearChecks();
        });
    }

    private void log(Exception e) {
        log.error(e.getMessage(), e.getCause());
        AllAlerts.alertError(e.getMessage());
    }
}
