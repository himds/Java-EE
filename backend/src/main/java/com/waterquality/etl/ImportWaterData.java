package com.waterquality.etl;

import java.sql.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ImportWaterData {
    
    // 数据库配置
    private static final String URL = "jdbc:mysql://localhost:3307/waterdb?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";
    private static final String USER = "wateruser";
    private static final String PASSWORD = "waterpass";
    
    public static void main(String[] args) {
        System.out.println("开始导入水质数据...");
        
        // 数据目录 - 使用Windows绝对路径
        String dataDir = "C:\\Users\\sincerely\\Desktop\\EAU\\water-quality-project\\data\\Données database\\Données database";
        System.out.println("使用数据目录: " + dataDir);
        
        // 同时尝试其他可能的路径格式
        String[] possiblePaths = {
            "C:\\Users\\sincerely\\Desktop\\EAU\\water-quality-project\\data\\Données database\\Données database",
            "C:/Users/sincerely/Desktop/EAU/water-quality-project/data/Données database/Données database",
            "../data/Données database/Données database",
            "../../data/Données database/Données database"
        };
        
        File dataDirFile = null;
        for (String path : possiblePaths) {
            File testFile = new File(path);
            System.out.println("检查路径: " + path);
            System.out.println("  是否存在: " + testFile.exists());
            System.out.println("  是目录: " + testFile.isDirectory());
            
            if (testFile.exists() && testFile.isDirectory()) {
                dataDirFile = testFile;
                dataDir = path;
                System.out.println("✅ 找到有效路径: " + path);
                break;
            }
        }
        
        if (dataDirFile == null) {
            System.out.println("❌ 错误: 无法找到数据目录!");
            System.out.println("请检查数据文件是否在正确的位置。");
            System.out.println("预期位置: C:\\Users\\sincerely\\Desktop\\EAU\\water-quality-project\\data\\Données database\\Données database");
            return;
        }
        
        System.out.println("✅ 数据目录验证通过: " + dataDir);
        
        try {
            // 1. 连接数据库
            Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
            conn.setAutoCommit(false); // 关闭自动提交，提高性能
            
            System.out.println("数据库连接成功!");
            
            // 2. 导入 communes 数据
            importCommunes(conn, dataDir + File.separator + "Table communes");
            
            // 3. 导入 prelevements 数据
            importPrelevements(conn, dataDir + File.separator + "Table prelevements");
            
            // 4. 导入 resultats 数据
            importResultats(conn, dataDir + File.separator + "Table resultats");
            
            // 5. 提交事务
            conn.commit();
            conn.close();
            
            System.out.println("ETL处理完成!");
            System.out.println("注意: 如果没有显示导入行数，可能没有找到数据文件或数据已存在。");
            
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("数据导入失败!");
        }
    }
    
    // 导入 communes 数据
    private static void importCommunes(Connection conn, String folderPath) throws Exception {
        System.out.println("导入 communes 数据...");
        System.out.println("文件夹路径: " + folderPath);
        
        File folder = new File(folderPath);
        System.out.println("文件夹是否存在: " + folder.exists());
        System.out.println("是目录: " + folder.isDirectory());
        
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt"));
        
        if (files == null) {
            System.out.println("文件夹为空或无法访问");
            return;
        }
        
        System.out.println("找到文件数量: " + files.length);
        
        if (files.length == 0) {
            System.out.println("未找到 communes 数据文件!");
            return;
        }
        
        String sql = "INSERT INTO communes (code_insee, nom_commune, departement) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE nom_commune = VALUES(nom_commune), departement = VALUES(departement)";
        
        PreparedStatement pstmt = conn.prepareStatement(sql);
        int totalCount = 0;
        
        for (File file : files) {
            System.out.println("处理文件: " + file.getName());
            int fileCount = 0;
            
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                br.readLine(); // 跳过表头
                
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    
                    // 解析 CSV 行
                    String[] parts = parseCSVLine(line);
                    if (parts.length < 3) continue;
                    
                    String cddept = cleanValue(parts[0]);
                    String codeInsee = cleanValue(parts[1]);
                    String nomCommune = cleanValue(parts[2]);
                    
                    // 设置参数
                    pstmt.setString(1, codeInsee);
                    pstmt.setString(2, nomCommune);
                    pstmt.setString(3, cddept);
                    
                    pstmt.executeUpdate();
                    fileCount++;
                    
                    // 每1000条提交一次
                    if (fileCount % 1000 == 0) {
                        conn.commit();
                    }
                }
                
                conn.commit(); // 提交剩余的数据
                totalCount += fileCount;
                System.out.println("  导入 " + fileCount + " 行");
                
            } catch (Exception e) {
                System.out.println("处理文件 " + file.getName() + " 时出错: " + e.getMessage());
            }
        }
        
        pstmt.close();
        System.out.println("Communes 数据导入完成: 总共 " + totalCount + " 行");
    }
    
    // 导入 prelevements 数据
    private static void importPrelevements(Connection conn, String folderPath) throws Exception {
        System.out.println("导入 prelevements 数据...");
        
        File folder = new File(folderPath);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt"));
        
        if (files == null || files.length == 0) {
            System.out.println("未找到 prelevements 数据文件!");
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
            System.out.println("处理文件: " + file.getName());
            int fileCount = 0;
            
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                br.readLine(); // 跳过表头
                
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    
                    // 解析 CSV 行（处理引号内的逗号）
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
                    
                    // 只取第一个字符（数据库是 CHAR(1)）
                    bacterio = bacterio.length() > 0 ? String.valueOf(bacterio.charAt(0)) : "";
                    chimique = chimique.length() > 0 ? String.valueOf(chimique.charAt(0)) : "";
                    refBact = refBact.length() > 0 ? String.valueOf(refBact.charAt(0)) : "";
                    refChim = refChim.length() > 0 ? String.valueOf(refChim.charAt(0)) : "";
                    
                    // 设置参数
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
                    
                    // 每1000条提交一次
                    if (fileCount % 1000 == 0) {
                        conn.commit();
                    }
                }
                
                conn.commit(); // 提交剩余的数据
                totalCount += fileCount;
                System.out.println("  导入 " + fileCount + " 行");
                
            } catch (Exception e) {
                System.out.println("处理文件 " + file.getName() + " 时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        pstmt.close();
        System.out.println("Prelevements 数据导入完成: 总共 " + totalCount + " 行");
    }
    
    // 导入 resultats 数据
    private static void importResultats(Connection conn, String folderPath) throws Exception {
        System.out.println("导入 resultats 数据...");
        
        // 首先获取所有 prelevements 的映射
        Map<String, Integer> prelevementMap = loadPrelevementMap(conn);
        System.out.println("加载了 " + prelevementMap.size() + " 个 prelevements 映射");
        
        File folder = new File(folderPath);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt"));
        
        if (files == null || files.length == 0) {
            System.out.println("未找到 resultats 数据文件!");
            return;
        }
        
        String sql = "INSERT INTO resultats_analyses (prelevement_id, parametre, valeur_mesuree, limite_legale) VALUES (?, ?, ?, ?)";
        
        PreparedStatement pstmt = conn.prepareStatement(sql);
        int totalCount = 0;
        int skippedCount = 0;
        
        for (File file : files) {
            System.out.println("处理文件: " + file.getName());
            int fileCount = 0;
            int fileSkipped = 0;
            
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                br.readLine(); // 跳过表头
                
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    
                    // 解析 CSV 行
                    String[] parts = parseCSVLine(line);
                    if (parts.length < 4) {
                        fileSkipped++;
                        continue;
                    }
                    
                    String referenceprel = cleanValue(parts[0]);
                    String parametre = cleanValue(parts[1]);
                    String limitequal = cleanValue(parts[2]);
                    String valtraduiteStr = cleanValue(parts[3]);
                    
                    // 查找 prelevement_id
                    Integer prelevementId = prelevementMap.get(referenceprel);
                    if (prelevementId == null) {
                        fileSkipped++;
                        continue;
                    }
                    
                    // 转换数值
                    Double valeurMesuree = null;
                    try {
                        if (valtraduiteStr != null && !valtraduiteStr.isEmpty()) {
                            valeurMesuree = Double.parseDouble(valtraduiteStr);
                        }
                    } catch (NumberFormatException e) {
                        // 如果转换失败，保持为 null
                    }
                    
                    // 设置参数
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