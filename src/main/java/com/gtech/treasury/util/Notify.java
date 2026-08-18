package com.gtech.treasury.util;

import com.gtech.treasury.dao.ErrorLogDAO;

import javax.swing.*;
import java.awt.*;

/**
 * Kullanıcıya mesaj gösterirken AYNI ZAMANDA veritabanına (error_log) kaydeden
 * merkezi yardımcı sınıf. Böylece ekranda gösterilen her mesaj log'a düşer.
 *
 * Kullanım: Notify.warning(this, "TC 11 haneli olmalı");
 */
public final class Notify {

    private Notify() {
    }

    public static void info(Component parent, String message) {
        show(parent, message, "Bilgi", JOptionPane.INFORMATION_MESSAGE, "BILGI");
    }

    public static void warning(Component parent, String message) {
        show(parent, message, "Uyarı", JOptionPane.WARNING_MESSAGE, "UYARI");
    }

    public static void error(Component parent, String message) {
        show(parent, message, "Hata", JOptionPane.ERROR_MESSAGE, "HATA");
    }

    /**
     * Hata mesajını gösterir ve log'a ÖZEL bir kategori ile yazar.
     * Örn: category = "GIRIS HATASI | MUSTERI YOK" -> hata tablosunda hızlı teşhis.
     */
    public static void error(Component parent, String message, String category) {
        String[] loc = ErrorLogDAO.locations(new Throwable().getStackTrace(), Notify.class.getName());
        ErrorLogDAO.save(category, loc[0], loc[1], message);
        JOptionPane.showMessageDialog(parent, message, "Hata", JOptionPane.ERROR_MESSAGE);
    }

    private static void show(Component parent, String message, String title,
                             int messageType, String level) {
        // Önce logla (mesajı gösteren yer + onu çağıran yer), sonra göster
        String[] loc = ErrorLogDAO.locations(new Throwable().getStackTrace(), Notify.class.getName());
        ErrorLogDAO.save("EKRAN MESAJI | " + level, loc[0], loc[1], message);
        JOptionPane.showMessageDialog(parent, message, title, messageType);
    }
}
