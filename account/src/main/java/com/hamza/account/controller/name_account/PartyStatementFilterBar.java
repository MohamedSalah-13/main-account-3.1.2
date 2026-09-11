package com.hamza.account.controller.name_account;

import com.hamza.account.config.AppIcon;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.statement.PartyMovementKind;
import com.hamza.account.features.party.statement.PartyStatementFilter;
import com.hamza.account.features.party.statement.PartyStatementOptions;
import com.hamza.account.features.party.statement.PartyStatementTreasuryOption;
import com.hamza.account.features.party.statement.PartyStatementUserOption;
import com.hamza.account.features.party.statement.StatementPeriod;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.others.DoubleSetting;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import static com.hamza.controlsfx.others.Utils.setOptionalNumberFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * The filter bar of a party statement: the controls, and nothing that decides anything.
 * <p>
 * It reads its controls into a {@link PartyStatementFilter} and hands it to a listener. Which rows
 * that filter then hides, and what it must leave alone, is the filter's own business - see its
 * javadoc for the rule about the two balances, which is the one a later filter will break.
 * <p>
 * <b>Why a class of its own rather than thirty fields on the screen.</b> Two screens want the same
 * bar, the statement and the accounts list; and a bar that lives beside its screen ends up with the
 * screen's state threaded through it. Here the only thing that leaves is a filter.
 * <p>
 * The period presets come from {@link StatementPeriod}, which is where the date arithmetic is - an
 * off-by-one in a quarter looks exactly like a quarter on screen. The chosen period, and the page
 * size, are remembered per user in {@code Preferences}: someone who opens a statement every morning
 * on "this month" should not re-pick it every morning. Nothing else is remembered, because a
 * remembered treasury filter is a statement that is quietly missing rows.
 */
public final class PartyStatementFilterBar extends VBox {

    private static final String REMEMBERED_PERIOD = "party.statement.period";

    private final PartyKind kind;
    private final int partyId;
    private final Preferences preferences =
            Preferences.userNodeForPackage(PartyStatementFilterBar.class);

    private final DatePicker dateFrom = new DatePicker();
    private final DatePicker dateTo = new DatePicker();
    private final ComboBox<PartyMovementKind> comboKind = new ComboBox<>();
    private final ComboBox<PartyStatementTreasuryOption> comboTreasury = new ComboBox<>();
    private final ComboBox<PartyStatementUserOption> comboUser = new ComboBox<>();
    private final TextField amountFrom = new TextField();
    private final TextField amountTo = new TextField();
    private final TextField search = new TextField();
    private final CheckBox deferredOnly = new CheckBox(text("party.statement.filter.deferred"));

    /** Replaced when the period is ALL: the party's first movement, which only the database knows. */
    private LocalDate earliestMovement = LocalDate.now();

    private Consumer<PartyStatementFilter> onSearch = filter -> { };
    private boolean loading;

    public PartyStatementFilterBar(PartyKind kind, int partyId) {
        super(8);
        this.kind = kind;
        this.partyId = partyId;
        setId("party-statement-filters");
        getStyleClass().add("app-card");
        getChildren().addAll(periodRow(), filterRow());
    }

    /** Called whenever the user asks for a different set of rows. */
    public void setOnSearch(Consumer<PartyStatementFilter> listener) {
        this.onSearch = listener == null ? filter -> { } : listener;
    }

    /**
     * Fills the combos, and opens on the period this user last chose.
     *
     * @param earliest the party's first movement, for the "all" preset
     */
    public void initialise(PartyStatementOptions options, LocalDate earliest) {
        this.earliestMovement = earliest == null ? LocalDate.now() : earliest;
        loading = true;
        try {
            comboTreasury.getItems().setAll(all(new PartyStatementTreasuryOption(0, "", true)));
            comboTreasury.getItems().addAll(options.treasuries());
            comboTreasury.getSelectionModel().selectFirst();

            comboUser.getItems().setAll(all(new PartyStatementUserOption(0, "")));
            comboUser.getItems().addAll(options.users());
            comboUser.getSelectionModel().selectFirst();
        } finally {
            loading = false;
        }
        applyPeriod(rememberedPeriod());
    }

    /** The filter the controls describe right now. */
    public PartyStatementFilter filter(int page, int pageSize) {
        LocalDate from = dateFrom.getValue() == null ? earliestMovement : dateFrom.getValue();
        LocalDate to = dateTo.getValue() == null ? LocalDate.now() : dateTo.getValue();
        if (from.isAfter(to)) {
            from = to;
        }
        Set<PartyMovementKind> kinds = comboKind.getValue() == null
                ? Set.of() : Set.of(comboKind.getValue());
        return new PartyStatementFilter(kind, partyId, from, to, kinds,
                idOrNull(comboTreasury.getValue() == null ? 0 : comboTreasury.getValue().id()),
                idOrNull(comboUser.getValue() == null ? 0 : comboUser.getValue().id()),
                amount(amountFrom), amount(amountTo), search.getText(),
                deferredOnly.isSelected(), page, pageSize);
    }

    /** Back to the party's whole history, with every other filter cleared. */
    public void reset() {
        loading = true;
        try {
            comboKind.getSelectionModel().select(null);
            comboTreasury.getSelectionModel().selectFirst();
            comboUser.getSelectionModel().selectFirst();
            amountFrom.clear();
            amountTo.clear();
            search.clear();
            deferredOnly.setSelected(false);
        } finally {
            loading = false;
        }
        applyPeriod(StatementPeriod.ALL);
    }

