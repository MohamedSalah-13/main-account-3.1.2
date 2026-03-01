package com.hamza.controlsfx.notifications;

import com.hamza.controlsfx.button.api.ActionInterface;
import javafx.scene.Node;


public interface NotificationAction extends ActionInterface {

    String titleName();

    String text();

    Node graphic_design();
}
