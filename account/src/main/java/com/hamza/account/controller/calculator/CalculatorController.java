package com.hamza.account.controller.calculator;

import com.hamza.account.openFxml.FxmlPath;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FxmlPath(pathFile = "calculator/calculator-view.fxml")
public class CalculatorController {

    @FXML
    private TextField display;

    private String currentInput = "";
    private double firstOperand = 0;
    private String operator = "";
    private boolean startNewInput = true;

    @FXML
    public void initialize() {
        display.setText("0");
    }

    @FXML
    public void handleDigitAction(MouseEvent event) {
        if (startNewInput) {
            display.setText("");
            startNewInput = false;
        }

        Button button = (Button) event.getSource();
        String digit = button.getText();
        currentInput += digit;
        display.setText(currentInput);
    }

    @FXML
    public void handleOperatorAction(MouseEvent event) {
        Button button = (Button) event.getSource();

        if (!operator.isEmpty()) {
            calculateResult();
        }

        firstOperand = Double.parseDouble(display.getText());
        operator = button.getText();
        startNewInput = true;
        currentInput = "";
    }

    @FXML
    public void handleEqualsAction() {
        if (operator.isEmpty()) {
            return;
        }

        calculateResult();
        operator = "";
        startNewInput = true;
        currentInput = "";
    }

    @FXML
    public void handleClearAction() {
        display.setText("0");
        currentInput = "";
        firstOperand = 0;
        operator = "";
        startNewInput = true;
    }

    @FXML
    public void handleDecimalAction() {
        if (startNewInput) {
            display.setText("0");
            currentInput = "0";
            startNewInput = false;
        }

        if (!currentInput.contains(".")) {
            currentInput += ".";
            display.setText(currentInput);
        }
    }

    private void calculateResult() {
        if (currentInput.isEmpty()) {
            return;
        }

        double secondOperand = Double.parseDouble(currentInput);
        double result = 0;

        switch (operator) {
            case "+":
                result = firstOperand + secondOperand;
                break;
            case "-":
                result = firstOperand - secondOperand;
                break;
            case "*":
                result = firstOperand * secondOperand;
                break;
            case "/":
                if (secondOperand != 0) {
                    result = firstOperand / secondOperand;
                } else {
                    display.setText("Error");
                    return;
                }
                break;
        }

        display.setText(formatResult(result));
        firstOperand = result;
    }

    private String formatResult(double result) {
        // Remove trailing zeros for integer results
        if (result == (long) result) {
            return String.format("%d", (long) result);
        } else {
            return String.format("%s", result);
        }
    }
}