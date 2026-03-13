package com.waterquality.service;

import com.waterquality.model.Prelevement;

public class WaterQualityColorService {
    public String getColor(Prelevement prelevement) {
        if (prelevement == null) return "#9ca3af";
        if ("N".equalsIgnoreCase(prelevement.getPlvconformitebacterio())) return "#e11d48";
        if ("N".equalsIgnoreCase(prelevement.getPlvconformitechimique())) return "#f97316";
        if ("N".equalsIgnoreCase(prelevement.getPlvconformitereferencebact()) || "N".equalsIgnoreCase(prelevement.getPlvconformitereferencechim())) return "#facc15";
        return "#22c55e";
    }

    public String getToneName(Prelevement prelevement) {
        if (prelevement == null) return "Aucune donnée";
        if ("N".equalsIgnoreCase(prelevement.getPlvconformitebacterio())) return "Alerte forte";
        if ("N".equalsIgnoreCase(prelevement.getPlvconformitechimique())) return "Alerte chimique";
        if ("N".equalsIgnoreCase(prelevement.getPlvconformitereferencebact()) || "N".equalsIgnoreCase(prelevement.getPlvconformitereferencechim())) return "Surveillance";
        return "Conforme";
    }
}
