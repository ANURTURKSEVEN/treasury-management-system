package com.gtech.treasury;

import com.gtech.treasury.dao.ErrorLogDAO;
import com.gtech.treasury.ui.LoginFrame;
import com.gtech.treasury.util.UITheme;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import javax.swing.*;

/**
 * Uygulamanın giriş noktası.
 * Modern temayı uygular, global hata yakalayıcıları kurar, Login ekranını açar.
 */ 

public class Main {

    public static void main(String[] args) {
        UITheme.apply();
        installGlobalErrorHandlers();

        SwingUtilities.invokeLater(() -> {
            System.out.println("Treasury Management System");
            new LoginFrame().setVisible(true);
        });
    }

    /**
     * Uygulamanın HER YERİNDE yakalanmayan hataları error_log tablosuna yazar.
     *  1) Normal thread'ler için: setDefaultUncaughtExceptionHandler
     *  2) Arayüz (EDT) hataları için: dispatchEvent'i saran özel EventQueue
     */
    private static void installGlobalErrorHandlers() {
        System.out.println(">>> Global hata yakalayici KURULDU (yeni surum calisiyor) <<<");

        // 1) Arka plan thread'lerindeki yakalanmayan hatalar
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            ErrorLogDAO.log(throwable, "Yakalanmayan hata");
            throwable.printStackTrace();
        });

        // 2) Swing arayüzünde (EDT) oluşan hatalar
        Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue() {
            @Override
            protected void dispatchEvent(AWTEvent event) {
                try {
                    super.dispatchEvent(event);
                } catch (Throwable t) {
                    ErrorLogDAO.log(t, "Arayüz (UI) hatası");
                    t.printStackTrace();
                }
            }
        });
    }
}
