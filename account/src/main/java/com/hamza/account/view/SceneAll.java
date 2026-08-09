package com.hamza.account.view;

import com.hamza.account.config.ThemeManager;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.scene.Parent;
import javafx.scene.Scene;

public class SceneAll extends Scene {

    public SceneAll(Parent parent) {
        super(parent);
//        Style_Sheet.changeStyle(this);
        ThemeManager.apply(this);
        ChangeOrientation.sceneOrientation(this);
    }
}
