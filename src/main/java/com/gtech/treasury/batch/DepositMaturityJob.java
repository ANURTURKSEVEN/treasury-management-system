package com.gtech.treasury.batch;

import com.gtech.treasury.dao.BorrowingDAO;

/**
 * VADELİ MEVDUAT VADE SONU BATCH'İ.
 * Vadesi dolan mevduatları otomatik kapatır (anapara + faiz müşteriye ödenir).
 * Windows Görev Zamanlayıcı her gün çalıştırır.
 */
public class DepositMaturityJob {
    public static void main(String[] args) {
        System.out.println("[BATCH] Vadeli mevduat vade sonu işlemi başladı...");
        try {
            int n = new BorrowingDAO().matureDue();
            System.out.println("[BATCH] " + n + " mevduat vade sonu kapatıldı.");
        } catch (Exception e) {
            System.err.println("[BATCH] HATA: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
