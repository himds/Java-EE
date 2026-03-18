package com.waterquality.service;

import com.waterquality.dao.DatabaseConnection;
import com.waterquality.util.ConformityColor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service gérant la récupération des données pour l'affichage de la carte.
 * Calcule l'état sanitaire de chaque commune en se basant sur son dernier prélèvement.
 */
public class MapDataService {

    /**
     * Extrait la liste de toutes les communes avec leur statut de conformité.
     * @return Une liste de Maps, où chaque Map représente une "feature" (commune) pour la carte.
     */
    public List<Map<String, Object>> getMapFeatures() throws Exception {
        
        // récupère chaque commune (c) et lui joint uniquement
        // l'ID du prélèvement (p) le plus récent trouvé dans la table 'prelevements'.
        String sql =
            "SELECT c.code_insee, c.nom_commune, c.departement, " +
            "  p.plvconformitebacterio, p.plvconformitechimique, " +
            "  p.plvconformitereferencebact, p.plvconformitereferencechim " +
            "FROM communes c " +
            "LEFT JOIN prelevements p ON p.id = (" +
            "  SELECT p2.id FROM prelevements p2 " +
            "  WHERE p2.code_insee = c.code_insee " +
            "  ORDER BY p2.dateprel DESC, p2.id DESC LIMIT 1" +
            ")";

        List<Map<String, Object>> features = new ArrayList<>();

        // Connexion à la base et exécution de la requête
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                // Extraction des données de base de la commune
                String codeInsee = rs.getString("code_insee");
                String nomCommune = rs.getString("nom_commune");
                String departement = rs.getString("departement");
                
                // Extraction des indicateurs de conformité du dernier prélèvement
                String bacterio = rs.getString("plvconformitebacterio");
                String chimique = rs.getString("plvconformitechimique");
                String refBact = rs.getString("plvconformitereferencebact");
                String refChim = rs.getString("plvconformitereferencechim");

                // Calcul de la couleur et du statut via la classe utilitaire
                // Si les champs sont null (pas de prélèvement), fromPrelevement renverra du gris.
                String[] colorStatus = ConformityColor.fromPrelevement(bacterio, chimique, refBact, refChim);

                // Construction de l'objet de données pour cette commune
                Map<String, Object> f = new HashMap<>();
                f.put("id", codeInsee); // L'ID sert de clé pour lier au GeoJSON sur le front
                f.put("name", nomCommune != null ? nomCommune : "");
                f.put("departement", departement != null ? departement : "");
                f.put("color", colorStatus[0]);  // Exemple : "#2ecc71" (vert)
                f.put("status", colorStatus[1]); // Exemple : "Conforme"
                
                features.add(f);
            }
        }

        return features;
    }
}