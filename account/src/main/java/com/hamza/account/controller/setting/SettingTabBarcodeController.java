package com.hamza.account.controller.setting;

import com.hamza.account.config.PropertiesName;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.barcodeprint.BarcodeNameOverflow;
import com.hamza.account.config.DefaultStock;
import com.hamza.account.features.scalebarcode.ScaleBarcodeService;
import com.hamza.account.features.scalebarcode.ScaleBarcodeValueType;
import com.hamza.account.features.checkbox.api.CheckBox_Setting;
import com.hamza.account.features.checkbox.impl.setting.BarcodePrintDoubleLabel;
import com.hamza.account.features.checkbox.impl.setting.BarcodePrintName;
import com.hamza.account.features.checkbox.impl.setting.BarcodePrintPrice;
import com.hamza.account.features.checkbox.impl.setting.CheckPrintBarcode;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.SupGroupService;
import com.hamza.account.service.UnitsService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.account.service.ItemsService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.shape.Rectangle;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.ResourceBundle;

import static com.hamza.account.config.PropertiesName.*;
import static com.hamza.account.controller.setting.ComboSetting.comboSubSetting;
import static com.hamza.account.controller.setting.ComboSetting.comboTypeSetting;

@Log4j2
@FxmlPath(pathFile = "include/settingTabBarcode.fxml")
@RequiredArgsConstructor
public class SettingTabBarcodeController implements Initializable {

