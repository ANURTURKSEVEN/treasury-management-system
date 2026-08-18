package com.gtech.treasury.util;

// --- OpenPDF (com.lowagie.text) sınıfları ---
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

// --- Java standart ---
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Locale;

// --- Proje modelleri ---
import com.gtech.treasury.model.ActivityLog;
import com.gtech.treasury.model.Customer;

/**
 * PDF üretimi: hesap ekstresi ve işlem dekontu.
 * OpenPDF kütüphanesi kullanır (pom.xml'e eklenmeli).
 */
public class PdfService {

    // Türkçe sayı biçimi için yerel ayar (1.234,56 gibi)
    private static final Locale TR = Locale.forLanguageTag("tr");

    /**
     * İstenen boyut/stilde bir Font döndürür.
     * Arial'ı IDENTITY_H + gömülü (EMBEDDED) yükler ki ş/ğ/İ/ı doğru bassın.
     */
    private static Font font(int size, int style) {
        try {
            BaseFont bf = BaseFont.createFont(
                    "C:\\Windows\\Fonts\\arial.ttf",
                    BaseFont.IDENTITY_H,   // Unicode kodlaması → Türkçe karakter desteği
                    BaseFont.EMBEDDED);    // fontu PDF'in içine göm
            return new Font(bf, size, style);
        } catch (Exception e) {
            // Font bulunamazsa çökme; Helvetica'ya düş (Türkçe karakter bozulabilir)
            return new Font(Font.HELVETICA, size, style);
        }
    }

    /** Tutarı Türkçe biçimde döndürür: 1.234,56 */
    private static String para(double tutar) {
        return String.format(TR, "%,.2f", tutar);
    }

    /** Tablo başlığı hücresi (gri zeminli, kalın). */
    private static PdfPCell baslikHucre(String metin) {
        PdfPCell c = new PdfPCell(new Phrase(metin, font(10, Font.BOLD)));
        c.setBackgroundColor(new java.awt.Color(230, 230, 230));
        c.setPadding(5);
        return c;
    }

    /** Normal veri hücresi. */
    private static PdfPCell hucre(String metin, int hizala) {
        PdfPCell c = new PdfPCell(new Phrase(metin == null ? "" : metin, font(9, Font.NORMAL)));
        c.setPadding(4);
        c.setHorizontalAlignment(hizala);
        return c;
    }

    // =====================================================================
    // 1) HESAP EKSTRESİ
    // =====================================================================
    public static void ekstreUret(File out, Customer musteri,
                                  String baslangic, String bitis,
                                  List<ActivityLog> hareketler) throws Exception {
        // Hesap no'suz çağrı → 0 geçilir (hesap satırı yazılmaz)
        ekstreUret(out, musteri, 0L, baslangic, bitis, hareketler);
    }

