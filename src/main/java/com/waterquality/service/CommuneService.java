package com.waterquality.service;

import com.waterquality.model.Commune;
import com.waterquality.dao.DatabaseConnection;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** 从 MySQL 读取 communes。 */
public class CommuneService {

    public List<Commune> getCommunesFromDb() throws Exception {
        List<Commune> list = new ArrayList<>();
        String sql = "SELECT code_insee, nom_commune, departement FROM communes LIMIT 500";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Commune(
                    rs.getString("code_insee"),
                    rs.getString("nom_commune"),
                    rs.getString("departement")
                ));
            }
        }
        return list;
    }
}
