package com.hamza.account.view;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class NoteText extends Application {

    private final TextArea txtNotes;

    public NoteText(TextArea txtNotes) {
        this.txtNotes = txtNotes;
    }

    @Override
    public void start(Stage stage) throws Exception {
        TextArea notesEditor = new TextArea(txtNotes.getText());
        notesEditor.setPrefRowCount(10);
        notesEditor.setPrefColumnCount(40);
        notesEditor.setWrapText(true);
        notesEditor.textProperty().bindBidirectional(txtNotes.textProperty());

        ComboBox<String> fontSize = new ComboBox<>();
        fontSize.getItems().addAll("8", "10", "12", "14", "16", "18", "20");
        fontSize.setValue("12");
        fontSize.setMaxWidth(100);

        ColorPicker textColor = new ColorPicker();

        ToggleGroup alignment = new ToggleGroup();
        ToggleButton leftAlign = new ToggleButton("⫷");
        ToggleButton centerAlign = new ToggleButton("☰");
        ToggleButton rightAlign = new ToggleButton("⫸");
        leftAlign.setToggleGroup(alignment);
        centerAlign.setToggleGroup(alignment);
        rightAlign.setToggleGroup(alignment);

        HBox controls = new HBox(5);
        controls.setPadding(new Insets(5));
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.getChildren().addAll(
                new Label("حجم الخط"), fontSize,
                new Label("لون الخط"), textColor,
                new Label("محاذاة"), leftAlign, centerAlign, rightAlign
        );


        textColor.valueProperty().addListener((observableValue, color, t1) -> {
//            txtNotes.setStyle("-fx-text-fill: " + t1);
            notesEditor.setStyle(String.format(notesEditor.getStyle() + "; -fx-text-fill: %s ;", colorToCss(t1)));
        });

        fontSize.valueProperty().addListener((observableValue, s, t1) ->
                notesEditor.setStyle(notesEditor.getStyle() + "; -fx-font-size: " + t1));

        leftAlign.selectedProperty().addListener((observableValue, aBoolean, t1) ->
                notesEditor.setStyle(notesEditor.getStyle() + "; -fx-text-alignment: left"));
        centerAlign.selectedProperty().addListener((observableValue, aBoolean, t1) ->
                notesEditor.setStyle(notesEditor.getStyle() + "; -fx-text-alignment: center"));
        rightAlign.selectedProperty().addListener((observableValue, aBoolean, t1) ->
                notesEditor.setStyle(notesEditor.getStyle() + "; -fx-text-alignment: right"));

        VBox root = new VBox(10);
        root.getChildren().addAll(controls, notesEditor);
        VBox.setVgrow(notesEditor, Priority.SOMETIMES);
        stage.setTitle("Notes Editor");
        stage.setScene(new SceneAll(root));
        stage.setResizable(false);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.showAndWait();
//        txtNotes.setText(notesEditor.getText());
    }

    private String colorToCss(Color color) {
        return String.format("rgba(%d, %d, %d, %.2f)",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255),
                color.getOpacity());
    }
}
