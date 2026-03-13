package com.waterquality.model;

import java.util.List;

public class MapResponse {
    private String mode;
    private Integer year;
    private String pollutant;
    private List<MapFeature> features;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public String getPollutant() { return pollutant; }
    public void setPollutant(String pollutant) { this.pollutant = pollutant; }
    public List<MapFeature> getFeatures() { return features; }
    public void setFeatures(List<MapFeature> features) { this.features = features; }
}
