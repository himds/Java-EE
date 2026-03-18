package com.waterquality.model;

/**
 * Résultat d'analyse — correspond à la table resultats_analyses.
 * Relation : prelevement (1) —— (N) resultat_analyse.
 */
public class ResultatAnalyse {

    private Integer id;              // PK, auto-incrémenté
    private Integer prelevementId;   // ID prélèvement → prelevements.id
    private String parametre;        // Nom du paramètre (libmajparametre)
    private Double valeurMesuree;    // Valeur mesurée (valtraduite)
    private Double limiteLegale;     // Limite légale (limitequal), colonne limite_legale

    public ResultatAnalyse() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getPrelevementId() { return prelevementId; }
    public void setPrelevementId(Integer prelevementId) { this.prelevementId = prelevementId; }

    public String getParametre() { return parametre; }
    public void setParametre(String parametre) { this.parametre = parametre; }

    public Double getValeurMesuree() { return valeurMesuree; }
    public void setValeurMesuree(Double valeurMesuree) { this.valeurMesuree = valeurMesuree; }

    public Double getLimiteLegale() { return limiteLegale; }
    public void setLimiteLegale(Double limiteLegale) { this.limiteLegale = limiteLegale; }
}
