package com.hamza.account.test;

import com.hamza.account.table.EditCell;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.function.Function;


public class TableViewCommitOnFocusLoss extends Application {


    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {

        TableView<Person> table = new TableView<>();

        table.getSelectionModel().setCellSelectionEnabled(true);
        table.setEditable(true);

        table.getColumns().add(createColumn("First Name", Person::firstNameProperty));
        table.getColumns().add(createColumn("Last Name", Person::lastNameProperty));
        table.getColumns().add(createColumn("Email", Person::emailProperty));

        table.getItems().addAll(
                new Person("Jacob", "Smith", "jacob.smith@example.com"),
                new Person("Isabella", "Johnson", "isabella.johnson@example.com"),
                new Person("Ethan", "Williams", "ethan.williams@example.com"),
                new Person("Emma", "Jones", "emma.jones@example.com"),
                new Person("Michael", "Brown", "michael.brown@example.com")
        );

        Button showDataButton = new Button("Debug data");
        showDataButton.setOnAction(event -> table.getItems().stream()
                .map(p -> String.format("%s %s", p.getFirstName(), p.getLastName()))
                .forEach(System.out::println));

        Scene scene = new Scene(new BorderPane(table, null, null, showDataButton, null), 880, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private <T> TableColumn<T, String> createColumn(String title, Function<T, StringProperty> property) {
        TableColumn<T, String> col = new TableColumn<>(title);
        col.setCellValueFactory(cellData -> property.apply(cellData.getValue()));
        col.setCellFactory(column -> EditCell.createStringEditCell());
        return col;
    }

    public static class Person {
        private final StringProperty firstName = new SimpleStringProperty();
        private final StringProperty lastName = new SimpleStringProperty();
        private final StringProperty email = new SimpleStringProperty();

        public Person(String firstName, String lastName, String email) {
            setFirstName(firstName);
            setLastName(lastName);
            setEmail(email);
        }

        public final StringProperty firstNameProperty() {
            return this.firstName;
        }

        public final java.lang.String getFirstName() {
            return this.firstNameProperty().get();
        }

        public final void setFirstName(final java.lang.String firstName) {
            this.firstNameProperty().set(firstName);
        }

        public final StringProperty lastNameProperty() {
            return this.lastName;
        }

        public final java.lang.String getLastName() {
            return this.lastNameProperty().get();
        }

        public final void setLastName(final java.lang.String lastName) {
            this.lastNameProperty().set(lastName);
        }

        public final StringProperty emailProperty() {
            return this.email;
        }

        public final java.lang.String getEmail() {
            return this.emailProperty().get();
        }

        public final void setEmail(final java.lang.String email) {
            this.emailProperty().set(email);
        }


    }

}

