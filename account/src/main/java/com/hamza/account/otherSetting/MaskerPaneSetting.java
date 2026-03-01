package com.hamza.account.otherSetting;

import com.hamza.controlsfx.language.Setting_Language;
import javafx.concurrent.Task;
import javafx.scene.effect.BlendMode;
import javafx.scene.layout.StackPane;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.controlsfx.control.MaskerPane;

@Setter
@Getter
@Log4j2
public class MaskerPaneSetting extends MaskerPane {

    private Task<Void> voidTask;

    public MaskerPaneSetting(StackPane stackPane) {
        this.setVisible(false);
        this.setText(Setting_Language.PLEASE_WAIT);
        this.setBlendMode(BlendMode.EXCLUSION);
        stackPane.getChildren().add(this);
    }

    public void showMaskerPane(MaskerPaneSetting.ActionMasherPane actionEvent) {
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
        void action();
    }
}
