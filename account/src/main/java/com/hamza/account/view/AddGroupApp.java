package com.hamza.account.view;

import com.hamza.account.controller.others.AddSubGroupController;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.language.Setting_Language;

public class AddGroupApp {
    public AddGroupApp() throws Exception {
        final AddSubGroupController areaController = new AddSubGroupController();
        OpenFxmlApplication openFxmlApplication = new OpenFxmlApplication(areaController);
        new OpenApplicationWithData<>(areaController.getToolbarAccountActionInterface()
                , areaController.createAreaTableView()
                , openFxmlApplication.getPane(), Setting_Language.WORD_ADD_GROUP);
    }
}
