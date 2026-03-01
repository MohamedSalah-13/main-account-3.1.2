package com.hamza.account.model.domain;

import com.hamza.account.model.base.BaseGroups;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class SubGroups extends BaseGroups {

    private ObjectProperty<MainGroups> mainGroups = new SimpleObjectProperty<>();

    public SubGroups(int id) {
        setId(id);
    }

    public MainGroups getMainGroups() {
        return mainGroups.get();
    }

    public void setMainGroups(MainGroups mainGroups) {
        this.mainGroups.set(mainGroups);
    }

    public ObjectProperty<MainGroups> mainGroupsProperty() {
        return mainGroups;
    }
}
