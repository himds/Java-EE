package com.waterquality.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Database {

    private static final String URL = System.getenv("DB_URL") != null && !System.getenv("DB_URL").isBlank()
            ? System.getenv("DB_URL")
            : "jdbc:mysql://localhost:3307/waterdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8";

    private static final String USER = System.getenv().getOrDefault("DB_USER", "wateruser");
    private static final String PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "waterpass");

    private Database() {}

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}