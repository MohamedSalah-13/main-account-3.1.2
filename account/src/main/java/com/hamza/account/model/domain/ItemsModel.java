package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.UnitExtends;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor
public class ItemsModel extends UnitExtends {

    @ColumnData(titleName = NamesTables.CODE)
    private IntegerProperty id = new SimpleIntegerProperty();
    @ColumnData(titleName = NamesTables.STRING)
    private StringProperty barcode = new SimpleStringProperty();
    @ColumnData(titleName = NamesTables.NAME_ITEM)
    private StringProperty nameItem = new SimpleStringProperty();
    @ColumnData(titleName = NamesTables.BUY_PRICE)
    private DoubleProperty buyPrice = new SimpleDoubleProperty();
    @ColumnData(titleName = NamesTables.SEL_PRICE)
    private DoubleProperty selPrice1 = new SimpleDoubleProperty();
    @ColumnData(titleName = NamesTables.SEL_PRICE + "2")
    private DoubleProperty selPrice2 = new SimpleDoubleProperty();
    @ColumnData(titleName = NamesTables.SEL_PRICE + "3")
    private DoubleProperty selPrice3 = new SimpleDoubleProperty();
    @ColumnData(titleName = NamesTables.MINI_QUANTITY)
    private DoubleProperty mini_quantity = new SimpleDoubleProperty();
    @ColumnData(titleName = NamesTables.FIRST_BALANCE)
    private DoubleProperty firstBalanceForStock = new SimpleDoubleProperty();

    private ObjectProperty<SubGroups> subGroups = new SimpleObjectProperty<>();
    private ObjectProperty<Stock> itemStock = new SimpleObjectProperty<>();
    private byte[] item_image;

    private BooleanProperty activeItem = new SimpleBooleanProperty();
    private boolean hasValidate;
    private boolean hasPackage;
    private int numberValidityDays;
    private int alertDaysBeforeExpiry;

    private List<ItemsUnitsModel> itemsUnitsModelList=new ArrayList<>();;
    private List<Items_Package> items_packageList=new ArrayList<>();

    @ColumnData(titleName = NamesTables.SUM_ALL_BALANCE)
    private DoubleProperty sumAllBalance = new SimpleDoubleProperty();
    private DoubleProperty sumPurchase = new SimpleDoubleProperty();
    private DoubleProperty sumSales = new SimpleDoubleProperty();
    private DoubleProperty sumPurchaseRe = new SimpleDoubleProperty();
    private DoubleProperty sumSalesRe = new SimpleDoubleProperty();
    private DoubleProperty fromStock = new SimpleDoubleProperty();
    private DoubleProperty toStock = new SimpleDoubleProperty();
    private DoubleProperty sumAllBalanceByBuyPrice = new SimpleDoubleProperty();
    private DoubleProperty sumAllBalanceBySelPrice = new SimpleDoubleProperty();

    public ItemsModel(Integer id) {
        initialize(id, null, null);
    }

    public ItemsModel(Integer id, String name) {
        initialize(id, null, name);
    }

    public ItemsModel(String barcode, String name) {
        initialize(null, barcode, name);
    }

    public ItemsModel(Integer id, String barcode, String name) {
        initialize(id, barcode, name);
    }

    private void initialize(Integer id, String barcode, String name) {
        if (id != null) {
            this.id = new SimpleIntegerProperty(id);
        }
        if (barcode != null) {
            this.barcode = new SimpleStringProperty(barcode);
        }
        if (name != null) {
            this.nameItem = new SimpleStringProperty(name);
        }
    }

    public int getId() {
        return id.get();
    }

    public void setId(int id) {
        this.id.set(id);
    }

