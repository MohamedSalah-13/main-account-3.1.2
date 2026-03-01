package com.hamza.account.controller.invoice;

import com.hamza.account.openFxml.FxmlPath;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

@FxmlPath(pathFile = "include/add-box-to-invoice.fxml")
public class AddBoxToInvoiceController {
    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty name = new SimpleStringProperty();
    @FXML
    private Label labelTitle, labelName;

    @FXML
    private void initialize() {
        labelTitle.textProperty().bind(title);
        labelName.textProperty().bind(name);
    }

    public String getTitle() {
        return title.get();
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public StringProperty titleProperty() {
        return title;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public StringProperty nameProperty() {
        return name;
    }
}
