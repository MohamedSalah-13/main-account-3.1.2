package com.hamza.controlsfx.file.network;

import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

@Log4j2
public class GetPassword extends Application {
    private static final String cmdCommand = "cmd /c";
    private static final Map<String, String> wifiPassord = new HashMap<>();
    private static final File document = new File("wifiPass.txt");
    private static List<String> wifiList = new ArrayList<>();

    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Displays a result box with the password for the specified WiFi network.
     *
     * @param wifiName the name of the WiFi network for which the password is shown
     * @param answer the password to be displayed
     */
    private static void resultBox(String wifiName, String answer) {
        Alert resultBox = new Alert(Alert.AlertType.INFORMATION);
        resultBox.setTitle("Password for " + wifiName);
        resultBox.setHeaderText(null);
        resultBox.setContentText(answer);
        ButtonType export = new ButtonType("Export all Wifi", ButtonBar.ButtonData.FINISH);
        ButtonType OK = new ButtonType("Ok", ButtonBar.ButtonData.OK_DONE);
        resultBox.getDialogPane().getButtonTypes().setAll(export, OK);
        Optional<ButtonType> result = resultBox.showAndWait();
        result.ifPresent(buttonType -> {
            if (buttonType == export) {
                writeData();
                open(document);
            }
        });
    }

