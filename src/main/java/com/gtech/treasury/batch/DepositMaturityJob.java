package com.gtech.treasury.batch;

import com.gtech.treasury.dao.BorrowingDAO;
import com.gtech.treasury.dao.MoneyMarketBorrowingDAO;

/**
 * VADE SONU (EOD) BATCH'İ.
 * Vadesi dolan (1) vadeli mevduatları ve (2) para piyasası borçlanmalarını otomatik kapatır.
 * Windows Görev Zamanlayıcı her gün çalıştırır.
 */
public class DepositMaturityJob {
    public static void main(String[] args) {
        System.out.println("[BATCH] Vade sonu işlemleri başladı...");
        try {
            int n = new BorrowingDAO().matureDue();
            System.out.println("[BATCH] " + n + " vadeli mevduat vade sonu kapatıldı.");
            int m = new MoneyMarketBorrowingDAO().matureDue();
            System.out.println("[BATCH] " + m + " para piyasası borçlanma vade sonu kapatıldı.");
            int l = new com.gtech.treasury.dao.MoneyMarketLendingDAO().matureDue();
            System.out.println("[BATCH] " + l + " para piyasası plasman vade sonu tahsil edildi.");
        } catch (Exception e) {
            System.err.println("[BATCH] HATA: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
