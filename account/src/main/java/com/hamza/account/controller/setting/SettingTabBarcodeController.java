package com.hamza.account.controller.setting;

import com.hamza.account.checkbox.api.CheckBox_Setting;
import com.hamza.account.checkbox.impl.setting.BarcodePrintDoubleLabel;
import com.hamza.account.checkbox.impl.setting.BarcodePrintName;
import com.hamza.account.checkbox.impl.setting.BarcodePrintPrice;
import com.hamza.account.checkbox.impl.setting.CheckPrintBarcode;
import com.hamza.account.config.FxmlConstants;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.SelPriceTypeModel;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.SelPriceItemService;
import com.hamza.account.service.SupGroupService;
import com.hamza.account.service.UnitsService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
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

    private final BarcodePrintPrice barcodePrintPrice = new BarcodePrintPrice();
    private final CheckPrintBarcode checkPrintBarcode = new CheckPrintBarcode();
    private final BarcodePrintDoubleLabel barcodePrintDoubleLabel = new BarcodePrintDoubleLabel();
    private final BarcodePrintName barcodePrintName = new BarcodePrintName();
    private final DoubleProperty v1 = new SimpleDoubleProperty(0);
    private final DoubleProperty v2 = new SimpleDoubleProperty(0);
    private final DoubleProperty v3 = new SimpleDoubleProperty(0);
    private final DoubleProperty v4 = new SimpleDoubleProperty(0);
    private final DaoFactory daoFactory;
    private final ServiceData serviceData;
    private final Publisher<HashMap<Integer, String>> publisher;
    @FXML
    private CheckBox show2, showName, showPrice, showCurrency, showBarcode, checkActivateBarcodeScale;
    @FXML
    private VBox box;
    @FXML
    private BorderPane borderPane;
    @FXML
    private ComboBox<String> comboMain, comboSub, comboType;
    @FXML
    private Label labelMain, labelSub, labelType;
    @FXML
    private TextField textBarcodeStart, textCountScale, textCountBarcode, textCountItem;
    @FXML
    private TextField textPrice1, textPrice2, textPrice3;
    private VBox boxFirst;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        otherSetting();
        action();
        comboSetting(daoFactory);
        barcodeScaleSetting();
        showCurrency.setDisable(true);
        showCurrency.setText("عرض العملة");
    }

    private void otherSetting() {
        boxFirst = getLabelBarcode();
//        boxLast = getLabelBarcode();
        box.getChildren().add(boxFirst);
        borderPane.setTop(boxSpinner(Setting_Language.TOP, v1));
        borderPane.setBottom(boxSpinner(Setting_Language.BOTTOM, v3));
        borderPane.setLeft(boxSpinner(Setting_Language.RIGHT, v4));
        borderPane.setRight(boxSpinner(Setting_Language.LEFT, v2));
        labelMain.setText(Setting_Language.WORD_MAIN_G);
        labelSub.setText(Setting_Language.WORD_SUB_G);
        labelType.setText(Setting_Language.UNITS);

        var priceSelService = loadPriceNames();

        textPrice1.textProperty().addListener((observableValue, s, t1) -> updateSelPriceName(1, t1, priceSelService));
        textPrice2.textProperty().addListener((observableValue, s, t1) -> updateSelPriceName(2, t1, priceSelService));
        textPrice3.textProperty().addListener((observableValue, s, t1) -> updateSelPriceName(3, t1, priceSelService));
        textBarcodeStart.disableProperty().bind(checkActivateBarcodeScale.selectedProperty().not());
        textCountScale.disableProperty().bind(checkActivateBarcodeScale.selectedProperty().not());
        textCountBarcode.disableProperty().bind(checkActivateBarcodeScale.selectedProperty().not());
        textCountItem.disableProperty().bind(checkActivateBarcodeScale.selectedProperty().not());
    }

    @NotNull
    private SelPriceItemService loadPriceNames() {
        SelPriceItemService priceSelService = null;
        try {
            priceSelService = serviceData.getSelPriceItemService();
            var priceList = priceSelService.getSelPriceTypeList();
            textPrice1.setText(priceList.getFirst().getName());
            textPrice2.setText(priceList.get(1).getName());
            textPrice3.setText(priceList.get(2).getName());
        } catch (DaoException e) {
            log.error("Failed to load price names", e);
            AllAlerts.showExceptionDialog(e);
        }
        return priceSelService;
    }

    private void updateSelPriceName(int id, String name, SelPriceItemService priceSelService) {
        var selPriceTypeModel = new SelPriceTypeModel();
        try {
            selPriceTypeModel.setId(id);
            selPriceTypeModel.setName(name);
            var update = priceSelService.update(selPriceTypeModel);
            if (update >= 1) {
                var map = priceSelService.getIntegerStringHashMap();
                publisher.setAvailability(map);
            }
        } catch (DaoException e) {
            AllAlerts.showExceptionDialog(e);
            log.error(e.getMessage(), e.getCause());
        }
    }

    private void action() {
        new CheckBox_Setting(show2, barcodePrintDoubleLabel);
        new CheckBox_Setting(showName, barcodePrintName);
        new CheckBox_Setting(showPrice, barcodePrintPrice);
        new CheckBox_Setting(showBarcode, checkPrintBarcode);
    }

    private HBox boxSpinner(String string, DoubleProperty v) {
        HBox hBox = new HBox(5);
        hBox.setSpacing(5);
        hBox.setAlignment(Pos.CENTER);
        ObservableList<VBox> children = FXCollections.observableArrayList();
        children.addAll(boxFirst);
        hBox.getChildren().addAll(new Label(string), integerSpinner(children, v));
        return hBox;
    }

    private Spinner<Integer> integerSpinner(ObservableList<VBox> node, DoubleProperty v) {
        Spinner<Integer> spinner = new Spinner<>();
        spinner.setPrefWidth(80);
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10));
        return spinner;
    }

    private VBox getLabelBarcode() {
        try {
            FXMLLoader fxmlLoader = new FxmlConstants().labelBarcode;
            VBox load = fxmlLoader.load();
            LabelBarcodeController barcodeController = fxmlLoader.getController();
            barcodeController.showNameProperty().bind(showName.selectedProperty().not());
            barcodeController.showBarcodeProperty().bind(showBarcode.selectedProperty().not());
            barcodeController.showPriceProperty().bind(showPrice.selectedProperty().not());
            return load;
        } catch (IOException e) {
            AllAlerts.showExceptionDialog(e);
            log.error(e.getMessage(), e.getCause());
        }
        return null;
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
            log.error(e.getMessage(), e.getCause());
            return List.of();
        }
    }

    private List<String> getSubGroupsNames(SupGroupService supGroupService) {
        try {
            return supGroupService.getSubGroupsNames();
        } catch (Exception e) {
            log.error(e.getMessage(), e.getCause());
            return List.of();
        }
    }

    private void barcodeScaleSetting() {

        checkActivateBarcodeScale.setSelected(getSettingBarcodeScaleActive());
        checkActivateBarcodeScale.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            setSettingBarcodeScaleActive(t1);
        });

        setTextBarcodeData(textBarcodeStart, getSettingBarcodeStart());
        setTextBarcodeData(textCountScale, getSettingBarcodeCountScale());
        setTextBarcodeData(textCountBarcode, getSettingBarcodeLength());
        setTextBarcodeData(textCountItem, getSettingBarcodeCountItem());
    }

    private void setTextBarcodeData(TextField textField, int property) {
        textField.setText(String.valueOf(property));
        textField.textProperty().addListener((observableValue, s, t1) -> {
            if (!t1.matches("\\d*")) {
                textField.setText(t1.replaceAll("\\D", "0"));
            } else {
                if (textField.equals(textBarcodeStart)) {
                    setSettingBarcodeStart(Integer.parseInt(t1));
                }
                if (textField.equals(textCountScale)) {
                    setSettingBarcodeCountScale(Integer.parseInt(t1));
                }
                if (textField.equals(textCountBarcode)) {
                    setSettingBarcodeLength(Integer.parseInt(t1));
                }
                if (textField.equals(textCountItem)) {
                    setSettingBarcodeCountItem(Integer.parseInt(t1));
                }
            }
        });
    }
}
