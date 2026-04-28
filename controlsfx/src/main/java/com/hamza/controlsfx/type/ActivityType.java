package com.hamza.controlsfx.type;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ActivityType {
    ACTIVE("active"),
    NOT_ACTIVE("inactive");

    private static final Map<String, ActivityType> BY_TYPE =
            Arrays.stream(values())
                    .collect(Collectors.toUnmodifiableMap(ActivityType::getType, Function.identity()));

    private final ReadOnlyStringWrapper type;

    ActivityType(String type) {
        this.type = new ReadOnlyStringWrapper(type);
    }

    public static Optional<ActivityType> getByType(String type) {
        return Optional.ofNullable(BY_TYPE.get(type));
    }

    public String getType() {
        return type.get();
    }

    public ReadOnlyStringProperty typeProperty() {
        return type.getReadOnlyProperty();
    }
}