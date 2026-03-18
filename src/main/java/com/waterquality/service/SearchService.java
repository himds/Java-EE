package com.waterquality.service;

import com.waterquality.dao.DatabaseConnection;
import com.waterquality.model.Commune;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * 提供基于名称的城市搜索，用于前端自动补全。
 */
public class SearchService {

    /**
     * 按城市名称模糊搜索 communes，返回最多 30 条。
     *
     * @param query 用户输入，例如 "rou"
     * @return 匹配的城市列表
     * @throws Exception SQL 或连接异常
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

