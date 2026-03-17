package com.waterquality.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    // 默认：用于本机运行时连接 Docker MySQL（docker-compose 映射端口 3307->3306）
    // 容器内运行时会优先使用环境变量 DB_URL/DB_USER/DB_PASSWORD。
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3307/waterdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8";

    private static final String DEFAULT_USER = "wateruser";
    private static final String DEFAULT_PASSWORD = "waterpass";

    private static final String URL = getEnvOrDefault("DB_URL", DEFAULT_URL);
    private static final String USER = getEnvOrDefault("DB_USER", DEFAULT_USER);
    private static final String PASSWORD = getEnvOrDefault("DB_PASSWORD", DEFAULT_PASSWORD);
    private static volatile boolean driverLoaded = false;

    private DatabaseConnection() {}

    private static String getEnvOrDefault(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v != null && !v.trim().isEmpty()) ? v.trim() : defaultValue;
    }

    public static Connection getConnection() throws SQLException {
        // In servlet containers, the MySQL driver may be in the webapp classloader and not auto-registered.
        // Explicitly loading the driver avoids "No suitable driver found" at runtime.
        if (!driverLoaded) {
            synchronized (DatabaseConnection.class) {
                if (!driverLoaded) {
                    try {
                        Class.forName("com.mysql.cj.jdbc.Driver");
                    } catch (ClassNotFoundException e) {
                        throw new SQLException("MySQL JDBC driver not found on classpath", e);
                    }
                    driverLoaded = true;
                }
            }
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
