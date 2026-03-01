package com.hamza.controlsfx.resize;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public class Opacity_Move_Stage {

    /**
     * Stores the initial x-coordinate of the mouse pointer when a mouse press event occurs.
     */
    private Double initialMouseX = 0.0;
    /**
     * Stores the initial Y-coordinate of the mouse when a drag operation starts on a pane.
     * This value is used to calculate the new position of the Stage during the drag.
     */
    private Double initialMouseY = 0.0;

    /**
     * Constructor to set up a pane to allow dragging and opacity change of the associated stage.
     *
     * @param pane the {@link Pane} which will be used for moving the stage
     */
    public Opacity_Move_Stage(Pane pane) {
        pane.setOnMousePressed(event -> {
            initialMouseX = event.getSceneX();
            initialMouseY = event.getSceneY();
        });
        pane.setOnMouseDragged(event -> {
            Stage stage = stage(event);
            stage.setX(event.getScreenX() - initialMouseX);
            stage.setY(event.getScreenY() - initialMouseY);
            stage.setOpacity(.8f);
        });

        pane.setOnDragDone(this::updateOpacityWhenReleasedOrDragOne);
        pane.setOnMouseReleased(this::updateOpacityWhenReleasedOrDragOne);
    }

    /**
     * Updates the opacity of the stage to full opacity (1.0) when a drag event is finished
     * or when the mouse is released.
     *
     * @param event The event that triggers this method, typically a MouseEvent or DragEvent.
     */
    private void updateOpacityWhenReleasedOrDragOne(Event event) {
        Stage stage = stage(event);
        stage.setOpacity(1.0f);
    }

    /**
     * Retrieves the {@link Stage} from the provided {@link Event}.
     *
     * @param event the event from which to extract the stage
     * @return the extracted Stage instance associated with the event
     */
    private Stage stage(Event event) {
        return (Stage) (((Node) event.getSource()).getScene().getWindow());
    }
}
