package com.hamza.account.controller.items;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.MainGroups;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.util.ImageChoose;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.Popup;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.util.List;

import static com.hamza.controlsfx.others.Utils.setTextFormatter;

@Log4j2
@FxmlPath(pathFile = "items/update-some-items.fxml")
public class UpdateSomeItems extends ServiceData {

    private final List<ItemsModel> itemsModelList;
    private final ImageChoose imageChoose = new ImageChoose();
    private final ImageView imageView = new ImageView();
    private boolean isActiveProperty = false;
    @FXML
    private CheckBox checkUpdateGroup, checkUpdateActive, checkUpdateBuy, checkUpdateSell, checkDeleteImage, checkMini, checkFirstBalance;
    @FXML
    private ComboBox<String> comboActive, comboMainGroup, comboSubGroup;
    @FXML
    private TextField textBuyPrice, textSellPrice, textFirstBalance, textMini;
    @FXML
    private RadioButton radioDeleteImage, radioAddImage;
    @FXML
    private Text textPath;
    @FXML
    private ImageView imageInformation;
    @FXML
    private Button btnSave, btnClose;
    @FXML
    private StackPane stackPane;
    private MaskerPaneSetting maskerPaneSetting;

    public UpdateSomeItems(DaoFactory daoFactory, List<ItemsModel> itemsModelList) throws Exception {
        super(daoFactory);
        this.itemsModelList = itemsModelList;
    }

