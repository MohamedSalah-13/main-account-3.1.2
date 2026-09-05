package com.hamza.account.features.masterdata;

/** A detached list row; never carries JavaFX state or an implicit user session. */
public record MasterDataEntry(int id, String name, int parentId, double factor, long contentCount) {
    public MasterDataEntry(int id, String name, int parentId, double factor) {
        this(id, name, parentId, factor, 0);
    }

    public boolean hasNoContents(MasterDataKind kind) {
        return (kind == MasterDataKind.MAIN || kind == MasterDataKind.SUB) && contentCount == 0;
    }

    /**
     * The factor as a person writes it: a carton of twelve is 12, not 12.0. The column stores
     * {@code DECIMAL(14,3)} and a fractional factor is legitimate, so the trailing zeros are
     * dropped rather than the number rounded - 1.5 stays 1.5 and 0.001 stays 0.001.
     */
    public String factorText() {
        return factorText(factor);
    }

    public static String factorText(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
