package com.hamza.account.controller.setting;

import com.hamza.account.config.PropertiesName;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import lombok.extern.log4j.Log4j2;

import java.util.prefs.Preferences;

/**
 * Controller for the font and color customization dialog.
 */
@Log4j2
public class FontColorController {

    public static final String APPLICATION_CSS = "application-css";
    // Preferences key for storing settings
    public static final Preferences PREFS = Preferences.userNodeForPackage(FontColorController.class);
    private static final String PREF_FONT_FAMILY = "fontFamily";
    private static final String PREF_FONT_SIZE = "fontSize";
    private static final String PREF_TEXT_COLOR = "textColor";
    private static final String PREF_BACKGROUND_COLOR = "backgroundColor";
    private static final String PREF_BUTTON_COLOR = "buttonColor";
    private static final String PREF_TABLE_COLOR = "tableColor";
    private static final String PREF_TABLE_BOARD_COLOR = "tableBoardColor";
    private static final String PREF_TABLE_AQUA_COLOR = "tableAquaColor";
    // Default values
    private static final String DEFAULT_FONT_FAMILY = "System";
    private static final double DEFAULT_FONT_SIZE = 12.0;
    private static final Color DEFAULT_TEXT_COLOR = Color.BLACK;
    private static final Color DEFAULT_BACKGROUND_COLOR = Color.WHITE;
    private static final Color DEFAULT_BUTTON_COLOR = Color.LIGHTGRAY;
    private static final Color DEFAULT_TABLE_COLOR = Color.WHITE;
    private static final Color DEFAULT_TABLE_BOARD_COLOR = Color.LIGHTGRAY;
    private static final Color DEFAULT_TABLE_AQUA_COLOR = Color.AQUA;

    @FXML
    private ComboBox<String> fontFamilyComboBox;
    @FXML
    private Slider fontSizeSlider;
    @FXML
    private ColorPicker textColorPicker;
    @FXML
    private ColorPicker backgroundColorPicker;
    @FXML
    private ColorPicker buttonColorPicker;
    @FXML
    private ColorPicker tableColorPicker;
    @FXML
    private ColorPicker tableBoardColorPicker;
    @FXML
    private ColorPicker tableAquaColorPicker;
    @FXML
    private Label previewLabel;
    @FXML
    private Button applyButton;
    @FXML
    private TableView tableView;
    @FXML
    private CheckBox checkActive;
    @FXML
    private TabPane tabPane;

    @FXML
    public void initialize() {
        // Populate font families
        fontFamilyComboBox.getItems().addAll(Font.getFamilies());

        String savedFontFamily = PREFS.get(PREF_FONT_FAMILY, DEFAULT_FONT_FAMILY);
        double savedFontSize = PREFS.getDouble(PREF_FONT_SIZE, DEFAULT_FONT_SIZE);
        String savedTextColor = PREFS.get(PREF_TEXT_COLOR, colorToString(DEFAULT_TEXT_COLOR));
        String savedBackgroundColor = PREFS.get(PREF_BACKGROUND_COLOR, colorToString(DEFAULT_BACKGROUND_COLOR));
        String savedButtonColor = PREFS.get(PREF_BUTTON_COLOR, colorToString(DEFAULT_BUTTON_COLOR));
        String savedTableColor = PREFS.get(PREF_TABLE_COLOR, colorToString(DEFAULT_TABLE_COLOR));
        String savedTableBoardColor = PREFS.get(PREF_TABLE_BOARD_COLOR, colorToString(DEFAULT_TABLE_BOARD_COLOR));
        String savedTableAquaColor = PREFS.get(PREF_TABLE_AQUA_COLOR, colorToString(DEFAULT_TABLE_AQUA_COLOR));

        // Set initial values
        fontFamilyComboBox.setValue(savedFontFamily);
        fontSizeSlider.setValue(savedFontSize);
        textColorPicker.setValue(stringToColor(savedTextColor));
        backgroundColorPicker.setValue(stringToColor(savedBackgroundColor));
        buttonColorPicker.setValue(stringToColor(savedButtonColor));
        tableColorPicker.setValue(stringToColor(savedTableColor));
        tableBoardColorPicker.setValue(stringToColor(savedTableBoardColor));
        tableAquaColorPicker.setValue(stringToColor(savedTableAquaColor));

        // Set up listeners to update preview
        fontFamilyComboBox.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        fontSizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        textColorPicker.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        backgroundColorPicker.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        buttonColorPicker.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        tableColorPicker.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        tableBoardColorPicker.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        tableAquaColorPicker.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview());

        // Initial preview update
        updatePreview();

