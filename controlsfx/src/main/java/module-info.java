module com.hamza.controlsfx {
    requires javafx.fxml;
    requires javafx.graphics;
    requires org.controlsfx.controls;
    requires com.jfoenix;
    requires lombok;
    requires annotations;
    requires de.jensd.fx.glyphs.fontawesome;
    requires java.sql;
    requires org.apache.poi.ooxml;
    requires java.desktop;
    requires json.simple;
    requires junrar;
    requires org.apache.logging.log4j;
    requires zip4j;
    requires jasperreports;
    requires org.apache.commons.io;
    requires javafx.swing;

    opens com.hamza.controlsfx.controller to javafx.fxml;
    exports com.hamza.controlsfx.controller;
    exports com.hamza.controlsfx.util;
    exports com.hamza.controlsfx.observer;
    exports com.hamza.controlsfx.others;
    exports com.hamza.controlsfx.alert;
    exports com.hamza.controlsfx.button;
    exports com.hamza.controlsfx.button.api;
    exports com.hamza.controlsfx.button.button_column;
    exports com.hamza.controlsfx.notifications;
    exports com.hamza.controlsfx.table;
    exports com.hamza.controlsfx.interfaceData;
    exports com.hamza.controlsfx.view;
    opens com.hamza.controlsfx.view to javafx.fxml;
    exports com.hamza.controlsfx.menu;
    opens com.hamza.controlsfx.menu to javafx.fxml;
    exports com.hamza.controlsfx.table.colorRow;
    exports com.hamza.controlsfx.table.columnEdit;
    exports com.hamza.controlsfx.table.conditional;
    exports com.hamza.controlsfx.tasks;
    opens com.hamza.controlsfx to javafx.fxml;
    exports com.hamza.controlsfx;
    exports com.hamza.controlsfx.database;
    exports com.hamza.controlsfx.backupPane;
    exports com.hamza.controlsfx.util.crypto;
    exports com.hamza.controlsfx.dateTime;
    exports com.hamza.controlsfx.excel;

    exports com.hamza.controlsfx.type;

    exports com.hamza.controlsfx.language;
    opens com.hamza.controlsfx.language to javafx.fxml;
    exports com.hamza.controlsfx.jasperData;
    opens com.hamza.controlsfx.interfaceData to javafx.fxml;
    opens com.hamza.controlsfx.util to javafx.fxml;
}