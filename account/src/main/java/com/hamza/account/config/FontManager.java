package com.hamza.account.config;

import com.hamza.controlsfx.FontResources;
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

/** Manages selectable, persisted application fonts. */
@Log4j2
public final class FontManager {
    private static final String PREF_NODE = "com.hamza.account.font";
    private static final String KEY_FAMILY = "fontFamily";
    private static final String KEY_CUSTOM_FONTS = "customFonts";
    private static final String ENTRY_SEPARATOR = ";";
    private static final String FIELD_SEPARATOR = "|";
    private static final Preferences PREFS = Preferences.userRoot().node(PREF_NODE);
    public static final String DEFAULT_FAMILY = "Cairo";
    private static final Map<String, String> BUILT_IN = new LinkedHashMap<>();

    static {
        BUILT_IN.put("Cairo", "Cairo/static/Cairo-Regular.ttf");
        BUILT_IN.put("Cantarell", "Cantarell/Cantarell-Regular.ttf");
        BUILT_IN.put("El Messiri", "El_Messiri/static/ElMessiri-Regular.ttf");
        BUILT_IN.put("Gafata", "Gafata/Gafata-Regular.ttf");
        BUILT_IN.put("Grand Hotel", "Grand_Hotel/GrandHotel-Regular.ttf");
        BUILT_IN.put("JetBrains Mono", "JetBrains_Mono/static/JetBrainsMono-Regular.ttf");
        BUILT_IN.put("Lemonada", "Lemonada/static/Lemonada-Regular.ttf");
        BUILT_IN.put("New Rocker", "New_Rocker/NewRocker-Regular.ttf");
        BUILT_IN.put("Ubuntu", "Ubuntu/Ubuntu-Regular.ttf");
    }

    private FontManager() { }

    /** Registers bundled and previously selected custom fonts before the UI appears. */
    public static void initialize() {
        BUILT_IN.values().forEach(FontManager::loadBuiltIn);
        customFonts().forEach(entry -> loadFromFile(new File(entry.path())));
    }

    private static void loadBuiltIn(String resourcePath) {
        try (InputStream in = FontResources.open(resourcePath)) {
            if (in == null) {
                log.warn("Bundled font resource was not found: {}", resourcePath);
            } else if (Font.loadFont(in, 20) == null) {
                log.warn("JavaFX could not register bundled font: {}", resourcePath);
            }
        } catch (IOException e) {
            log.warn("Failed to load built-in font {}: {}", resourcePath, e.getMessage());
        }
    }

    public static String getCurrentFamily() { return PREFS.get(KEY_FAMILY, DEFAULT_FAMILY); }
    public static void setCurrentFamily(String family) {
        if (family != null && !family.isBlank()) PREFS.put(KEY_FAMILY, family);
    }

    public static List<String> allFamilies() {
        List<String> all = new ArrayList<>(BUILT_IN.keySet());
        for (FontEntry entry : customFonts()) if (!all.contains(entry.family())) all.add(entry.family());
        return all;
    }

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
        try (InputStream in = new FileInputStream(file)) { return Font.loadFont(in, 20); }
        catch (IOException e) {
            log.warn("Failed to load font file {}: {}", file, e.getMessage());
            return null;
        }
    }

    /** Inspects the selected file itself, so an OS fallback cannot look like Arabic support. */
    public static ArabicSupport arabicSupport(String family) {
        if (family == null || family.isBlank()) return ArabicSupport.UNDETERMINED;
        String resourcePath = BUILT_IN.get(family);
        if (resourcePath != null) {
            try (InputStream in = FontResources.open(resourcePath)) { return supportsArabic(in); }
            catch (IOException e) {
                log.warn("Failed to inspect bundled font {}: {}", resourcePath, e.getMessage());
                return ArabicSupport.UNDETERMINED;
            }
        }
        return customFonts().stream().filter(entry -> entry.family().equals(family)).findFirst()
                .map(entry -> supportsArabic(new File(entry.path()))).orElse(ArabicSupport.UNDETERMINED);
    }

    public static Font previewFont(String family) {
        return Font.font(family == null || family.isBlank() ? DEFAULT_FAMILY : family, 22);
    }

    private static ArabicSupport supportsArabic(InputStream in) {
        if (in == null) return ArabicSupport.UNDETERMINED;
        try {
            java.awt.Font font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, in);
            return font.canDisplayUpTo("مرحبا بالعربية") == -1 ? ArabicSupport.SUPPORTED : ArabicSupport.NOT_SUPPORTED;
        } catch (java.awt.FontFormatException | IOException e) { return ArabicSupport.UNDETERMINED; }
    }

    private static ArabicSupport supportsArabic(File file) {
        if (file == null || !file.isFile()) return ArabicSupport.UNDETERMINED;
        try (InputStream in = new FileInputStream(file)) { return supportsArabic(in); }
        catch (IOException e) { return ArabicSupport.UNDETERMINED; }
    }

    public static String fontFamilyStyle() {
        return "-fx-font-family: \"" + getCurrentFamily().replace("\"", "") + "\";";
    }

    private static List<FontEntry> customFonts() {
        String raw = PREFS.get(KEY_CUSTOM_FONTS, "");
        List<FontEntry> entries = new ArrayList<>();
        if (raw.isBlank()) return entries;
        for (String chunk : raw.split(ENTRY_SEPARATOR)) {
            int sep = chunk.indexOf(FIELD_SEPARATOR);
            if (sep > 0) entries.add(new FontEntry(chunk.substring(0, sep), chunk.substring(sep + 1)));
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

    public enum ArabicSupport { SUPPORTED, NOT_SUPPORTED, UNDETERMINED }
    private record FontEntry(String family, String path) { }
}