        checkActive.setSelected(PropertiesName.getFontColorActive());
        checkActive.selectedProperty().addListener((observableValue, aBoolean, t1) -> PropertiesName.setFontColorActive(t1));
        // disabled
        tabPane.disableProperty().bind(checkActive.selectedProperty().not());

    }

    /**
     * Updates the preview label with the current font and color settings.
     */
    private void updatePreview() {
        String fontFamily = fontFamilyComboBox.getValue();
        double fontSize = fontSizeSlider.getValue();
        Color textColor = textColorPicker.getValue();
        Color backgroundColor = backgroundColorPicker.getValue();
        Color buttonColor = buttonColorPicker.getValue();
        Color tableColor = tableColorPicker.getValue();
        Color tableBoardColor = tableBoardColorPicker.getValue();
        Color tableAquaColor = tableAquaColorPicker.getValue();

        // Update preview label font
        previewLabel.setFont(new Font(fontFamily, fontSize));

        // Update preview label colors
        previewLabel.setTextFill(textColor);
        previewLabel.setStyle(String.format("-fx-border-color: lightgray; -fx-border-radius: 5; -fx-background-color: %s; -fx-button-color: %s;",
                colorToCss(backgroundColor), colorToCss(buttonColor)));

        applyButton.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s;", colorToCss(buttonColor), colorToCss(textColor)));
        tableView.setStyle(String.format("-fx-background-color: %s; -fx-border-color: %s;", colorToCss(tableColor), colorToCss(tableBoardColor)));
    }

    /**
     * Applies the selected font and color settings.
     */
    public void applySettings() {
        try {
            // Save properties
            PREFS.put(PREF_FONT_FAMILY, fontFamilyComboBox.getValue());
            PREFS.put(PREF_FONT_SIZE, String.valueOf(fontSizeSlider.getValue()));
            PREFS.put(PREF_TEXT_COLOR, colorToString(textColorPicker.getValue()));
            PREFS.put(PREF_BACKGROUND_COLOR, colorToString(backgroundColorPicker.getValue()));
            PREFS.put(PREF_BUTTON_COLOR, colorToString(buttonColorPicker.getValue()));
            PREFS.put(PREF_TABLE_COLOR, colorToString(tableColorPicker.getValue()));
            PREFS.put(PREF_TABLE_BOARD_COLOR, colorToString(tableBoardColorPicker.getValue()));
            PREFS.put(PREF_TABLE_AQUA_COLOR, colorToString(tableAquaColorPicker.getValue()));
            // Apply settings to the application
            applyFontAndColorToApplication();

            log.info("Font and color settings applied successfully");
        } catch (Exception e) {
            log.error("Error applying font and color settings", e);
        }
    }

    /**
     * Applies the font and color settings to the application.
     */
    private void applyFontAndColorToApplication() {
        String fontFamily = fontFamilyComboBox.getValue();
        double fontSize = fontSizeSlider.getValue();
        Color textColor = textColorPicker.getValue();
        Color backgroundColor = backgroundColorPicker.getValue();
        Color buttonColor = buttonColorPicker.getValue();
        Color tableColor = tableColorPicker.getValue();
        Color tableBoardColor = tableBoardColorPicker.getValue();
        Color tableAquaColor = tableAquaColorPicker.getValue();

        // Create CSS style
        String fontStyle = String.format("-fx-font-family: '%s'; -fx-font-size: %.1fpx;",
                fontFamily, fontSize);
        String textColorStyle = String.format("-fx-text-fill: %s;", colorToCss(textColor));
        String textFillColorStyle = String.format("-fx-fill: %s;", colorToCss(textColor));
        String backgroundColorStyle = String.format("-fx-background-color: %s;", colorToCss(backgroundColor));
        String buttonColorStyle = String.format("-fx-button-color: %s;", colorToCss(buttonColor));
        String backgroundColorStyleRoot = String.format("-fx-root-background-color: %s;", colorToCss(backgroundColor));
        String tableColorStyle = String.format("-fx-background-color-table: %s;", colorToCss(tableColor));
        String tableBoardColorStyle = String.format("-fx-border-color-table: %s;", colorToCss(tableBoardColor));
        String tableAquaColorStyle = String.format("-fx-background-color-aquaRow: %s;", colorToCss(tableAquaColor));

        // Apply to root scene - this will be applied when the dialog is closed
        String css = String.format("* {%s %s %s %s %s} .root { %s %s %s %s %s}",
                buttonColorStyle, backgroundColorStyleRoot, tableColorStyle, tableBoardColorStyle, tableAquaColorStyle
                , fontStyle, textColorStyle, textFillColorStyle, backgroundColorStyle, buttonColorStyle);

        // Store in properties for application to use on startup
        PREFS.put(APPLICATION_CSS, css);
    }

    /**
     * Converts a Color to a CSS color string.
     */
    private String colorToCss(Color color) {
        return String.format("rgba(%d, %d, %d, %.2f)",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255),
                color.getOpacity());
    }

    /**
     * Converts a Color to a string representation for storage.
     */
    private String colorToString(Color color) {
        return String.format("%.6f,%.6f,%.6f,%.6f",
                color.getRed(), color.getGreen(), color.getBlue(), color.getOpacity());
    }

    /**
     * Converts a string representation to a Color.
     */
    private Color stringToColor(String colorStr) {
        try {
            String[] parts = colorStr.split(",");
            return new Color(
                    Double.parseDouble(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]));
        } catch (Exception e) {
            log.error("Error parsing color string: {}", colorStr, e);
            return DEFAULT_TEXT_COLOR;
        }
    }
}
