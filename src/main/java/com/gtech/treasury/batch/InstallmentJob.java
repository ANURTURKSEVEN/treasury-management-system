package com.gtech.treasury.batch;

import com.gtech.treasury.dao.LendingDAO;

/**
 * KREDİ TAKSİT TAHSİLAT BATCH'İ.
 * Vadesi bugün veya geçmiş, ödenmemiş taksitleri müşteri hesabından otomatik tahsil eder.
 * Windows Görev Zamanlayıcı her gün çalıştırır.
 */
public class InstallmentJob {
    public static void main(String[] args) {
        System.out.println("[BATCH] Kredi taksit tahsilatı başladı...");
        try {
            int n = new LendingDAO().collectDue();
            System.out.println("[BATCH] " + n + " taksit tahsil edildi (vadesi gelenler).");
        } catch (Exception e) {
            System.err.println("[BATCH] HATA: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
