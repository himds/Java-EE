package com.waterquality.model;

public class MapFeature {
    private String id;
    private String type;
    private String name;
    private String color;
    private String status;
    private String departement;
    private Integer sampleCount;
    private Double centerLat;
    private Double centerLon;
    private Double minLat;
    private Double maxLat;
    private Double minLon;
    private Double maxLon;
    private Integer year;
    private String pollutant;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDepartement() { return departement; }
    public void setDepartement(String departement) { this.departement = departement; }
    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }
    public Double getCenterLat() { return centerLat; }
    public void setCenterLat(Double centerLat) { this.centerLat = centerLat; }
    public Double getCenterLon() { return centerLon; }
    public void setCenterLon(Double centerLon) { this.centerLon = centerLon; }
    public Double getMinLat() { return minLat; }
    public void setMinLat(Double minLat) { this.minLat = minLat; }
    public Double getMaxLat() { return maxLat; }
    public void setMaxLat(Double maxLat) { this.maxLat = maxLat; }
    public Double getMinLon() { return minLon; }
    public void setMinLon(Double minLon) { this.minLon = minLon; }
    public Double getMaxLon() { return maxLon; }
    public void setMaxLon(Double maxLon) { this.maxLon = maxLon; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public String getPollutant() { return pollutant; }
    public void setPollutant(String pollutant) { this.pollutant = pollutant; }
}
