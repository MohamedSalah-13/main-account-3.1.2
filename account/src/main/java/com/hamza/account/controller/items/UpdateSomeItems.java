package com.hamza.account.controller.items;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.MainGroups;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.service.ItemsService;
import com.hamza.account.service.MainGroupService;
import com.hamza.account.service.SupGroupService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.util.ImageChoose;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
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

@Log4j2
@FxmlPath(pathFile = "items/update-some-items.fxml")
public class UpdateSomeItems {

    private final List<ItemsModel> itemsModelList;

    private final ImageChoose imageChoose = new ImageChoose();
    private final ImageView imageView = new ImageView();

    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final MainGroupService mainGroupService = ServiceRegistry.get(MainGroupService.class);
    private final SupGroupService supGroupService = ServiceRegistry.get(SupGroupService.class);

    @FXML
    private CheckBox checkUpdateGroup;
    @FXML
    private CheckBox checkUpdateActive;
    @FXML
    private CheckBox checkUpdateBuy;
    @FXML
    private CheckBox checkUpdateSell;
    @FXML
    private CheckBox checkDeleteImage;
    @FXML
    private CheckBox checkMini;
    @FXML
    private CheckBox checkFirstBalance;

    @FXML
    private ComboBox<String> comboActive;
    @FXML
    private ComboBox<String> comboMainGroup;
    @FXML
    private ComboBox<String> comboSubGroup;

    @FXML
    private TextField textBuyPrice;
    @FXML
    private TextField textSellPrice;
    @FXML
    private TextField textFirstBalance;
    @FXML
    private TextField textMini;

    @FXML
    private RadioButton radioDeleteImage;
    @FXML
    private RadioButton radioAddImage;
    @FXML
    private Text textPath;
    @FXML
    private ImageView imageInformation;

    @FXML
    private Button btnSave;
    @FXML
    private Button btnClose;
    @FXML
    private Text textItemsCount;
    @FXML
    private TableView<ItemsModel> tableItems;

    @FXML
    private StackPane stackPane;

    private MaskerPaneSetting maskerPaneSetting;

    public UpdateSomeItems(List<ItemsModel> itemsModelList) {
        this.itemsModelList = itemsModelList;
    }

    @FXML
    public void initialize() {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);

