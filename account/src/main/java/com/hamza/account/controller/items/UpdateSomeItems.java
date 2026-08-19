package com.hamza.account.controller.items;

import com.hamza.account.config.UiScale;
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
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.util.ImageChoose;
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
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ToDoubleFunction;

import static com.hamza.controlsfx.others.Utils.setTextFormatter;

@Log4j2
@FxmlPath(pathFile = "items/update-some-items.fxml")
public class UpdateSomeItems {

    private final List<ItemsModel> itemsModelList;
    private final ImageChoose imageChoose = new ImageChoose();
    private final ImageView imageView = new ImageView();
    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final MainGroupService mainGroupService = ServiceRegistry.get(MainGroupService.class);
    private final SupGroupService supGroupService = ServiceRegistry.get(SupGroupService.class);
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
    private FontIcon imageInformation;
    @FXML
    private FontIcon headerIcon;
    @FXML
    private FontIcon iconBuyPercent, iconSellPercent;
    @FXML
    private Button btnSave, btnClose;
    @FXML
    private StackPane stackPane;
    private MaskerPaneSetting maskerPaneSetting;

    public UpdateSomeItems(List<ItemsModel> itemsModelList) {
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

    /**
     * The size icons render at, scaled from {@link UiScale} the same way
     * {@code UnitsController} does, so a screen opened at a larger font size
     * still gets icons to match.
     */
    private int iconSize() {
        return (int) Math.round(16 * UiScale.factor());
    }

    private FontIcon icon(Ikon code) {
        FontIcon fontIcon = new FontIcon(code);
        fontIcon.setIconSize(iconSize());
        fontIcon.getStyleClass().add("icon-graphic");
        return fontIcon;
    }

    private void comboSetting() {
        var lm = LanguageManager.getInstance();
        btnSave.setText(lm.getString("common.save"));
        btnClose.setText(lm.getString("common.close"));
        btnSave.setGraphic(icon(Feather.SAVE));
        btnClose.setGraphic(icon(Feather.X));

        headerIcon.setIconCode(Feather.SLIDERS);
        headerIcon.setIconSize(iconSize() * 2);

        imageInformation.setIconCode(Feather.INFO);
        imageInformation.setIconSize(iconSize());

        iconBuyPercent.setIconCode(Feather.PERCENT);
        iconSellPercent.setIconCode(Feather.PERCENT);
        iconBuyPercent.setIconSize(iconSize());
        iconSellPercent.setIconSize(iconSize());

        checkUpdateGroup.setText(lm.getString("item.update.group"));
        checkUpdateActive.setText(lm.getString("item.update.active"));
        checkUpdateBuy.setText(lm.getString("item.update.buy.price"));
        checkUpdateSell.setText(lm.getString("item.update.sell.price"));
        checkDeleteImage.setText(lm.getString("item.update.image"));
        checkMini.setText(lm.getString("item.update.mini.quantity"));
        checkFirstBalance.setText(lm.getString("item.update.first.balance"));

        comboMainGroup.setPromptText(lm.getString("mainGroup"));
        comboSubGroup.setPromptText(lm.getString("subGroup"));
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
                var selectedSubGroup = supGroupService.getSubGroupsByName(t1);
                itemsModelList.forEach(itemsModel -> itemsModel.setSubGroups(selectedSubGroup));
            } catch (DaoException e) {
                logError(e);
            }
        });

        comboActive.setPromptText(lm.getString("activated"));
        var statusActive = lm.getString("activated");
        var statusInactive = lm.getString("in_active");

        comboActive.setItems(FXCollections.observableArrayList(statusActive, statusInactive));
        comboActive.getSelectionModel().selectFirst();
        setTextFormatter(textBuyPrice, textSellPrice, textMini, textFirstBalance);
        textBuyPrice.setTextFormatter(getTextFormatter());
        textSellPrice.setTextFormatter(getTextFormatter());
        textMini.setTextFormatter(getTextFormatter());
        textFirstBalance.setTextFormatter(getTextFormatter());
        radioAddImage.setTooltip(new Tooltip(lm.getString("item.tooltip.add.image.all")));
        radioDeleteImage.setTooltip(new Tooltip(lm.getString("item.tooltip.delete.image.all")));
        radioDeleteImage.disableProperty().bind(checkDeleteImage.selectedProperty().not());
        radioAddImage.disableProperty().bind(checkDeleteImage.selectedProperty().not());

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
        var infoText = new Text(lm.getString("item.popup.image.info"));
        infoText.getStyleClass().add("text-explain");
        popup.getContent().add(infoText);
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
            if (!applyRequestedUpdates(itemsModelList)) {
                return;
            }

            maskerPaneSetting.showMaskerPane(LanguageManager.getInstance().getString("item.dialog.update.items.title"),
                    () -> itemsService.updateGroup(itemsModelList));

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
        } catch (Exception e) {
            logError(e);
        }
    }

    /**
     * Applies every checked bulk-edit option to {@code itemsModelList} in memory and
     * reports whether at least one was applied - which is what {@link #saveData()}
     * uses to decide whether the batch is worth sending to the database at all.
     * <p>
     * Each option used to live in one branch of an if/else chain, so checking more
     * than one box silently applied only the first that matched - and the buy-price
     * branch fell out of the chain without ever signalling "applied", so raising buy
     * prices in bulk computed new values in memory and then threw them away instead
     * of reaching the database. Every option is now independent of the others.
     */
    private boolean applyRequestedUpdates(List<ItemsModel> itemsModelList) throws Exception {
        boolean anyApplied = false;

        if (checkUpdateGroup.isSelected()) {
            requireSubGroupSelected();
            anyApplied = true;
        }
        if (checkUpdateActive.isSelected()) {
            applyActiveUpdate(itemsModelList);
            anyApplied = true;
        }
        if (checkUpdateBuy.isSelected()) {
            applyPercentageChange(itemsModelList, textBuyPrice, ItemsModel::getBuyPrice, ItemsModel::setBuyPrice);
            anyApplied = true;
        }
        if (checkUpdateSell.isSelected()) {
            applyPercentageChange(itemsModelList, textSellPrice, ItemsModel::getSelPrice1, ItemsModel::setSelPrice1);
            anyApplied = true;
        }
        if (checkMini.isSelected()) {
            var mini = requirePositiveNumber(textMini.getText());
            itemsModelList.forEach(itemsModel -> itemsModel.setMini_quantity(mini));
            anyApplied = true;
        }
        if (checkFirstBalance.isSelected()) {
            var firstBalance = requireNumber(textFirstBalance.getText());
            itemsModelList.forEach(itemsModel -> itemsModel.setFirstBalanceForStock(firstBalance));
            anyApplied = true;
        }
        if (checkDeleteImage.isSelected()) {
            applyImageUpdate(itemsModelList);
            anyApplied = true;
        }

        return anyApplied;
    }

    private void requireSubGroupSelected() throws UserValidationException {
        if (comboSubGroup.getSelectionModel().isEmpty()) {
            comboSubGroup.getSelectionModel().selectFirst();
            throw new UserValidationException(LanguageManager.getInstance().getString("item.error.select.group"));
        }
    }

    private void applyActiveUpdate(List<ItemsModel> itemsModelList) throws UserValidationException {
        var selectionModel = comboActive.getSelectionModel();
        if (selectionModel.isEmpty()) {
            throw new UserValidationException(LanguageManager.getInstance().getString("item.error.select.status"));
        }
        isActiveProperty = selectionModel.getSelectedItem().equals(LanguageManager.getInstance().getString("activated"));
        itemsModelList.forEach(itemsModel -> itemsModel.setActiveItem(isActiveProperty));
    }

    private void applyPercentageChange(List<ItemsModel> itemsModelList, TextField field,
                                        ToDoubleFunction<ItemsModel> getter, ObjDoubleConsumer<ItemsModel> setter) throws UserValidationException {
        var percentage = requirePositiveNumber(field.getText());
        itemsModelList.forEach(itemsModel -> {
            var current = getter.applyAsDouble(itemsModel);
            setter.accept(itemsModel, current + (current * percentage / 100));
        });
    }

    /**
     * The chosen image is the same for every item, so it is converted to bytes once
     * and the resulting array shared across the batch - the previous version
     * re-encoded it inside the per-item loop and raised one error alert per item on
     * failure instead of one for the whole batch.
     */
    private void applyImageUpdate(List<ItemsModel> itemsModelList) throws Exception {
        if (radioDeleteImage.isSelected()) {
            itemsModelList.forEach(itemsModel -> itemsModel.setItem_image(null));
        }
        if (radioAddImage.isSelected()) {
            var imageBytes = imageChoose.convertFxImageToBytes(imageView.getImage());
            itemsModelList.forEach(itemsModel -> itemsModel.setItem_image(imageBytes));
        }
    }

    private double requireNumber(String text) throws UserValidationException {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException | NullPointerException e) {
            throw new UserValidationException(LanguageManager.getInstance().getString("msg.insert.all"));
        }
    }

    private double requirePositiveNumber(String text) throws UserValidationException {
        var value = requireNumber(text);
        if (value <= 0) {
            throw new UserValidationException(LanguageManager.getInstance().getString("item.error.increase.positive"));
        }
        return value;
    }

    private void logError(Exception e) {
        AllAlerts.handleError(LanguageManager.getInstance().getString("item.dialog.update.items.title"), e);
    }

}
