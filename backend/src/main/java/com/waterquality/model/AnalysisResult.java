package com.waterquality.model;

public class AnalysisResult {
    private int id;
    private int prelevementId;
    private String parametre;
    private Double valeurMesuree;
    private Double limiteLegale;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getPrelevementId() { return prelevementId; }
    public void setPrelevementId(int prelevementId) { this.prelevementId = prelevementId; }
    public String getParametre() { return parametre; }
    public void setParametre(String parametre) { this.parametre = parametre; }
    public Double getValeurMesuree() { return valeurMesuree; }
    public void setValeurMesuree(Double valeurMesuree) { this.valeurMesuree = valeurMesuree; }
    public Double getLimiteLegale() { return limiteLegale; }
    public void setLimiteLegale(Double limiteLegale) { this.limiteLegale = limiteLegale; }
}
