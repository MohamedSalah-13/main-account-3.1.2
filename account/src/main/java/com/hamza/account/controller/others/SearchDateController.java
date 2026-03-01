package com.hamza.account.controller.others;

import com.hamza.account.openFxml.FxmlPath;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.DateSetting;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import lombok.Getter;

@FxmlPath(pathFile = "include/search-date.fxml")
public class SearchDateController {

    @Getter
    @FXML
    private DatePicker dateFrom, dateTo;
    @FXML
    private Label labelDateFrom;
    @FXML
    private Label labelDateTo;

    @FXML
    private void initialize() {
        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);
        labelDateFrom.setText(Setting_Language.WORD_FROM);
        labelDateTo.setText(Setting_Language.WORD_TO);
    }
}
