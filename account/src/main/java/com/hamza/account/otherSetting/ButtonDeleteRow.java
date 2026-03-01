package com.hamza.account.otherSetting;

import com.hamza.account.config.Image_Setting;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.button.api.ButtonColumnI;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.scene.Node;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class ButtonDeleteRow implements ButtonColumnI {

    @NotNull
    @Override
    public String textName() {
        return "";
    }

    @Override
    public Node imageNode() {
        return new ImageDesign(new Image_Setting().trash);
    }

    @Override
    public void action(int i) throws Exception {

    }

    @NotNull
    @Override
    public String columnTitle() {
        return Setting_Language.WORD_DELETE;
    }

}
