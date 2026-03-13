package com.waterquality.model;

public class Commune {
    private String codeInsee;
    private String nomCommune;
    private Double latitude;
    private Double longitude;
    private String departement;
    private String color;
    private String status;

    public String getCodeInsee() { return codeInsee; }
    public void setCodeInsee(String codeInsee) { this.codeInsee = codeInsee; }
    public String getNomCommune() { return nomCommune; }
    public void setNomCommune(String nomCommune) { this.nomCommune = nomCommune; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public String getDepartement() { return departement; }
    public void setDepartement(String departement) { this.departement = departement; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
