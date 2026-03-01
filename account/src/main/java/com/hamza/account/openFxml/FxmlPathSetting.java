package com.hamza.account.openFxml;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;

public class FxmlPathSetting {

    /**
     * Retrieves the table (file path) specified in the {@link FxmlPath} annotation of the given class.
     *
     * @param <S>    the type of the class
     * @param aClass the class whose {@link FxmlPath} annotation is to be processed
     * @return the file path specified in the {@link FxmlPath} annotation, or null if the annotation is not present
     */
    public <S> String getFxmlPath(@NotNull Class<S> aClass) {
        Annotation annotation = aClass.getAnnotation(FxmlPath.class);
        if (annotation instanceof FxmlPath fxmlPath) {
            return fxmlPath.pathFile();
        }
        return null;
    }
}
