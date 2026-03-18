package com.waterquality.service;

import com.waterquality.dao.DatabaseConnection;
import com.waterquality.model.Commune;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Service de recherche textuelle.
 * Il permet de suggérer une liste de communes suite à une saisie utilisateur
 */
public class SearchService {

    /**
     * Effectue une recherche dans la table des communes.
     * @param query La chaîne tapée par l'utilisateur (ex: "rou")
     * @return Une liste d'objets Commune (maximum 30 résultats pour la performance)
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

        // LOWER(...) permet de rendre la recherche insensible à la casse (Majuscules/Minuscules).
        // LIKE ? avec des % permet de trouver le texte n'importe où dans le nom.
        // LIMIT 30 évite de renvoyer toutes les communes avec un "a" dans le nom si l'utilisateur tape juste la lettre "a".
        String sql =
                "SELECT code_insee, nom_commune, departement " +
                "FROM communes " +
                "WHERE LOWER(nom_commune) LIKE ? " +
                "ORDER BY nom_commune ASC " +
                "LIMIT 30";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // On entoure la saisie par des "%" : 
            // "%rou%" trouvera "Rouen", "Saint-Etienne-du-Rouvray", etc.
            ps.setString(1, "%" + trimmed.toLowerCase() + "%");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // On transforme chaque ligne de la base de données en un objet Java "Commune"
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