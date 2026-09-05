package com.hamza.account.features.masterdata;

import com.hamza.controlsfx.error.UserValidationException;

/** Validation returns message keys; localization belongs to the UI boundary. */
public record MasterDataForm(int id, String name, int parentId, double factor) {
    public static MasterDataForm parse(MasterDataKind kind, int id, String name, int parentId, String factorText)
            throws UserValidationException {
        if (name == null || name.isBlank()) throw new UserValidationException("masterdata.error.name");
        int maxLength = kind == MasterDataKind.AREA ? 100 : 50;
        if (name.strip().codePointCount(0, name.strip().length()) > maxLength)
            throw new UserValidationException("masterdata.error.length");
        if (kind == MasterDataKind.SUB && parentId <= 0)
            throw new UserValidationException("masterdata.error.parent");
        double factor = 1;
        if (kind == MasterDataKind.UNIT && factorText != null && !factorText.isBlank()) {
            try { factor = Double.parseDouble(factorText.strip()); }
            catch (NumberFormatException e) { throw new UserValidationException("masterdata.error.factor", e); }
        }
        if (!Double.isFinite(factor) || factor < 0.001 || factor > 99999999999.999)
            throw new UserValidationException("masterdata.error.factor");
        return new MasterDataForm(id, name.strip(), parentId, factor);
    }
}
