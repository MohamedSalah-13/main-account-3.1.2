package com.hamza.account.controller.reports;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToolBar;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class ToolbarReportsNameController {

    @FXML
    private ToolBar toolBar;
    @FXML
    private Button btnPrint, btnRefresh;
    @FXML
    private Label labelTitle;

    @FXML
    public void initialize() {
        nameSetting();
    }

    private void nameSetting() {
        btnPrint.setText(Setting_Language.WORD_PRINT);
        btnRefresh.setText(Setting_Language.WORD_REFRESH);
    }

    public void setReportToolbar(@NotNull ToolbarReportsNameInterface toolbarReportsNameInterface, Node... nodes) {
        btnPrint.setOnAction(event -> {
            try {
                toolbarReportsNameInterface.print();
            } catch (Exception e) {
                log.error(e.getMessage(), e.getCause());
                AllAlerts.alertError(e.getMessage());
            }
        });
        btnRefresh.setOnAction(event -> {
            try {
                toolbarReportsNameInterface.refresh();
            } catch (Exception e) {
                log.error(e.getMessage(), e.getCause());
                AllAlerts.alertError(e.getMessage());
            }
        });
        labelTitle.setText(toolbarReportsNameInterface.setTitle());

        for (Node node : nodes) {
            toolBar.getItems().add(node);
        }
    }

}

