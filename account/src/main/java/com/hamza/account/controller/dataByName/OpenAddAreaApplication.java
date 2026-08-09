package com.hamza.account.controller.dataByName;

import com.hamza.account.view.OpenApplicationWithData;

public class OpenAddAreaApplication<T> {

    public OpenAddAreaApplication(AreaInterface<T> areaInterface, String textName) throws Exception {
        final AddAreaController<T> areaController = new AddAreaController<>(areaInterface);
        new OpenApplicationWithData<>(areaController.getToolbarAccountActionInterface()
                , areaController.createAreaTableView()
                , areaController, textName);
    }
}
