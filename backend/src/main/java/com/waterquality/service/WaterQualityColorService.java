package com.waterquality.service;

import com.waterquality.model.Prelevement;

/**
 * Couleur selon: plvconformitebacterio, plvconformitechimique,
 * plvconformitereferencebact, plvconformitereferencechim (C/N).
 * Vert: C C C C | Jaune: bacterio=C chimique=C mais ref N | Orange: bacterio=C chimique=N | Rouge: bacterio=N
 */
public class WaterQualityColorService {
    public String getColor(Prelevement prelevement) {
        if (prelevement == null) return "#9ca3af";
        String b = trimCN(prelevement.getPlvconformitebacterio());
        String c = trimCN(prelevement.getPlvconformitechimique());
        String rb = trimCN(prelevement.getPlvconformitereferencebact());
        String rc = trimCN(prelevement.getPlvconformitereferencechim());
        if (b == null && c == null && rb == null && rc == null) return "#9ca3af";
        if ("N".equalsIgnoreCase(b)) return "#e11d48";
        if ("N".equalsIgnoreCase(c)) return "#f97316";
        if ("N".equalsIgnoreCase(rb) || "N".equalsIgnoreCase(rc)) return "#facc15";
        return "#22c55e";
    }

    public String getToneName(Prelevement prelevement) {
        if (prelevement == null) return "Aucune donnée";
        String b = trimCN(prelevement.getPlvconformitebacterio());
        String c = trimCN(prelevement.getPlvconformitechimique());
        String rb = trimCN(prelevement.getPlvconformitereferencebact());
        String rc = trimCN(prelevement.getPlvconformitereferencechim());
        if (b == null && c == null && rb == null && rc == null) return "Aucune donnée";
        if ("N".equalsIgnoreCase(b)) return "Alerte forte";
        if ("N".equalsIgnoreCase(c)) return "Alerte chimique";
        if ("N".equalsIgnoreCase(rb) || "N".equalsIgnoreCase(rc)) return "Surveillance";
        return "Conforme";
    }

    private static String trimCN(String s) {
        return s == null ? null : s.trim();
    }
}
