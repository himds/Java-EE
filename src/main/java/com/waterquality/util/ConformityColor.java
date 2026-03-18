package com.waterquality.util;

/**
 * Calcule la couleur d'affichage et le libellé de statut à partir des quatre champs de conformité du prélèvement.
 * Règles : tout conforme → vert ; conformité santé + indicateur(s) anormal(aux) → jaune ;
 * bactério OK et chimie non OK → orange ; aucune donnée → gris ; sinon → rouge.
 */
public final class ConformityColor {

    public static final String GREEN  = "#22c55e";
    public static final String YELLOW = "#eab308";
    public static final String ORANGE = "#f97316";
    public static final String RED    = "#ef4444";
    public static final String GREY   = "#9ca3af";

    private ConformityColor() {}

    private static boolean isConforme(String value) {
        if (value == null) return false;
        return "C".equalsIgnoreCase(value.trim());
    }

    /**
     * Retourne [couleur hexadécimale, libellé de statut] à partir des quatre champs.
     * Retourne gris si aucune donnée (tous null ou vides).
     */
    public static String[] fromPrelevement(String bacterio, String chimique, String refBact, String refChim) {
        boolean b = isConforme(bacterio);
        boolean c = isConforme(chimique);
        boolean rb = isConforme(refBact);
        boolean rc = isConforme(refChim);

        boolean hasData = (bacterio != null && !bacterio.trim().isEmpty())
            || (chimique != null && !chimique.trim().isEmpty())
            || (refBact != null && !refBact.trim().isEmpty())
            || (refChim != null && !refChim.trim().isEmpty());

        if (!hasData) {
            return new String[] { GREY, "Aucune donnée" };
        }

        if (b && c && rb && rc) {
            return new String[] { GREEN, "Conforme" };
        }

        if (rb && rc && (!b || !c)) {
            return new String[] { YELLOW, "Conformité santé, indicateur(s) anormal(aux)" };
        }

        if (b && rb && (!c || !rc)) {
            return new String[] { ORANGE, "Bactériologie conforme, chimie non conforme" };
        }

        return new String[] { RED, "Non conforme" };
    }
}
