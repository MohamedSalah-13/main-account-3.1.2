package com.hamza.account.type;

import com.hamza.controlsfx.language.Setting_Language;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProcessesDataType {

    DELETE(Setting_Language.WORD_DELETE),
    INSERT(Setting_Language.WORD_INSERT),
    UPDATE(Setting_Language.WORD_UPDATE);
    private final StringProperty type;

    ProcessesDataType(String string) {
        this.type = new SimpleStringProperty(string);
    }

    public String getType() {
        return type.get();
    }

    public void setType(String type) {
        this.type.set(type);
    }

    public StringProperty typeProperty() {
        return type;
    }
}
