package com.hamza.account.controller.others;

import com.hamza.account.controller.search.SearchInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.view.TableWithTextSearchApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.beans.property.*;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.Optional;

@Log4j2
@FxmlPath(pathFile = "others/text-search.fxml")
public class TextSearchController<T> {
    private final SearchInterface<T> searchInterface;
    private final StringProperty textName = new SimpleStringProperty();
    private final ObjectProperty<T> itemSearchProperty = new SimpleObjectProperty<>();
    private final BooleanProperty disableButton = new SimpleBooleanProperty();
    @FXML
    private Button btnSearchText;
    @FXML
    private TextField textSearch;

    public TextSearchController(SearchInterface<T> searchInterface) {
        this.searchInterface = searchInterface;
    }

    @FXML
    public void initialize() {
        textSearch.setPromptText(Setting_Language.WORD_NAME);
        textSearch.textProperty().bindBidirectional(textName);
        textSearch.setEditable(false);
        btnSearchText.setTooltip(new Tooltip(Setting_Language.WORD_SEARCH));
        btnSearchText.disableProperty().bind(disableButton);

        btnSearchText.setOnAction(actionEvent -> {
            try {
                TableWithTextSearchApplication<T> tableWithTextSearchApplication = new TableWithTextSearchApplication<>(searchInterface);
                Optional<T> customers = tableWithTextSearchApplication.showAndWait();
                customers.ifPresent(itemsModel -> {
                    itemSearchProperty.set(itemsModel);
                    textSearch.setText(searchInterface.getName(itemsModel));
                });
            } catch (IOException e) {
                logError(e);
            }
        });

    }

    private void logError(Exception e) {
        AllAlerts.alertError(e.getMessage());
        log.error(e.getMessage(), e.getCause());
    }

    public String getTextName() {
        return textName.get();
    }

    public void setTextName(String textName) {
        this.textName.set(textName);
    }

    public StringProperty textNameProperty() {
        return textName;
    }

    public boolean isDisableButton() {
        return disableButton.get();
    }

    public BooleanProperty disableButtonProperty() {
        return disableButton;
    }

    public Object getItemSearchProperty() {
        return itemSearchProperty.get();
    }

    public ObjectProperty<T> itemSearchPropertyProperty() {
        return itemSearchProperty;
    }

}
