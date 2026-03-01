package com.hamza.account.openFxml;

import com.hamza.account.Main;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.Pane;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;


@Getter
public class OpenFxmlApplication {

    private final Pane pane;

    /**
     * Initializes an instance of the OpenFxmlApplication with the specified controller.
     *
     * @param controller The controller for which the FXML file will be loaded.
     * @throws IOException If there is an error during loading the FXML file.
     */
    public OpenFxmlApplication(@NotNull Object controller) throws IOException {
        this(controller, true);
    }

    /**
     * Initializes the OpenFxmlApplication with the given controller and a flag to determine
     * whether to remove the controller from the FXMLLoader.
     *
     * @param controller       the controller object to be associated with the FXML file
     * @param removeController a boolean flag indicating whether to remove the controller
     *                         from the FXMLLoader. If true, the controller will not be set
     *                         on the FXMLLoader.
     * @throws IOException if an error occurs during the loading of the FXML file
     */
    public OpenFxmlApplication(@NotNull Object controller, boolean removeController) throws IOException {
        pane = loadPane(controller, removeController);
    }

    /**
     * Loads an FXML file and returns its corresponding Pane object. The controller for the FXML can
     * be optionally set based on the {@code removeController} flag.
     *
     * @param controller       the controller to be used with the FXML file, if {@code removeController} is false
     * @param removeController a flag indicating whether to remove the controller from the FXML loader
     * @return the loaded Pane from the FXML file
     * @throws IOException if an I/O error occurs during loading
     */
    private Pane loadPane(@NotNull Object controller, boolean removeController) throws IOException {
        String fxmlPath = new FxmlPathSetting().getFxmlPath(controller.getClass());
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("view/" + fxmlPath));
        if (removeController) {
            fxmlLoader.setController(controller);
        }
        return fxmlLoader.load();
    }
}
