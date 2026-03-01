package com.hamza.account.view;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import static com.hamza.controlsfx.language.Setting_Language.UPDATE_DATE;

public class ChangeTimeApplication extends Application {


    private final VBox box = new VBox(10);
    private final Button button = new Button(UPDATE_DATE);
    private final Label label = new Label(Setting_Language.TIME_NOT);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        box.setPadding(new Insets(10));
        box.setAlignment(Pos.CENTER);
        VBox box1 = new VBox(5);
        box1.setPadding(new Insets(10));
        box1.setAlignment(Pos.CENTER);
        box1.getStyleClass().add("pane-box2");
        label.setStyle("-fx-font-size: 15px;" +
                "-fx-text-fill: red");
        box1.getChildren().addAll(label, button);
        box.getChildren().add(box1);
        box.setPrefSize(300, 200);
        VBox.setVgrow(box1, Priority.SOMETIMES);

        button.setOnAction(actionEvent -> {
            try {
                openTime();
            } catch (Exception e) {
                AllAlerts.alertError(e.getMessage());
            }
        });

        AppSettingInterface appSettingInterface = new AppSettingInterface() {
            @Override
            public @NotNull Pane pane() {
                return box;
            }

            @Override
            public String title() {
                return UPDATE_DATE;
            }

        };
        new OpenApplication<>(appSettingInterface);
    }

    @SuppressWarnings("deprecation")
    private void openTime() throws Exception {
        Process exec = Runtime.getRuntime().exec("control /name Microsoft.DateAndTime");
        int processComplete = exec.waitFor();
        if (processComplete == 1) {
            System.exit(0);
        }
    }
}
