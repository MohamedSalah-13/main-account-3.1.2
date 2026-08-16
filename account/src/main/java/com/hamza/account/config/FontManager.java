package com.hamza.account.config;

import javafx.scene.text.Font;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Selectable UI typeface, persisted and applied the same way as {@link ThemeManager}
 * and {@link UiScale}: a family name is stamped as an inline {@code -fx-font-family}
 * style by {@link ThemeManager#apply}, overriding app-theme.css's own font-family
 * rule from one place. Built-in choices are the font files already bundled under
 * {@code controlsfx/.../font/}; a user can additionally point at a {@code .ttf}/
 * {@code .otf} file on disk, which is registered with {@link Font#loadFont} and
 * remembered so it is registered again on the next run.
 */
@Log4j2
public final class FontManager {

    private static final String PREF_NODE = "com.hamza.account.font";
    private static final String KEY_FAMILY = "fontFamily";
    private static final String KEY_CUSTOM_FONTS = "customFonts";
    private static final String ENTRY_SEPARATOR = ";";
    private static final String FIELD_SEPARATOR = "|";

    private static final Preferences PREFS = Preferences.userRoot().node(PREF_NODE);

    public static final String DEFAULT_FAMILY = "Cairo";

    /** Display name -> classpath resource under {@code com/hamza/controlsfx/font} used to register it. */
    private static final Map<String, String> BUILT_IN = new LinkedHashMap<>();

    static {
        BUILT_IN.put("Cairo", "Cairo/Cairo-VariableFont_wght.ttf");
        BUILT_IN.put("Cantarell", "Cantarell/Cantarell-Regular.ttf");
        BUILT_IN.put("El Messiri", "El_Messiri/ElMessiri-VariableFont_wght.ttf");
        BUILT_IN.put("Gafata", "Gafata/Gafata-Regular.ttf");
        BUILT_IN.put("Grand Hotel", "Grand_Hotel/GrandHotel-Regular.ttf");
        BUILT_IN.put("JetBrains Mono", "JetBrains_Mono/JetBrainsMono-VariableFont_wght.ttf");
        BUILT_IN.put("Lemonada", "Lemonada/Lemonada-VariableFont_wght.ttf");
        BUILT_IN.put("New Rocker", "New_Rocker/NewRocker-Regular.ttf");
        BUILT_IN.put("Ubuntu", "Ubuntu/Ubuntu-Regular.ttf");
    }

    private FontManager() {
    }

    /** Registers every built-in font plus every previously added custom font. Called once at startup. */
    public static void initialize() {
        BUILT_IN.values().forEach(FontManager::loadBuiltIn);
        customFonts().forEach(entry -> loadFromFile(new File(entry.path())));
    }

    private static void loadBuiltIn(String resourcePath) {
        try (InputStream in = FontManager.class.getResourceAsStream("/com/hamza/controlsfx/font/" + resourcePath)) {
            if (in != null) {
                Font.loadFont(in, 20);
            }
        } catch (IOException e) {
            log.warn("Failed to load built-in font {}: {}", resourcePath, e.getMessage());
        }
    }

    public static String getCurrentFamily() {
        return PREFS.get(KEY_FAMILY, DEFAULT_FAMILY);
    }

    public static void setCurrentFamily(String family) {
        if (family == null || family.isBlank()) return;
        PREFS.put(KEY_FAMILY, family);
    }

    /** Every family the user can currently pick: built-in plus whatever custom fonts were added. */
    public static List<String> allFamilies() {
        List<String> all = new ArrayList<>(BUILT_IN.keySet());
        for (FontEntry entry : customFonts()) {
            if (!all.contains(entry.family())) {
                all.add(entry.family());
            }
        }
        return all;
    }

    /**
     * Registers a font file with JavaFX and remembers its path so it is loaded again
     * on the next run. Returns the family JavaFX resolved it to, or {@code null} if
     * the file could not be read as a font.
     */
    public static String addCustomFont(File file) {
        Font font = loadFromFile(file);
        if (font == null) return null;

        List<FontEntry> entries = customFonts();
        entries.removeIf(entry -> entry.path().equals(file.getAbsolutePath()));
        entries.add(new FontEntry(font.getFamily(), file.getAbsolutePath()));
        persistCustomFonts(entries);
        return font.getFamily();
    }

    private static Font loadFromFile(File file) {
        if (file == null || !file.isFile()) return null;
        try (InputStream in = new FileInputStream(file)) {
            return Font.loadFont(in, 20);
        } catch (IOException e) {
            log.warn("Failed to load font file {}: {}", file, e.getMessage());
            return null;
        }
    }

    private static List<FontEntry> customFonts() {
        String raw = PREFS.get(KEY_CUSTOM_FONTS, "");
        List<FontEntry> entries = new ArrayList<>();
        if (raw.isBlank()) return entries;
        for (String chunk : raw.split(ENTRY_SEPARATOR)) {
            int sep = chunk.indexOf(FIELD_SEPARATOR);
            if (sep > 0) {
                entries.add(new FontEntry(chunk.substring(0, sep), chunk.substring(sep + 1)));
            }
        }
        return entries;
    }

    private static void persistCustomFonts(List<FontEntry> entries) {
        StringBuilder raw = new StringBuilder();
        for (FontEntry entry : entries) {
            if (!raw.isEmpty()) raw.append(ENTRY_SEPARATOR);
            raw.append(entry.family()).append(FIELD_SEPARATOR).append(entry.path());
        }
        PREFS.put(KEY_CUSTOM_FONTS, raw.toString());
    }

    /** The inline style {@link ThemeManager#apply} stamps to override every stylesheet's font-family. */
    public static String fontFamilyStyle() {
        return "-fx-font-family: \"" + getCurrentFamily().replace("\"", "") + "\";";
    }

    private record FontEntry(String family, String path) {
    }
}
