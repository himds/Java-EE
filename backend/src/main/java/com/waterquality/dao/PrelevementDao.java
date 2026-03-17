package com.waterquality.dao;

import com.waterquality.model.Prelevement;
import com.waterquality.util.Database;
import com.waterquality.util.InseeUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class PrelevementDao {
    public Optional<Prelevement> findLatestByCommune(String codeInsee) throws Exception {
        String sql = baseLatestQuery() + " WHERE code_insee = ? ORDER BY dateprel DESC, id DESC LIMIT 1";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, codeInsee);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        }
    }

    public Optional<Prelevement> findLatestByCommuneAndFilters(String codeInsee, Integer year, String pollutant) throws Exception {
        StringBuilder sql = new StringBuilder(baseLatestQuery()).append(" WHERE code_insee = ? ");
        if (year != null) sql.append(" AND YEAR(dateprel) = ? ");
        sql.append(buildPollutantCondition(pollutant));
        sql.append(" ORDER BY dateprel DESC, id DESC LIMIT 1");

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            statement.setString(index++, codeInsee);
            if (year != null) statement.setInt(index++, year);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        }
    }

    public Map<String, Prelevement> findLatestByCommuneAndFilters(Integer year, String pollutant) throws Exception {
        StringBuilder sql = new StringBuilder(baseLatestQuery()).append(" WHERE 1=1 ");
        if (year != null) sql.append(" AND YEAR(dateprel) = ? ");
        sql.append(buildPollutantCondition(pollutant));
        sql.append(" ORDER BY code_insee, dateprel DESC, id DESC");

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            if (year != null) statement.setInt(index++, year);
            try (ResultSet rs = statement.executeQuery()) {
                Map<String, Prelevement> latestByCommune = new LinkedHashMap<>();
                while (rs.next()) {
                    String code = InseeUtils.normalize(rs.getString("code_insee"));
                    latestByCommune.putIfAbsent(code, map(rs));
                }
                return latestByCommune;
            }
        }
    }

    private String baseLatestQuery() {
        return "SELECT id, code_insee, referenceprel, dateprel, heureprel, conclusionprel, " +
               "plvconformitebacterio, plvconformitechimique, plvconformitereferencebact, plvconformitereferencechim FROM prelevements";
    }

    private String buildPollutantCondition(String pollutant) {
        return "";
    }

    private Prelevement map(ResultSet rs) throws Exception {
        Prelevement prelevement = new Prelevement();
        prelevement.setId(rs.getInt("id"));
        prelevement.setCodeInsee(InseeUtils.normalize(rs.getString("code_insee")));
        prelevement.setReferenceprel(rs.getString("referenceprel"));
        prelevement.setDateprel(rs.getDate("dateprel") != null ? String.valueOf(rs.getDate("dateprel")) : null);
        prelevement.setHeureprel(rs.getString("heureprel"));
        prelevement.setConclusionprel(rs.getString("conclusionprel"));
        prelevement.setPlvconformitebacterio(rs.getString("plvconformitebacterio"));
        prelevement.setPlvconformitechimique(rs.getString("plvconformitechimique"));
        prelevement.setPlvconformitereferencebact(rs.getString("plvconformitereferencebact"));
        prelevement.setPlvconformitereferencechim(rs.getString("plvconformitereferencechim"));
        return prelevement;
    }
}
