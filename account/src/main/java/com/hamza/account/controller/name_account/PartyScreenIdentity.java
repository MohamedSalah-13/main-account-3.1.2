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

    /** The heading of the accounts screen - what every customer or supplier owes. */
    public PartyFormProfile balancesProfile() {
        LanguageManager language = LanguageManager.getInstance();
        boolean customer = kind == PartyKind.CUSTOMER;
        return new PartyFormProfile(
                language.getString(customer
                        ? "party.balances.customers.title" : "party.balances.suppliers.title"),
                language.getString(customer
                        ? "party.balances.customers.subtitle" : "party.balances.suppliers.subtitle"),
                icon,
                styleClass);
    }

    /** The heading of the ageing report - what is owed, split by how overdue it is. */
    public PartyFormProfile ageingProfile() {
        LanguageManager language = LanguageManager.getInstance();
        boolean customer = kind == PartyKind.CUSTOMER;
        return new PartyFormProfile(
                language.getString(customer
                        ? "party.ageing.identity.customers.title" : "party.ageing.identity.suppliers.title"),
                language.getString(customer
                        ? "party.ageing.identity.customers.subtitle" : "party.ageing.identity.suppliers.subtitle"),
                icon,
                styleClass);
    }

    /** The heading of the trend chart - what was charged and what was collected, over time. */
    public PartyFormProfile trendProfile() {
        LanguageManager language = LanguageManager.getInstance();
        boolean customer = kind == PartyKind.CUSTOMER;
        return new PartyFormProfile(
                language.getString(customer
                        ? "party.trend.customers.title" : "party.trend.suppliers.title"),
                language.getString(customer
                        ? "party.trend.customers.subtitle" : "party.trend.suppliers.subtitle"),
                icon,
                styleClass);
    }

    /** The heading of the dialog that records one movement on a party's account. */
    public PartyFormProfile paymentProfile() {
        LanguageManager language = LanguageManager.getInstance();
        boolean customer = kind == PartyKind.CUSTOMER;
        return new PartyFormProfile(
                language.getString(customer
                        ? "party.payment.customers.title" : "party.payment.suppliers.title"),
                language.getString(customer
                        ? "party.payment.customers.subtitle" : "party.payment.suppliers.subtitle"),
                icon,
                styleClass);
    }
}
