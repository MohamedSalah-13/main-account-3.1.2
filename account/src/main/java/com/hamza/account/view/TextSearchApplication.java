package com.hamza.account.view;

import com.hamza.account.controller.others.TextSearchController;
import com.hamza.account.controller.search.SearchInterface;
import com.hamza.account.openFxml.OpenFxmlApplication;
import javafx.scene.layout.Pane;
import lombok.Getter;

import java.io.IOException;

@Getter
public class TextSearchApplication<T> {

    private final TextSearchController<T> textSearchController;
    private final Pane pane;

    public TextSearchApplication(SearchInterface<T> searchInterface) throws IOException {
        this.textSearchController = new TextSearchController<>(searchInterface);
        pane = new OpenFxmlApplication(this.textSearchController).getPane();
    }
}
