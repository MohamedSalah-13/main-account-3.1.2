package com.hamza.controlsfx.button.button_column;

import com.hamza.controlsfx.HelloApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.api.ButtonColumnI;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Log4j2
public class ButtonColumn<T> extends TableColumn<T, String> {

    public ButtonColumn(@NotNull ButtonColumnI buttonColumnI) {
        setText(buttonColumnI.columnTitle());
        setCellValueFactory(new PropertyValueFactory<>(buttonColumnI.columnName()));

        String externalForm = Objects.requireNonNull(HelloApplication.class.getResource("css/style.css")).toExternalForm();

        Callback<TableColumn<T, String>, TableCell<T, String>> cellFactory = new Callback<>() {
            @Override
            public TableCell<T, String> call(final TableColumn<T, String> param) {
                return new TableCell<>() {
                    final Button btn = new Button(buttonColumnI.textName(), buttonColumnI.imageNode());

                    @Override
                    public void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setGraphic(null);
                            setText(null);
                        } else {
                            btn.getStylesheets().add(externalForm);
                            btn.getStyleClass().addAll("button-other","app-neutral-button");
                            btn.setDisable(buttonColumnI.isButtonDisabled(getIndex()));
                            btn.setOnAction(event -> {
                                try {
                                    buttonColumnI.action(getIndex());
                                } catch (Exception e) {
                                    log.error(e.getMessage(), e.getCause());
                                    AllAlerts.alertError(e.getMessage());
                                }
                            });

                            setGraphic(btn);
                            setText(null);
                        }
                    }
                };
            }
        };

        setCellFactory(cellFactory);
    }
}
