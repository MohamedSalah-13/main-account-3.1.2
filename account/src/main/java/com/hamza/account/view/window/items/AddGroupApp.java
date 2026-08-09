package com.hamza.account.view.window.items;

import com.hamza.account.controller.others.AddSubGroupController;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.account.view.common.OpenApplicationWithData;

public class AddGroupApp {
    public AddGroupApp(Publisher<String> publisherAddGroup) throws Exception {
        final AddSubGroupController areaController = new AddSubGroupController(publisherAddGroup);
        OpenFxmlApplication openFxmlApplication = new OpenFxmlApplication(areaController);
        new OpenApplicationWithData<>(areaController.getToolbarAccountActionInterface()
                , areaController.createAreaTableView()
                , openFxmlApplication.getPane(), Setting_Language.WORD_ADD_GROUP);
    }
}
