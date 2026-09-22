package com.hamza.account.controller.reports;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.productprofile.ProductFeatureAccess;
import com.hamza.account.features.report.ReportCatalog;
import com.hamza.account.features.report.ReportEntry;
import com.hamza.account.features.report.ReportSection;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Every report the program has, in one place, each opening where it has always opened.
 *
 * <p>The sidebar's reports section showed ten reports while more than twenty lived behind buttons
 * on other screens - the items, the expenses, the employees, the party balances - and a person who
 * had not been shown them did not know they existed. This lists them all, grouped, searchable, and
 * only those the reader may open in this edition ({@link ReportCatalog}).</p>
 *
 * <p><b>It hosts nothing.</b> A card runs the report's existing entry point - the sidebar's own button
 * where there is one, the screen's own constructor where the report opened from a button on another
 * screen - after asking for the product feature the way the sidebar does. So a report cannot come to
 * say one thing here and another where it has always been: {@code docs/reports-plan.md} §3.3.</p>
 */
public class ReportsHubController implements AppSettingInterface {

    /** What a card runs: the report's existing entry point. */
    @FunctionalInterface
    public interface Opener {
        void open() throws Exception;
    }

    private final Map<ReportEntry, Opener> openers;
    private final TextField search = new TextField();
    private final VBox sections = new VBox(14);

    public ReportsHubController(Map<ReportEntry, Opener> openers) {
        this.openers = Objects.requireNonNull(openers, "openers");
    }

    @Override
    public Pane pane() {
        Label title = new Label(text("report.hub.title"));
        title.getStyleClass().add("page-title");
        Label subtitle = new Label(text("report.hub.subtitle"));
        subtitle.getStyleClass().add("page-subtitle");
        subtitle.setWrapText(true);

        search.setPromptText(text("report.hub.search"));
        search.setPrefWidth(320);
        search.setId("report-hub-search");
        search.textProperty().addListener((observable, was, now) -> draw());

        HBox bar = new HBox(10, AppIcon.SEARCH.graphic(), search);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("app-card");
        bar.setPadding(new Insets(10));

        ScrollPane scroll = new ScrollPane(sections);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox body = new VBox(12, new VBox(4, title, subtitle), bar, scroll);
        body.setPadding(new Insets(16));
        body.getStyleClass().add("app-container");

        StackPane screen = new StackPane(body);
        screen.getStyleClass().add("app-root");
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("report-hub");
        draw();
        return screen;
    }

    /** Lists what this reader may open, narrowed by the search - asked again on every keystroke. */
    private void draw() {
        ProductFeatureAccess features = ServiceRegistry.get(ProductFeatureAccess.class);
        Map<ReportSection, List<ReportEntry>> visible = ReportCatalog.visible(AuthorizationGuard::isGranted,
                feature -> features == null || features.isEnabled(feature), search.getText(),
                ReportsHubController::text);
        sections.getChildren().clear();
        if (visible.isEmpty()) {
            Label none = new Label(text(search.getText().isBlank() ? "report.hub.none" : "report.hub.no.match"));
            none.getStyleClass().add("form-label");
            sections.getChildren().add(none);
            return;
        }
        visible.forEach((section, entries) -> {
            Label heading = new Label(text(section.titleKey()));
            heading.getStyleClass().add("section-title");
            FlowPane cards = new FlowPane(12, 12);
            for (ReportEntry entry : entries) {
                cards.getChildren().add(card(entry));
            }
            sections.getChildren().add(new VBox(8, heading, cards));
        });
    }

    private VBox card(ReportEntry entry) {
        Label title = new Label(text(entry.titleKey()));
        title.getStyleClass().add("stat-title");
        title.setWrapText(true);
        Label description = new Label(text(entry.descriptionKey()));
        // A class that names its own colour: the theme paints a bare label white, and stat-subtitle is
        // coloured only by dashboard.css, which this screen does not load - so the first draft's
        // descriptions were white on a white card.
        description.getStyleClass().add("form-hint");
        description.setWrapText(true);
        description.setMinHeight(Region.USE_PREF_SIZE);

        Button open = new Button(text("report.hub.open"), AppIcon.REPORT.graphic());
        open.getStyleClass().add("app-primary-button");
        open.setOnAction(event -> open(entry));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        VBox card = new VBox(6, title, description, spacer, open);
        card.getStyleClass().addAll("dashboard-tile", "report-hub-card");
        card.setPrefWidth(300);
        card.setMinHeight(130);
        card.setPadding(new Insets(12));
        card.setId("report-hub-" + entry.name().toLowerCase());
        return card;
    }

    /** As the sidebar does: the product feature first, then the report's own entry point. */
    private void open(ReportEntry entry) {
        try {
            ProductFeatureAccess features = ServiceRegistry.get(ProductFeatureAccess.class);
            if (features != null) {
                features.require(entry.feature());
            }
            Opener opener = openers.get(entry);
            if (opener == null) {
                throw new IllegalStateException("no opener for " + entry);
            }
            opener.open();
        } catch (Exception e) {
            AllAlerts.handleError(text(entry.titleKey()), e);
        }
    }

    @Override
    public String title() {
        return text("report.hub.title");
    }

    @Override
    public boolean resize() {
        return true;
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
