package com.waterquality.model;

import java.time.LocalDate;

/**
 * 水样检测记录 — 对应表 prelevements
 * 关系: commune (1) —— (N) prelevement
 */
public class Prelevement {

    private Integer id;                    // PK, 自增
    private String codeInsee;              // 城市ID → communes.code_insee
    private String referenceprel;          // 检测编号，唯一
    private LocalDate dateprel;             // 检测日期
    private String heureprel;              // 检测时间
    private String conclusionprel;         // 检测结论
    private String plvconformitebacterio;   // C/N bacterio
    private String plvconformitechimique;   // C/N chimique
    private String plvconformitereferencebact;  // C/N ref bacterio
    private String plvconformitereferencechim; // C/N ref chimique

    public Prelevement() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getCodeInsee() { return codeInsee; }
    public void setCodeInsee(String codeInsee) { this.codeInsee = codeInsee; }

    public String getReferenceprel() { return referenceprel; }
    public void setReferenceprel(String referenceprel) { this.referenceprel = referenceprel; }

    public LocalDate getDateprel() { return dateprel; }
    public void setDateprel(LocalDate dateprel) { this.dateprel = dateprel; }

    public String getHeureprel() { return heureprel; }
    public void setHeureprel(String heureprel) { this.heureprel = heureprel; }

    public String getConclusionprel() { return conclusionprel; }
    public void setConclusionprel(String conclusionprel) { this.conclusionprel = conclusionprel; }

    public String getPlvconformitebacterio() { return plvconformitebacterio; }
    public void setPlvconformitebacterio(String plvconformitebacterio) { this.plvconformitebacterio = plvconformitebacterio; }

    public String getPlvconformitechimique() { return plvconformitechimique; }
    public void setPlvconformitechimique(String plvconformitechimique) { this.plvconformitechimique = plvconformitechimique; }

    public String getPlvconformitereferencebact() { return plvconformitereferencebact; }
    public void setPlvconformitereferencebact(String plvconformitereferencebact) { this.plvconformitereferencebact = plvconformitereferencebact; }

    public String getPlvconformitereferencechim() { return plvconformitereferencechim; }
    public void setPlvconformitereferencechim(String plvconformitereferencechim) { this.plvconformitereferencechim = plvconformitereferencechim; }
}
