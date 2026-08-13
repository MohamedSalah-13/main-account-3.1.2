---
name: javafx-localization-review
description: Keep this JavaFX accounting application's Arabic and English UI synchronized. Use whenever changing Java classes, FXML, dialogs, alerts, validation messages, table columns, menus, tooltips, reports, exports, or any behavior that adds or changes user-visible text or language-dependent layout. Also use when reviewing such changes, even if localization was not mentioned explicitly. Do not use for internal-only logs, SQL identifiers, persisted business data, or code with no user-visible effect.
---

# JavaFX Localization Review

Treat localization as part of the requested implementation, not as a separate follow-up.

## Workflow

1. Inspect the changed class and its paired FXML, controller, dialog, report, or export path. Identify every new or changed user-visible string and any layout whose direction depends on the locale.
2. Reuse an existing translation key when its meaning matches exactly. Otherwise add a stable, lowercase, dotted key grouped by feature, such as `login.invalid.credentials`.
3. Update all three bundles together:
   - `controlsfx/src/main/resources/i18n/messages.properties` - Arabic default
   - `controlsfx/src/main/resources/i18n/messages_ar.properties` - Arabic
   - `controlsfx/src/main/resources/i18n/messages_en.properties` - English
4. Keep the default and Arabic values identical. Keep placeholders compatible across every locale: the same `%s`, `%d`, argument order, escapes, and line-break intent.
5. Wire the text through the project's existing localization path:
   - In FXML loaded with a `ResourceBundle`, use `%key` for `text`, `promptText`, titles, tooltips, and similar attributes.
   - In Java, resolve text at the point of use with `LanguageManager.getInstance().getString("key")`, or its varargs overload for formatted text.
   - Ensure any direct `FXMLLoader` for localized FXML receives `LanguageManager.getInstance().getResourceBundle()`. The standard `OpenFxmlApplication` path already does.
   - Do not add user-visible Arabic or English literals, and do not add new translated `static final String` caches that can freeze the old locale.
6. Review bidirectional behavior. Use the existing `ChangeOrientation.sceneOrientation(...)` path for scenes/dialogs and avoid adding unconditional `RIGHT_TO_LEFT` to bilingual UI. Check alignment, icon order, punctuation, and mixed Arabic/Latin or numeric content in both directions.
7. Search the touched files for remaining hard-coded user-visible strings. Limit cleanup to the requested change and its immediate UI path unless the user asks for a broader migration.
8. Run `python .agents/skills/javafx-localization-review/scripts/check_bundles.py`. Fix every reported missing key, duplicate key, or default/Arabic mismatch.
9. Perform the repository's normal clean build/test verification for the code changed. In the final response, state that Arabic and English resources were reviewed and name any UI path that could not be exercised at runtime.

## Review standard

A change is incomplete when it introduces or changes user-visible text without updating both languages, when an FXML `%key` is loaded without a resource bundle, or when the edited screen is forced into the wrong text direction. Do not claim localization verification from compilation alone; visually exercise both Arabic and English when the application and database are available.
