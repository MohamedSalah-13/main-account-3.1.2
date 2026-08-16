package com.hamza.account.delete;

import com.hamza.controlsfx.language.LanguageManager;

/**
 * One kind of thing that still points at a row, and how many of them there are -
 * "12 فاتورة بيع".
 * <p>
 * The count is what makes a refusal answerable: "مستخدمة في فواتير" tells the
 * user to go looking, "مستخدمة في 12 فاتورة بيع" tells them what they are
 * looking for. {@code labelKey} is an i18n bundle key, resolved through the
 * current language when the sentence is actually rendered.
 */
public record Reference(String labelKey, int count) {

    @Override
    public String toString() {
        return count + " " + LanguageManager.getInstance().getString(labelKey);
    }
}