    private void applyPeriod(StatementPeriod period) {
        LocalDate today = LocalDate.now();
        loading = true;
        try {
            dateFrom.setValue(period.needsEarliestMovement() ? earliestMovement : period.from(today));
            dateTo.setValue(period.to(today));
        } finally {
            loading = false;
        }
        preferences.put(REMEMBERED_PERIOD, period.name());
        fire();
    }

    private StatementPeriod rememberedPeriod() {
        try {
            return StatementPeriod.valueOf(preferences.get(REMEMBERED_PERIOD, StatementPeriod.ALL.name()));
        } catch (IllegalArgumentException renamedOrRemoved) {
            return StatementPeriod.ALL;
        }
    }

    /** The one-click periods, and the two pickers for anything else. */
    private FlowPane periodRow() {
        FlowPane row = new FlowPane(8, 8);
        row.setAlignment(Pos.CENTER_LEFT);
        for (StatementPeriod period : StatementPeriod.values()) {
            Button button = new Button(text(period.messageKey()));
            button.getStyleClass().add("app-neutral-button");
            button.setId("period-" + period.name().toLowerCase(java.util.Locale.ROOT));
            button.setOnAction(event -> applyPeriod(period));
            row.getChildren().add(button);
        }

        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);
        dateFrom.setId("statement-date-from");
        dateTo.setId("statement-date-to");
        dateFrom.setOnAction(event -> fire());
        dateTo.setOnAction(event -> fire());

        row.getChildren().addAll(new Label(text("from")), dateFrom,
                new Label(text("to")), dateTo);
        return row;
    }

    private FlowPane filterRow() {
        comboKind.getItems().add(null);
        comboKind.getItems().addAll(PartyMovementKind.values());
        comboKind.setConverter(converter(value ->
                value == null ? text("party.statement.filter.all") : text(value.messageKey())));
        comboKind.setId("statement-kind");

        comboTreasury.setConverter(converter(option -> option == null || option.id() == 0
                ? text("party.statement.filter.all") : option.name()));
        comboTreasury.setId("statement-treasury");

        comboUser.setConverter(converter(option -> option == null || option.id() == 0
                ? text("party.statement.filter.all") : option.name()));
        comboUser.setId("statement-user");

        // Not setTextFormatter, which seeds "0.0": an untouched amount filter has to
        // mean "no bound", not "exactly zero". See Utils.setOptionalNumberFormatter.
        setOptionalNumberFormatter(amountFrom, amountTo);
        amountFrom.setPromptText(text("party.statement.filter.amount.from"));
        amountTo.setPromptText(text("party.statement.filter.amount.to"));
        search.setPromptText(text("party.statement.filter.text"));
        search.setId("statement-search");

        comboKind.setOnAction(event -> fire());
        comboTreasury.setOnAction(event -> fire());
        comboUser.setOnAction(event -> fire());
        deferredOnly.setOnAction(event -> fire());

        Button apply = new Button(text("search"), AppIcon.SEARCH.graphic());
        apply.getStyleClass().add("app-primary-button");
        apply.setId("statement-apply");
        apply.setOnAction(event -> fire());

        Button clear = new Button(text("party.statement.filter.reset"), AppIcon.CLEAR.graphic());
        clear.getStyleClass().add("app-neutral-button");
        clear.setId("statement-reset");
        clear.setOnAction(event -> reset());

        // The Enter order, declared once and in the order the bar is actually filled: a minimum,
        // a maximum, some text, then the button - rule ق-ل9, pinned by
        // KeyboardNavigationArchitectureTest. Someone narrowing a range types three things and
        // should not reach for the mouse between them.
        //
        // Escape is an event filter on the bar rather than a handler on each box, because
        // whenEnterPressed installs setOnKeyPressed and the two would overwrite each other -
        // whichever ran second would win, silently, and one of the keys would stop working.
        whenEnterPressed(amountFrom, amountTo, search, apply);
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                reset();
                event.consume();
            }
        });

        // A FlowPane, not an HBox: in a dialog narrower than the main window an HBox squeezes its
        // labels until they read "نوع الحر..." and "المستخ...", which is a filter bar nobody can
        // use. Wrapping keeps every caption whole at any width.
        FlowPane row = new FlowPane(8, 8,
                new Label(text("party.statement.filter.kind")), comboKind,
                new Label(text("party.statement.filter.treasury")), comboTreasury,
                new Label(text("party.statement.filter.user")), comboUser,
                amountFrom, amountTo, search, deferredOnly, apply, clear);
        row.setAlignment(Pos.CENTER_LEFT);
        search.setPrefWidth(240);
        return row;
    }

    private void fire() {
        if (!loading) {
            onSearch.accept(filter(0, PartyStatementFilter.DEFAULT_PAGE_SIZE));
        }
    }

    private static Integer idOrNull(int id) {
        return id == 0 ? null : id;
    }

    private static BigDecimal amount(TextField field) {
        String value = field.getText();
        if (value == null || value.isBlank()) {
            return null;
        }
        return BigDecimal.valueOf(DoubleSetting.parseDoubleOrDefault(value));
    }

    @SafeVarargs
    private static <T> java.util.List<T> all(T... first) {
        return new java.util.ArrayList<>(java.util.Arrays.asList(first));
    }

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return label.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }

    /** For a caller that wants the set of kinds rather than the single combo's value. */
    static Set<PartyMovementKind> asSet(PartyMovementKind kind) {
        Set<PartyMovementKind> kinds = new LinkedHashSet<>();
        if (kind != null) {
            kinds.add(kind);
        }
        return kinds;
    }
}
