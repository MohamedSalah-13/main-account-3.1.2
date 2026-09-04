package com.hamza.account.features.scalebarcode;

/**
 * The GS1 modulo-10 check digit, the one EAN-13 and EAN-8 both use.
 * <p>
 * The weights alternate 3 and 1 <b>counting from the right-hand end of the data</b>, and
 * that anchoring is the whole subtlety. The earlier implementation weighted from the left
 * - {@code i % 2 == 0 ? 1 : 3} - which lands on the same answer for the twelve data
 * digits of an EAN-13 and on the opposite one for any odd count, EAN-8's seven included.
 * Since the barcode's length is a setting here, a scale emitting anything but thirteen
 * digits had every one of its valid barcodes rejected.
 */
public final class ScaleBarcodeCheckDigit {

    private ScaleBarcodeCheckDigit() {
    }

    /**
     * The check digit that belongs after {@code dataDigits}.
     *
     * @param dataDigits the barcode without its last character; digits only
     */
    public static char of(String dataDigits) {
        int sum = 0;
        int weight = 3;
        for (int index = dataDigits.length() - 1; index >= 0; index--) {
            sum += Character.getNumericValue(dataDigits.charAt(index)) * weight;
            weight = weight == 3 ? 1 : 3;
        }
        return Character.forDigit((10 - (sum % 10)) % 10, 10);
    }
}
