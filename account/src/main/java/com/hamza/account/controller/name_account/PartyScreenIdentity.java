package com.hamza.account.controller.name_account;

import com.hamza.account.config.AppIcon;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.table.TableScreenProfile;
import com.hamza.controlsfx.language.LanguageManager;

/**
 * One visual identity for every screen that belongs to a customer or supplier.
 *
 * <p>The CSS class is intentionally not tied to the list screen. The add/edit and
 * account screens can apply the same class and icon when they adopt the identity,
 * keeping the distinction semantic instead of repeating colours in controllers.</p>
 */
public record PartyScreenIdentity(PartyKind kind, String styleClass, AppIcon icon) {

    public static PartyScreenIdentity forKind(PartyKind kind) {
        if (kind == PartyKind.CUSTOMER) {
            return new PartyScreenIdentity(kind, "party-customers", AppIcon.CUSTOMERS);
        }
        return new PartyScreenIdentity(kind, "party-suppliers", AppIcon.SUPPLIERS);
    }

    public TableScreenProfile listProfile() {
        LanguageManager language = LanguageManager.getInstance();
        boolean customer = kind == PartyKind.CUSTOMER;
        String title = language.getString(customer
                ? "party.list.customers.title" : "party.list.suppliers.title");
        String subtitle = language.getString(customer
                ? "party.list.customers.subtitle" : "party.list.suppliers.subtitle");
        String addText = language.getString(customer
                ? "party.list.customers.add" : "party.list.suppliers.add");

        return TableScreenProfile.identified(
                title,
                subtitle,
                language.getString("party.list.search.prompt"),
                addText,
                icon,
                styleClass,
                false,
                false,
                true);
    }

    /** The heading content for adding or editing one party. */
    public PartyFormProfile formProfile(boolean editing) {
        LanguageManager language = LanguageManager.getInstance();
        boolean customer = kind == PartyKind.CUSTOMER;
        String prefix = customer ? "party.form.customers" : "party.form.suppliers";
        return new PartyFormProfile(
                language.getString(prefix + (editing ? ".edit.title" : ".add.title")),
                language.getString(prefix + ".subtitle"),
                icon,
                styleClass);
    }
}
