package com.hamza.controlsfx.controller;

import javafx.concurrent.Task;
import javafx.scene.effect.BlendMode;
import javafx.scene.layout.StackPane;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.controlsfx.control.MaskerPane;

import java.io.IOException;

@Getter
@Log4j2
public class MaskerPaneSetting extends MaskerPane {

    private Task<Void> voidTask;

    public MaskerPaneSetting(StackPane stackPane) {
        this.setVisible(false);
        this.setText("من فضلك انتظر");
        this.setBlendMode(BlendMode.EXCLUSION);
        stackPane.getChildren().add(this);
    }

    public void showMaskerPane(ActionMasherPane actionEvent) {
        voidTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                MaskerPaneSetting.this.setVisible(true);
                actionEvent.action();
                Thread.sleep(1000);
                MaskerPaneSetting.this.setVisible(false);
                return null;
            }
        };

        new Thread(voidTask).start();
    }

    @FunctionalInterface
    public interface ActionMasherPane {
        void action() throws IOException, InterruptedException;
    }
}
