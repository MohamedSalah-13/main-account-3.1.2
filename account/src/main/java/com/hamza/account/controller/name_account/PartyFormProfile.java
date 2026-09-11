package com.hamza.account.controller.name_account;

import com.hamza.account.config.AppIcon;

/**
 * Localized visual content for a customer or supplier add/edit form.
 *
 * <p>The controller owns the layout; this value keeps its identity content in the same
 * semantic place as the party list profile, rather than duplicating customer/supplier
 * choices in each form.</p>
 */
public record PartyFormProfile(String title, String subtitle, AppIcon icon, String styleClass) {
}
