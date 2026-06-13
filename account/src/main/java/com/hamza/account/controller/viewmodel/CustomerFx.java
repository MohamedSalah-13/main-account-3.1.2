package com.hamza.account.controller.viewmodel;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.domain.Area;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.SelPriceTypeModel;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.*;

import java.math.BigDecimal;

/**
 * ViewModel خاص بطبقة العرض (JavaFX) فقط.
 * يغلّف الـ Domain ويوفر Properties للـ Binding مع TableView.
 */
public class CustomerFx {

    @ColumnData(titleName = NamesTables.CODE)
    private final IntegerProperty id = new SimpleIntegerProperty();
    @ColumnData(titleName = NamesTables.NAME)
    private final StringProperty name = new SimpleStringProperty();
    @ColumnData(titleName = NamesTables.TEL)
    private final StringProperty tel = new SimpleStringProperty();
    @ColumnData(titleName = NamesTables.ADDRESS)
    private final StringProperty address = new SimpleStringProperty();
    @ColumnData(titleName = NamesTables.NOTES)
    private final StringProperty notes = new SimpleStringProperty();
    @ColumnData(titleName = NamesTables.FIRST_BALANCE)
    private final ObjectProperty<BigDecimal> firstBalance = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<Area> area = new SimpleObjectProperty<>();
    private final ObjectProperty<SelPriceTypeModel> selPriceType = new SimpleObjectProperty<>();

    public CustomerFx() {
    }

    /** التحويل من Domain إلى ViewModel */
    public CustomerFx(Customers customer) {
        id.set(customer.getId());
        name.set(customer.getName());
        tel.set(customer.getTel());
        address.set(customer.getAddress());
        notes.set(customer.getNotes());
        firstBalance.set(customer.getFirstBalance());
        area.set(customer.getArea());
        selPriceType.set(customer.getSelPriceType());
    }

    /** التحويل من ViewModel إلى Domain (لإرساله للـ Service/DAO) */
//    public Customers toDomain() {
//        return Customers.builder()
//                .id(id.get())
//                .name(name.get())
//                .tel(tel.get())
//                .address(address.get())
//                .notes(notes.get())
//                .firstBalance(firstBalance.get())
//                .creditLimit(creditLimit.get())
//                .area(area.get())
//                .selPriceType(selPriceType.get())
//                .build();
//    }

    // ===== Getters / Setters / Properties =====
    public int getId() { return id.get(); }
    public void setId(int value) { id.set(value); }
    public IntegerProperty idProperty() { return id; }

    public String getName() { return name.get(); }
    public void setName(String value) { name.set(value); }
    public StringProperty nameProperty() { return name; }

    public String getTel() { return tel.get(); }
    public void setTel(String value) { tel.set(value); }
    public StringProperty telProperty() { return tel; }

    public String getAddress() { return address.get(); }
    public void setAddress(String value) { address.set(value); }
    public StringProperty addressProperty() { return address; }

    public String getNotes() { return notes.get(); }
    public void setNotes(String value) { notes.set(value); }
    public StringProperty notesProperty() { return notes; }

    public BigDecimal getFirstBalance() { return firstBalance.get(); }
    public void setFirstBalance(BigDecimal value) { firstBalance.set(value); }
    public ObjectProperty<BigDecimal> firstBalanceProperty() { return firstBalance; }

    public Area getArea() { return area.get(); }
    public void setArea(Area value) { area.set(value); }
    public ObjectProperty<Area> areaProperty() { return area; }

    public SelPriceTypeModel getSelPriceType() { return selPriceType.get(); }
    public void setSelPriceType(SelPriceTypeModel value) { selPriceType.set(value); }
    public ObjectProperty<SelPriceTypeModel> selPriceTypeProperty() { return selPriceType; }

    @Override
    public String toString() {
        return getName();
    }
}
