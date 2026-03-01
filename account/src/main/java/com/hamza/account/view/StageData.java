package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.table.StageDimensions;
import javafx.stage.Stage;

public class StageData extends Stage {
    public StageData() {
        super();
        getIcons().add(new javafx.scene.image.Image(new Image_Setting().tools));
        setResizable(true);
        initModality(javafx.stage.Modality.APPLICATION_MODAL);
        show();
        StageDimensions.stageDimensions(getClass(), this);
    }
}
