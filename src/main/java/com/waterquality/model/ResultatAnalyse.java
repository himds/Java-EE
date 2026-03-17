package com.waterquality.model;

/**
 * 检测指标结果 — 对应表 resultats_analyses
 * 关系: prelevement (1) —— (N) resultat_analyse
 */
public class ResultatAnalyse {

    private Integer id;              // PK, 自增
    private Integer prelevementId;    // 检测ID → prelevements.id
    private String parametre;        // 指标名称 (libmajparametre)
    private Double valeurMesuree;    // 测量值 (valtraduite)
    private Double limiteLegale;     // 标准值 (limitequal)，表中列为 limite_legale

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
