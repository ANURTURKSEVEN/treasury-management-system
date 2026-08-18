package com.gtech.treasury.util;

/**
 * O an sisteme giriş yapmış kullanıcıyı tutan basit oturum bilgisi.
 * Hata loglarında "kim yaptı" bilgisini doldurmak için kullanılır.
 */
public final class Session {

    private static String currentUsername = "ANONIM";

    private Session() {
    }

    public static void setCurrentUsername(String username) {
        currentUsername = username;
    }

    public static String getCurrentUsername() {
        return currentUsername;
    }

    public static void clear() {
        currentUsername = "ANONIM";
    }
}
