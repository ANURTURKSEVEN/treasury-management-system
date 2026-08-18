package com.gtech.treasury.util;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;

/**
 * Uygulamanın görsel temasını tek yerden yönetir.
 * FlatLaf modern görünümünü uygular ve ortak stil ayarlarını yapar.
 */
public final class UITheme {

    // Marka rengi (butonlar, vurgular)
    public static final Color PRIMARY = new Color(0x2D6CDF);

    private UITheme() {
    }

    /** Uygulama başında bir kere çağrılır: modern görünümü etkinleştirir. */
    public static void apply() {
        try {
            // Modern düz görünüm
            FlatLightLaf.setup();

            // Genel ince ayarlar (yuvarlak köşeler, boşluklar, yazı tipi)
            UIManager.put("Button.arc", 14);
            UIManager.put("Component.arc", 12);
            UIManager.put("TextComponent.arc", 10);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
            UIManager.put("Table.rowHeight", 30);
            UIManager.put("Component.focusWidth", 1);

            // Marka vurgusu
            UIManager.put("Component.accentColor", PRIMARY);

            // Biraz daha büyük ve okunaklı yazı tipi
            Font base = new Font("Segoe UI", Font.PLAIN, 14);
            UIManager.put("defaultFont", base);

        } catch (Exception e) {
            System.err.println("Tema uygulanamadı, varsayılan görünüm kullanılacak: " + e.getMessage());
        }
    }

    /** Pencereyi tam ekran (maximize) açar. */
    public static void maximize(JFrame frame) {
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    /** Verilen bileşene başlık görünümlü büyük bir etiket üretir. */
    public static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 22f));
        return label;
    }

    /** Vurgulu (marka renkli) buton stili uygular. */
    public static void stylePrimary(JButton button) {
        button.setBackground(PRIMARY);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
    }
}
