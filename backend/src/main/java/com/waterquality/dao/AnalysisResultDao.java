package com.waterquality.dao;

import com.waterquality.model.AnalysisResult;
import com.waterquality.util.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class AnalysisResultDao {
    public List<AnalysisResult> findByPrelevementId(int prelevementId) throws Exception {
        String sql = "SELECT id, prelevement_id, parametre, valeur_mesuree, limite_legale FROM resultats_analyses WHERE prelevement_id = ? ORDER BY id";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, prelevementId);
            try (ResultSet rs = statement.executeQuery()) {
                List<AnalysisResult> results = new ArrayList<>();
                while (rs.next()) {
                    AnalysisResult result = new AnalysisResult();
                    result.setId(rs.getInt("id"));
                    result.setPrelevementId(rs.getInt("prelevement_id"));
                    result.setParametre(rs.getString("parametre"));
                    result.setValeurMesuree((Double) rs.getObject("valeur_mesuree"));
                    result.setLimiteLegale((Double) rs.getObject("limite_legale"));
                    results.add(result);
                }
                return results;
            }
        }
    }
}