    @NotNull
    private static TextFormatter<Object> getTextFormatter() {
        return new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.matches("^\\d*\\.?\\d*$")) {
                return change;
            }
            return null;
        });
    }

    @FXML
    public void initialize() {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        comboSetting();
        checkSetting();
    }

    private void comboSetting() {
        btnSave.setText(Setting_Language.WORD_SAVE);
        btnClose.setText(Setting_Language.WORD_CLOSE);
        var imageSetting = new Image_Setting();
        btnSave.setGraphic(ImageChoose.createIcon(imageSetting.save));
        btnClose.setGraphic(ImageChoose.createIcon(imageSetting.cancel));

        checkUpdateGroup.setText("تعديل المجموعة");
        checkUpdateActive.setText("تعديل الحالة");
        checkUpdateBuy.setText("تعديل سعر الشراء");
        checkUpdateSell.setText("تعديل سعر البيع");
        checkDeleteImage.setText("تعديل الصورة");
        checkMini.setText("تعديل اقل كمية");
        checkFirstBalance.setText("تعديل رصيد اول");

        comboMainGroup.setPromptText(Setting_Language.WORD_MAIN_G);
        comboSubGroup.setPromptText(Setting_Language.WORD_SUB_G);
        // combo items

        ObservableList<String> observableListMain = FXCollections.observableArrayList(getMainGroupsNames());
        comboMainGroup.setItems(observableListMain);

        comboMainGroup.valueProperty().addListener((observableValue, string, t1) -> {
            comboSubGroup.getItems().clear();
            // add items
            try {
                MainGroups mainGroupsByName = mainGroupService.getMainGroupsByName(t1);
                ObservableList<String> observableListSub = FXCollections.observableArrayList(supGroupService.getSubGroupsNamesByMainId(mainGroupsByName.getId()));
                comboSubGroup.getItems().addAll(observableListSub);
            } catch (Exception e) {
                logError(e);
            }
        });

        comboSubGroup.valueProperty().addListener((observableValue, string, t1) -> {
            try {
                for (ItemsModel itemsModel : itemsModelList) {
                    itemsModel.setSubGroups(supGroupService.getSubGroupsByName(comboSubGroup.getSelectionModel().getSelectedItem()));
//                    System.out.println(itemsModel.getSubGroups());
                }
            } catch (DaoException e) {
                logError(e);
            }
        });

        comboActive.setPromptText(Setting_Language.WORD_ACTIVE);
        var statusActive = Setting_Language.WORD_ACTIVE;
        var statusInactive = Setting_Language.WORD_INACTIVE;

        comboActive.setItems(FXCollections.observableArrayList(statusActive, statusInactive));
        comboActive.getSelectionModel().selectFirst();
        setTextFormatter(textBuyPrice, textSellPrice, textMini, textFirstBalance);
        textBuyPrice.setTextFormatter(getTextFormatter());
        textSellPrice.setTextFormatter(getTextFormatter());
        textMini.setTextFormatter(getTextFormatter());
        textFirstBalance.setTextFormatter(getTextFormatter());
        radioAddImage.setTooltip(new Tooltip("Click to add an image for the items"));
        radioDeleteImage.setTooltip(new Tooltip("Click to delete the image for the items"));
        radioDeleteImage.disableProperty().bind(checkDeleteImage.selectedProperty().not());
        radioAddImage.disableProperty().bind(checkDeleteImage.selectedProperty().not());

//        radioDeleteImage.setOnAction(actionEvent -> textPath.setText(""));
        radioAddImage.setOnAction(actionEvent -> {
            try {
                imageChoose.onAddImage(imageView);
                if (imageView.getImage() == null) {
                    textPath.setText("");
                    radioDeleteImage.setSelected(true);
                }
            } catch (FileNotFoundException e) {
                logError(e);
            }
        });

        final Popup popup = new Popup();
        popup.setAutoHide(true);
        var e = new Text("حذف جميع الصورة \n او إضافة صورة واحدة لجميع الأصناف");
        e.getStyleClass().add("text-explain");
        popup.getContent().add(e);
        imageInformation.setOnMouseEntered(mouseEvent -> popup.show(imageInformation, mouseEvent.getScreenX() - 150, mouseEvent.getScreenY() - 50));
        imageInformation.setOnMouseExited(mouseEvent -> popup.hide());

        btnSave.setOnAction(actionEvent -> saveData());
        btnClose.setOnAction(actionEvent -> stackPane.getScene().getWindow().hide());

    }

    @NotNull
    private List<String> getMainGroupsNames() {
        try {
            return mainGroupService.getMainGroupsNames();
        } catch (DaoException e) {
            logError(e);
            return List.of();
        }
    }

    private void checkSetting() {
        btnSave.disableProperty().bind(checkUpdateSell.selectedProperty().not()
                .and(checkUpdateBuy.selectedProperty().not())
                .and(checkMini.selectedProperty().not())
                .and(checkFirstBalance.selectedProperty().not())
                .and(checkUpdateGroup.selectedProperty().not())
                .and(checkUpdateActive.selectedProperty().not())
                .and(checkDeleteImage.selectedProperty().not()));

        comboMainGroup.disableProperty().bind(checkUpdateGroup.selectedProperty().not());
        comboSubGroup.disableProperty().bind(checkUpdateGroup.selectedProperty().not());
        textBuyPrice.disableProperty().bind(checkUpdateBuy.selectedProperty().not());
        textSellPrice.disableProperty().bind(checkUpdateSell.selectedProperty().not());
        textMini.disableProperty().bind(checkMini.selectedProperty().not());
        textFirstBalance.disableProperty().bind(checkFirstBalance.selectedProperty().not());
        comboActive.disableProperty().bind(checkUpdateActive.selectedProperty().not());

        checkUpdateSell.selectedProperty().addListener((observableValue, aBoolean, t1) -> resetFieldIfFalse(textSellPrice, t1));
        checkUpdateBuy.selectedProperty().addListener((observableValue, aBoolean, t1) -> resetFieldIfFalse(textBuyPrice, t1));
        checkMini.selectedProperty().addListener((observableValue, aBoolean, t1) -> resetFieldIfFalse(textMini, t1));
        checkFirstBalance.selectedProperty().addListener((observableValue, aBoolean, t1) -> resetFieldIfFalse(textFirstBalance, t1));

    }

    private void resetFieldIfFalse(TextField field, Boolean t1) {
        if (!t1) {
            field.setText("0.0");
        }
    }

    private void saveData() {
        try {
            var i = updateGroups(itemsModelList);
            log.info("Update groups result: {}", i);
            if (i == 1) {
                maskerPaneSetting.showMaskerPane(() -> {
                    try {
                        itemsService.updateGroup(itemsModelList);
                    } catch (Exception e) {
                        logError(e);
                    }
                });

                maskerPaneSetting.getVoidTask().setOnSucceeded(workerStateEvent -> {
                    AllAlerts.alertSave();
                    checkUpdateGroup.setSelected(false);
                    checkUpdateActive.setSelected(false);
                    checkUpdateBuy.setSelected(false);
                    checkUpdateSell.setSelected(false);
                    checkDeleteImage.setSelected(false);
                    checkMini.setSelected(false);
                    checkFirstBalance.setSelected(false);
                });
            }
        } catch (Exception e) {
            logError(e);
        }


    }

    private int updateGroups(List<ItemsModel> itemsModelList) throws Exception {
        // check groups
        if (checkUpdateGroup.isSelected()) {
            if (comboSubGroup.getSelectionModel().isEmpty()) {
                comboSubGroup.getSelectionModel().selectFirst();
                throw new Exception("من فضلك حدد المجموعة");
            }
            return 1;
        }

        // check activation
        else if (checkUpdateActive.isSelected()) {
            var selectionModel = comboActive.getSelectionModel();
            if (selectionModel.isEmpty()) {
                throw new Exception("من فضلك حدد الحالة");
            }
            if (selectionModel.getSelectedItem().equals(Setting_Language.WORD_ACTIVE)) {
                isActiveProperty = true;
            }
            itemsModelList.forEach(itemsModel -> itemsModel.setActiveItem(isActiveProperty));
            return 1;
        }

        // check buy
        else if (checkUpdateBuy.isSelected()) {
            var percentageIncrease = Double.parseDouble(textBuyPrice.getText());
            if (percentageIncrease <= 0) {
                throw new Exception("الزيادة المطلوبة يجب ان تكون اكبر من الصفر");
            }

            itemsModelList.forEach(itemsModel -> {
                var buyPrice = itemsModel.getBuyPrice();
                var v = ((buyPrice * percentageIncrease) / 100) + buyPrice;
                itemsModel.setBuyPrice(v);
            });
        }

        // check sell
        else if (checkUpdateSell.isSelected()) {
            var percentageDecrease = Double.parseDouble(textSellPrice.getText());
            if (percentageDecrease <= 0) {
                throw new Exception("الزيادة المطلوبة يجب ان تكون اكبر من الصفر");
            }

            itemsModelList.forEach(itemsModel -> {
                var selPrice1 = itemsModel.getSelPrice1();
                var v = ((selPrice1 * percentageDecrease) / 100) + selPrice1;
                itemsModel.setSelPrice1(v);
            });
            return 1;
        }

        // check mini
        else if (checkMini.isSelected()) {
            itemsModelList.forEach(itemsModel -> itemsModel.setMini_quantity(Double.parseDouble(textMini.getText())));
            return 1;
        }

        // check first balance
        else if (checkFirstBalance.isSelected()) {
            itemsModelList.forEach(itemsModel -> itemsModel.setFirstBalanceForStock(Double.parseDouble(textFirstBalance.getText())));
            return 1;
        }

        // check image
        else if (checkDeleteImage.isSelected()) {
            if (radioDeleteImage.isSelected()) {
                itemsModelList.forEach(itemsModel -> itemsModel.setItem_image(null));
            }

            if (radioAddImage.isSelected()) {
                itemsModelList.forEach(itemsModel -> {
                    try {
                        itemsModel.setItem_image(imageChoose.convertFxImageToBytes(imageView.getImage()));
                    } catch (Exception e) {
                        logError(e);
                    }
                });
            }
            return 1;
        }
        return 0;
    }

    private void logError(Exception e) {
        log.error(e.getMessage(), e.getCause());
        AllAlerts.showExceptionDialog(e);
    }

}
