package com.waterquality.service;

import com.waterquality.dao.DatabaseConnection;
import com.waterquality.model.Commune;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Recherche de communes par nom pour l'autocomplétion côté front.
 */
public class SearchService {

    /**
     * Recherche floue par nom de commune, au plus 30 résultats.
     *
     * @param query saisie utilisateur (ex. "rou")
     * @return liste des communes correspondantes
     * @throws Exception en cas d'erreur SQL ou de connexion
     */
    public List<Commune> searchCommunesByName(String query) throws Exception {
        List<Commune> result = new ArrayList<>();

        if (query == null) {
            return result;
        }

        String trimmed = query.trim();
        if (trimmed.length() < 1) {
            return result;
        }

        String sql =
                "SELECT code_insee, nom_commune, departement " +
                "FROM communes " +
                "WHERE LOWER(nom_commune) LIKE ? " +
                "ORDER BY nom_commune ASC " +
                "LIMIT 30";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, "%" + trimmed.toLowerCase() + "%");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Commune c = new Commune(
                            rs.getString("code_insee"),
                            rs.getString("nom_commune"),
                            rs.getString("departement")
                    );
                    result.add(c);
                }
            }
        }

        return result;
    }
}

