package com.hamza.controlsfx.controller;

import com.hamza.controlsfx.interfaceData.ChangePassInt;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.ImageSetting;
import com.hamza.controlsfx.others.ShowPassService;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.net.URL;
import java.util.ResourceBundle;

@Log4j2
@RequiredArgsConstructor
public class ChangePassController implements Initializable {

    private final ChangePassInt changePassInt;
    @FXML
    private ImageView imagePassOld, imagePassNew, imagePassRe;
    @FXML
    private Label LabelPassOld, labelPassNew, labelPassRe;
    @FXML
    private PasswordField txtPassNew, txtPassOld, txtPassRe;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        otherSetting();
    }

    private void otherSetting() {
        LabelPassOld.setText(Setting_Language.WORD_OLD_PASS);
        labelPassNew.setText(Setting_Language.WORD_NEW_PASS);
        labelPassRe.setText(Setting_Language.PASS_OK);

        txtPassOld.setPromptText(Setting_Language.WORD_OLD_PASS);
        txtPassNew.setPromptText(Setting_Language.WORD_NEW_PASS);
        txtPassRe.setPromptText(Setting_Language.PASS_OK);

        showPass(txtPassOld, imagePassOld);
        showPass(txtPassNew, imagePassNew);
        showPass(txtPassRe, imagePassRe);

    }

    public BooleanBinding getOr() {
        return txtPassOld.textProperty().isEmpty().or(txtPassNew.textProperty().isEmpty()).or(txtPassRe.textProperty().isEmpty());
    }

    public int saveData() throws Exception {
        String expectedPass = txtPassOld.getText();
        var anObject = changePassInt.actualPass();
        if (expectedPass.equals(anObject)) {
            String passNew = txtPassNew.getText();
            String passNewRe = txtPassRe.getText();
            if (passNew.isEmpty() || passNewRe.isEmpty()) {
                throw new Exception(Setting_Language.PLEASE_INSERT_ALL_DATA);
            }
            if (passNew.equals(passNewRe)) {
                //update pass
                return changePassInt.updatePass(passNew) ? 1 : 0;
            } else throw new Exception(Setting_Language.PASS_NO_RIGHT);

        } else {
            txtPassOld.setText("");
            txtPassOld.requestFocus();
            throw new Exception("كلمة المرور غير صحيحة");
        }
    }

    private void showPass(PasswordField passwordField, ImageView imageView) {
        SimpleBooleanProperty simpleBooleanProperty = new SimpleBooleanProperty();
        ShowPassService.show(passwordField, simpleBooleanProperty);
        Image show = new Image(new ImageSetting().show);
        Image hide = new Image(new ImageSetting().hide);
        imageView.setImage(show);

        imageView.setOnMouseClicked(mouseEvent -> {
            if (imageView.getImage().equals(show)) {
                simpleBooleanProperty.setValue(true);
                imageView.setImage(hide);
            } else {
                simpleBooleanProperty.setValue(false);
                imageView.setImage(show);
            }
        });

    }
}
