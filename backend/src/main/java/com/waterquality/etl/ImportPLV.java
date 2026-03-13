package com.waterquality.etl;

import com.waterquality.util.Database;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class ImportPLV {
    public static void main(String[] args) throws Exception {
        String file = args.length > 0 ? args[0] : "data/DIS_PLV_2025.txt";
        Path path = Path.of(file).normalize();

        try (Connection connection = Database.getConnection();
             BufferedReader reader = new BufferedReader(new FileReader(path.toFile()));
             PreparedStatement communeStmt = connection.prepareStatement(
                     "INSERT INTO communes(code_insee, nom_commune, latitude, longitude, departement) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE nom_commune=VALUES(nom_commune), latitude=VALUES(latitude), longitude=VALUES(longitude), departement=VALUES(departement)"
             );
             PreparedStatement prelevementStmt = connection.prepareStatement(
                     "INSERT INTO prelevements(code_insee, dateprel, conclusionprel, plvconformitebacterio, plvconformitechimique, plvconformitereferencebact, plvconformitereferencechim) VALUES(?,?,?,?,?,?,?)"
             )) {

            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                if (firstLine && line.toLowerCase().contains("code")) {
                    firstLine = false;
                    continue;
                }
                firstLine = false;

                String[] cols = line.split(";", -1);
                if (cols.length < 10) continue;

                communeStmt.setString(1, cols[0].trim());
                communeStmt.setString(2, cols[1].trim());
                communeStmt.setObject(3, parseDouble(cols[2]));
                communeStmt.setObject(4, parseDouble(cols[3]));
                communeStmt.setString(5, cols[4].trim());
                communeStmt.addBatch();

                prelevementStmt.setString(1, cols[0].trim());
                prelevementStmt.setString(2, cols[5].trim());
                prelevementStmt.setString(3, cols[6].trim());
                prelevementStmt.setString(4, cols[7].trim());
                prelevementStmt.setString(5, cols[8].trim());
                prelevementStmt.setString(6, cols.length > 9 ? cols[9].trim() : null);
                prelevementStmt.setString(7, cols.length > 10 ? cols[10].trim() : null);
                prelevementStmt.addBatch();
            }

            communeStmt.executeBatch();
            prelevementStmt.executeBatch();
        }
    }

    private static Double parseDouble(String value) {
        try {
            if (value == null || value.isBlank()) return null;
            return Double.parseDouble(value.replace(',', '.').trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
