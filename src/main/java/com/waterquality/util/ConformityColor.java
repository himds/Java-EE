package com.waterquality.util;

/**
 * Cette classe permet de centraliser les règles de calcul des couleurs.
 * Elle traduit les indicateurs de conformité de la base de données en codes couleurs HEX et en libellés.
 */
public final class ConformityColor {

    // Palettes de couleurs
    public static final String GREEN  = "#22c55e"; // Succès / Tout est OK
    public static final String YELLOW = "#eab308"; // Alerte mineure
    public static final String ORANGE = "#f97316"; // Problème chimique
    public static final String RED    = "#ef4444"; // Danger / Non conforme
    public static final String GREY   = "#9ca3af"; // Information manquante

    private ConformityColor() {}

    /**
     * Vérifie si un code de conformité correspond à "Conforme" (lettre 'C').
     */
    private static boolean isConforme(String value) {
        if (value == null) return false;
        return "C".equalsIgnoreCase(value.trim());
    }

    /**
     * Calcule la couleur et le texte de statut selon la hiérarchie de sécurité sanitaire.
     * * @return String[] où [0] est le code couleur et [1] le message de statut.
     */
    public static String[] fromPrelevement(String bacterio, String chimique, String refBact, String refChim) {
        
        // On évalue chaque champ individuellement
        boolean b = isConforme(bacterio);
        boolean c = isConforme(chimique);
        boolean rb = isConforme(refBact);
        boolean rc = isConforme(refChim);

        // Vérification de la présence de données
        boolean hasData = (bacterio != null && !bacterio.trim().isEmpty())
            || (chimique != null && !chimique.trim().isEmpty())
            || (refBact != null && !refBact.trim().isEmpty())
            || (refChim != null && !refChim.trim().isEmpty());

        if (!hasData) {
            return new String[] { GREY, "Aucune donnée" };
        }

        // Tout est conforme (Santé + Références)
        if (b && c && rb && rc) {
            return new String[] { GREEN, "Conforme" };
        }

        // Les indicateurs de référence sont OK, mais il y a une anomalie légère
        if (rb && rc && (!b || !c)) {
            return new String[] { YELLOW, "Conformité santé, indicateur(s) anormal(aux)" };
        }

        // La bactériologie est OK, mais la chimie pose problème
        if (b && rb && (!c || !rc)) {
            return new String[] { ORANGE, "Bactériologie conforme, chimie non conforme" };
        }

        // Par défaut, si aucun des critères précédents n'est rempli
        return new String[] { RED, "Non conforme" };
    }
}