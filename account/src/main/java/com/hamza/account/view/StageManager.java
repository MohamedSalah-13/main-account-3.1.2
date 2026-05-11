package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.table.StageDimensions;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StageManager {

    private static final Map<String, Stage> OPEN_STAGES = new ConcurrentHashMap<>();

    private StageManager() {
    }

    public static Stage show(String key, Parent root, String title) {
        return show(key, new Scene(root), title, true, null, null);
    }

    public static Stage show(String key, Scene scene, String title) {
        return show(key, scene, title, true, null, null);
    }

    public static Stage showModal(String key, Parent root, String title) {
        return show(key, new Scene(root), title, true, Modality.APPLICATION_MODAL, null);
    }

    public static Stage showModal(String key, Scene scene, String title) {
        return show(key, scene, title, true, Modality.APPLICATION_MODAL, null);
    }

    public static Stage show(
            String key,
            Scene scene,
            String title,
            boolean resizable,
            Modality modality,
            Class<?> dimensionsOwner
    ) {
        Stage existingStage = OPEN_STAGES.get(key);

        if (existingStage != null) {
            if (existingStage.isShowing()) {
                existingStage.toFront();
                existingStage.requestFocus();

                if (existingStage.isIconified()) {
                    existingStage.setIconified(false);
                }

                return existingStage;
            }

            OPEN_STAGES.remove(key);
        }

        Stage stage = new Stage();
        stage.setScene(scene);
        stage.setTitle(title);
        stage.setResizable(resizable);

        try {
            stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().tools));
        } catch (Exception ignored) {
            // تجاهل الخطأ لو الأيقونة غير موجودة أو حدثت مشكلة في تحميلها
        }

        if (modality != null) {
            stage.initModality(modality);
        }

        stage.setOnCloseRequest(event -> OPEN_STAGES.remove(key));
        stage.setOnHidden(event -> OPEN_STAGES.remove(key));

        OPEN_STAGES.put(key, stage);
        stage.show();

        if (dimensionsOwner != null) {
            StageDimensions.stageDimensions(dimensionsOwner, stage);
        }

        return stage;
    }

    public static boolean isOpen(String key) {
        Stage stage = OPEN_STAGES.get(key);
        return stage != null && stage.isShowing();
    }

    public static Stage get(String key) {
        return OPEN_STAGES.get(key);
    }

    public static void close(String key) {
        Stage stage = OPEN_STAGES.remove(key);

        if (stage != null) {
            stage.close();
        }
    }

    public static void closeAll() {
        OPEN_STAGES.values().forEach(Stage::close);
        OPEN_STAGES.clear();
    }
}