    public IntegerProperty idProperty() {
        return id;
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

    public String getNameItem() {
        return nameItem.get();
    }

    public void setNameItem(String nameItem) {
        this.nameItem.set(nameItem);
    }

    public StringProperty nameItemProperty() {
        return nameItem;
    }

    public double getBuyPrice() {
        return buyPrice.get();
    }

    public void setBuyPrice(double buyPrice) {
        this.buyPrice.set(buyPrice);
    }

    public DoubleProperty buyPriceProperty() {
        return buyPrice;
    }

    public double getSelPrice1() {
        return selPrice1.get();
    }

    public void setSelPrice1(double selPrice1) {
        this.selPrice1.set(selPrice1);
    }

    public DoubleProperty selPrice1Property() {
        return selPrice1;
    }

    public double getSelPrice2() {
        return selPrice2.get();
    }

    public void setSelPrice2(double selPrice2) {
        this.selPrice2.set(selPrice2);
    }

    public DoubleProperty selPrice2Property() {
        return selPrice2;
    }

    public double getSelPrice3() {
        return selPrice3.get();
    }

    public void setSelPrice3(double selPrice3) {
        this.selPrice3.set(selPrice3);
    }

    public DoubleProperty selPrice3Property() {
        return selPrice3;
    }

    public double getMini_quantity() {
        return mini_quantity.get();
    }

    public void setMini_quantity(double mini_quantity) {
        this.mini_quantity.set(mini_quantity);
    }

    public DoubleProperty mini_quantityProperty() {
        return mini_quantity;
    }

    public SubGroups getSubGroups() {
        return subGroups.get();
    }

    public void setSubGroups(SubGroups subGroups) {
        this.subGroups.set(subGroups);
    }

    public ObjectProperty<SubGroups> subGroupsProperty() {
        return subGroups;
    }

    public Stock getItemStock() {
        return itemStock.get();
    }

    public void setItemStock(Stock itemStock) {
        this.itemStock.set(itemStock);
    }

    public ObjectProperty<Stock> itemStockProperty() {
        return itemStock;
    }

    public double getFirstBalanceForStock() {
        return firstBalanceForStock.get();
    }

    public void setFirstBalanceForStock(double firstBalanceForStock) {
        this.firstBalanceForStock.set(firstBalanceForStock);
    }

    public DoubleProperty firstBalanceForStockProperty() {
        return firstBalanceForStock;
    }

    public double getSumAllBalance() {
        return sumAllBalance.get();
    }

    public void setSumAllBalance(double sumAllBalance) {
        this.sumAllBalance.set(sumAllBalance);
    }

    public DoubleProperty sumAllBalanceProperty() {
        return sumAllBalance;
    }

    public double getSumPurchase() {
        return sumPurchase.get();
    }

    public void setSumPurchase(double sumPurchase) {
        this.sumPurchase.set(sumPurchase);
    }

    public DoubleProperty sumPurchaseProperty() {
        return sumPurchase;
    }

    public double getSumSales() {
        return sumSales.get();
    }

    public void setSumSales(double sumSales) {
        this.sumSales.set(sumSales);
    }

    public DoubleProperty sumSalesProperty() {
        return sumSales;
    }

    public double getSumPurchaseRe() {
        return sumPurchaseRe.get();
    }

    public void setSumPurchaseRe(double sumPurchaseRe) {
        this.sumPurchaseRe.set(sumPurchaseRe);
    }

    public DoubleProperty sumPurchaseReProperty() {
        return sumPurchaseRe;
    }

    public double getSumSalesRe() {
        return sumSalesRe.get();
    }

    public void setSumSalesRe(double sumSalesRe) {
        this.sumSalesRe.set(sumSalesRe);
    }

    public DoubleProperty sumSalesReProperty() {
        return sumSalesRe;
    }

    public double getFromStock() {
        return fromStock.get();
    }

    public void setFromStock(double fromStock) {
        this.fromStock.set(fromStock);
    }

    public DoubleProperty fromStockProperty() {
        return fromStock;
    }

    public double getToStock() {
        return toStock.get();
    }

    public void setToStock(double toStock) {
        this.toStock.set(toStock);
    }

    public DoubleProperty toStockProperty() {
        return toStock;
    }

    public double getSumAllBalanceByBuyPrice() {
        return sumAllBalanceByBuyPrice.get();
    }

    public void setSumAllBalanceByBuyPrice(double sumAllBalanceByBuyPrice) {
        this.sumAllBalanceByBuyPrice.set(sumAllBalanceByBuyPrice);
    }

    public DoubleProperty sumAllBalanceByBuyPriceProperty() {
        return sumAllBalanceByBuyPrice;
    }

    public double getSumAllBalanceBySelPrice() {
        return sumAllBalanceBySelPrice.get();
    }

    public void setSumAllBalanceBySelPrice(double sumAllBalanceBySelPrice) {
        this.sumAllBalanceBySelPrice.set(sumAllBalanceBySelPrice);
    }

    public DoubleProperty sumAllBalanceBySelPriceProperty() {
        return sumAllBalanceBySelPrice;
    }

    public boolean isActiveItem() {
        return activeItem.get();
    }

    public void setActiveItem(boolean activeItem) {
        this.activeItem.set(activeItem);
    }

    public BooleanProperty activeItemProperty() {
        return activeItem;
    }
}
