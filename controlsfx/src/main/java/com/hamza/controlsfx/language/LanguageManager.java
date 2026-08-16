package com.hamza.controlsfx.language;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.NodeOrientation;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

/**
 * Loads {@code i18n/messages_<lang>.properties} bundles and is the single place
 * the rest of the app asks "what language, what direction, what text". A
 * supported language is discovered from the classpath rather than hardcoded, so
 * dropping in a new {@code messages_xx.properties} is the whole change needed to
 * add a language - see {@link #supportedLocales()}.
 */
@Log4j2
public class LanguageManager {

    private static volatile LanguageManager instance;
    private ResourceBundle resourceBundle;
    private Locale currentLocale;

    public static final String BUNDLE_NAME = "i18n.messages";
    private static final String PREF_LANGUAGE_KEY = "app.language";

    /** Bundle keys every {@code messages_<lang>.properties} is expected to carry. */
    private static final String KEY_DISPLAY_NAME = "language.display.name";
    private static final String KEY_DIRECTION = "language.direction";
    private static final String DIRECTION_RTL = "rtl";

    private static final Pattern BUNDLE_FILE = Pattern.compile("messages_([a-zA-Z]{2,8})\\.properties");

    private final Preferences preferences;
    private final ObjectProperty<Locale> currentLocaleProperty = new SimpleObjectProperty<>();

    public static final Locale ARABIC = Locale.of("ar");
    public static final Locale ENGLISH = Locale.ENGLISH;

    private LanguageManager() {
        preferences = Preferences.userNodeForPackage(LanguageManager.class);
        loadSavedLanguage();
    }

    public static LanguageManager getInstance() {
        if (instance == null) {
            synchronized (LanguageManager.class) {
                if (instance == null) {
                    instance = new LanguageManager();
                }
            }
        }
        return instance;
    }

    private void loadSavedLanguage() {
        String savedLanguage = preferences.get(PREF_LANGUAGE_KEY, ARABIC.getLanguage());
        Locale locale = ENGLISH.getLanguage().equals(savedLanguage) ? ENGLISH : ARABIC;
        setLocale(locale);
    }

    public void setLocale(Locale locale) {
        this.currentLocale = locale;
        this.resourceBundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
        preferences.put(PREF_LANGUAGE_KEY, locale.getLanguage());
        currentLocaleProperty.set(locale);
    }

    public Locale getCurrentLocale() {
        return currentLocale;
    }

    /** Read-only observable of the active locale, for UI that wants to react to a switch. */
    public ObjectProperty<Locale> currentLocaleProperty() {
        return currentLocaleProperty;
    }

    /**
     * Every language the app can offer, discovered from {@code i18n/messages_*.properties}
     * on the classpath - adding a language is dropping in that file, nothing here changes.
     * Ordered with the current locale first, then alphabetically by language tag.
     */
    public List<Locale> supportedLocales() {
        Set<Locale> found = new LinkedHashSet<>();
        try {
            URL folder = LanguageManager.class.getClassLoader().getResource("i18n");
            if (folder != null) {
                switch (folder.getProtocol()) {
                    case "file" -> scanDirectory(folder, found);
                    case "jar" -> scanJar(folder, found);
                    default -> log.warn("Unsupported classpath protocol for i18n bundles: {}", folder.getProtocol());
                }
            }
        } catch (IOException | URISyntaxException e) {
            log.warn("Failed to scan for language bundles, falling back to ar/en", e);
        }
        if (found.isEmpty()) {
            found.add(ARABIC);
            found.add(ENGLISH);
        }
        List<Locale> ordered = new ArrayList<>(found);
        ordered.sort(Comparator.comparing((Locale l) -> !l.equals(currentLocale))
                .thenComparing(Locale::getLanguage));
        return ordered;
    }

    private void scanDirectory(URL folder, Set<Locale> found) throws URISyntaxException, IOException {
        Path dir = Path.of(folder.toURI());
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "messages_*.properties")) {
            for (Path entry : stream) {
                addIfMatches(entry.getFileName().toString(), found);
            }
        }
    }

    private void scanJar(URL folder, Set<Locale> found) throws IOException {
        JarURLConnection connection = (JarURLConnection) folder.openConnection();
        try (JarFile jarFile = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                int slash = name.lastIndexOf('/');
                addIfMatches(slash < 0 ? name : name.substring(slash + 1), found);
            }
        }
    }

    private void addIfMatches(String fileName, Set<Locale> found) {
        var matcher = BUNDLE_FILE.matcher(fileName);
        if (matcher.matches()) {
            found.add(Locale.of(matcher.group(1)));
        }
    }

    /** The name a language calls itself by, read from its own bundle (not the JRE's locale names, which aren't translated). */
    public String displayNameOf(Locale locale) {
        try {
            return ResourceBundle.getBundle(BUNDLE_NAME, locale).getString(KEY_DISPLAY_NAME);
        } catch (MissingResourceException e) {
            return locale.getDisplayName(locale);
        }
    }

    /** Layout direction for the active language, read from its bundle rather than hardcoded per-locale. */
    public NodeOrientation getNodeOrientation() {
        return isRtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT;
    }

    public boolean isRtl() {
        try {
            return DIRECTION_RTL.equalsIgnoreCase(getResourceBundle().getString(KEY_DIRECTION));
        } catch (MissingResourceException e) {
            // Bundles that predate the direction key: Arabic was the only RTL language shipped.
            return isArabic();
        }
    }

    public String getString(String key) {
        try {
            return getResourceBundle().getString(key);
        } catch (MissingResourceException e) {
            log.warn("Missing translation key: {}", key);
            return key;
        }
    }

    public String getString(String key, Object... args) {
        try {
            String message = getResourceBundle().getString(key);
            return String.format(message, args);
        } catch (MissingResourceException | IllegalFormatException e) {
            log.warn("Unable to resolve translation key: {}", key, e);
            return key;
        }
    }

    public void toggleLanguage() {
        if (ARABIC.equals(currentLocale)) {
            setLocale(ENGLISH);
        } else {
            setLocale(ARABIC);
        }
    }

    public boolean isArabic() {
        return ARABIC.equals(currentLocale);
    }

    public boolean isEnglish() {
        return ENGLISH.equals(currentLocale);
    }

    public String getTextDirection() {
        return isArabic() ? "rtl" : "ltr";
    }

    public ResourceBundle getResourceBundle() {
        if (resourceBundle == null) {
            resourceBundle = ResourceBundle.getBundle(BUNDLE_NAME, Objects.requireNonNullElse(currentLocale, ARABIC));
        }
        return resourceBundle;
    }

    public void reload() {
        ResourceBundle.clearCache();
        this.resourceBundle = ResourceBundle.getBundle(BUNDLE_NAME, Objects.requireNonNullElse(currentLocale, ARABIC));
    }
}
