package com.hamza.account.controller.name_account;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The coloured heading a customer or supplier screen opens with: icon, title, subtitle.
 *
 * <p>One builder for the add/edit form, the accounts screen and the collection dialog, so
 * the three cannot drift into three slightly different headers. The list screen declares
 * the same nodes in {@code main-tableview.fxml} with the same style classes. The colours
 * are in the theme and follow the {@code party-customers}/{@code party-suppliers} class on
 * the screen's root, so nothing here says which of the two it is.</p>
 */
final class PartyIdentityHeader {

    private PartyIdentityHeader() {
    }

    static HBox of(PartyFormProfile profile) {
        HBox iconBox = new HBox(profile.icon().graphic(32));
        iconBox.setAlignment(Pos.CENTER);
        iconBox.getStyleClass().add("party-screen-icon-box");

        Label title = new Label(profile.title());
        title.getStyleClass().add("party-screen-title");
        Label subtitle = new Label(profile.subtitle());
        subtitle.getStyleClass().add("party-screen-subtitle");
        subtitle.setWrapText(true);

        VBox textBox = new VBox(3, title, subtitle);
        textBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        HBox header = new HBox(14, iconBox, textBox);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMaxWidth(Double.MAX_VALUE);
        header.getStyleClass().add("party-screen-header");
        return header;
    }
}
