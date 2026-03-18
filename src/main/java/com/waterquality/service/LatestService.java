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
 * Service chargé de récupérer les informations de base d'une commune
 * et les données de son dernier prélèvement enregistré.
 */
public class LatestService {

    // Requête pour obtenir le prélèvement le plus récent (tri par date descendante et ID)
    private static final String SQL =
        "SELECT p.id, p.code_insee, p.referenceprel, p.dateprel, p.plvconformitebacterio, " +
        "       p.plvconformitechimique, p.plvconformitereferencebact, p.plvconformitereferencechim " +
        "FROM prelevements p " +
        "WHERE p.code_insee = ? " +
        "ORDER BY p.dateprel DESC, p.id DESC LIMIT 1";

    // Requête pour obtenir les infos de la commune
    private static final String COMMUNE_SQL =
        "SELECT c.code_insee, c.nom_commune, c.departement FROM communes c WHERE c.code_insee = ?";

    /**
     * Récupère les informations pour une commune donnée.
     * @param codeInsee Le code INSEE de la commune.
     * @return Une Map contenant deux objets : "commune" (identités) et "prelevement" (données santé).
     */
    public Map<String, Object> getLatestByCodeInsee(String codeInsee) throws Exception {
        Map<String, Object> out = new HashMap<>();
        String normalized = codeInsee != null ? codeInsee.trim() : "";

        try (Connection conn = DatabaseConnection.getConnection()) {
            
            // Recherche de la Commune
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

            // Si la commune n'existe pas en base, on s'arrête là
            if (commune.isEmpty()) {
                out.put("commune", null);
                out.put("prelevement", null);
                return out;
            }

            // Recherche du dernier Prélèvement
            Map<String, Object> prelevement = null;
            try (PreparedStatement ps = conn.prepareStatement(SQL)) {
                ps.setString(1, normalized);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        // On récupère les 4 indicateurs de conformité
                        String bacterio = rs.getString("plvconformitebacterio");
                        String chimique = rs.getString("plvconformitechimique");
                        String refBact = rs.getString("plvconformitereferencebact");
                        String refChim = rs.getString("plvconformitereferencechim");

                        // Appel à l'utilitaire ConformityColor pour traduire ces textes en 
                        // une couleur (ex: #FF0000) et un statut (ex: "Non conforme")
                        String[] colorStatus = ConformityColor.fromPrelevement(bacterio, chimique, refBact, refChim);

                        prelevement = new HashMap<>();
                        prelevement.put("id", rs.getInt("id"));
                        prelevement.put("referenceprel", rs.getString("referenceprel"));
                        
                        Date d = rs.getDate("dateprel");
                        prelevement.put("dateprel", d != null ? d.toString() : null);
                        
                        // On injecte les résultats du calcul de conformité
                        prelevement.put("color", colorStatus[0]);
                        prelevement.put("status", colorStatus[1]);
                    }
                }
            }

            // Consolidation des données
            if (prelevement != null) {
                // Si on a un prélèvement, la commune prend sa couleur et son statut
                commune.put("status", prelevement.get("status"));
                commune.put("color", prelevement.get("color"));
            } else {
                // Cas d'une commune "blanche" (existante mais sans analyses)
                commune.put("status", "Aucune donnée");
                commune.put("color", ConformityColor.GREY);
            }
            
            out.put("commune", commune);
            out.put("prelevement", prelevement);
        }
        return out;
    }
}