    /** Hesap no'yu da başlığa yazan sürüm (tek bir hesabın ekstresi için). */
    public static void ekstreUret(File out, Customer musteri, long hesapNo,
                                  String baslangic, String bitis,
                                  List<ActivityLog> hareketler) throws Exception {

        Document doc = new Document(PageSize.A4, 40, 40, 50, 50); // kenar boşlukları
        PdfWriter.getInstance(doc, new FileOutputStream(out));
        doc.open();

        // --- Başlık (ortalı, kalın) ---
        Paragraph baslik = new Paragraph("HESAP EKSTRESİ", font(16, Font.BOLD));
        baslik.setAlignment(Element.ALIGN_CENTER);
        baslik.setSpacingAfter(15);
        doc.add(baslik);

        // --- Müşteri bilgileri ---
        String adSoyad = musteri.getCustomerName() + " " + musteri.getSurname();
        doc.add(new Paragraph("Müşteri No: " + musteri.getCustomerNo(), font(11, Font.NORMAL)));
        doc.add(new Paragraph("Ad Soyad: " + adSoyad, font(11, Font.NORMAL)));
        if (hesapNo > 0) {
            doc.add(new Paragraph("Hesap No: " + hesapNo, font(11, Font.NORMAL)));
        }
        doc.add(new Paragraph("Tarih Aralığı: " + baslangic + "  -  " + bitis, font(11, Font.NORMAL)));

        Paragraph bosluk = new Paragraph(" ");
        bosluk.setSpacingAfter(10);
        doc.add(bosluk);

        // --- Hareket tablosu ---
        // Kolon oranları: Tarih | Saat | İşlem | Açıklama | Tutar | Döviz
        PdfPTable table = new PdfPTable(new float[]{2f, 1.5f, 2f, 3.5f, 2f, 1.2f});
        table.setWidthPercentage(100);

        table.addCell(baslikHucre("Tarih"));
        table.addCell(baslikHucre("Saat"));
        table.addCell(baslikHucre("İşlem"));
        table.addCell(baslikHucre("Açıklama"));
        table.addCell(baslikHucre("Tutar"));
        table.addCell(baslikHucre("Döviz"));

        double toplamTutar = 0;
        for (ActivityLog a : hareketler) {
            table.addCell(hucre(a.getDatePart(), Element.ALIGN_LEFT));
            table.addCell(hucre(a.getTimePart(), Element.ALIGN_LEFT));
            table.addCell(hucre(a.getActionType(), Element.ALIGN_LEFT));
            table.addCell(hucre(a.getDescription(), Element.ALIGN_LEFT));
            table.addCell(hucre(a.getAmount() == 0 ? "-" : para(a.getAmount()), Element.ALIGN_RIGHT));
            table.addCell(hucre(a.getCurrency(), Element.ALIGN_CENTER));
            toplamTutar += a.getAmount();
        }
        doc.add(table);

        // --- Özet ---
        Paragraph ozet = new Paragraph(
                "\nHareket sayısı: " + hareketler.size()
              + "     Toplam işlem tutarı: " + para(toplamTutar),
                font(11, Font.BOLD));
        ozet.setSpacingBefore(12);
        doc.add(ozet);

        doc.close();
    }

    // =====================================================================
    // 2) İŞLEM DEKONTU
    // =====================================================================
    public static void dekontUret(File out, String islemTuru, int musteriNo,
                                  double tutar, String doviz, String aciklama,
                                  String tarih) throws Exception {

        Document doc = new Document(PageSize.A5, 40, 40, 40, 40);
        PdfWriter.getInstance(doc, new FileOutputStream(out));
        doc.open();

        Paragraph banka = new Paragraph("GTECH TREASURY BANK", font(14, Font.BOLD));
        banka.setAlignment(Element.ALIGN_CENTER);
        doc.add(banka);

        Paragraph baslik = new Paragraph("İŞLEM DEKONTU", font(12, Font.BOLD));
        baslik.setAlignment(Element.ALIGN_CENTER);
        baslik.setSpacingAfter(15);
        doc.add(baslik);

        // Alanları 2 kolonlu tabloyla düzgün hizalayalım
        PdfPTable t = new PdfPTable(new float[]{1.2f, 2.5f});
        t.setWidthPercentage(100);
        t.addCell(hucre("Tarih:", Element.ALIGN_LEFT));       t.addCell(hucre(tarih, Element.ALIGN_LEFT));
        t.addCell(hucre("Müşteri No:", Element.ALIGN_LEFT));  t.addCell(hucre(String.valueOf(musteriNo), Element.ALIGN_LEFT));
        t.addCell(hucre("İşlem Türü:", Element.ALIGN_LEFT));  t.addCell(hucre(islemTuru, Element.ALIGN_LEFT));
        t.addCell(hucre("Tutar:", Element.ALIGN_LEFT));       t.addCell(hucre(para(tutar) + " " + doviz, Element.ALIGN_LEFT));
        t.addCell(hucre("Açıklama:", Element.ALIGN_LEFT));    t.addCell(hucre(aciklama, Element.ALIGN_LEFT));
        doc.add(t);

        Paragraph alt = new Paragraph("\nBu belge sistem tarafından otomatik üretilmiştir.",
                font(8, Font.ITALIC));
        alt.setSpacingBefore(20);
        alt.setAlignment(Element.ALIGN_CENTER);
        doc.add(alt);

        doc.close();
    }
}
