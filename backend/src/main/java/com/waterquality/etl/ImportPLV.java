package com.waterquality.etl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waterquality.util.Database;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImportPLV {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        String plvFile = args.length > 0 ? args[0] : "../data/DIS_PLV_2025.txt";
        String geoJsonFile = args.length > 1 ? args[1] : "../frontend/data/communes.geojson";

        Path plvPath = Path.of(plvFile).normalize();
        Path geoPath = Path.of(geoJsonFile).normalize();
        Map<String, double[]> centroids = Files.exists(geoPath) ? loadCommuneCentroids(geoPath) : Map.of();

        try (Connection connection = Database.getConnection();
             BufferedReader reader = new BufferedReader(new FileReader(plvPath.toFile()));
             PreparedStatement communeStmt = connection.prepareStatement(
                     """
                     INSERT INTO communes(code_insee, nom_commune, latitude, longitude, departement, cdreseau, nomreseau)
                     VALUES(?,?,?,?,?,?,?)
                     ON DUPLICATE KEY UPDATE
                       nom_commune = VALUES(nom_commune),
                       latitude = COALESCE(communes.latitude, VALUES(latitude)),
                       longitude = COALESCE(communes.longitude, VALUES(longitude)),
                       departement = VALUES(departement),
                       cdreseau = VALUES(cdreseau),
                       nomreseau = VALUES(nomreseau)
                     """
             );
             PreparedStatement prelevementStmt = connection.prepareStatement(
                     """
                     INSERT INTO prelevements(
                       code_insee, cdreseau, referenceprel, dateprel, heureprel, conclusionprel,
                       ugelib, distrlib, moalib,
                       plvconformitebacterio, plvconformitechimique,
                       plvconformitereferencebact, plvconformitereferencechim
                     ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                     """
             )) {

            connection.setAutoCommit(false);
            String line;
            boolean firstLine = true;
            int batchSize = 0;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                if (firstLine) {
                    firstLine = false;
                    if (line.toLowerCase().contains("cddept")) continue;
                }

                List<String> cols = parseCsvLine(line);
                if (cols.size() < 18) continue;

                String departement = clean(cols.get(0));
                String cdreseau = clean(cols.get(1));
                String codeInsee = clean(cols.get(2));
                String nomCommune = clean(cols.get(3));
                String referenceprel = clean(cols.get(7));
                String dateprel = clean(cols.get(8));
                String heureprel = clean(cols.get(9));
                String conclusionprel = clean(cols.get(10));
                String ugelib = clean(cols.get(11));
                String distrlib = clean(cols.get(12));
                String moalib = clean(cols.get(13));
                String bacterio = clean(cols.get(14));
                String chimique = clean(cols.get(15));
                String refBact = clean(cols.get(16));
                String refChim = clean(cols.get(17));

                double[] centroid = centroids.get(codeInsee);
                communeStmt.setString(1, codeInsee);
                communeStmt.setString(2, nomCommune);
                communeStmt.setObject(3, centroid == null ? null : centroid[0]);
                communeStmt.setObject(4, centroid == null ? null : centroid[1]);
                communeStmt.setString(5, departement);
                communeStmt.setString(6, cdreseau);
                communeStmt.setString(7, nomCommune);
                communeStmt.addBatch();

                prelevementStmt.setString(1, codeInsee);
                prelevementStmt.setString(2, cdreseau);
                prelevementStmt.setString(3, referenceprel);
                prelevementStmt.setObject(4, dateprel == null || dateprel.isBlank() ? null : LocalDate.parse(dateprel));
                prelevementStmt.setString(5, heureprel);
                prelevementStmt.setString(6, conclusionprel);
                prelevementStmt.setString(7, ugelib);
                prelevementStmt.setString(8, distrlib);
                prelevementStmt.setString(9, moalib);
                prelevementStmt.setString(10, bacterio);
                prelevementStmt.setString(11, chimique);
                prelevementStmt.setString(12, refBact);
                prelevementStmt.setString(13, refChim);
                prelevementStmt.addBatch();

                batchSize++;
                if (batchSize >= 1000) {
                    communeStmt.executeBatch();
                    prelevementStmt.executeBatch();
                    connection.commit();
                    batchSize = 0;
                }
            }

            if (batchSize > 0) {
                communeStmt.executeBatch();
                prelevementStmt.executeBatch();
                connection.commit();
            }
        }
    }

    private static String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.isBlank() ? null : cleaned;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private static Map<String, double[]> loadCommuneCentroids(Path geoPath) throws Exception {
        Map<String, double[]> result = new HashMap<>();
        JsonNode root = OBJECT_MAPPER.readTree(Files.readString(geoPath));
        for (JsonNode feature : root.path("features")) {
            String code = feature.path("properties").path("code").asText(null);
            if (code == null || code.isBlank()) continue;
            JsonNode coordinates = feature.path("geometry").path("coordinates");
            double[] bounds = extractBounds(coordinates, new double[]{Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY});
            if (Double.isFinite(bounds[0])) {
                double lat = (bounds[1] + bounds[3]) / 2.0;
                double lon = (bounds[0] + bounds[2]) / 2.0;
                result.put(code, new double[]{lat, lon});
            }
        }
        return result;
    }

    private static double[] extractBounds(JsonNode node, double[] bounds) {
        if (node == null || node.isMissingNode()) return bounds;
        if (node.isArray() && node.size() >= 2 && node.get(0).isNumber() && node.get(1).isNumber()) {
            double lon = node.get(0).asDouble();
            double lat = node.get(1).asDouble();
            bounds[0] = Math.min(bounds[0], lon);
            bounds[1] = Math.min(bounds[1], lat);
            bounds[2] = Math.max(bounds[2], lon);
            bounds[3] = Math.max(bounds[3], lat);
            return bounds;
        }
        for (JsonNode child : node) {
            extractBounds(child, bounds);
        }
        return bounds;
    }
}
