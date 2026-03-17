package com.waterquality.model;

/**
 * 城市/地区 — 对应表 communes
 */
public class Commune {

    private String codeInsee;    // 城市ID (INSEE), PK
    private String nomCommune;    // 城市名称
    private String departement;   // 部门编号 (cddept)

    public Commune() {}

    public Commune(String codeInsee, String nomCommune, String departement) {
        this.codeInsee = codeInsee;
        this.nomCommune = nomCommune;
        this.departement = departement;
    }

    public String getCodeInsee() { return codeInsee; }
    public void setCodeInsee(String codeInsee) { this.codeInsee = codeInsee; }

    public String getNomCommune() { return nomCommune; }
    public void setNomCommune(String nomCommune) { this.nomCommune = nomCommune; }

    public String getDepartement() { return departement; }
    public void setDepartement(String departement) { this.departement = departement; }
}
