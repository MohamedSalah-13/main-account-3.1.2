package com.hamza.account.choiceDialoge;

import com.hamza.account.config.Image_Setting;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.scene.image.Image;

import java.io.InputStream;

public class ChoosePrinter implements ChoiceData {

    @Override
    public String titleName() {
        return Setting_Language.WORD_PRINT;
    }

    @Override
    public String HeaderText() {
        return Setting_Language.CHOOSE_PRINTER + ": ";
    }

    @Override
    public String contentName() {
        return Setting_Language.WORD_NAME;
    }

    @Override
    public InputStream graphic() {
        return new Image_Setting().print;
    }

    @Override
    public Image stageGraphic() {
        return new Image(new Image_Setting().print);
    }

}
