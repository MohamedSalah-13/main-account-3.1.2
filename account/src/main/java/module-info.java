module com.hamza.account {
    requires barbecue;
    requires javafx.swing;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires jasperreports;
    requires org.slf4j;
    requires com.hamza.controlsfx;
    requires lombok;
    requires annotations;
    requires org.apache.logging.log4j;
    requires java.sql;
    requires javafx.fxml;
    requires org.controlsfx.controls;
    requires de.jensd.fx.glyphs.fontawesome;
    requires com.jfoenix;
    requires json.simple;
    requires java.prefs;
    requires com.fasterxml.jackson.databind;
    requires jbcrypt;
    requires java.desktop;
    requires javafx.base;
    requires kernel;
    requires layout;
    requires io;
    requires javafx.graphics;
    requires javafx.controls;
    requires com.github.librepdf.openpdf;
    requires org.json;
    requires com.ibm.icu;
    requires eu.hansolo.tilesfx;
    requires fx.commons;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.feather;
    // Automatic modules (the Flyway jars carry no module-info). flyway.mysql is never
    // referenced in code - it is found through ServiceLoader - but it has to be named here
    // so the module graph actually resolves it, or MySQL support goes missing at runtime.
    requires flyway.core;
    requires flyway.mysql;

    // The migration scripts live in db/migration, which reads as the package name "db.migration",
    // so on the module path they are encapsulated and Flyway's loader gets null back
    // ("Unable to obtain inputstream for resource"). This has to stay unqualified: a qualified
    // "opens ... to flyway.core" is not enough, because Flyway reads them through the class
    // loader, and ClassLoader.getResourceAsStream only sees packages opened unconditionally.
    opens db.migration;

    // Same reason as db.migration: InvoiceThemeColorsTest reads the stylesheets through
    // ClassLoader.getResourceAsStream (not Class.getResourceAsStream, which would need
    // no opening for a same-module resource) so it can check every theme carries the
    // same invoice-type classes without a JavaFX toolkit or a rendered screen.
    opens com.hamza.account.css;
//    requires spring.context;
//    requires spring.beans;
//    requires licensing.base;
//    requires typography;

    exports com.hamza.account.backup;
    opens com.hamza.account.backup to javafx.fxml;
    exports com.hamza.account.controller.login;
    opens com.hamza.account.controller.login to javafx.fxml;
    exports com.hamza.account.perm;
    exports com.hamza.account.model.domain;
    exports com.hamza.account.type;
    exports com.hamza.account.interfaces.impl_invoiceBuy;
    opens com.hamza.account.interfaces.impl_invoiceBuy to javafx.fxml;

    exports com.hamza.account.features.checkbox.api;
    opens com.hamza.account.features.checkbox.api to javafx.fxml;

    exports com.hamza.account.interfaces.impl_dataInterface;
    opens com.hamza.account.interfaces.impl_dataInterface to javafx.fxml;
    exports com.hamza.account.interfaces.api;
    opens com.hamza.account.interfaces.api to javafx.fxml;

    exports com.hamza.account.service;
    opens com.hamza.account.service to javafx.fxml;
    exports com.hamza.account.service.version;
    opens com.hamza.account.service.version to javafx.fxml;
    exports com.hamza.account.features.choiceDialoge;

    exports com.hamza.account.config;
    exports com.hamza.account.interfaces.impl_totalDesgin;
    opens com.hamza.account.interfaces.impl_totalDesgin to javafx.fxml;
    opens com.hamza.account.features.choiceDialoge to javafx.fxml;
    opens com.hamza.account.model.domain to javafx.fxml;
    exports com.hamza.account.table;
    exports com.hamza.account.features.checkbox.impl.setting;
    opens com.hamza.account.features.checkbox.impl.setting to javafx.fxml;
    exports com.hamza.account.model.dao;
    exports com.hamza.account.interfaces.impl_namesDao;
    opens com.hamza.account.interfaces.impl_namesDao to javafx.fxml;
    exports com.hamza.account.interfaces.impl_account;
    opens com.hamza.account.interfaces.impl_account to javafx.fxml;
    exports com.hamza.account.interfaces.names;
    opens com.hamza.account.interfaces.names to javafx.fxml;

    opens com.hamza.account.reportData to javafx.fxml;
    exports com.hamza.account.reportData;
    exports com.hamza.account.interfaces;
    opens com.hamza.account.interfaces to javafx.fxml;
    exports com.hamza.account.openFxml;
    opens com.hamza.account.openFxml;
    exports com.hamza.account.otherSetting;
    opens com.hamza.account.otherSetting to javafx.fxml;
    exports com.hamza.account.controller.dataByName;
    opens com.hamza.account.controller.dataByName to javafx.fxml;
    opens com.hamza.account.controller.invoice to javafx.fxml;
    exports com.hamza.account.controller.invoice;
    opens com.hamza.account.controller.model to javafx.fxml;
    exports com.hamza.account.controller.model;
    exports com.hamza.account.controller.convert_treasury;
    opens com.hamza.account.controller.convert_treasury to javafx.fxml;
    exports com.hamza.account.dash;
    opens com.hamza.account.dash to javafx.fxml;
    exports com.hamza.account.controller.search;
    opens com.hamza.account.controller.main to javafx.fxml;
    exports com.hamza.account.controller.main;
    exports com.hamza.account.controller.setting;
    opens com.hamza.account.controller.setting to javafx.fxml;
    exports com.hamza.account.controller.others;
    opens com.hamza.account.controller.others to javafx.fxml;
    exports com.hamza.account.controller.reports;
    opens com.hamza.account.controller.reports to javafx.fxml;
    exports com.hamza.account.controller.name_account;
    opens com.hamza.account.controller.name_account to javafx.fxml;
    exports com.hamza.account.controller.items;
    opens com.hamza.account.controller.items to javafx.fxml;
    exports com.hamza.account.controller.users;
    opens com.hamza.account.controller.users to javafx.fxml;
    exports com.hamza.account.controller.pricecheck;
    opens com.hamza.account.controller.pricecheck to javafx.fxml;
    opens com.hamza.account.table;
    exports com.hamza.account.view.barcode;
    opens com.hamza.account.view.barcode to javafx.fxml;
    exports com.hamza.account.model.base;
    opens com.hamza.account.model.base to javafx.fxml;
    exports com.hamza.account;
    opens com.hamza.account to javafx.fxml;
    opens com.hamza.account.config;
    exports com.hamza.account.features.key_setting;
    opens com.hamza.account.features.key_setting to javafx.fxml;
    exports com.hamza.account.features.export;
    opens com.hamza.account.features.export;

    // JasperReports reads its data source by bean getter, and the inventory sheet's
    // rows are InventoryRow now rather than ItemsModel. Reflection on a public getter
    // needs the package exported - the same reason com.hamza.account.model.domain is,
    // for the reports that still print item models. Opened as well, unqualified, so a
    // bean introspector that reaches for setAccessible is not a runtime surprise in a
    // code path that only runs when someone presses "طباعة".
    exports com.hamza.account.features.inventory;
    opens com.hamza.account.features.inventory;
    // Same reason as com.hamza.account.features.inventory just above: the transfer
    // history report reads StockTransferReportRow by bean getter, and a package
    // reflection reaches into has to be exported (and opened, since Commons BeanUtils
    // calls setAccessible) or JasperReports fails the moment "طباعة" is pressed - not
    // at compile time, since javac never checks module boundaries against reflection.
    exports com.hamza.account.features.stocktransfer;
    opens com.hamza.account.features.stocktransfer;
    exports com.hamza.account.features.stockcount;
    opens com.hamza.account.features.stockcount;
    opens com.hamza.account.features.events;
    exports com.hamza.account.features.events;
    opens com.hamza.account.document;
    exports com.hamza.account.document;
    exports com.hamza.account.view;
    opens com.hamza.account.view to javafx.fxml;
}
