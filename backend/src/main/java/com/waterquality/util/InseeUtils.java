package com.waterquality.util;

/**
 * French commune INSEE code: always 5 digits with leading zeros (e.g. 01001).
 * Normalizes so API and frontend GeoJSON match.
 */
public final class InseeUtils {
    private InseeUtils() {}

    /** INSEE 5 digits with leading zeros (e.g. 1001 -> 01001). */
    public static String normalize(String codeInsee) {
        if (codeInsee == null || codeInsee.isBlank()) return codeInsee;
        String s = codeInsee.trim();
        if (s.length() >= 5) return s;
        String padded = "00000" + s;
        return padded.substring(padded.length() - 5);
    }
}
