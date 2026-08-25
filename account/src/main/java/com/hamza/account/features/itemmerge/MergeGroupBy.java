package com.hamza.account.features.itemmerge;

/**
 * How the screen decides that two items might be the same thing.
 * <p>
 * There is no way to know from the data alone - that is a judgement about the world, and
 * the user makes it. What the application can do is put the likely pairs next to each
 * other, and the three ways of doing that answer three different shapes of the problem:
 * <ul>
 *   <li>{@link #NAME} - the same item entered twice, spelling and spacing aside. The
 *       classic duplicate.</li>
 *   <li>{@link #FIRST_WORD} - "شيبسي بالطماطم" and "شيبسي أطعم": different names for
 *       flavours of one thing, which is what the barcode-per-item era produced and the
 *       reason this feature exists. Narrowed by the sub-group, or every item whose name
 *       starts with a common word lands in one heap.</li>
 *   <li>{@link #PRICE} - the same sub-group at the same price, which is how a shopkeeper
 *       describes the set: "كلها بنفس السعر".</li>
 * </ul>
 * Arabic spelling is normalised before any of them: the hamza forms of alef, the two
 * forms of ya, and taa marbuta all fold together, because whether a name was typed with
 * أ or ا says nothing about whether it is the same item.
 */
public enum MergeGroupBy {

    NAME("item.merge.group.name"),
    FIRST_WORD("item.merge.group.first.word"),
    PRICE("item.merge.group.price");

    private final String titleKey;

    MergeGroupBy(String titleKey) {
        this.titleKey = titleKey;
    }

    public String titleKey() {
        return titleKey;
    }
}
