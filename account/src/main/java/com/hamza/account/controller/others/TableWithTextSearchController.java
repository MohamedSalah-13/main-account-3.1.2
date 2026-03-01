package com.hamza.account.controller.others;

import com.hamza.account.controller.search.SearchInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import static com.hamza.controlsfx.table.TextSearch.searchTableFromExitedText;

@Log4j2
@FxmlPath(pathFile = "table-with-text.fxml")
@RequiredArgsConstructor
public class TableWithTextSearchController<T> {

    private final ObservableList<T> itemsModels = FXCollections.observableArrayList();
    private final ObjectProperty<T> selectedItem = new SimpleObjectProperty<>();
    private final SearchInterface<T> searchInterface;
    @FXML
    private TableView<T> tableView;
    @FXML
    private TextField txtSearch;
    @FXML
    private Label labelSearch;
    @FXML
    private StackPane stackPane;
    private MaskerPaneSetting maskerPaneSetting;

    @FXML
    public void initialize() {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        getTable();
        addOtherData();
    }

    private void getTable() {
        new TableColumnAnnotation().getTable(tableView, searchInterface.getSearchClass());
        tableView.setItems(itemsModels);
        if (searchInterface.selectMultiple())
            tableView.selectionModelProperty().get().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);

        TableSetting.tableMenuSetting(getClass(), tableView);
    }

    private void addOtherData() {
        Platform.runLater(() -> txtSearch.requestFocus());

        maskerPaneSetting.showMaskerPane(() -> {
            try {
                itemsModels.clear();
                itemsModels.setAll(searchInterface.searchItems());
            } catch (Exception e) {
                log.error(e.getMessage(), e.getCause());
                AllAlerts.alertError(e.getMessage());
            }
        });

        txtSearch.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.DOWN) {
                tableView.getSelectionModel().selectFirst();
                tableView.requestFocus();
            }
        });
        tableView.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent == null) {
                return;
            }
            if (mouseEvent.getClickCount() == 2) {
                selectedItem.set(tableView.getSelectionModel().getSelectedItem());
            }
        });

//        tableView.setOnKeyPressed(keyEvent -> {
//            if (keyEvent.getCode() == KeyCode.ENTER) {
//                selectedItem.set(tableView.getSelectionModel().getSelectedItem());
//            }
//        });
        tableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> selectedItem.set(newValue));
        labelSearch.setText(Setting_Language.WORD_SEARCH);
        txtSearch.setPromptText(Setting_Language.WORD_SEARCH);
        txtSearch.setOnKeyReleased(event -> searchTableFromExitedText(tableView, txtSearch.getText(), itemsModels));
    }

    public Object getSelectedItem() {
        return selectedItem.get();
    }

    public ObjectProperty<T> selectedItemProperty() {
        return selectedItem;
    }

}
