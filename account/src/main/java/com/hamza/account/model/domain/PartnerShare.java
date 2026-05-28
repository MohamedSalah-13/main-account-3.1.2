package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@NoArgsConstructor
public class PartnerShare extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private int id;

    private int capitalId;

    @ColumnData(titleName = "رأس المال")
    private final StringProperty capitalName = new SimpleStringProperty();

    private int partnerId;

    @ColumnData(titleName = "اسم الشريك")
    private final StringProperty partnerName = new SimpleStringProperty();

    @ColumnData(titleName = "مبلغ الحصة")
    private final DoubleProperty shareAmount = new SimpleDoubleProperty(0.0);

    @ColumnData(titleName = "نسبة الحصة %")
    private final DoubleProperty sharePercentage = new SimpleDoubleProperty(0.0);

    @ColumnData(titleName = "نسبة الربح %")
    private final DoubleProperty profitPercentage = new SimpleDoubleProperty(0.0);

    @ColumnData(titleName = "نسبة الخسارة %")
    private final DoubleProperty lossPercentage = new SimpleDoubleProperty(0.0);

    @ColumnData(titleName = "تاريخ المساهمة")
    private final ObjectProperty<LocalDate> contributionDate = new SimpleObjectProperty<>();

    @ColumnData(titleName = "شريك مدير")
    private final BooleanProperty managingPartner = new SimpleBooleanProperty(false);

    private final StringProperty notes = new SimpleStringProperty();

    private int userId;

    // ===== capitalName =====
    public String getCapitalName() { return capitalName.get(); }
    public void setCapitalName(String capitalName) { this.capitalName.set(capitalName); }
    public StringProperty capitalNameProperty() { return capitalName; }

    // ===== partnerName =====
    public String getPartnerName() { return partnerName.get(); }
    public void setPartnerName(String partnerName) { this.partnerName.set(partnerName); }
    public StringProperty partnerNameProperty() { return partnerName; }

    // ===== shareAmount =====
    public double getShareAmount() { return shareAmount.get(); }
    public void setShareAmount(double shareAmount) { this.shareAmount.set(shareAmount); }
    public DoubleProperty shareAmountProperty() { return shareAmount; }

    // ===== sharePercentage =====
    public double getSharePercentage() { return sharePercentage.get(); }
    public void setSharePercentage(double sharePercentage) { this.sharePercentage.set(sharePercentage); }
    public DoubleProperty sharePercentageProperty() { return sharePercentage; }

    // ===== profitPercentage =====
    public double getProfitPercentage() { return profitPercentage.get(); }
    public void setProfitPercentage(double profitPercentage) { this.profitPercentage.set(profitPercentage); }
    public DoubleProperty profitPercentageProperty() { return profitPercentage; }

    // ===== lossPercentage =====
    public double getLossPercentage() { return lossPercentage.get(); }
    public void setLossPercentage(double lossPercentage) { this.lossPercentage.set(lossPercentage); }
    public DoubleProperty lossPercentageProperty() { return lossPercentage; }

    // ===== contributionDate =====
    public LocalDate getContributionDate() { return contributionDate.get(); }
    public void setContributionDate(LocalDate contributionDate) { this.contributionDate.set(contributionDate); }
    public ObjectProperty<LocalDate> contributionDateProperty() { return contributionDate; }

    // ===== managingPartner =====
    public boolean isManagingPartner() { return managingPartner.get(); }
    public void setManagingPartner(boolean managingPartner) { this.managingPartner.set(managingPartner); }
    public BooleanProperty managingPartnerProperty() { return managingPartner; }

    // ===== notes =====
    public String getNotes() { return notes.get(); }
    public void setNotes(String notes) { this.notes.set(notes); }
    public StringProperty notesProperty() { return notes; }
}
