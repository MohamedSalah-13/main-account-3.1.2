package com.hamza.controlsfx.filechooser;

import com.hamza.controlsfx.file.Extensions;
import com.hamza.controlsfx.language.Error_Text_Show;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class ChooseImage {

    /**
     * A constant error message indicating that no such file or directory was found.
     */
    private static final String ERROR_MESSAGE = Error_Text_Show.NO_SUCH_FILE_OR_DIRECTORY;

    /**
     * Opens a file chooser dialog to select a PNG image file,
     * and loads the selected image into the provided ImageView.
     *
     * @param targetImageView the ImageView instance where the selected image will be displayed
     * @return the absolute path of the selected image file, or null if no file was selected
     * @throws FileNotFoundException if the selected file does not exist
     */
    public String selectImage(ImageView targetImageView) throws FileNotFoundException {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(Extensions.FILTER_IMAGE_PNG);
        File selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null) {
            return loadImage(targetImageView, selectedFile);
        }
        return null;
    }

    /**
     * Loads an image from the specified file and sets it to the given ImageView.
     *
     * @param imageView the ImageView where the image will be displayed
     * @param file the file from which the image will be loaded
     * @return the absolute path of the loaded file
     * @throws FileNotFoundException if the specified file does not exist
     */
    private String loadImage(ImageView imageView, File file) throws FileNotFoundException {
        try {
            InputStream stream = new FileInputStream(file.getAbsolutePath());
            Image image = new Image(stream);
            imageView.setImage(image);
            return file.getAbsolutePath();
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException(ERROR_MESSAGE);
        }
    }
}
