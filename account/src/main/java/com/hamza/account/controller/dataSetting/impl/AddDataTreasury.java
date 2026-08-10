package com.hamza.account.controller.dataSetting.impl;

import com.hamza.account.controller.dataSetting.AddDataInterface;
import com.hamza.account.features.events.TreasuriesChanged;
import com.hamza.account.service.TreasuryService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.observer.AppEvent;
import lombok.Setter;

import java.util.List;

public class AddDataTreasury implements AddDataInterface {
    @Setter
    private TreasuryService treasuryService;

    @Override
    public void addData() {
        //TODO 5/23/2025 9:10 AM m13id: add treasury
    }

    @Override
    public void updateData(String name) throws Exception {

    }

    @Override
    public int deleteData(String name) throws Exception {
        var treasuryByName = treasuryService.getTreasuryByName(name);
        return treasuryService.delete(treasuryByName.getId());
    }

    @Override
    public List<String> listData() throws DaoException {
        return treasuryService.listTreasuryModelNames();
    }

    @Override
    public String titlePane() {
        return "الخزينة";
    }

    @Override
    public Class<? extends AppEvent> refreshOn() {
        return TreasuriesChanged.class;
    }
}
