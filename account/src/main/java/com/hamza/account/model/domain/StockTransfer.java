package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StockTransfer extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private int id;
    private ObjectProperty<Stock> stockFrom = new SimpleObjectProperty<>();
    private ObjectProperty<Stock> stockTo = new SimpleObjectProperty<>();
    @ColumnData(titleName = NamesTables.DATE)
    private LocalDate date;
    private List<StockTransferListItems> transferListItems;

    public Stock getStockFrom() {
        return stockFrom.get();
    }

    public void setStockFrom(Stock stockFrom) {
        this.stockFrom.set(stockFrom);
    }

    public ObjectProperty<Stock> stockFromProperty() {
        return stockFrom;
    }

    public Stock getStockTo() {
        return stockTo.get();
    }

    public void setStockTo(Stock stockTo) {
        this.stockTo.set(stockTo);
    }

    public ObjectProperty<Stock> stockToProperty() {
        return stockTo;
    }
}
