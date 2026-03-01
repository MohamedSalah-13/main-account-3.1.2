package com.hamza.account.controller.others;

import com.hamza.account.openFxml.FxmlPath;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.others.Utils;
import javafx.fxml.FXML;
import javafx.scene.control.*;

@FxmlPath(pathFile = "add-treasury-amount.fxml")
public class AddTreasuryAmount {

    @FXML
    private TextField txtAmount, txtCode;
    @FXML
    private DatePicker dateAdd;
    @FXML
    private ComboBox<String> comboTreasury;
    @FXML
    private Label labelCode, labelDate, labelTreasury, labelAmount, labelDescription;
    @FXML
    private TextArea txtDescription;

    @FXML
    public void initialize() {
        //TODO 12/30/2024 4:36 PM Mohamed: complete data
        DateSetting.dateAction(dateAdd);
        Utils.setTextFormatter(txtAmount);

        txtDescription.setPromptText(Setting_Language.NOTES);
        labelCode.setText(Setting_Language.WORD_CODE);
        labelDate.setText(Setting_Language.WORD_DATE);
        labelAmount.setText(Setting_Language.THE_AMOUNT);
        labelTreasury.setText(Setting_Language.TREASURY);
        labelDescription.setText(Setting_Language.NOTES);
    }
}
