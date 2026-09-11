package com.hamza.account.model.base;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.domain.Area;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@NoArgsConstructor
public abstract class BaseNames extends DForColumnTable {

    private IntegerProperty id = new SimpleIntegerProperty();
    private StringProperty name = new SimpleStringProperty();
    private String tel;
    private String address;
    private String notes;
    private double first_balance;
    private ObjectProperty<Area> area = new SimpleObjectProperty<>();

    // ---- V56 -------------------------------------------------------------------
    // Plain fields, not JavaFX properties: nothing binds them, and new-code-rules
    // wants a model without javafx in it. The five above stay properties only
    // because the invoice tables bind to them.

    /** The only way to send a statement without printing it. */
    private String email;

    /** The e-invoice needs it for a registered party; kept here, not looked up on paper. */
    private String tax_number;

    /**
     * The days agreed with this party. Zero means cash.
     * <p>
     * It is what an ageing report counts from: "ninety days overdue" without an agreed
     * term counts ninety days from the invoice rather than from the day it fell due,
     * and for a party on thirty days those are not the same date.
     */
    private int payment_terms_days;

    /**
     * The day {@link #first_balance} is as at.
     * <p>
     * <b>No view reads it yet.</b> The ledger dates an opening balance by
     * {@code created_at} - the day the party was entered into the system - and moving
     * that onto this column moves a movement in every existing party's history. That is
     * a decision taken on its own; see {@code docs/party-plan.md}. Until then this field
     * is recorded and shown, and changes no figure.
     */
    private LocalDate opening_balance_date;

    /**
     * Whether the party is still dealt with ({@code is_active}). Defaults to true,
     * including for a model built by hand: a party nobody has said anything about is an
     * active one, and a default of false would retire every row saved through a path
     * that forgets it.
     * <p>
     * Named for the reader rather than for the column - Lombok would otherwise generate
     * {@code isIs_active()} - and the column name lives in the DAO's mapper, which is
     * where every other spelling difference between these two tables lives too.
     */
    private boolean active = true;


    public int getId() {
        return id.get();
    }

    public void setId(int id) {
        this.id.set(id);
    }

    public IntegerProperty idProperty() {
        return id;
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

    public String getTel() {
        return tel;
    }

    public void setTel(String tel) {
        this.tel = tel;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public double getFirst_balance() {
        return first_balance;
    }

    public void setFirst_balance(double first_balance) {
        this.first_balance = first_balance;
    }

    public Area getArea() {
        return area.get();
    }

    public void setArea(Area area) {
        this.area.set(area);
    }

    public ObjectProperty<Area> areaProperty() {
        return area;
    }
}
