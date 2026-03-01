package com.hamza.controlsfx.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import lombok.RequiredArgsConstructor;

import java.io.InputStream;
import java.net.URL;
import java.util.ResourceBundle;

@RequiredArgsConstructor
public class BoxController implements Initializable {

    private final StringProperty textSumTotals = new SimpleStringProperty();
    private final String title;
    private final InputStream image;
    private final String color;
    @FXML
    private VBox box;
    @FXML
    private VBox box_image;
    @FXML
    private Text textName, textTotal;
    @FXML
    private ImageView imageView;


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        HBox node = (HBox) box.getChildren().getFirst();
        Separator separator = (Separator) box.getChildren().get(1);
        node.prefHeightProperty().bind(box.heightProperty().multiply(.7));

        box_image.pseudoClassStateChanged(PseudoClass.getPseudoClass(color), !color.isEmpty());
        separator.pseudoClassStateChanged(PseudoClass.getPseudoClass(color), !color.isEmpty());
        textName.pseudoClassStateChanged(PseudoClass.getPseudoClass(color), !color.isEmpty());
        textTotal.pseudoClassStateChanged(PseudoClass.getPseudoClass(color), !color.isEmpty());

        textName.setText(title);
        textTotal.textProperty().bind(textSumTotalsProperty());

        // add image
        if (image != null)
            imageView.setImage(new Image(image));
    }

    public String getTextSumTotals() {
        return textSumTotals.get();
    }

    public void setTextSumTotals(String textSumTotals) {
        this.textSumTotals.set(textSumTotals);
    }

    public StringProperty textSumTotalsProperty() {
        return textSumTotals;
    }
}
