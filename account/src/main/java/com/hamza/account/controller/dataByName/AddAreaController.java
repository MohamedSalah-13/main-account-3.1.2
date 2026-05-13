package com.hamza.account.controller.dataByName;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.Disable;
import com.hamza.controlsfx.interfaceData.TableViewShowDataInt;
import com.hamza.controlsfx.interfaceData.ToolbarAccountInt;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;

import java.util.Comparator;
import java.util.List;

@Log4j2
public class AddAreaController<T> extends VBox {

    private final AreaInterface<T> areaInterface;
    private final ObservableList<T> areaObservableList;
    private final TextField textCode;
    private final TextField textName;

    public AddAreaController(AreaInterface<T> areaInterface) {
        this.areaInterface = areaInterface;
        areaObservableList = FXCollections.observableArrayList(getCollection(areaInterface));
        areaObservableList.sort(Comparator.comparingInt(areaInterface.getIdFunction()));

        textName = new TextField();
        textName.setPromptText(Setting_Language.WORD_NAME);
        textName.setPrefWidth(300);
        textCode = new TextField("0");
        textCode.setEditable(false);
        ColumnConstraints column1 = new ColumnConstraints();
        ColumnConstraints column2 = new ColumnConstraints();
        column2.setPrefWidth(300);
        column2.setMaxWidth(Region.USE_PREF_SIZE);


        Label labelCode = new Label(Setting_Language.WORD_CODE);
        Label labelName = new Label(Setting_Language.WORD_NAME);
        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.add(labelCode, 0, 0);
        gridPane.add(textCode, 1, 0);
        gridPane.add(labelName, 0, 1);
        gridPane.add(textName, 1, 1);
        gridPane.getColumnConstraints().addAll(column1, column2);
        gridPane.minWidth(Region.USE_PREF_SIZE);
        getChildren().addAll(gridPane);

//        this.setPadding(new Insets(10));
        this.getStyleClass().add("app-card");
    }

    private List<? extends T> getCollection(AreaInterface<T> areaInterface) {
        try {
            return areaInterface.listData();
        } catch (Exception e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
            return List.of();
        }
    }

    public TableViewShowDataInt<T> createAreaTableView() {
        return new TableViewShowDataInt<>() {
            @Override
            public List<T> dataList() {
                return areaObservableList;
            }

            @Override
            public Class<? super T> classForColumn() {
                return areaInterface.classData();
            }
        };

    }

    public ToolbarAccountInt<T> getToolbarAccountActionInterface() {
        return new ToolbarAccountInt<>() {
            @Override
            public void addNewAccount() {
                textCode.setText("0");
                textName.clear();
            }

            @Override
            public int deleteAccount() throws Exception {
                if (textCode.getText().isEmpty()) {
                    return 0;
                }
                var id = Integer.parseInt(textCode.getText());
                return areaInterface.deleteData(id);

            }

            @Disable
            @Override
            public void printAccount() {

            }

            @Override
            public T saveAccount() throws Exception {
                if (textCode.getText().isEmpty()) {
                    textCode.setText("0");
                }
                if (textName.getText().isEmpty()) {
                    throw new Exception("empty name");
                }

                var id = Integer.parseInt(textCode.getText());
                var object = areaInterface.object(id, textName.getText());
                var i = 0;
                if (id > 0) {
                    i = areaInterface.update(object);
                } else i = areaInterface.insert(object);
                return i > 0 ? object : null;
            }

            @Override
            public void firstPage(T t) {
                setArea(t);
            }

            @Override
            public void previousPage(T t) {
                updateAreaBasedOnCode(t);
            }

            @Override
            public void nextPage(T t) {
                updateAreaBasedOnCode(t);
            }

            @Override
            public void lastPage(T t) {
                setArea(t);
            }

            @Override
            public ObservableList<T> observableList() {
                return areaObservableList;
            }

            @Override
            public void afterSaveOrDelete() {
                addNewAccount();
                textCode.setText("0");
                textName.clear();
                areaObservableList.clear();
                areaObservableList.addAll(getCollection(areaInterface));
                areaObservableList.sort(Comparator.comparingInt(areaInterface.getIdFunction()));
                log.info("Area Saved");
            }

            @Override
            public Publisher<String> publisherTable() {
                return areaInterface.publisherTable();
            }
        };
    }

    private void updateAreaBasedOnCode(T t) {
        if (textCode.getText().isEmpty()) {
            textCode.setText("0");
        }
        setArea(t);
    }

    private void setArea(T t) {
        if (t == null) {
            textCode.setText("0");
            textName.clear();
            return;
        }
        textCode.setText(String.valueOf(areaInterface.getId(t)));
        textName.setText(areaInterface.getName(t));
    }
}