        setupItemsTable();
        setupTexts();
        setupButtons();
        setupComboBoxes();
        setupTextFields();
        setupImageControls();
        setupCheckBoxes();
        setupActions();
    }
    private void setupItemsTable() {
        tableItems.getColumns().clear();

        TableColumn<ItemsModel, Number> columnNumber = new TableColumn<>("#");
        columnNumber.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(tableItems.getItems().indexOf(cellData.getValue()) + 1)
        );
        columnNumber.setPrefWidth(55);

        TableColumn<ItemsModel, String> columnName = new TableColumn<>("اسم الصنف");
        columnName.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(cellData.getValue().getNameItem())
        );
        columnName.setPrefWidth(220);

        TableColumn<ItemsModel, Number> columnBuyPrice = new TableColumn<>("سعر الشراء");
        columnBuyPrice.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(cellData.getValue().getBuyPrice())
        );
        columnBuyPrice.setPrefWidth(100);

        TableColumn<ItemsModel, Number> columnSellPrice = new TableColumn<>("سعر البيع");
        columnSellPrice.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(cellData.getValue().getSelPrice1())
        );
        columnSellPrice.setPrefWidth(100);

        TableColumn<ItemsModel, Number> columnMiniQuantity = new TableColumn<>("أقل كمية");
        columnMiniQuantity.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(cellData.getValue().getMini_quantity())
        );
        columnMiniQuantity.setPrefWidth(100);

        TableColumn<ItemsModel, Number> columnFirstBalance = new TableColumn<>("رصيد أول");
        columnFirstBalance.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(cellData.getValue().getFirstBalanceForStock())
        );
        columnFirstBalance.setPrefWidth(100);

        tableItems.getColumns().addAll(
                columnNumber,
                columnName,
                columnBuyPrice,
                columnSellPrice,
                columnMiniQuantity,
                columnFirstBalance
        );

        tableItems.setItems(FXCollections.observableArrayList(itemsModelList));
        tableItems.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        textItemsCount.setText("عدد الأصناف: " + tableItems.getItems().size());
    }

    private void setupTexts() {
        checkUpdateGroup.setText("تعديل المجموعة");
        checkUpdateActive.setText("تعديل الحالة");
        checkUpdateBuy.setText("تعديل سعر الشراء");
        checkUpdateSell.setText("تعديل سعر البيع");
        checkDeleteImage.setText("تعديل صور الأصناف");
        checkMini.setText("تعديل أقل كمية");
        checkFirstBalance.setText("تعديل رصيد أول");

        comboMainGroup.setPromptText(Setting_Language.WORD_MAIN_G);
        comboSubGroup.setPromptText(Setting_Language.WORD_SUB_G);
        comboActive.setPromptText(Setting_Language.WORD_ACTIVE);

        textBuyPrice.setPromptText("نسبة الزيادة %");
        textSellPrice.setPromptText("نسبة الزيادة %");
        textMini.setPromptText("أقل كمية");
        textFirstBalance.setPromptText("رصيد أول");

        radioDeleteImage.setText("حذف الصور الحالية");
        radioAddImage.setText("تعيين صورة واحدة للجميع");

        textPath.setText("لم يتم اختيار صورة");

        btnSave.setText(Setting_Language.WORD_SAVE);
        btnClose.setText(Setting_Language.WORD_CLOSE);
    }

    private void setupButtons() {
        var imageSetting = new Image_Setting();

        btnSave.setGraphic(ImageChoose.createIcon(imageSetting.save));
        btnClose.setGraphic(ImageChoose.createIcon(imageSetting.cancel));
    }

    private void setupComboBoxes() {
        comboMainGroup.setItems(FXCollections.observableArrayList(getMainGroupsNames()));

        comboMainGroup.valueProperty().addListener((observableValue, oldValue, newValue) -> {
            comboSubGroup.getItems().clear();

            if (newValue == null || newValue.isBlank()) {
                return;
            }

            try {
                MainGroups mainGroup = mainGroupService.getMainGroupsByName(newValue);
                ObservableList<String> subGroups =
                        FXCollections.observableArrayList(
                                supGroupService.getSubGroupsNamesByMainId(mainGroup.getId())
                        );

                comboSubGroup.setItems(subGroups);
                comboSubGroup.getSelectionModel().clearSelection();
            } catch (Exception e) {
                logError(e);
            }
        });

        comboActive.setItems(FXCollections.observableArrayList(
                Setting_Language.WORD_ACTIVE,
                Setting_Language.WORD_INACTIVE
        ));
        comboActive.getSelectionModel().selectFirst();
    }

    private void setupTextFields() {
        textBuyPrice.setTextFormatter(decimalTextFormatter());
        textSellPrice.setTextFormatter(decimalTextFormatter());
        textMini.setTextFormatter(decimalTextFormatter());
        textFirstBalance.setTextFormatter(decimalTextFormatter());

        textBuyPrice.setText("0.0");
        textSellPrice.setText("0.0");
        textMini.setText("0.0");
        textFirstBalance.setText("0.0");
    }

    private void setupImageControls() {
        radioDeleteImage.setSelected(true);

        radioAddImage.setTooltip(new Tooltip("اختيار صورة واحدة وتطبيقها على كل الأصناف المحددة"));
        radioDeleteImage.setTooltip(new Tooltip("حذف الصور من كل الأصناف المحددة"));

        radioDeleteImage.disableProperty().bind(checkDeleteImage.selectedProperty().not());
        radioAddImage.disableProperty().bind(checkDeleteImage.selectedProperty().not());

        radioAddImage.setOnAction(actionEvent -> chooseImage());
        radioDeleteImage.setOnAction(actionEvent -> {
            imageView.setImage(null);
            textPath.setText("سيتم حذف الصور الحالية");
        });

        setupImageInformationPopup();
    }

    private void setupImageInformationPopup() {
        final Popup popup = new Popup();
        popup.setAutoHide(true);

        var text = new Text("يمكنك حذف جميع الصور الحالية\nأو تعيين صورة واحدة لجميع الأصناف المحددة");
        text.getStyleClass().add("text-explain");

        popup.getContent().add(text);

        imageInformation.setOnMouseEntered(mouseEvent ->
                popup.show(
                        imageInformation,
                        mouseEvent.getScreenX() - 170,
                        mouseEvent.getScreenY() - 55
                )
        );

        imageInformation.setOnMouseExited(mouseEvent -> popup.hide());
    }

    private void setupCheckBoxes() {
        btnSave.disableProperty().bind(
                checkUpdateSell.selectedProperty().not()
                        .and(checkUpdateBuy.selectedProperty().not())
                        .and(checkMini.selectedProperty().not())
                        .and(checkFirstBalance.selectedProperty().not())
                        .and(checkUpdateGroup.selectedProperty().not())
                        .and(checkUpdateActive.selectedProperty().not())
                        .and(checkDeleteImage.selectedProperty().not())
        );

        comboMainGroup.disableProperty().bind(checkUpdateGroup.selectedProperty().not());
        comboSubGroup.disableProperty().bind(checkUpdateGroup.selectedProperty().not());

        comboActive.disableProperty().bind(checkUpdateActive.selectedProperty().not());

        textBuyPrice.disableProperty().bind(checkUpdateBuy.selectedProperty().not());
        textSellPrice.disableProperty().bind(checkUpdateSell.selectedProperty().not());
        textMini.disableProperty().bind(checkMini.selectedProperty().not());
        textFirstBalance.disableProperty().bind(checkFirstBalance.selectedProperty().not());

        checkUpdateBuy.selectedProperty().addListener((observableValue, oldValue, selected) ->
                resetFieldIfUnchecked(textBuyPrice, selected)
        );

        checkUpdateSell.selectedProperty().addListener((observableValue, oldValue, selected) ->
                resetFieldIfUnchecked(textSellPrice, selected)
        );

        checkMini.selectedProperty().addListener((observableValue, oldValue, selected) ->
                resetFieldIfUnchecked(textMini, selected)
        );

        checkFirstBalance.selectedProperty().addListener((observableValue, oldValue, selected) ->
                resetFieldIfUnchecked(textFirstBalance, selected)
        );

        checkDeleteImage.selectedProperty().addListener((observableValue, oldValue, selected) -> {
            if (!selected) {
                imageView.setImage(null);
                radioDeleteImage.setSelected(true);
                textPath.setText("لم يتم اختيار صورة");
            } else if (radioDeleteImage.isSelected()) {
                textPath.setText("سيتم حذف الصور الحالية");
            }
        });
    }

    private void setupActions() {
        btnSave.setOnAction(actionEvent -> saveData());
        btnClose.setOnAction(actionEvent -> closeWindow());
    }

    @NotNull
    private TextFormatter<Object> decimalTextFormatter() {
        return new TextFormatter<>(change -> {
            String newText = change.getControlNewText();

            if (newText == null || newText.isBlank()) {
                return change;
            }

            if (newText.matches("\\d*(\\.\\d*)?")) {
                return change;
            }

            return null;
        });
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

    private void chooseImage() {
        try {
            imageChoose.onAddImage(imageView);

            if (imageView.getImage() == null) {
                textPath.setText("لم يتم اختيار صورة");
                radioDeleteImage.setSelected(true);
                return;
            }

            textPath.setText("تم اختيار صورة");
        } catch (FileNotFoundException e) {
            logError(e);
            textPath.setText("لم يتم اختيار صورة");
            radioDeleteImage.setSelected(true);
        }
    }

    private void resetFieldIfUnchecked(TextField field, boolean selected) {
        if (!selected) {
            field.setText("0.0");
        }
    }

    private void saveData() {
        if (itemsModelList == null || itemsModelList.isEmpty()) {
            AllAlerts.alertError("لا توجد أصناف محددة للتعديل");
            return;
        }

        try {
            boolean hasUpdates = applySelectedUpdates();

            if (!hasUpdates) {
                AllAlerts.alertError("من فضلك اختر تعديل واحد على الأقل");
                return;
            }

            maskerPaneSetting.showMaskerPane(() -> {
                try {
                    itemsService.updateGroup(itemsModelList);
                } catch (Exception e) {
                    logError(e);
                }
            });

            maskerPaneSetting.getVoidTask().setOnSucceeded(workerStateEvent -> {
                AllAlerts.alertSave();
                resetScreen();
            });

            maskerPaneSetting.getVoidTask().setOnFailed(workerStateEvent -> {
                Throwable exception = maskerPaneSetting.getVoidTask().getException();

                if (exception instanceof Exception e) {
                    logError(e);
                } else if (exception != null) {
                    log.error("Error while updating selected items", exception);
                    AllAlerts.alertError(exception.getMessage());
                }
            });

        } catch (Exception e) {
            logError(e);
        }
    }

    private boolean applySelectedUpdates() throws Exception {
        boolean hasUpdates = false;

        if (checkUpdateGroup.isSelected()) {
            updateItemsGroup();
            hasUpdates = true;
        }

        if (checkUpdateActive.isSelected()) {
            updateItemsActiveStatus();
            hasUpdates = true;
        }

        if (checkUpdateBuy.isSelected()) {
            updateItemsBuyPrice();
            hasUpdates = true;
        }

        if (checkUpdateSell.isSelected()) {
            updateItemsSellPrice();
            hasUpdates = true;
        }

        if (checkMini.isSelected()) {
            updateItemsMiniQuantity();
            hasUpdates = true;
        }

        if (checkFirstBalance.isSelected()) {
            updateItemsFirstBalance();
            hasUpdates = true;
        }

        if (checkDeleteImage.isSelected()) {
            updateItemsImage();
            hasUpdates = true;
        }

        return hasUpdates;
    }

    private void updateItemsGroup() throws Exception {
        if (comboMainGroup.getSelectionModel().isEmpty()) {
            throw new Exception("من فضلك حدد المجموعة الرئيسية");
        }

        if (comboSubGroup.getSelectionModel().isEmpty()) {
            throw new Exception("من فضلك حدد المجموعة الفرعية");
        }

        var subGroup = supGroupService.getSubGroupsByName(
                comboSubGroup.getSelectionModel().getSelectedItem()
        );

        itemsModelList.forEach(itemsModel -> itemsModel.setSubGroups(subGroup));
    }

    private void updateItemsActiveStatus() throws Exception {
        if (comboActive.getSelectionModel().isEmpty()) {
            throw new Exception("من فضلك حدد الحالة");
        }

        boolean active = comboActive.getSelectionModel()
                .getSelectedItem()
                .equals(Setting_Language.WORD_ACTIVE);

        itemsModelList.forEach(itemsModel -> itemsModel.setActiveItem(active));
    }

    private void updateItemsBuyPrice() throws Exception {
        double percentageIncrease = getRequiredPositiveNumber(
                textBuyPrice,
                "نسبة زيادة سعر الشراء يجب أن تكون أكبر من الصفر"
        );

        itemsModelList.forEach(itemsModel -> {
            double buyPrice = itemsModel.getBuyPrice();
            double newPrice = buyPrice + ((buyPrice * percentageIncrease) / 100);
            itemsModel.setBuyPrice(newPrice);
        });
    }

    private void updateItemsSellPrice() throws Exception {
        double percentageIncrease = getRequiredPositiveNumber(
                textSellPrice,
                "نسبة زيادة سعر البيع يجب أن تكون أكبر من الصفر"
        );

        itemsModelList.forEach(itemsModel -> {
            double sellPrice = itemsModel.getSelPrice1();
            double newPrice = sellPrice + ((sellPrice * percentageIncrease) / 100);
            itemsModel.setSelPrice1(newPrice);
        });
    }

    private void updateItemsMiniQuantity() throws Exception {
        double miniQuantity = getRequiredNumber(textMini, "من فضلك أدخل أقل كمية");

        itemsModelList.forEach(itemsModel -> itemsModel.setMini_quantity(miniQuantity));
    }

    private void updateItemsFirstBalance() throws Exception {
        double firstBalance = getRequiredNumber(textFirstBalance, "من فضلك أدخل رصيد أول");

        itemsModelList.forEach(itemsModel -> itemsModel.setFirstBalanceForStock(firstBalance));
    }

    private void updateItemsImage() throws Exception {
        if (radioDeleteImage.isSelected()) {
            itemsModelList.forEach(itemsModel -> itemsModel.setItem_image(null));
            return;
        }

        if (radioAddImage.isSelected()) {
            if (imageView.getImage() == null) {
                throw new Exception("من فضلك اختر صورة أولاً");
            }

            byte[] imageBytes = imageChoose.convertFxImageToBytes(imageView.getImage());
            itemsModelList.forEach(itemsModel -> itemsModel.setItem_image(imageBytes));
            return;
        }

        throw new Exception("من فضلك اختر طريقة تعديل الصورة");
    }

    private double getRequiredPositiveNumber(TextField field, String errorMessage) throws Exception {
        double value = getRequiredNumber(field, errorMessage);

        if (value <= 0) {
            throw new Exception(errorMessage);
        }

        return value;
    }

    private double getRequiredNumber(TextField field, String errorMessage) throws Exception {
        String text = field.getText();

        if (text == null || text.isBlank()) {
            throw new Exception(errorMessage);
        }

        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new Exception(errorMessage, e);
        }
    }

    private void resetScreen() {
        checkUpdateGroup.setSelected(false);
        checkUpdateActive.setSelected(false);
        checkUpdateBuy.setSelected(false);
        checkUpdateSell.setSelected(false);
        checkDeleteImage.setSelected(false);
        checkMini.setSelected(false);
        checkFirstBalance.setSelected(false);

        comboMainGroup.getSelectionModel().clearSelection();
        comboSubGroup.getItems().clear();
        comboActive.getSelectionModel().selectFirst();

        textBuyPrice.setText("0.0");
        textSellPrice.setText("0.0");
        textMini.setText("0.0");
        textFirstBalance.setText("0.0");

        radioDeleteImage.setSelected(true);
        imageView.setImage(null);
        textPath.setText("لم يتم اختيار صورة");
    }

    private void closeWindow() {
        if (stackPane != null && stackPane.getScene() != null) {
            stackPane.getScene().getWindow().hide();
        }
    }

    private void logError(Exception e) {
        log.error("Error while updating selected items", e);
        AllAlerts.showExceptionDialog(e);
    }
}