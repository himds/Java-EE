package com.waterquality.dao;

import com.waterquality.model.Commune;
import com.waterquality.model.MapFeature;
import com.waterquality.util.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CommuneDao {
    public List<Commune> findAll(int limit) throws Exception {
        String sql = "SELECT code_insee, nom_commune, latitude, longitude, departement FROM communes ORDER BY nom_commune LIMIT ?";
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
        String sql = "SELECT code_insee, nom_commune, latitude, longitude, departement FROM communes WHERE LOWER(nom_commune) LIKE ? ORDER BY nom_commune LIMIT ?";
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
        String sql = "SELECT code_insee, nom_commune, latitude, longitude, departement FROM communes WHERE code_insee = ? LIMIT 1";
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
                SELECT departement,
                       COUNT(*) AS sample_count,
                       AVG(latitude) AS center_lat,
                       AVG(longitude) AS center_lon,
                       MIN(latitude) AS min_lat,
                       MAX(latitude) AS max_lat,
                       MIN(longitude) AS min_lon,
                       MAX(longitude) AS max_lon
                FROM communes
                WHERE latitude IS NOT NULL AND longitude IS NOT NULL AND departement IS NOT NULL AND departement <> ''
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
                feature.setCenterLat((Double) rs.getObject("center_lat"));
                feature.setCenterLon((Double) rs.getObject("center_lon"));
                feature.setMinLat((Double) rs.getObject("min_lat"));
                feature.setMaxLat((Double) rs.getObject("max_lat"));
                feature.setMinLon((Double) rs.getObject("min_lon"));
                feature.setMaxLon((Double) rs.getObject("max_lon"));
                features.add(feature);
            }
            return features;
        }
    }

    private Commune map(ResultSet rs) throws Exception {
        Commune commune = new Commune();
        commune.setCodeInsee(rs.getString("code_insee"));
        commune.setNomCommune(rs.getString("nom_commune"));
        commune.setLatitude((Double) rs.getObject("latitude"));
        commune.setLongitude((Double) rs.getObject("longitude"));
        commune.setDepartement(rs.getString("departement"));
        return commune;
    }
}
