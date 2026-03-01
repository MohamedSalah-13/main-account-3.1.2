package com.hamza.account.openFxml;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to define the FXML file path associated with a class or field.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface FxmlPath {

    /**
     * Represents the file path associated with the FXML resource for a given class or field.
     *
     * @return the file path to the FXML resource
     */
    @NotNull String pathFile();

}
