package com.hamza.account.table;

import com.hamza.account.controller.others.TableController;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.view.OpenApplication;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import javafx.application.Application;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class TableOpen<T> extends Application {

    private final TableInterface<T> tableInterface;
    @Getter
    private final Pane pane;

    public TableOpen(TableInterface<T> tableInterface) throws Exception {
        this.tableInterface = tableInterface;
        TableController<T> controller = new TableController<>(this.tableInterface);
        pane = new OpenFxmlApplication(controller).getPane();
    }

    @Override
    public void start(Stage stage) throws Exception {
        AppSettingInterface appSettingInterface = new AppSettingInterface() {
            @Override
            public @NotNull Pane pane() {
                return pane;
            }

            @Override
            public String title() {
                return tableInterface.titleName();
            }

            @Override
            public boolean resize() {
                return tableInterface.resizeTable();
            }

            @Override
            public double minHeight() {
                return pane.getMinHeight();
            }

            @Override
            public double minWidth() {
                return pane.getMinWidth();
            }
        };
        new OpenApplication<>(appSettingInterface);

    }

}
