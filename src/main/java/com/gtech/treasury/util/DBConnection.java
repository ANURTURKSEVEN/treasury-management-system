package com.gtech.treasury.util;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Veritabanı bağlantısını yöneten yardımcı (util) sınıf.
 * Ayarları classpath'teki db.properties dosyasından okur.
 * Aşama 4 (JDBC) + Aşama 15 (Utility / Config dosyası).
 */
public class DBConnection {

    private static String url;
    private static String user;
    private static String password;

    // Sınıf ilk kullanıldığında bir kere ayarları yükle
    static {
        try (InputStream in =
                     DBConnection.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in == null) {
                throw new RuntimeException("db.properties bulunamadı! (src/main/resources altında olmalı)");
            }
            Properties props = new Properties();
            props.load(in);
            url = props.getProperty("db.url");
            user = props.getProperty("db.user");
            password = props.getProperty("db.password");
        } catch (Exception e) {
            throw new RuntimeException("Veritabanı ayarları yüklenemedi: " + e.getMessage(), e);
        }
    }

    /** Yeni bir veritabanı bağlantısı döndürür. */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** Bağlantıyı hızlıca test etmek için basit bir main. */
    public static void main(String[] args) {
        try (Connection conn = getConnection()) {
            System.out.println("Bağlantı BAŞARILI! -> " + conn.getCatalog());
        } catch (SQLException e) {
            System.out.println("Bağlantı HATASI: " + e.getMessage());
        }
    }
}
