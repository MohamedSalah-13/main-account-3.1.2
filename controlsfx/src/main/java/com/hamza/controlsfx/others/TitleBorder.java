package com.hamza.controlsfx.others;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import org.controlsfx.tools.Borders;

public class TitleBorder {

    /**
     * Adds a titled border with an etched style and shadow to the specified pane.
     *
     * @param pane the Pane to which the border is to be added
     * @param title the title text to display on the border
     * @return a Node object representing the pane with the added border and title
     */
    public static Node titleBorder(Pane pane, String title) {
        return Borders.wrap(pane)
                .etchedBorder().title(title).shadow(Color.GRAY).build()
                .build();
    }

    /**
     * Adds a gray line border around the specified pane.
     *
     * @param pane the pane to which the line border should be added
     * @return the pane with an added line border
     */
    public static Node lineBorder(Pane pane) {
        return Borders.wrap(pane)
                .lineBorder().color(Color.GRAY).build()
                .build();

    }
}
