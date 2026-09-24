package com.hamza.account.features.wipe;

import com.hamza.account.wipe.WipeTarget;

import java.util.List;

/**
 * One card on the delete-data screen: a title and the targets under it.
 * <p>
 * How the targets are grouped is the screen's to say and belongs nowhere near the wipe itself -
 * which targets exist and what has to go with what is {@code WipeCatalog}'s.
 *
 * @param id       what the screen picks the card's icon by
 * @param titleKey the bundle key of the card's title
 * @param targets  the targets on the card, in the order they are listed
 */
public record WipeSection(String id, String titleKey, List<WipeTarget> targets) {

    public WipeSection {
        targets = List.copyOf(targets);
    }
}
