package com.hamza.account.openFxml;

import com.hamza.account.Main;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.Pane;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;


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
        var resourceBundle = LanguageManager.getInstance().getResourceBundle();
        URL location = Main.class.getResource("view/" + fxmlPath);
        FXMLLoader fxmlLoader = new FXMLLoader(location, resourceBundle);
        if (removeController) {
            bindController(fxmlLoader, location, controller);
        }
        return fxmlLoader.load();
    }

    /**
     * Hands the loader the controller instance the caller already built, whether or
     * not the FXML names a controller of its own.
     * <p>
     * {@code setController} and {@code fx:controller} cannot both be used - the
     * loader refuses the file with "Controller value already specified" - which is
     * why the screens loaded through here carry {@code @FxmlPath} and leave the FXML
     * silent about its controller. That costs the IDE: an {@code @FXML} field has
     * nothing tying it to the file, so a renamed {@code fx:id} is found by the user
     * rather than by the editor.
     * <p>
     * A controller <i>factory</i> has no such conflict. A file that declares
     * {@code fx:controller} asks for its class and gets this instance back, so the
     * declaration is honoured and the object the caller holds - the one
     * {@code OpenApplication} then calls {@code afterSaved()} on - is still the one
     * wired to the controls. Anything else the file asks for (an
     * {@code fx:include} with a controller of its own) is built normally.
     */
    private void bindController(FXMLLoader fxmlLoader, URL location, Object controller) {
        // A path that resolves to nothing is the loader's to report ("Location is not
        // set"), not something to fail on while sniffing the file.
        if (location == null || !declaresController(location)) {
            fxmlLoader.setController(controller);
            return;
        }
        fxmlLoader.setControllerFactory(type -> {
            if (type.isInstance(controller)) {
                return controller;
            }
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot create the controller " + type.getName(), e);
            }
        });
    }

    private static final Pattern FX_CONTROLLER = Pattern.compile("fx:controller\\s*=");

    private boolean declaresController(URL location) {
        try (InputStream in = location.openStream()) {
            return FX_CONTROLLER.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8)).find();
        } catch (IOException e) {
            // The loader is about to open the same file and will report it properly.
            return false;
        }
    }
}
