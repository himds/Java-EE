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
 * 为地图提供市镇列表及按最新 prélèvement 四字段计算的颜色/状态。
 */
public class MapDataService {

    /**
     * 查询所有市镇及其最新 prélèvement 的四个合规字段，计算 color/status，返回供前端地图使用的 features。
     */
    public List<Map<String, Object>> getMapFeatures() throws Exception {
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

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String codeInsee = rs.getString("code_insee");
                String nomCommune = rs.getString("nom_commune");
                String departement = rs.getString("departement");
                String bacterio = rs.getString("plvconformitebacterio");
                String chimique = rs.getString("plvconformitechimique");
                String refBact = rs.getString("plvconformitereferencebact");
                String refChim = rs.getString("plvconformitereferencechim");

                String[] colorStatus = ConformityColor.fromPrelevement(bacterio, chimique, refBact, refChim);

                Map<String, Object> f = new HashMap<>();
                f.put("id", codeInsee);
                f.put("name", nomCommune != null ? nomCommune : "");
                f.put("departement", departement != null ? departement : "");
                f.put("color", colorStatus[0]);
                f.put("status", colorStatus[1]);
                features.add(f);
            }
        }

        return features;
    }
}
