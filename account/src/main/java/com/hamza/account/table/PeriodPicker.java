package com.hamza.account.table;

import com.hamza.account.features.party.statement.StatementPeriod;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The period a report is read over, as one control: a preset - today, this week, this month, last month,
 * this quarter, this year - and the two dates it sets, which can be typed over, when the preset reads
 * "custom". It says <b>which</b> report this is, so a screen puts it in its bar beside the search and
 * never in a filters panel ({@link ListToolbar}).
 *
 * <p>The presets are {@link StatementPeriod}'s, so a week runs Saturday to Friday here as on every
 * statement; "everything there has ever been" is left out, being a question no report here reads in one
 * go. The screen is told once per change, however many controls moved to make it: choosing a preset sets
 * both dates and calls {@link #setOnChange} once, not three times.</p>
 */
public final class PeriodPicker {

    private final ComboBox<Choice> presets = new ComboBox<>();
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final HBox node;
    private Runnable onChange = () -> {
    };
    /** Set while the picker moves its own controls, so their listeners do not each report a change. */
    private boolean arranging;
    private boolean presetChosen;

    /** @param idPrefix names the three controls, {@code <prefix>-period}, {@code -from} and {@code -to} */
    public PeriodPicker(String idPrefix) {
        presets.setId(idPrefix + "-period");
        presets.getItems().setAll(Choice.all());
        presets.setConverter(Choice.CONVERTER);
        presets.setOnAction(event -> {
            Choice choice = presets.getValue();
            if (!arranging && choice != null && choice.preset() != null) {
                choose(choice.preset());
            }
        });
        from.setId(idPrefix + "-from");
        to.setId(idPrefix + "-to");
        for (DatePicker picker : new DatePicker[]{from, to}) {
            picker.setPrefWidth(130);
            picker.valueProperty().addListener((observable, was, now) -> {
                if (!arranging) {
                    arranging = true;
                    try {
                        presets.setValue(Choice.CUSTOM);
                    } finally {
                        arranging = false;
                    }
                    presetChosen = false;
                    onChange.run();
                }
            });
        }
        Label caption = new Label(LanguageManager.getInstance().getString("report.period.caption"));
        caption.getStyleClass().add("form-label");
        Label dash = new Label("–");
        dash.getStyleClass().add("form-label");
        node = new HBox(8, caption, presets, from, dash, to);
        node.setAlignment(Pos.CENTER_LEFT);
    }

    public HBox node() {
        return node;
    }

    public LocalDate from() {
        return from.getValue();
    }

    public LocalDate to() {
        return to.getValue();
    }

    /** What to do when the period changes - once per change, on the JavaFX thread. */
    public void setOnChange(Runnable onChange) {
        this.onChange = Objects.requireNonNull(onChange, "onChange");
    }

    /** Whether the last change was a preset chosen, rather than a date typed or picked. */
    public boolean lastChangeWasPreset() {
        return presetChosen;
    }

    /** Sets a preset's dates, as choosing it does, and reports the change. */
    public void choose(StatementPeriod preset) {
        Objects.requireNonNull(preset, "preset");
        LocalDate today = LocalDate.now();
        arranging = true;
        try {
            presets.setValue(Choice.of(preset));
            from.setValue(preset.from(today));
            to.setValue(preset.to(today));
        } finally {
            arranging = false;
        }
        presetChosen = true;
        onChange.run();
    }

    /**
     * Sets two dates as "custom" and reports the change once - for a screen opened on a period another
     * screen was showing, such as the summary's own dates.
     */
    public void chooseDates(LocalDate start, LocalDate end) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        arranging = true;
        try {
            presets.setValue(Choice.CUSTOM);
            from.setValue(start);
            to.setValue(end);
        } finally {
            arranging = false;
        }
        presetChosen = false;
        onChange.run();
    }

    /** A preset, or "custom" for dates typed in - a record because "custom" is not a preset. */
    private record Choice(StatementPeriod preset) {
        static final Choice CUSTOM = new Choice(null);
        static final StringConverter<Choice> CONVERTER = new StringConverter<>() {
            @Override
            public String toString(Choice choice) {
                if (choice == null) {
                    return "";
                }
                return LanguageManager.getInstance().getString(
                        choice.preset() == null ? "report.period.custom" : choice.preset().messageKey());
            }

            @Override
            public Choice fromString(String string) {
                return null;
            }
        };

        static Choice of(StatementPeriod preset) {
            return new Choice(preset);
        }

        static List<Choice> all() {
            List<Choice> choices = new ArrayList<>();
            for (StatementPeriod preset : StatementPeriod.values()) {
                if (!preset.needsEarliestMovement()) {
                    choices.add(new Choice(preset));
                }
            }
            choices.add(CUSTOM);
            return choices;
        }
    }
}
