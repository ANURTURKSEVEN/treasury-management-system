package com.gtech.treasury.batch;

import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.dao.RateDAO;
import com.gtech.treasury.util.TcmbRateService;

import java.util.Arrays;
import java.util.Map;

/**
 * KUR GÜNCELLEME BATCH'İ.
 * TCMB'den güncel döviz + efektif kurlarını çeker ve currency_rate tablosunu
 * günceller (değişiklik varsa yeni blok yazar, eski blok arşivlenir).
 *
 * Kur Gözlem ekranı bu tablodan okur; ekranda "Yenile" yoktur — güncelleme
 * bu batch ile olur. Windows Görev Zamanlayıcı her iş günü 15:30'da
 * (TCMB gösterge kurlarının yayınlandığı saat) çalıştırır.
 */
public class RateUpdateJob {

    public static void main(String[] args) {
        System.out.println("[BATCH] TCMB kur güncellemesi başladı...");
        try {
            Map<String, double[]> rates =
                    TcmbRateService.fetchRates(Arrays.asList("USD", "EUR", "GBP"));

            if (rates.isEmpty()) {
                System.out.println("[BATCH] TCMB'den kur alınamadı (bugün henüz yayınlanmamış olabilir).");
                return;
            }

            RateDAO rateDAO = new RateDAO();
            int batchId = rateDAO.updateBatch(rates);

            if (batchId > 0) {
                StringBuilder d = new StringBuilder("Kaynak: TCMB | Blok #" + batchId + " | ");
                for (Map.Entry<String, double[]> e : rates.entrySet()) {
                    double[] v = e.getValue();
                    d.append(e.getKey())
                     .append(": alış=").append(v[0]).append(" satış=").append(v[1]);
                    if (v.length > 3) {
                        d.append(" ef.alış=").append(v[2]).append(" ef.satış=").append(v[3]);
                    }
                    d.append("  ");
                }
                ActivityLogDAO.log("RATE_UPDATE", 0,
                        "Kurlar TCMB'den güncellendi (Blok #" + batchId + ")", d.toString().trim());
                System.out.println("[BATCH] Kurlar güncellendi — yeni blok #" + batchId);
            } else {
                System.out.println("[BATCH] Kurlar zaten güncel (TCMB ile aynı, değişiklik yok).");
            }
        } catch (Exception e) {
            System.err.println("[BATCH] HATA: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
