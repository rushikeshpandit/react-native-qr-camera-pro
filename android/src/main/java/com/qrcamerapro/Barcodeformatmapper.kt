package com.qrcamerapro

import com.google.mlkit.vision.barcode.common.Barcode

/**
 * Maps ML Kit barcode format integers to the string constants expected by JavaScript.
 *
 * Isolated into its own object so the mapping can be extended or tested without
 * touching camera or module logic (SRP / OCP).
 */
internal object BarcodeFormatMapper {

    /**
     * Returns the JS-facing barcode type string for a given ML Kit format constant.
     *
     * Falls back to `"UNKNOWN"` for any format not explicitly listed — this is
     * preferable to throwing, since new barcode formats may be added by ML Kit
     * in future without a corresponding update to this mapper.
     *
     * @param format An ML Kit [Barcode.FORMAT_*] integer constant.
     * @return A stable string identifier consumable by JavaScript callers.
     */
    fun toJsString(format: Int): String = when (format) {
        Barcode.FORMAT_QR_CODE    -> "QR_CODE"
        Barcode.FORMAT_EAN_8      -> "EAN_8"
        Barcode.FORMAT_EAN_13     -> "EAN_13"
        Barcode.FORMAT_PDF417     -> "PDF_417"
        Barcode.FORMAT_AZTEC      -> "AZTEC"
        Barcode.FORMAT_CODE_128   -> "CODE_128"
        Barcode.FORMAT_CODE_39    -> "CODE_39"
        Barcode.FORMAT_CODE_93    -> "CODE_93"
        Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
        Barcode.FORMAT_ITF        -> "ITF"
        Barcode.FORMAT_UPC_E      -> "UPC_E"
        else                      -> "UNKNOWN"
    }
}