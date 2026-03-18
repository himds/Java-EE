package com.waterquality.util;

import com.waterquality.dao.DatabaseConnection;

import java.sql.*;
import java.io.*;
import java.util.*;

/**
 * Import ETL : même source de données que l'API.
 * - En local : sans DB_URL, connexion par défaut à localhost:3307 (MySQL Docker).
 * - Pour importer vers MySQL Docker : démarrer Docker puis lancer ce main ; pour MySQL local : définir DB_URL (ex. port 3306).
 */
public class ImportWaterData {

    public static void main(String[] args) {
        System.out.println("Début de l'import des données qualité de l'eau...");

        // Répertoire des données (chemin absolu Windows par défaut)
        String dataDir = "C:\\Users\\sincerely\\Desktop\\EAU\\water-quality-project\\data\\Données database\\Données database";
        System.out.println("Répertoire utilisé : " + dataDir);

        // Autres chemins possibles
        String[] possiblePaths = {
                "C:\\Users\\sincerely\\Desktop\\EAU\\water-quality-project\\data\\Données database\\Données database",
                "C:/Users/sincerely/Desktop/EAU/water-quality-project/data/Données database/Données database",
                "../data/Données database/Données database",
                "../../data/Données database/Données database"
        };

        File dataDirFile = null;
        for (String path : possiblePaths) {
            File testFile = new File(path);
            System.out.println("Vérification du chemin : " + path);
            System.out.println("  Existe : " + testFile.exists());
            System.out.println("  Est un répertoire : " + testFile.isDirectory());

            if (testFile.exists() && testFile.isDirectory()) {
                dataDirFile = testFile;
                dataDir = path;
                System.out.println("Répertoire trouvé : " + path);
                break;
            }
        }

        if (dataDirFile == null) {
            System.out.println("Erreur : répertoire des données introuvable.");
            System.out.println("Vérifiez l'emplacement des fichiers.");
            System.out.println("Emplacement attendu : C:\\Users\\sincerely\\Desktop\\EAU\\water-quality-project\\data\\Données database\\Données database");
            return;
        }

        System.out.println("Répertoire des données validé : " + dataDir);

        try {
            // 1. Connexion BDD (comme DatabaseConnection : DB_URL ou localhost:3307 par défaut)
            Connection conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            String url = conn.getMetaData().getURL();
            System.out.println("Connexion BDD OK " + (url.contains("3307") ? "(Docker MySQL)" : url));

            // 2. Import des communes
            importCommunes(conn, dataDir + File.separator + "Table communes");

            // 3. Import des prélèvements
            importPrelevements(conn, dataDir + File.separator + "Table prelevements");

            // 4. Import des resultats
            importResultats(conn, dataDir + File.separator + "Table resultats");

            // 5. Commit
            conn.commit();
            conn.close();

            System.out.println("ETL terminé.");
            System.out.println("Si aucun nombre de lignes n'est affiché, les fichiers sont peut-être absents ou les données déjà présentes.");

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Échec de l'import.");
        }
    }

