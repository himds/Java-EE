package com.waterquality.model;

import java.time.LocalDate;

/**
 * Prélèvement d'eau — correspond à la table prelevements.
 * Relation : commune (1) —— (N) prelevement.
 */
public class Prelevement {

    private Integer id;                    // PK, auto-incrémenté
    private String codeInsee;              // Code INSEE → communes.code_insee
    private String referenceprel;          // Référence du prélèvement (unique)
    private LocalDate dateprel;            // Date du prélèvement
    private String heureprel;              // Heure du prélèvement
    private String conclusionprel;        // Conclusion du prélèvement
    private String plvconformitebacterio;  // C/N bactériologie
    private String plvconformitechimique;  // C/N chimique
    private String plvconformitereferencebact;  // C/N référence bactériologie
    private String plvconformitereferencechim;  // C/N référence chimique

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
