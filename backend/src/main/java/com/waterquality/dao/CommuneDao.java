package com.waterquality.dao;

import com.waterquality.model.Commune;
import com.waterquality.model.MapFeature;
import com.waterquality.util.Database;
import com.waterquality.util.InseeUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CommuneDao {
    public List<Commune> findAll(int limit) throws Exception {
        String sql = "SELECT code_insee, nom_commune, departement FROM communes ORDER BY nom_commune LIMIT ?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                List<Commune> communes = new ArrayList<>();
                while (rs.next()) communes.add(map(rs));
                return communes;
            }
        }
    }

    public List<Commune> search(String query, int limit) throws Exception {
        String sql = "SELECT code_insee, nom_commune, departement FROM communes WHERE LOWER(nom_commune) LIKE ? ORDER BY nom_commune LIMIT ?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "%" + query.toLowerCase() + "%");
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                List<Commune> communes = new ArrayList<>();
                while (rs.next()) communes.add(map(rs));
                return communes;
            }
        }
    }

    public Optional<Commune> findByCodeInsee(String codeInsee) throws Exception {
        String sql = "SELECT code_insee, nom_commune, departement FROM communes WHERE code_insee = ? LIMIT 1";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, codeInsee);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        }
    }

    public List<MapFeature> findDepartmentAggregates() throws Exception {
        String sql = """
                SELECT departement, COUNT(*) AS sample_count
                FROM communes
                WHERE departement IS NOT NULL AND departement <> ''
                GROUP BY departement
                ORDER BY departement
                """;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<MapFeature> features = new ArrayList<>();
            while (rs.next()) {
                MapFeature feature = new MapFeature();
                feature.setId("dept-" + rs.getString("departement"));
                feature.setType("department");
                feature.setDepartement(rs.getString("departement"));
                feature.setName("Département " + rs.getString("departement"));
                feature.setSampleCount(rs.getInt("sample_count"));
                features.add(feature);
            }
            return features;
        }
    }

    private Commune map(ResultSet rs) throws Exception {
        Commune commune = new Commune();
        commune.setCodeInsee(InseeUtils.normalize(rs.getString("code_insee")));
        commune.setNomCommune(rs.getString("nom_commune"));
        commune.setDepartement(rs.getString("departement"));
        commune.setLatitude(null);
        commune.setLongitude(null);
        return commune;
    }
}
