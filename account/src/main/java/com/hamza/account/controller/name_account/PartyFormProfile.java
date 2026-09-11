package com.hamza.account.controller.name_account;

import com.hamza.account.config.AppIcon;

/**
 * Localized heading content for a customer or supplier screen other than the list: the
 * add/edit form, the accounts screen and the account-movement dialog.
 *
 * <p>The controller owns the layout and {@link PartyIdentityHeader} draws it; this value
 * keeps its identity content in the same semantic place as the party list profile, rather
 * than duplicating customer/supplier choices in each screen.</p>
 */
public record PartyFormProfile(String title, String subtitle, AppIcon icon, String styleClass) {
}