    /** What a barcode label can measure, in millimetres. Below the first no printer feeds it. */
    private static final double LABEL_MIN_MM = 10;
    private static final double LABEL_MAX_MM = 300;
    /** One daemon thread for the try box's item lookup; the tab may be reopened many times. */
    private static final java.util.concurrent.ExecutorService TEST_READER =
            java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "scale-barcode-test");
                thread.setDaemon(true);
                return thread;
            });

    private final BarcodePrintPrice barcodePrintPrice = new BarcodePrintPrice();
    private final CheckPrintBarcode checkPrintBarcode = new CheckPrintBarcode();
    private final BarcodePrintDoubleLabel barcodePrintDoubleLabel = new BarcodePrintDoubleLabel();
    private final BarcodePrintName barcodePrintName = new BarcodePrintName();

    private final DaoFactory daoFactory;
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    @FXML
    private CheckBox show2, showName, showPrice, showBarcode, checkActivateBarcodeScale, checkHasCheckDigit,
            checkValidateCheckDigit;
    @FXML
    private ComboBox<String> comboMain, comboSub, comboType, comboNameOverflow, comboScaleValueType;
    @FXML
    private Label labelMain, labelSub, labelType, previewName, previewBarcode, previewDetails, previewSize;
    @FXML
    private Label labelComposition, labelValueDigits, labelCompositionProblem, labelTestResult;
    @FXML
    private TextField textMinWeight, textMaxWeight, textTestBarcode;
    @FXML
    private Rectangle previewFrame;
    @FXML
    private TextField textBarcodeStart, textCountScale, textCountBarcode, textCountItem;
    @FXML
    private TextField textNameMaxCharacters, textNameFontSize, textLabelWidthMm, textLabelHeightMm;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        otherSetting();
        action();
        comboSetting(daoFactory);
        barcodeScaleSetting();
        barcodeLabelSetting();
        configureLabelPreview();
    }

    /**
     * The try box stays usable whether or not the scale reader is switched on: its whole
     * purpose is working out the right layout, which is what someone does <em>before</em>
     * turning the feature on. The fields that only mean something once it is on stay
     * gated.
     */
    private void otherSetting() {
        labelMain.setText(LanguageManager.getInstance().getString("mainGroup"));
        labelSub.setText(LanguageManager.getInstance().getString("subGroup"));
        labelType.setText(LanguageManager.getInstance().getString("settings.barcode.units"));

        textBarcodeStart.disableProperty().bind(checkActivateBarcodeScale.selectedProperty().not());
        textCountScale.disableProperty().bind(checkActivateBarcodeScale.selectedProperty().not());
        textCountBarcode.disableProperty().bind(checkActivateBarcodeScale.selectedProperty().not());
        textCountItem.disableProperty().bind(checkActivateBarcodeScale.selectedProperty().not());
        checkHasCheckDigit.disableProperty().bind(checkActivateBarcodeScale.selectedProperty().not());
        checkValidateCheckDigit.disableProperty().bind(checkActivateBarcodeScale.selectedProperty().not());
        textMinWeight.disableProperty().bind(checkActivateBarcodeScale.selectedProperty().not());
        textMaxWeight.disableProperty().bind(checkActivateBarcodeScale.selectedProperty().not());
    }

    private void action() {
        new CheckBox_Setting(show2, barcodePrintDoubleLabel);
        new CheckBox_Setting(showName, barcodePrintName);
        new CheckBox_Setting(showPrice, barcodePrintPrice);
        new CheckBox_Setting(showBarcode, checkPrintBarcode);
    }


    private void comboSetting(DaoFactory daoFactory) {
        SupGroupService supGroupService = new SupGroupService(daoFactory);
        UnitsService unitsService = new UnitsService(daoFactory);
        List<String> unitsModelNames = getUnitsModelNames(unitsService);
        comboSub.setItems(FXCollections.observableArrayList(getSubGroupsNames(supGroupService)));
        comboType.setItems(FXCollections.observableArrayList(unitsModelNames));

        comboSubSetting(comboSub, supGroupService, true, comboMain);
        comboTypeSetting(comboType, unitsService, true);
    }


    private List<String> getUnitsModelNames(UnitsService unitsService) {
        try {
            return unitsService.getUnitsModelNames();
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
            return List.of();
        }
    }

    private List<String> getSubGroupsNames(SupGroupService supGroupService) {
        try {
            return supGroupService.getSubGroupsNames();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return List.of();
        }
    }

    private void configureLabelPreview() {
        showName.selectedProperty().addListener((observable, oldValue, value) -> updateLabelPreview());
        showPrice.selectedProperty().addListener((observable, oldValue, value) -> updateLabelPreview());
        showBarcode.selectedProperty().addListener((observable, oldValue, value) -> updateLabelPreview());
        textNameMaxCharacters.textProperty().addListener((observable, oldValue, value) -> updateLabelPreview());
        textNameFontSize.textProperty().addListener((observable, oldValue, value) -> updateLabelPreview());
        comboNameOverflow.valueProperty().addListener((observable, oldValue, value) -> updateLabelPreview());
        textLabelWidthMm.textProperty().addListener((observable, oldValue, value) -> updateLabelPreview());
        textLabelHeightMm.textProperty().addListener((observable, oldValue, value) -> updateLabelPreview());
        updateLabelPreview();
    }

    private void updateLabelPreview() {
        if (previewName == null || textNameMaxCharacters == null || comboNameOverflow == null) return;
        int limit = readPreviewInteger(textNameMaxCharacters, 28);
        int fontSize = readPreviewInteger(textNameFontSize, 7);
        int index = Math.max(0, comboNameOverflow.getSelectionModel().getSelectedIndex());
        var rendered = com.hamza.account.features.barcodeprint.BarcodeLabelText.renderName(
                LanguageManager.getInstance().getString("settings.barcode.previewSampleName"),
                BarcodeNameOverflow.values()[index], limit, fontSize);
        previewName.setText(showName.isSelected() && rendered.visible() ? rendered.value() : "");
        previewName.setStyle("-fx-font-size: " + rendered.fontSize() + "px;");
        previewFrame.setWidth(Math.min(260, Math.max(70, getBarcodeLabelWidthMm() * 4)));
        previewFrame.setHeight(Math.min(145, Math.max(45, getBarcodeLabelHeightMm() * 4)));
        previewBarcode.setText("||| ||| ||| ||| |||");
        previewSize.setText(String.format(LanguageManager.getInstance().getString("settings.barcode.preview.size"), getBarcodeLabelWidthMm(), getBarcodeLabelHeightMm()));
        previewDetails.setText((showBarcode.isSelected() ? "1234567890123" : "")
                + (showBarcode.isSelected() && showPrice.isSelected() ? " - " : "")
                + (showPrice.isSelected() ? "125.00" : ""));
    }

    private int readPreviewInteger(TextField field, int fallback) {
        try { return Integer.parseInt(field.getText()); } catch (NumberFormatException ignored) { return fallback; }
    }

    private void barcodeLabelSetting() {
        var language = LanguageManager.getInstance();
        comboNameOverflow.setItems(FXCollections.observableArrayList(
                language.getString("settings.barcode.nameOverflow.ellipsis"),
                language.getString("settings.barcode.nameOverflow.shrink"),
                language.getString("settings.barcode.nameOverflow.hide")));
        comboNameOverflow.getSelectionModel().select(switch (BarcodeNameOverflow.fromSetting(getBarcodeLabelNameOverflow())) {
            case ELLIPSIS -> 0;
            case SHRINK -> 1;
            case HIDE -> 2;
        });
        comboNameOverflow.valueProperty().addListener((observable, oldValue, value) -> {
            int index = comboNameOverflow.getSelectionModel().getSelectedIndex();
            if (index >= 0) setBarcodeLabelNameOverflow(BarcodeNameOverflow.values()[index].name());
        });
        setPositiveInteger(textNameMaxCharacters, getBarcodeLabelNameMaxCharacters(), 1, 200,
                PropertiesName::setBarcodeLabelNameMaxCharacters);
        setPositiveInteger(textNameFontSize, getBarcodeLabelNameFontSize(), 4, 30,
                PropertiesName::setBarcodeLabelNameFontSize);
        // The label's size on paper. Both fields were on screen and wired to nothing:
        // setPositiveDecimal existed and was never called, so they opened empty, saved
        // nothing, and the size stayed at whatever the defaults were - while
        // Print_Reports hands it to BarcodeLabelLayout on every barcode printed.
        setPositiveDecimal(textLabelWidthMm, getBarcodeLabelWidthMm(), LABEL_MIN_MM, LABEL_MAX_MM,
                PropertiesName::setBarcodeLabelWidthMm);
        setPositiveDecimal(textLabelHeightMm, getBarcodeLabelHeightMm(), LABEL_MIN_MM, LABEL_MAX_MM,
                PropertiesName::setBarcodeLabelHeightMm);
    }

    private void setPositiveDecimal(TextField field, double value, double minimum, double maximum,
                                    java.util.function.DoubleConsumer saver) {
        field.setText(String.valueOf(value));
        field.textProperty().addListener((observable, oldValue, text) -> {
            try {
                double parsed = Double.parseDouble(text);
                if (parsed >= minimum && parsed <= maximum) {
                    saver.accept(parsed);
                    updateLabelPreview();
                }
            } catch (NumberFormatException ignored) {
                // Half-typed input. The saved value stands until the field reads as a number.
            }
        });
    }

    private void setPositiveInteger(TextField field, int value, int minimum, int maximum,
                                    java.util.function.IntConsumer saver) {
        field.setText(String.valueOf(value));
        field.textProperty().addListener((observable, oldValue, text) -> {
            if (text.matches("\\d+")) {
                int parsed = Integer.parseInt(text);
                if (parsed >= minimum && parsed <= maximum) saver.accept(parsed);
            }
        });
    }

    private void barcodeScaleSetting() {

        checkActivateBarcodeScale.setSelected(getSettingBarcodeScaleActive());
        checkActivateBarcodeScale.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            setSettingBarcodeScaleActive(t1);
        });

        setTextBarcodeData(textBarcodeStart, getSettingBarcodeStart());
        setTextBarcodeData(textCountScale, getSettingBarcodeScaleCodeDigits());
        setTextBarcodeData(textCountBarcode, getSettingBarcodeLength());
        setTextBarcodeData(textCountItem, getSettingBarcodeCountItem());
        comboScaleValueType.setItems(FXCollections.observableArrayList(
                LanguageManager.getInstance().getString("settings.barcode.valueType.weight"),
                LanguageManager.getInstance().getString("settings.barcode.valueType.totalPrice")));
        comboScaleValueType.getSelectionModel().select(ScaleBarcodeValueType.valueOf(getSettingBarcodeValueType()).ordinal());
        comboScaleValueType.valueProperty().addListener((observable, oldValue, value) ->
                setSettingBarcodeValueType(ScaleBarcodeValueType.values()[comboScaleValueType.getSelectionModel().getSelectedIndex()].name()));

        checkHasCheckDigit.setSelected(getSettingBarcodeHasCheckDigit());
        checkHasCheckDigit.selectedProperty().addListener((observable, oldValue, value) -> {
            setSettingBarcodeHasCheckDigit(value);
            updateComposition();
        });

        // Both of these were read by the reader and had nowhere to be set: an operator
        // whose weight was refused as out of range could not see the range, let alone
        // change it.
        checkValidateCheckDigit.setSelected(getSettingBarcodeValidateCheckDigit());
        checkValidateCheckDigit.selectedProperty().addListener((observable, oldValue, value) -> {
            setSettingBarcodeValidateCheckDigit(value);
            runTestBarcode();
        });
        setWeightLimit(textMinWeight, getSettingBarcodeMinWeight(), PropertiesName::setSettingBarcodeMinWeight);
        setWeightLimit(textMaxWeight, getSettingBarcodeMaxWeight(), PropertiesName::setSettingBarcodeMaxWeight);

        textTestBarcode.textProperty().addListener((observable, oldValue, value) -> runTestBarcode());
        updateComposition();
    }

    private void setWeightLimit(TextField field, double value, java.util.function.DoubleConsumer saver) {
        field.setText(String.valueOf(value));
        field.textProperty().addListener((observable, oldValue, text) -> {
            try {
                double parsed = Double.parseDouble(text);
                if (parsed > 0) {
                    saver.accept(parsed);
                    runTestBarcode();
                }
            } catch (NumberFormatException ignored) {
                // Half-typed. The stored limit stands until the field reads as a number.
            }
        });
    }

    /**
     * Reads the barcode in the try box with the settings as they stand, and says what it
     * came to - or why it could not.
     * <p>
     * The four numbers above describe a layout whose effect is otherwise invisible until
     * someone scans something at a till, which is how a mislabelled field survived for as
     * long as it did. This is the same reader the invoice screens use, so what it says
     * here is what they will do.
     * <p>
     * The item lookup goes to the database, so it runs off the JavaFX thread; a result
     * for a barcode that has since been edited is dropped rather than shown.
     */
    private void runTestBarcode() {
        String barcode = textTestBarcode.getText() == null ? "" : textTestBarcode.getText().trim();
        if (barcode.isEmpty()) {
            labelTestResult.setText("");
            return;
        }
        var valueType = ScaleBarcodeValueType.valueOf(getSettingBarcodeValueType());
        var service = new ScaleBarcodeService(new ItemsService(daoFactory));
        TEST_READER.execute(() -> {
            String message;
            try {
                var reading = service.read(barcode, DefaultStock.ID, valueType);
                message = LanguageManager.getInstance().getString("settings.barcode.test.result",
                        reading.item().getNameItem(), reading.quantity(), reading.total());
            } catch (DaoException e) {
                message = e.getMessage();
            }
            String result = message;
            Platform.runLater(() -> {
                if (!barcode.equals(textTestBarcode.getText() == null ? "" : textTestBarcode.getText().trim())) return;
                labelTestResult.setText(result);
            });
        });
    }

    /**
     * Draws the layout the four numbers add up to, and says when they do not.
     * <p>
     * The four fields are the whole reason this tab was easy to misconfigure: they are
     * abstract counts whose effect only shows the next time a scale barcode is scanned,
     * and one of them was labelled as the weight while the parser read it as the scale's
     * prefix. Showing the composition turns each keystroke into something the operator
     * can check against the barcode printed in front of them.
     */
    private void updateComposition() {
        if (labelComposition == null) return;
        var format = ScaleBarcodeService.storedFormat();
        var language = LanguageManager.getInstance();

        labelComposition.setText(language.getString("settings.barcode.composition",
                format.prefixText(),
                "0".repeat(Math.max(1, format.itemDigits())),
                "0".repeat(Math.max(1, format.valueDigits())),
                format.hasCheckDigit() ? " | 0" : "",
                format.totalLength()));
        labelValueDigits.setText(language.getString("settings.barcode.composition.valueDigits",
                Math.max(0, format.valueDigits())));

        String problem = format.problemKey();
        labelCompositionProblem.setText(problem == null ? "" : language.getString(problem));
        labelCompositionProblem.setVisible(problem != null);
        labelCompositionProblem.setManaged(problem != null);
    }

    private void setTextBarcodeData(TextField textField, int property) {
        textField.setText(String.valueOf(property));
        textField.textProperty().addListener((observableValue, s, t1) -> {
            if (!t1.matches("\\d*")) {
                // Drop what is not a digit. This used to replace it with "0", so typing
                // "2a" left "20" behind - a number the operator never asked for.
                textField.setText(t1.replaceAll("\\D", ""));
            } else if (!t1.isEmpty()) {
                if (textField.equals(textBarcodeStart)) {
                    setSettingBarcodeStart(Integer.parseInt(t1));
                }
                if (textField.equals(textCountScale)) {
                    setSettingBarcodeScaleCodeDigits(Integer.parseInt(t1));
                }
                if (textField.equals(textCountBarcode)) {
                    setSettingBarcodeLength(Integer.parseInt(t1));
                }
                if (textField.equals(textCountItem)) {
                    setSettingBarcodeCountItem(Integer.parseInt(t1));
                }
                updateComposition();
            }
        });
    }
}
