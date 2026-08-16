package com.hamza.controlsfx.others;

import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.Scene;

public class ChangeOrientation {

    public static void sceneOrientation(Scene scene) {
        scene.setNodeOrientation(LanguageManager.getInstance().getNodeOrientation());
    }
}
