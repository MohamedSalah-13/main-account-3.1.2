package com.hamza.controlsfx.button.button_column;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.api.ButtonColumnBoolean;
import com.jfoenix.controls.JFXToggleButton;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class Button_Toggle_Table<T> extends TableColumn<T, String> {

    public Button_Toggle_Table(ButtonColumnBoolean buttonColumnI) {
        setText(buttonColumnI.columnTitle());
        setCellValueFactory(new PropertyValueFactory<>(buttonColumnI.columnName()));

        Callback<TableColumn<T, String>, TableCell<T, String>> cellFactory = new Callback<>() {
            @Override
            public TableCell<T, String> call(final TableColumn<T, String> param) {
                return new TableCell<>() {
                    private final JFXToggleButton btn = new JFXToggleButton();

                    @Override
                    public void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setGraphic(null);
                            setText(null);
                        } else {
                            btn.setSelected(buttonColumnI.selectButton(getIndex()));
                            btn.setDisable(buttonColumnI.isButtonDisabled(getIndex()));
                            btn.setOnAction(event -> {
                                try {
                                    buttonColumnI.action(getIndex(), btn.selectedProperty().getValue());
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
