package com.waterquality.service;

import com.waterquality.dao.DatabaseConnection;
import com.waterquality.model.ResultatAnalyse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Service chargé de récupérer les résultats détaillés d'un prélèvement spécifique.
 */
public class DetailsService {

    /**
     * Récupère la référence d'un prélèvement et la liste de toutes ses analyses chimiques/bactériologiques.
     * @param prelevementId L'identifiant unique du prélèvement.
     * @return Un tableau d'objets : [0] la référence (String), [1] la liste des analyses (List).
     */
    public Object[] getDetailsByPrelevementId(int prelevementId) throws Exception {
        // Requête pour récupérer le nom/référence du prélèvement
        String refSql = "SELECT referenceprel FROM prelevements WHERE id = ?";
        
        // Requête pour récupérer les mesures associées (Paramètre, Valeur, Limite)
        String analysesSql =
            "SELECT parametre, valeur_mesuree, limite_legale FROM resultats_analyses " +
            "WHERE prelevement_id = ? ORDER BY id";

        String referenceprel = null;
        List<ResultatAnalyse> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection()) {
            
            try (PreparedStatement ps = conn.prepareStatement(refSql)) {
                ps.setInt(1, prelevementId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) referenceprel = rs.getString("referenceprel");
                }
            }

            // On utilise un Set pour éviter d'afficher deux fois le même paramètre (doublons de données)
            Set<String> seenParametres = new LinkedHashSet<>();
            
            try (PreparedStatement ps = conn.prepareStatement(analysesSql)) {
                ps.setInt(1, prelevementId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String parametre = rs.getString("parametre");
                        String key = parametre != null ? parametre.trim() : "";

                        // Gestion des doublons : si on a déjà vu ce paramètre, on passe au suivant
                        if (!key.isEmpty() && seenParametres.contains(key)) continue;
                        if (!key.isEmpty()) seenParametres.add(key);

                        // Création d'un objet ResultatAnalyse et remplissage des données
                        ResultatAnalyse r = new ResultatAnalyse();
                        r.setPrelevementId(prelevementId);
                        r.setParametre(parametre);

                        // Gestion de la valeur mesurée (conversion sécurisée en Double)
                        Object vm = rs.getObject("valeur_mesuree");
                        r.setValeurMesuree(vm instanceof Number ? ((Number) vm).doubleValue() : null);

                        // Gestion de la limite légale (parfois stockée en texte ou en nombre)
                        Object ll = rs.getObject("limite_legale");
                        if (ll instanceof Number) {
                            r.setLimiteLegale(((Number) ll).doubleValue());
                        } else if (ll != null) {
                            // Tentative de conversion si c'est une chaîne de caractères
                            try { 
                                r.setLimiteLegale(Double.parseDouble(ll.toString().trim())); 
                            } catch (NumberFormatException ignored) { 
                                // Si ce n'est pas un nombre valide, on ignore l'erreur
                            }
                        }
                        
                        list.add(r); // Ajout à la liste finale
                    }
                }
            }
        }
        
        // On retourne les deux informations regroupées
        return new Object[] { referenceprel != null ? referenceprel : "", list };
    }
}