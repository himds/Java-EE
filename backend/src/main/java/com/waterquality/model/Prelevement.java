package com.waterquality.model;

public class Prelevement {
    private int id;
    private String codeInsee;
    private String dateprel;
    private String conclusionprel;
    private String plvconformitebacterio;
    private String plvconformitechimique;
    private String plvconformitereferencebact;
    private String plvconformitereferencechim;
    private String color;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getCodeInsee() { return codeInsee; }
    public void setCodeInsee(String codeInsee) { this.codeInsee = codeInsee; }
    public String getDateprel() { return dateprel; }
    public void setDateprel(String dateprel) { this.dateprel = dateprel; }
    public String getConclusionprel() { return conclusionprel; }
    public void setConclusionprel(String conclusionprel) { this.conclusionprel = conclusionprel; }
    public String getPlvconformitebacterio() { return plvconformitebacterio; }
    public void setPlvconformitebacterio(String value) { this.plvconformitebacterio = value; }
    public String getPlvconformitechimique() { return plvconformitechimique; }
    public void setPlvconformitechimique(String value) { this.plvconformitechimique = value; }
    public String getPlvconformitereferencebact() { return plvconformitereferencebact; }
    public void setPlvconformitereferencebact(String value) { this.plvconformitereferencebact = value; }
    public String getPlvconformitereferencechim() { return plvconformitereferencechim; }
    public void setPlvconformitereferencechim(String value) { this.plvconformitereferencechim = value; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
