package com.waterquality.model;

/**
 * Commune — correspond à la table communes.
 */
public class Commune {

    private String codeInsee;    // Code INSEE (PK)
    private String nomCommune;   // Nom de la commune
    private String departement;  // Code département (cddept)

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
