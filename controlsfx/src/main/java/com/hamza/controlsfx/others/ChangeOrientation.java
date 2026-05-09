package com.hamza.controlsfx.others;

import com.hamza.controlsfx.language.LanguageManager;
import javafx.geometry.NodeOrientation;
import javafx.scene.Scene;

public class ChangeOrientation {

    public static void sceneOrientation(Scene scene) {
        var arabic = LanguageManager.getInstance().isArabic();
        if (!arabic) {
            return;
        }
        scene.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
    }
}
