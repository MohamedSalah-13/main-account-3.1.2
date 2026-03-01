package com.hamza.controlsfx.interfaceData;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to indicate that a specific functionality should be disabled.
 * When applied to methods, it signals that related UI components or actions
 * should be disabled.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Disable {

    String[] value() default {};
}