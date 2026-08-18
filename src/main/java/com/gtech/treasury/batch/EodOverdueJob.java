package com.gtech.treasury.batch;

import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.dao.LendingDAO;
import com.gtech.treasury.dao.NotificationDAO;
import com.gtech.treasury.model.OverdueInstallment;
import com.gtech.treasury.model.ActivityLog;
import com.gtech.treasury.util.OverdueReport;
import com.gtech.treasury.util.CashflowReport;
import java.io.File;
import java.time.LocalDate;
import java.util.List;



/**
 * GÜN SONU (EOD) GECİKME RAPORU BATCH'İ.
 * Geciken kredileri çeker, reports klasörüne Excel yazar,
 * personele bildirim düşer ve activity_log'a iz bırakır.
 * Windows Görev Zamanlayıcı her akşam çalıştırır (arayüz yoktur).
 */
public class EodOverdueJob {

    public static void main(String[] args) {
        System.out.println("[BATCH] Gün sonu gecikme raporu başladı...");
        try {
            // 1) Geciken taksitleri çek (daha önce yazdığımız DAO metodu)
            List<OverdueInstallment> rows = new LendingDAO().getOverdue();

            // 2) Masaüstünde iki ayrı klasör hazırla (yoksa oluşturulur)
            File gecikenlerDir = new File("C:/Users/gtstaj0079/Desktop/TreasuryRaporlar/gecikenler");
            File nakitDir      = new File("C:/Users/gtstaj0079/Desktop/TreasuryRaporlar/nakit_akis");
            gecikenlerDir.mkdirs();
            nakitDir.mkdirs();

            // 3) Tarihli dosya adı: .../gecikenler/gecikme_2026-08-18.xlsx
            File out = new File(gecikenlerDir, "gecikme_" + LocalDate.now() + ".xlsx");
            OverdueReport.writeExcel(out, rows);

            // 4) Personele bildirim (çana düşer)
            new NotificationDAO().addForStaff(
                    "Gün sonu: geciken krediler",
                    rows.size() + " adet geciken taksit var. Rapor: " + out.getName(),
                    "EOD_OVERDUE", null);

            // 5) Denetim izi
            ActivityLogDAO.log("EOD_OVERDUE",
                    "Gün sonu gecikme raporu üretildi: " + rows.size() + " satır -> " + out.getPath());
            
            // 6) Bugünün banka nakit akışı raporu
            String bugun = LocalDate.now().toString();                    // "2026-08-18"
            List<ActivityLog> hareketler =
                    new ActivityLogDAO().search("", "", "", "", "", bugun, bugun);   // bugünün hareketleri
            File nakitOut = new File(nakitDir, "nakit_akis_" + bugun + ".xlsx");
            CashflowReport.writeExcel(nakitOut, hareketler);

            // Denetim izi (ikinci rapor için)
            ActivityLogDAO.log("EOD_CASHFLOW",
                    "Gün sonu nakit akış raporu üretildi: " + hareketler.size() + " hareket -> " + nakitOut.getPath());

            System.out.println("[BATCH] Tamam. Geciken: " + rows.size()
            + " satir, Nakit akis: " + hareketler.size() + " hareket.");
        } catch (Exception e) {
            System.err.println("[BATCH] HATA: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}