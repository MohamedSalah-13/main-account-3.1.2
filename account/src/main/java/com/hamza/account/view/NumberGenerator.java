package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.reportData.Print_Reports;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.*;

import static com.hamza.account.config.PropertiesName.getNumberGeneratorLastNumber;
import static com.hamza.account.config.PropertiesName.setNumberGeneratorLastNumber;
import static com.hamza.controlsfx.others.TextFormat.createNumericTextFormatter;

public class NumberGenerator extends Application {

    private TextField startField;
    private TextField endField;
    private TextField lastGeneratedField;
    private Label currentNumberLabel;
    private int currentNumber;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("مولد الأرقام - Number Generator");
        primaryStage.setResizable(false);
        primaryStage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().setting));

        // إنشاء الحقول والنصوص
        Label lastGeneratedLabel = new Label("آخر رقم تم توليده:");
        Label startLabel = new Label("بداية الأرقام:");
        Label endLabel = new Label("نهاية الأرقام:");
        Label currentLabel = new Label("الرقم الحالي:");

        lastGeneratedField = new TextField();
//        lastGeneratedField.setEditable(false); // غير قابل للتعديل
        startField = new TextField();
        endField = new TextField();

        lastGeneratedField.setText(String.valueOf(getNumberGeneratorLastNumber()));
        lastGeneratedField.setTextFormatter(createNumericTextFormatter());
        startField.setTextFormatter(createNumericTextFormatter());
        endField.setTextFormatter(createNumericTextFormatter());

        lastGeneratedField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.isEmpty()) {
                setNumberGeneratorLastNumber(Integer.parseInt(newValue));
            }
        });

        endField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.isEmpty()) {
                setNumberGeneratorLastNumber(Integer.parseInt(newValue));
            }
        });


        currentNumberLabel = new Label("0");
        currentNumberLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // أزرار التحكم
        Button generateButton = new Button("توليد رقم");
        Button resetButton = new Button("إعادة تعيين");
        Button setLastButton = new Button("تعيين كآخر رقم");

        // إضافة الأحداث للأزرار
        generateButton.setOnAction(e -> generateNumber());
        resetButton.setOnAction(e -> resetGenerator());
//        setLastButton.setOnAction(e -> setLastGenerated());

        VBox box = new VBox(20);
        // تخطيط الشبكة
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setVgap(15);
        grid.setHgap(15);

        // إضافة العناصر إلى الشبكة
        grid.add(lastGeneratedLabel, 0, 0);
        grid.add(lastGeneratedField, 1, 0);

        grid.add(startLabel, 0, 1);
        grid.add(startField, 1, 1);

        grid.add(endLabel, 0, 2);
        grid.add(endField, 1, 2);

        grid.add(currentLabel, 0, 3);
        grid.add(currentNumberLabel, 1, 3);

        VBox.setVgrow(grid, Priority.SOMETIMES);
//        grid.add(generateButton, 0, 4);
//        grid.add(resetButton, 1, 4);
//        grid.add(setLastButton, 0, 5, 2, 1);

        // جعل زر تعيين آخر رقم يأخذ عرض العمودين
//        setLastButton.setMaxWidth(Double.MAX_VALUE);

        HBox hBox = new HBox(10);
        hBox.setSpacing(5);
        hBox.setPadding(new Insets(10));
        hBox.setAlignment(Pos.CENTER_RIGHT);
        hBox.getStyleClass().add("pane-box-last");
        hBox.getChildren().addAll(generateButton, resetButton);
        box.getChildren().addAll(grid, hBox);
        // إنشاء المشهد وعرضه
        Scene scene = new SceneAll(box);
        primaryStage.setScene(scene);
        primaryStage.setWidth(400);
        primaryStage.setHeight(350);
        Toolkit.getDefaultToolkit().beep();
        primaryStage.show();
    }

    private void generateNumber() {
        try {
            int start = Integer.parseInt(startField.getText());
            int end = Integer.parseInt(endField.getText());

            if (start > end) {
                showAlert("خطأ", "رقم البداية يجب أن يكون أصغر من أو يساوي رقم النهاية");
                return;
            }

            // إذا كان هناك آخر رقم مخزن، نبدأ من الرقم التالي له
//            if (!lastGeneratedField.getText().isEmpty()) {
//                int lastGenerated = Integer.parseInt(lastGeneratedField.getText());
//                if (lastGenerated >= start && lastGenerated < end) {
//                    currentNumber = lastGenerated + 1;
//                } else {
//                    currentNumber = start;
//                }
//            } else {
//                currentNumber = start;
//            }

            // التأكد من أن الرقم الحالي ضمن النطاق
//            if (currentNumber > end) {
//                showAlert("انتهى النطاق", "تم الوصول إلى نهاية نطاق الأرقام");
//                currentNumber = start;
//            }

            var printReports = new Print_Reports();
            for (int i = start; i <= end; i++) {
//                currentNumber++;
                Thread.sleep(1000);
//                currentNumberLabel.setText(String.valueOf(i));
//                System.out.println(i);
                printReports.printReceiptNumberGenerate(i);
            }

        } catch (NumberFormatException | InterruptedException e) {
            showAlert("خطأ في الإدخال", "يرجى إدخال أرقام صحيحة في حقلي البداية والنهاية");
        }
    }

    private void resetGenerator() {
        startField.clear();
        endField.clear();
//        lastGeneratedField.clear();
        currentNumberLabel.setText("0");
        currentNumber = 0;
    }

    private void setLastGenerated() {
        if (!currentNumberLabel.getText().equals("0")) {
            lastGeneratedField.setText(currentNumberLabel.getText());
        } else {
            showAlert("لا يوجد رقم", "لا يوجد رقم حالياً لتعيينه كآخر رقم");
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        Toolkit.getDefaultToolkit().beep();
        alert.showAndWait();
    }
}