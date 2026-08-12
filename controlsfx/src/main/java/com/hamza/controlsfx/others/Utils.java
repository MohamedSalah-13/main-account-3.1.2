package com.hamza.controlsfx.others;

import javafx.scene.control.Control;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;

public class Utils {

    /**
     * Regular expression pattern used to match any character that is not a digit.
     * This can be used in conjunction with String methods like replaceAll to filter out non-digit characters from a string.
     */
    private static final String NON_DIGIT_REGEX = "\\D";

    /**
     * Sets a TextFormatter for each provided TextField in order to enforce a specific formatting.
     * The TextFormatter uses a default Double converter and a predefined filter.
     *
     * @param textFields one or more TextField objects to which the formatter will be applied
     */
    public static void setTextFormatter(TextField... textFields) {
        for (TextField textField : textFields) {
            textField.setTextFormatter(new TextFormatter<>(TextFormat.doubleStringConverter, 0.0, TextFormat.TEXT_FORMATTER_FILTER));
        }
    }

    /**
     * Clears the content of all provided TextField instances.
     *
     * @param textFields one or more TextField instances to be cleared
     */
    public static void clearAll(TextField... textFields) {
        for (TextField textField : textFields) {
            textField.clear();
        }
    }

    /**
     * Sets up key press event handlers on the given controls to move focus to the next control when Enter key is pressed.
     * Focus will shift sequentially from each control to the next.
     *
     * @param controls Varargs parameter representing the sequence of controls where Enter key press should move focus to the next control.
     */
    public static void whenEnterPressed(Control... controls) {
        for (int i = 0; i < controls.length - 1; i++) {
            setUpEnterKeyRequestFocus(controls, i);
        }
    }

    /**
     * Sets up an event handler for the specified control to request focus on the next control
     * when the Enter key is pressed.
     *
     * @param controls An array of Control objects that will be configured with the Enter key handler.
     * @param index    The index of the current control in the array to set the handler on.
     */
    private static void setUpEnterKeyRequestFocus(Control[] controls, int index) {
        controls[index].setOnKeyPressed(event -> {
            if (event.getCode().equals(KeyCode.ENTER)) {
                controls[index + 1].requestFocus();
            }
        });
    }

    /**
     * Replaces all non-digit characters in the text of the given TextField with an empty string.
     * This method sets up a listener on the textProperty of the TextField to automatically
     * remove any characters that are not digits whenever the text changes.
     *
     * @param textField the TextField to apply the non-digit character replacement to
     */
    public static void replaceNonDigitChar(TextField textField) {
        textField.textProperty().addListener((observableValue, oldText, newText) -> {
            if (!newText.matches("\\d*")) {
                textField.setText(newText.replaceAll(NON_DIGIT_REGEX, ""));
            }
        });
    }

}