    /**
     * Writes the Wi-Fi passwords to a file named "wifiPass.txt".
     * The method iterates over the wifiPassord map, writing each key-value pair
     * in the format "wifi: password" to the file.
     * If an IOException occurs during writing, it logs an informational message.
     */
    public static void writeData() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("wifiPass.txt"))) {
            for (String wifi : wifiPassord.keySet()) {
                writer.write(wifi + ": " + wifiPassord.get(wifi));
                writer.newLine();
            }
        } catch (IOException e) {
            log.info("can not save");
        }

    }

    /**
     * Prints the Wi-Fi passwords stored in the system.
     *
     * This method iterates through all stored Wi-Fi names and prints their
     * corresponding passwords using the logging framework.
     */
    public static void printWifiPassword() {
        for (String wifi : wifiPassord.keySet()) {
            log.info("{}: {}", wifi, wifiPassord.get(wifi));
        }
    }

    /**
     * Prints the list of available Wi-Fi networks to the standard output.
     * The list of Wi-Fi networks is obtained from the static variable wifiList.
     * Each Wi-Fi network is printed on a new line.
     */
    public static void printWifiList() {
        wifiList.forEach(System.out::println);
    }

    /**
     * The getWifiPassword method is responsible for retrieving the list of available Wi-Fi connections
     * and their associated passwords. It internally calls two methods: getWifiList and getPassWordList.
     *
     * getWifiList retrieves the list of available Wi-Fi connections.
     * getPassWordList retrieves the passwords associated with the Wi-Fi connections.
     *
     * This method does not take any parameters and does not return any value. Its main purpose is to
     * orchestrate the fetching of Wi-Fi names and their passwords.
     */
    public static void getWifiPassword() {
        getWifiList();
        getPassWordList();
    }

    /**
     * Executes a system command to retrieve the list of Wi-Fi profiles on the current machine
     * and adds them to a predefined list.
     *
     * This method uses deprecated methods and should be updated to a more secure approach.
     *
     * The method captures the output from the 'netsh wlan show profile' command, reads through
     * the relevant lines to extract Wi-Fi profile names and populates a list with these names.
     *
     * If an error occurs during execution, the error message is logged.
     */
    @SuppressWarnings("deprecation")
    public static void getWifiList() {
        try {
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec(cmdCommand + "netsh wlan show profile ");
            InputStream input = process.getInputStream();
            BufferedReader read = new BufferedReader(new InputStreamReader(input));
            for (int i = 1; i <= 9; i++) {
                read.readLine();
            }
            String line = "";
            while ((line = read.readLine()) != null) {
                line = line.substring(line.indexOf(":") + 1);
                wifiList.add(line);
            }
            input.close();
        } catch (Exception e) {
            log.info("Something went wrong {}", e.getMessage());
        }
    }

    /**
     * Retrieves a list of passwords for available Wi-Fi networks.
     *
     * This method executes a system command to get the security information
     * of Wi-Fi profiles and extracts the password for each network. It stores
     * the Wi-Fi network names along with their respective passwords in a map.
     *
     * The command used is intended for Windows systems and utilizes the
     * 'netsh wlan show profile' command to fetch Wi-Fi profile details.
     *
     * Caution: This method may suppress deprecation warnings and use of
     * 'Runtime.exec()' is required to run system-level commands.
     * Ensure that required permissions are granted.
     *
     * Note: If no password is found for a Wi-Fi network, a default message
     * indicating the absence of a password is stored.
     */
    @SuppressWarnings("deprecation")
    public static void getPassWordList() {
        Process process;
        BufferedReader reader;
        String line;
        String password = "";
        try {
            Runtime runtime = Runtime.getRuntime();
            for (String wifi : wifiList) {
                process = runtime.exec(cmdCommand + "netsh wlan show profile " + wifi + " key=clear");
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Security key"))
                        break;
                }
                if (line != null) {
                    if (line.contains("Present")) {
                        line = reader.readLine();
                        password = line.substring(line.indexOf(":") + 1);
                    } else password = "There is no password for this wifi";
                    wifiPassord.put(wifi, password);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e.getCause());
        }

    }

    /**
     * Opens a given document using the default application associated with its file type
     * on the desktop.
     *
     * @param document the file to be opened
     */
    public static void open(File document) {
        Desktop desktop = Desktop.getDesktop();
        try {
            desktop.open(document);
        } catch (IOException e) {
            log.error(e.getMessage(), e.getCause());
        }
    }

    /**
     * Initializes and displays the main application stage.
     *
     * @param primaryStage the primary stage for this application, onto which the
     *                     application scene can be set. Applications may create
     *                     other stages, if needed, but they will not be primary stages.
     */
    public void start(Stage primaryStage) {
        startBox();
    }

    /**
     * Displays a confirmation alert box at the start of an operation.
     * The alert box contains two buttons: "Scan" and "Exit".
     * If the "Scan" button is pressed, it initiates the process to scan for saved Wi-Fi networks and triggers the necessary actions.
     * If the "Exit" button is pressed, the alert box closes without further action.
     */
    private void startBox() {
        Alert startBox = new Alert(Alert.AlertType.CONFIRMATION);
        startBox.setTitle("Start");
        startBox.setHeaderText("Click Button Scan to scan for saved wifi in your computer");
        ButtonType scanButton = new ButtonType("Scan", ButtonBar.ButtonData.OK_DONE);
        ButtonType exitButton = new ButtonType("Exit", ButtonBar.ButtonData.CANCEL_CLOSE);
        startBox.getDialogPane().getButtonTypes().setAll(scanButton, exitButton);

        Optional<ButtonType> result = startBox.showAndWait();
        result.ifPresent(buttonType -> {
            if (buttonType == scanButton) {
                getWifiPassword();
                this.choiceBox();
            }
        });
    }

    /**
     * Displays a choice dialog containing available Wi-Fi networks and allows the user to select one.
     *
     * The method first retrieves the list of Wi-Fi network names from the `wifiPassord` map and populates
     * a choice dialog with these names. The title of the dialog is set to "Scan successful", and there is
     * no header text. The content text prompts the user to "Choose a wifi".
     *
     * If the user makes a selection and confirms it, the method fetches the corresponding Wi-Fi password
     * from the `wifiPassord` map and passes the selected network name and its password to the `resultBox`
     * method for further processing.
     */
    private void choiceBox() {
        wifiList = new ArrayList<>(wifiPassord.keySet());
        ChoiceDialog<String> dialog = new ChoiceDialog<>("", wifiList);
        dialog.setTitle("Scann successful");
        dialog.setHeaderText("");
        dialog.setContentText("Choose a wifi");
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String answer = wifiPassord.get(result.get());
            resultBox(result.get(), answer);
        }
    }
}


