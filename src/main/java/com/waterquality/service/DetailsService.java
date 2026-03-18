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
 * Récupère les resultats_analyses associés à un prélèvement (par id),
 * retourne referenceprel et pour chaque analyse : parametre, valeurMesuree, limiteLegale.
 */
public class DetailsService {

    /**
     * Récupère referenceprel et toutes les analyses pour un prelevement_id.
     * @return [0]=referenceprel, [1]=Liste de ResultatAnalyse
     */
    public Object[] getDetailsByPrelevementId(int prelevementId) throws Exception {
        String refSql = "SELECT referenceprel FROM prelevements WHERE id = ?";
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
            Set<String> seenParametres = new LinkedHashSet<>();
            try (PreparedStatement ps = conn.prepareStatement(analysesSql)) {
                ps.setInt(1, prelevementId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String parametre = rs.getString("parametre");
                        String key = parametre != null ? parametre.trim() : "";
                        if (!key.isEmpty() && seenParametres.contains(key)) continue;
                        if (!key.isEmpty()) seenParametres.add(key);

                        ResultatAnalyse r = new ResultatAnalyse();
                        r.setPrelevementId(prelevementId);
                        r.setParametre(parametre);
                        Object vm = rs.getObject("valeur_mesuree");
                        r.setValeurMesuree(vm instanceof Number ? ((Number) vm).doubleValue() : null);
                        Object ll = rs.getObject("limite_legale");
                        if (ll instanceof Number) r.setLimiteLegale(((Number) ll).doubleValue());
                        else if (ll != null) {
                            try { r.setLimiteLegale(Double.parseDouble(ll.toString().trim())); } catch (NumberFormatException ignored) { }
                        }
                        list.add(r);
                    }
                }
            }
        }
        return new Object[] { referenceprel != null ? referenceprel : "", list };
    }
}