    // Import des communes
    private static void importCommunes(Connection conn, String folderPath) throws Exception {
        System.out.println("Import des communes...");
        System.out.println("Dossier : " + folderPath);

        File folder = new File(folderPath);
        System.out.println("Dossier existe : " + folder.exists());
        System.out.println("Est un répertoire : " + folder.isDirectory());

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt"));

        if (files == null) {
            System.out.println("Dossier vide ou inaccessible.");
            return;
        }

        System.out.println("Nombre de fichiers : " + files.length);

        if (files.length == 0) {
            System.out.println("Aucun fichier communes trouvé.");
            return;
        }

        String sql = "INSERT INTO communes (code_insee, nom_commune, departement) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE nom_commune = VALUES(nom_commune), departement = VALUES(departement)";

        PreparedStatement pstmt = conn.prepareStatement(sql);
        int totalCount = 0;

        for (File file : files) {
            System.out.println("Fichier : " + file.getName());
            int fileCount = 0;

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                br.readLine(); // Ignorer l'en-tête

                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;

                    // Parser la ligne CSV
                    String[] parts = parseCSVLine(line);
                    if (parts.length < 3) continue;

                    String cddept = cleanValue(parts[0]);
                    String codeInsee = cleanValue(parts[1]);
                    String nomCommune = cleanValue(parts[2]);

                    // Paramètres
                    pstmt.setString(1, codeInsee);
                    pstmt.setString(2, nomCommune);
                    pstmt.setString(3, cddept);

                    pstmt.executeUpdate();
                    fileCount++;

                    // Commit tous les 1000 enregistrements
                    if (fileCount % 1000 == 0) {
                        conn.commit();
                    }
                }

                conn.commit(); // Reste des données
                totalCount += fileCount;
                System.out.println("  Importé " + fileCount + " lignes");

            } catch (Exception e) {
                System.out.println("Erreur sur le fichier " + file.getName() + " : " + e.getMessage());
            }
        }

        pstmt.close();
        System.out.println("Import communes terminé : " + totalCount + " lignes au total.");
    }

    // Import des prélèvements
    private static void importPrelevements(Connection conn, String folderPath) throws Exception {
        System.out.println("Import des prélèvements...");

        File folder = new File(folderPath);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt"));

        if (files == null || files.length == 0) {
            System.out.println("Aucun fichier prélèvements trouvé.");
            return;
        }

        String sql = "INSERT INTO prelevements (code_insee, referenceprel, dateprel, conclusionprel, " +
                "plvconformitebacterio, plvconformitechimique, plvconformitereferencebact, plvconformitereferencechim) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE code_insee = VALUES(code_insee), dateprel = VALUES(dateprel), conclusionprel = VALUES(conclusionprel), " +
                "plvconformitebacterio = VALUES(plvconformitebacterio), plvconformitechimique = VALUES(plvconformitechimique), " +
                "plvconformitereferencebact = VALUES(plvconformitereferencebact), plvconformitereferencechim = VALUES(plvconformitereferencechim)";

        PreparedStatement pstmt = conn.prepareStatement(sql);
        int totalCount = 0;

        for (File file : files) {
            System.out.println("Fichier : " + file.getName());
            int fileCount = 0;

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                br.readLine(); // Ignorer l'en-tête

                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;

                    // Parser la ligne CSV (virgules dans les guillemets)
                    String[] parts = parseCSVLine(line);
                    if (parts.length < 8) continue;

                    String codeInsee = cleanValue(parts[0]);
                    String referenceprel = cleanValue(parts[1]);
                    String dateprel = cleanValue(parts[2]);
                    String conclusionprel = cleanValue(parts[3]);
                    String bacterio = parts.length > 4 ? cleanValue(parts[4]) : "";
                    String chimique = parts.length > 5 ? cleanValue(parts[5]) : "";
                    String refBact = parts.length > 6 ? cleanValue(parts[6]) : "";
                    String refChim = parts.length > 7 ? cleanValue(parts[7]) : "";

                    // Premier caractère uniquement (BD CHAR(1))
                    bacterio = bacterio.length() > 0 ? String.valueOf(bacterio.charAt(0)) : "";
                    chimique = chimique.length() > 0 ? String.valueOf(chimique.charAt(0)) : "";
                    refBact = refBact.length() > 0 ? String.valueOf(refBact.charAt(0)) : "";
                    refChim = refChim.length() > 0 ? String.valueOf(refChim.charAt(0)) : "";

                    // Paramètres
                    pstmt.setString(1, codeInsee);
                    pstmt.setString(2, referenceprel);
                    pstmt.setString(3, dateprel);
                    pstmt.setString(4, conclusionprel);
                    pstmt.setString(5, bacterio);
                    pstmt.setString(6, chimique);
                    pstmt.setString(7, refBact);
                    pstmt.setString(8, refChim);

                    pstmt.executeUpdate();
                    fileCount++;

                    // Commit tous les 1000 enregistrements
                    if (fileCount % 1000 == 0) {
                        conn.commit();
                    }
                }

                conn.commit(); // Reste des données
                totalCount += fileCount;
                System.out.println("  Importé " + fileCount + " lignes");

            } catch (Exception e) {
                System.out.println("Erreur sur le fichier " + file.getName() + " : " + e.getMessage());
                e.printStackTrace();
            }
        }

        pstmt.close();
        System.out.println("Import prélèvements terminé : " + totalCount + " lignes au total.");
    }

    // Import des resultats
    private static void importResultats(Connection conn, String folderPath) throws Exception {
        System.out.println("Import des resultats...");

        // Charger le mapping prelevements (referenceprel -> id)
        Map<String, Integer> prelevementMap = loadPrelevementMap(conn);
        System.out.println("Mapping prélèvements chargé : " + prelevementMap.size() + " entrées.");

        File folder = new File(folderPath);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt"));

        if (files == null || files.length == 0) {
            System.out.println("Aucun fichier resultats trouvé.");
            return;
        }

        String sql = "INSERT INTO resultats_analyses (prelevement_id, parametre, valeur_mesuree, limite_legale) VALUES (?, ?, ?, ?)";

        PreparedStatement pstmt = conn.prepareStatement(sql);
        int totalCount = 0;
        int skippedCount = 0;

        for (File file : files) {
            System.out.println("Fichier : " + file.getName());
            int fileCount = 0;
            int fileSkipped = 0;

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                br.readLine(); // Ignorer l'en-tête

                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;

                    // Parser la ligne CSV
                    String[] parts = parseCSVLine(line);
                    if (parts.length < 4) {
                        fileSkipped++;
                        continue;
                    }

                    String referenceprel = cleanValue(parts[0]);
                    String parametre = cleanValue(parts[1]);
                    String limitequal = cleanValue(parts[2]);
                    String valtraduiteStr = cleanValue(parts[3]);

                    // Récupérer prelevement_id
                    Integer prelevementId = prelevementMap.get(referenceprel);
                    if (prelevementId == null) {
                        fileSkipped++;
                        continue;
                    }

                    // Conversion numérique
                    Double valeurMesuree = null;
                    try {
                        if (valtraduiteStr != null && !valtraduiteStr.isEmpty()) {
                            valeurMesuree = Double.parseDouble(valtraduiteStr);
                        }
                    } catch (NumberFormatException e) {
                        // En cas d'échec, rester à null
                    }

                    // Paramètres
                    pstmt.setInt(1, prelevementId);
                    pstmt.setString(2, parametre);
                    if (valeurMesuree != null) {
                        pstmt.setDouble(3, valeurMesuree);
                    } else {
                        pstmt.setNull(3, Types.DOUBLE);
                    }
                    pstmt.setString(4, limitequal);

                    pstmt.executeUpdate();
                    fileCount++;

                    // 每1000条提交一次
                    if (fileCount % 1000 == 0) {
                        conn.commit();
                    }
                }

                conn.commit(); // 提交剩余的数据
                totalCount += fileCount;
                skippedCount += fileSkipped;
                System.out.println("  导入 " + fileCount + " 行, 跳过 " + fileSkipped + " 行");

            } catch (Exception e) {
                System.out.println("处理文件 " + file.getName() + " 时出错: " + e.getMessage());
            }
        }

        pstmt.close();
        System.out.println("Resultats 数据导入完成: 总共 " + totalCount + " 行, 跳过 " + skippedCount + " 行");
    }

    // 加载 prelevements 映射
    private static Map<String, Integer> loadPrelevementMap(Connection conn) throws Exception {
        Map<String, Integer> map = new HashMap<>();

        String sql = "SELECT id, referenceprel FROM prelevements";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);

        while (rs.next()) {
            map.put(rs.getString("referenceprel"), rs.getInt("id"));
        }

        rs.close();
        stmt.close();
        return map;
    }

    // 解析 CSV 行（处理引号内的逗号）
    private static String[] parseCSVLine(String line) {
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
        return values.toArray(new String[0]);
    }

    // 清理值
    private static String cleanValue(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.isEmpty() ? null : cleaned;
    }
}