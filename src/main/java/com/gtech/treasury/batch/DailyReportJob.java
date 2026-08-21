package com.gtech.treasury.batch;

import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.model.ActivityLog;
import com.gtech.treasury.util.MailService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GÜNLÜK RAPOR BATCH'İ.
 * O gün (veya parametreyle verilen gün) yapılan tüm işlemleri activity_log'dan
 * çekip HTML tablo halinde e-posta ile gönderir.
 *
 * Uygulamadan bağımsız kendi main()'i vardır; Windows Görev Zamanlayıcı
 * her gün 17:00'de çalıştırır.
 *
 * Kullanım:
 *   java ... com.gtech.treasury.batch.DailyReportJob            -> bugünün raporu
 *   java ... com.gtech.treasury.batch.DailyReportJob 2026-08-04 -> belirli gün (test)
 */
public class DailyReportJob {

    // İşlem kodlarının Türkçe karşılıkları (ReportsPanel ile aynı)
    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put("CUSTOMER_ADD", "Müşteri Kaydı");
        LABELS.put("CUSTOMER_UPDATE", "Müşteri Güncelleme");
        LABELS.put("CUSTOMER_DELETE", "Müşteri Silme");
        LABELS.put("ACCOUNT_OPEN", "Hesap Açma");
        LABELS.put("ACCOUNT_CLOSE", "Hesap Kapatma");
        LABELS.put("ACCOUNT_DEPOSIT", "Para Yatırma");
        LABELS.put("ACCOUNT_WITHDRAW", "Para Çekme");
        LABELS.put("TRANSFER", "Havale");
        LABELS.put("EFT", "EFT (Başka Banka)");
        LABELS.put("FAST", "FAST (Anlık)");
        LABELS.put("BANK_ADJUST", "Banka Kasası Düzenleme");
        LABELS.put("LOAN_APPLIED", "Kredi Başvurusu");
        LABELS.put("LOAN_GIVEN", "Kredi Onay/Kullanım");
        LABELS.put("LOAN_APPROVED", "Kredi Onayı");
        LABELS.put("LOAN_DISBURSED", "Kredi Kullandırım");
        LABELS.put("MM_BORROW_CREATE", "PP Borçlanma");
        LABELS.put("MM_BORROW_MATURE", "PP Borçlanma Vade");
        LABELS.put("MM_BORROW_CANCEL", "PP Borçlanma İptal");
        LABELS.put("LOAN_REJECTED", "Kredi Reddi");
        LABELS.put("LOAN_INSTALLMENT", "Kredi Taksit Ödemesi");
        LABELS.put("LOAN_REPAID", "Kredi Geri Ödeme");
        LABELS.put("DEPOSIT_OPEN", "Mevduat Açılışı");
        LABELS.put("DEPOSIT_CLOSE", "Mevduat Vade Sonu");
        LABELS.put("DEPOSIT_BREAK", "Mevduat Erken Bozma");
        LABELS.put("SPOT_BUY", "Döviz Alış");
        LABELS.put("SPOT_SELL", "Döviz Satış");
        LABELS.put("RATE_UPDATE", "Kur Güncelleme");
        LABELS.put("USER_ADD", "Kullanıcı Ekleme");
        LABELS.put("USER_DELETE", "Kullanıcı Silme");
        LABELS.put("ROLE_CHANGE", "Rol Değiştirme");
        LABELS.put("PERMISSION_UPDATE", "Yetki Güncelleme");
    }

    public static void main(String[] args) {
        String day = (args.length > 0 && !args[0].isBlank())
                ? args[0].trim()
                : LocalDate.now().format(DateTimeFormatter.ISO_DATE);   // yyyy-MM-dd

        System.out.println("[BATCH] Günlük rapor hazırlanıyor: " + day);
        try {
            ActivityLogDAO dao = new ActivityLogDAO();
            List<ActivityLog> list = dao.search("", "", "", "", "", day, day);
            list.removeIf(a -> "LOGIN".equals(a.getActionType()));

            String html = buildHtml(day, list);
            MailService mail = new MailService();
            mail.sendHtml("Treasury Günlük İşlem Raporu - " + day, html);

            System.out.println("[BATCH] " + list.size() + " işlem raporlandı, e-posta gönderildi -> "
                    + mail.recipient());
        } catch (Exception e) {
            System.err.println("[BATCH] HATA: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);   // Görev Zamanlayıcı'da başarısız görünsün
        }
    }

    private static String buildHtml(String day, List<ActivityLog> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:Arial,Helvetica,sans-serif;color:#111;'>");
        sb.append("<h2 style='margin-bottom:4px;'>Treasury Günlük İşlem Raporu</h2>");
        sb.append("<p style='margin-top:0;color:#555;'><b>Tarih:</b> ").append(day)
          .append(" &nbsp;|&nbsp; <b>Toplam işlem:</b> ").append(list.size()).append("</p>");

        if (list.isEmpty()) {
            sb.append("<p>Bu tarihte kayıtlı işlem bulunmuyor.</p>");
        } else {
            sb.append("<table cellspacing='0' cellpadding='7' "
                    + "style='border-collapse:collapse;font-size:13px;'>");
            sb.append("<tr style='background:#1F2A44;color:#fff;text-align:left;'>")
              .append(th("Saat")).append(th("İşlem")).append(th("Kullanıcı"))
              .append(th("Müşteri No")).append(th("Tutar")).append(th("Döviz")).append(th("Açıklama"))
              .append("</tr>");
            boolean alt = false;
            for (ActivityLog a : list) {
                String bg = (alt = !alt) ? "#F3F4F6" : "#FFFFFF";
                sb.append("<tr style='background:").append(bg).append(";'>")
                  .append(td(a.getTimePart()))
                  .append(td(label(a.getActionType())))
                  .append(td(a.getUsername()))
                  .append(td(a.getCustomerNo() > 0 ? String.valueOf(a.getCustomerNo()) : "-"))
                  .append(td(a.getAmount() > 0 ? String.format("%,.2f", a.getAmount()) : "-"))
                  .append(td(a.getCurrency() == null ? "-" : a.getCurrency()))
                  .append(td(a.getDescription()))
                  .append("</tr>");
            }
            sb.append("</table>");
        }
        sb.append("<p style='color:#999;font-size:11px;margin-top:16px;'>"
                + "Bu e-posta Treasury Management System tarafından otomatik oluşturulmuştur.</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String th(String v) {
        return "<th style='border:1px solid #ccc;'>" + esc(v) + "</th>";
    }

    private static String td(String v) {
        return "<td style='border:1px solid #ccc;'>" + esc(v) + "</td>";
    }

    private static String esc(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String label(String code) {
        return LABELS.getOrDefault(code, code);
    }
}
