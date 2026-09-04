package com.hamza.controlsfx.others;

import javafx.scene.control.Control;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
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
     * Lets the user type into an integer spinner instead of only clicking its arrows,
     * and keeps what they typed.
     * <p>
     * Two things have to happen together, which is why this is a helper rather than a
     * {@code setEditable(true)} at each call site. An editable JavaFX spinner does not
     * commit its editor to its value until Enter is pressed, so a typed number is
     * silently discarded the moment focus moves elsewhere - the classic way an editable
     * spinner is worse than a read-only one. And a spinner whose step is small next to
     * its range is unusable by arrows alone: a range of a week in five-minute steps is
     * two thousand clicks.
     * <p>
     * Text that is not a number in range is not accepted and not silently coerced: the
     * editor goes back to the value the spinner still holds, so what is shown is always
     * what is stored.
     *
     * @param spinners the spinners to make typable; each must carry an
     *                 {@link javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory}
     */
    @SafeVarargs
    public static void makeTypable(Spinner<Integer>... spinners) {
        for (Spinner<Integer> spinner : spinners) {
            spinner.setEditable(true);
            spinner.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
                if (!isFocused) {
                    commitEditor(spinner);
                }
            });
            spinner.getEditor().setOnAction(event -> commitEditor(spinner));
        }
    }

    private static void commitEditor(Spinner<Integer> spinner) {
        var factory = spinner.getValueFactory();
        if (factory == null) {
            return;
        }
        try {
            int typed = Integer.parseInt(spinner.getEditor().getText().trim());
            if (factory instanceof SpinnerValueFactory.IntegerSpinnerValueFactory bounds
                    && (typed < bounds.getMin() || typed > bounds.getMax())) {
                throw new NumberFormatException("out of range");
            }
            factory.setValue(typed);
        } catch (NumberFormatException ignored) {
            // Not a number, or outside the range. Show what is actually held.
            spinner.getEditor().setText(String.valueOf(factory.getValue()));
        }
    }

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
