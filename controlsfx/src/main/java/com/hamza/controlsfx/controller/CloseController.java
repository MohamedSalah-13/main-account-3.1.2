package com.hamza.controlsfx.controller;

import com.hamza.controlsfx.others.CloseService;
import com.hamza.controlsfx.others.ImageSetting;
import com.hamza.controlsfx.resize.Opacity_Move_Stage;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.Getter;

public class CloseController extends CloseService {

    private static final Image IMAGE_MAX = new Image(new ImageSetting().IMAGE_MAX);
    private static final Image IMAGE_MINI = new Image(new ImageSetting().IMAGE_MINI);
    @Getter
    @FXML
    private Label title;
    @FXML
    private Button btnClose, btnMinus, btnResize;
    @FXML
    private ImageView viewResize;
    @Getter
    @FXML
    private ImageView imageView;
    @FXML
    private AnchorPane pane;

    @FXML
    private void initialize() {

        new Opacity_Move_Stage(pane);

        btnClose.setTooltip(new Tooltip("close"));
        btnResize.setTooltip(new Tooltip("Restore"));
        btnMinus.setTooltip(new Tooltip("Minimize"));

        btnMinus.setOnMouseClicked(e -> ((Stage) ((Node) e.getSource()).getScene().getWindow()).setIconified(true));
        btnClose.setOnMouseClicked(event -> {
            Stage stage = (Stage) (((Node) event.getSource()).getScene().getWindow());
            stage.close();
        });

        btnResize.setOnAction(event -> {
            Stage stage = (Stage) (((Node) event.getSource()).getScene().getWindow());
            toggleStageSize(stage);
        });

        pane.setOnMouseClicked(event -> {
            if (!btnResize.isDisabled()) {
                if (event.getClickCount() == 2) {
                    Stage stage = (Stage) (((Node) event.getSource()).getScene().getWindow());
                    toggleStageSize(stage);
                }
            }
        });
    }


    /**
     * Toggles the size of the given stage between its maximized and normal states.
     *
     * @param stage The stage to be toggled.
     */
    private void toggleStageSize(Stage stage) {
        Screen screen = Screen.getPrimary();
        Rectangle2D bounds = screen.getVisualBounds();
        if (!stage.isMaximized()) {
            maximizeStage(stage, bounds);
        } else {
            restoreStage(stage);
        }
    }

    /**
     * Maximizes the given stage to the specified bounds and updates the stage's UI representation.
     *
     * @param stage the stage to be maximized
     * @param bounds the boundaries to which the stage will be maximized
     */
    private void maximizeStage(Stage stage, Rectangle2D bounds) {
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        viewResize.setImage(IMAGE_MAX);
        stage.setMaximized(true);
    }

    /**
     * Restores the stage to its previous dimensions and location.
     *
     * @param stage the stage to be restored
     */
    private void restoreStage(Stage stage) {
        stage.setMaximized(false);
        stage.setMaxWidth(getStageWidth());
        stage.setMaxHeight(getStageHeight());
        stage.setX(getStageX());
        stage.setY(getStageY());
        viewResize.setImage(IMAGE_MINI);
    }

}
