package com.hamza.account.view;

import com.hamza.account.config.Style_Sheet;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.scene.Parent;
import javafx.scene.Scene;

public class SceneAll extends Scene {

    public SceneAll(Parent parent) {
        super(parent);
        Style_Sheet.changeStyle(this);
        ChangeOrientation.sceneOrientation(this);
    }
}
