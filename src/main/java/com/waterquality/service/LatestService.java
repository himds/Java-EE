package com.waterquality.service;

import com.waterquality.dao.DatabaseConnection;
import com.waterquality.model.Prelevement;
import com.waterquality.util.ConformityColor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Date;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Récupère le dernier prélèvement d'une commune par code_insee et calcule color/status.
 */
public class LatestService {

    private static final String SQL =
        "SELECT p.id, p.code_insee, p.referenceprel, p.dateprel, p.plvconformitebacterio, " +
        "       p.plvconformitechimique, p.plvconformitereferencebact, p.plvconformitereferencechim " +
        "FROM prelevements p " +
        "WHERE p.code_insee = ? " +
        "ORDER BY p.dateprel DESC, p.id DESC LIMIT 1";

    private static final String COMMUNE_SQL =
        "SELECT c.code_insee, c.nom_commune, c.departement FROM communes c WHERE c.code_insee = ?";

    /**
     * Retourne le dernier prélèvement de la commune et les infos commune (pour l'affichage).
     * Sans prélèvement, commune est renseigné mais prelevement est null.
     * @return Map : "commune" -> { codeInsee, nomCommune, departement, status, color },
     *               "prelevement" -> { id, dateprel, referenceprel, color } ou null
     */
    public Map<String, Object> getLatestByCodeInsee(String codeInsee) throws Exception {
        Map<String, Object> out = new HashMap<>();
        String normalized = codeInsee != null ? codeInsee.trim() : "";

        try (Connection conn = DatabaseConnection.getConnection()) {
            Map<String, Object> commune = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(COMMUNE_SQL)) {
                ps.setString(1, normalized);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        commune.put("codeInsee", rs.getString("code_insee"));
                        commune.put("nomCommune", rs.getString("nom_commune"));
                        commune.put("departement", rs.getString("departement"));
                    }
                }
            }
            if (commune.isEmpty()) {
                out.put("commune", null);
                out.put("prelevement", null);
                return out;
            }

            Map<String, Object> prelevement = null;
            try (PreparedStatement ps = conn.prepareStatement(SQL)) {
                ps.setString(1, normalized);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String bacterio = rs.getString("plvconformitebacterio");
                        String chimique = rs.getString("plvconformitechimique");
                        String refBact = rs.getString("plvconformitereferencebact");
                        String refChim = rs.getString("plvconformitereferencechim");
                        String[] colorStatus = ConformityColor.fromPrelevement(bacterio, chimique, refBact, refChim);

                        prelevement = new HashMap<>();
                        prelevement.put("id", rs.getInt("id"));
                        prelevement.put("referenceprel", rs.getString("referenceprel"));
                        Date d = rs.getDate("dateprel");
                        prelevement.put("dateprel", d != null ? d.toString() : null);
                        prelevement.put("color", colorStatus[0]);
                        prelevement.put("status", colorStatus[1]);
                    }
                }
            }

            if (prelevement != null) {
                commune.put("status", prelevement.get("status"));
                commune.put("color", prelevement.get("color"));
            } else {
                commune.put("status", "Aucune donnée");
                commune.put("color", ConformityColor.GREY);
            }
            out.put("commune", commune);
            out.put("prelevement", prelevement);
        }
        return out;
    }
}
