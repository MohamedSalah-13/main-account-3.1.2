package com.hamza.account.controller.items;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.controlsfx.others.DoubleSetting;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * The item's own scalar fields - name, barcode, prices, quantities, the active
 * and expiry-tracking flags - as one place instead of nine {@code TextField}s
 * and two {@code CheckBox}es each read, written and cleared by hand at three
 * separate call sites.
 * <p>
 * {@code AddItemController} binds each field bidirectionally to its control
 * once, in {@code bindItemForm()}; from then on this class is the source of
 * truth and the controls just mirror it. Adding a new scalar field to the item
 * means one more property here, one more bidirectional bind, and one more line
 * in {@link #load} and {@link #applyTo} - not a matching edit in
 * {@code selectData}, {@code insertData} and the {@code clearAll} call, which
 * is how {@code txtSelPrice2}/{@code txtSelPrice3} were once left out of the
 * clear and carried a saved item's prices onto the next one entered.
 * <p>
 * Deliberately not owned here: the main/sub group and unit combos, the units
 * table, the extra-barcode list and the image. Each of those needs a database
 * lookup or a second widget to resolve, which is the controller's job, not a
 * value holder's.
 * <p>
 * Holds no reference to any {@code javafx.scene.control} type, so it can be
 * exercised - see {@code ItemFormTest} - without a JavaFX toolkit running.
 */
public final class ItemForm {

    private final StringProperty barcode = new SimpleStringProperty("");
    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty buyPrice = new SimpleStringProperty("");
    private final StringProperty selPrice1 = new SimpleStringProperty("");
    private final StringProperty selPrice2 = new SimpleStringProperty("");
    private final StringProperty selPrice3 = new SimpleStringProperty("");
    private final StringProperty miniQuantity = new SimpleStringProperty("");
    private final StringProperty firstBalance = new SimpleStringProperty("");
    private final BooleanProperty active = new SimpleBooleanProperty(true);
    private final BooleanProperty hasValidate = new SimpleBooleanProperty(false);
    private final StringProperty validityDays = new SimpleStringProperty("0");
    private final StringProperty alertBeforeExpiry = new SimpleStringProperty("0");

    /**
     * Loads every field this form owns from a saved item - replaces the block
     * of nine {@code setText}/{@code setSelected} calls that used to open
     * {@code AddItemController.selectData()}.
     */
    public void load(ItemsModel model) {
        barcode.set(nullToEmpty(model.getBarcode()));
        name.set(nullToEmpty(model.getNameItem()));
        buyPrice.set(String.valueOf(model.getBuyPrice()));
        selPrice1.set(String.valueOf(model.getSelPrice1()));
        selPrice2.set(String.valueOf(model.getSelPrice2()));
        selPrice3.set(String.valueOf(model.getSelPrice3()));
        miniQuantity.set(String.valueOf(model.getMini_quantity()));
        firstBalance.set(String.valueOf(model.getFirstBalanceForStock()));
        active.set(model.isActiveItem());
        hasValidate.set(model.isHasValidate());
        validityDays.set(String.valueOf(model.getNumberValidityDays()));
        alertBeforeExpiry.set(String.valueOf(model.getAlertDaysBeforeExpiry()));
    }

    /**
     * Parses and writes every field this form owns onto {@code model}. Text is
     * trimmed and a blank number defaults to zero, exactly as
     * {@code insertData()} read the controls before.
     */
    public void applyTo(ItemsModel model) {
        model.setBarcode(nullToEmpty(getBarcode()).trim());
        model.setNameItem(nullToEmpty(getName()).trim());
        model.setBuyPrice(parsedBuyPrice());
        model.setSelPrice1(parsedSelPrice1());
        model.setSelPrice2(DoubleSetting.parseDoubleOrDefault(getSelPrice2()));
        model.setSelPrice3(DoubleSetting.parseDoubleOrDefault(getSelPrice3()));
        model.setMini_quantity(DoubleSetting.parseDoubleOrDefault(getMiniQuantity()));
        model.setFirstBalanceForStock(DoubleSetting.parseDoubleOrDefault(getFirstBalance()));
        model.setActiveItem(isActive());
        model.setHasValidate(isHasValidate());
        model.setNumberValidityDays(parseIntOrZero(getValidityDays()));
        model.setAlertDaysBeforeExpiry(parseIntOrZero(getAlertBeforeExpiry()));
    }

    /**
     * Blanks the fields a saved item leaves behind for the next one - replaces
     * the {@code clearAll(...)} call at the end of a non-duplicate save.
     * <p>
     * {@code active}, {@code hasValidate} and the two validity-day fields are
     * deliberately left alone: this blanks the item just entered, it does not
     * change what the next one starts with.
     */
    public void reset() {
        barcode.set("");
        name.set("");
        buyPrice.set("");
        selPrice1.set("");
        selPrice2.set("");
        selPrice3.set("");
        miniQuantity.set("");
        firstBalance.set("");
    }

    public boolean isNameBlank() {
        return getName() == null || getName().isBlank();
    }

    public boolean isBuyPriceNotPositive() {
        return parsedBuyPrice() <= 0;
    }

    public boolean isBarcodeBlank() {
        String value = getBarcode();
        return value == null || value.trim().isEmpty() || value.trim().equals("0");
    }

    /** Longer than the one length the screen accepts - see {@link BarcodeAvailability#MAX_LENGTH}. */
    public boolean isBarcodeTooLong() {
        String value = getBarcode();
        return value != null && value.trim().length() > BarcodeAvailability.MAX_LENGTH;
    }

    /**
     * Whether the first selling price fails to clear the buy price. Zero
     * markup is not a price; it is a loss on paper the moment the item moves.
     */
    public boolean isSellPriceNotAboveBuy() {
        return parsedSelPrice1() <= parsedBuyPrice();
    }

    /**
     * Whether the fields this form owns are missing something the save button
     * requires. The group and unit combos are the controller's own state, so
     * the controller composes this with its own checks rather than this
     * binding claiming to speak for the whole screen.
     */
    public BooleanBinding incompleteProperty() {
        return Bindings.createBooleanBinding(
                () -> isNameBlank() || isBuyPriceNotPositive(),
                name, buyPrice);
    }

    private double parsedBuyPrice() {
        return DoubleSetting.parseDoubleOrDefault(getBuyPrice());
    }

    private double parsedSelPrice1() {
        return DoubleSetting.parseDoubleOrDefault(getSelPrice1());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int parseIntOrZero(String text) {
        try {
            return text == null ? 0 : Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getBarcode() {
        return barcode.get();
    }

    public void setBarcode(String barcode) {
        this.barcode.set(barcode);
    }

    public StringProperty barcodeProperty() {
        return barcode;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public StringProperty nameProperty() {
        return name;
    }

    public String getBuyPrice() {
        return buyPrice.get();
    }

    public void setBuyPrice(String buyPrice) {
        this.buyPrice.set(buyPrice);
    }

    public StringProperty buyPriceProperty() {
        return buyPrice;
    }

    public String getSelPrice1() {
        return selPrice1.get();
    }

    public void setSelPrice1(String selPrice1) {
        this.selPrice1.set(selPrice1);
    }

    public StringProperty selPrice1Property() {
        return selPrice1;
    }

    public String getSelPrice2() {
        return selPrice2.get();
    }

    public void setSelPrice2(String selPrice2) {
        this.selPrice2.set(selPrice2);
    }

    public StringProperty selPrice2Property() {
        return selPrice2;
    }

    public String getSelPrice3() {
        return selPrice3.get();
    }

    public void setSelPrice3(String selPrice3) {
        this.selPrice3.set(selPrice3);
    }

    public StringProperty selPrice3Property() {
        return selPrice3;
    }

    public String getMiniQuantity() {
        return miniQuantity.get();
    }

    public void setMiniQuantity(String miniQuantity) {
        this.miniQuantity.set(miniQuantity);
    }

    public StringProperty miniQuantityProperty() {
        return miniQuantity;
    }

    public String getFirstBalance() {
        return firstBalance.get();
    }

    public void setFirstBalance(String firstBalance) {
        this.firstBalance.set(firstBalance);
    }

    public StringProperty firstBalanceProperty() {
        return firstBalance;
    }

    public boolean isActive() {
        return active.get();
    }

    public void setActive(boolean active) {
        this.active.set(active);
    }

    public BooleanProperty activeProperty() {
        return active;
    }

    public boolean isHasValidate() {
        return hasValidate.get();
    }

    public void setHasValidate(boolean hasValidate) {
        this.hasValidate.set(hasValidate);
    }

    public BooleanProperty hasValidateProperty() {
        return hasValidate;
    }

    public String getValidityDays() {
        return validityDays.get();
    }

    public void setValidityDays(String validityDays) {
        this.validityDays.set(validityDays);
    }

    public StringProperty validityDaysProperty() {
        return validityDays;
    }

    public String getAlertBeforeExpiry() {
        return alertBeforeExpiry.get();
    }

    public void setAlertBeforeExpiry(String alertBeforeExpiry) {
        this.alertBeforeExpiry.set(alertBeforeExpiry);
    }

    public StringProperty alertBeforeExpiryProperty() {
        return alertBeforeExpiry;
    }
}
