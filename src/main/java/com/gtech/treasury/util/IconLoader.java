package com.gtech.treasury.util;

import javax.swing.ImageIcon;
import java.awt.Image;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/** /icons/*.png dosyalarını classpath'ten yükler, ölçekler ve önbelleğe alır. */
public final class IconLoader {

    private static final Map<String, ImageIcon> CACHE = new HashMap<>();

    private IconLoader() {}

    /** name = dosya adı (uzantısız), size = kenar (px). Bulunamazsa null döner. */
    public static ImageIcon get(String name, int size) {
        String key = name + "@" + size;
        ImageIcon cached = CACHE.get(key);
        if (cached != null) return cached;
        URL url = IconLoader.class.getResource("/icons/" + name + ".png");
        if (url == null) return null;
        Image scaled = new ImageIcon(url).getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
        ImageIcon icon = new ImageIcon(scaled);
        CACHE.put(key, icon);
        return icon;
    }

    /** Metindeki emoji/simge karakterlerini (Swing'de □ çıkanları) temizler. */
    public static String stripEmoji(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{FE0F}]", "").trim();
    }

    /** Menü ekran anahtarı -> ikon adı eşlemesi. */
    public static String forKey(String key) {
        if (key == null) return "home";
        switch (key) {
            case "home":       return "home";
            case "CUSTOMER":   return "customers";
            case "ACCOUNTS":   return "accounts";
            case "MY_ACCOUNTS":return "accounts";
            case "TRANSFER":   return "transfer";
            case "DEPOSIT":    return "deposit";
            case "CASHFLOW":   return "cashflow";
            case "LENDING":    return "lending";
            case "BORROWING":  return "borrowing";
            case "FX_TRADE":   return "fx";
            case "SPOT":       return "fx";
            case "FX_WATCH":   return "fxwatch";
            case "REPORTS":    return "reports";
            case "USER_MGMT":  return "users";
            case "ROLE_PERM":  return "roleperm";
            case "ERROR_LOG":  return "errorlog";
            case "BANK":       return "bank";
            case "MY_INFO":    return "settings";
            default:           return "home";
        }
    }
}
