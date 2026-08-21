package com.gtech.treasury.util;

import com.gtech.treasury.model.MoneyMarketBorrowing;
import com.gtech.treasury.model.MoneyMarketLending;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Para piyasası pozisyon / fonlama raporu (.xlsx).
 *   Sayfa 1 "Açık Borçlanmalar": aktif deal'ler, vadeye göre sıralı.
 *   Sayfa 2 "Likidite": döviz bazında açık anapara + vade kovalarına göre dağılım.
 */
public class MmPositionReport {

    private static final String[] HEADERS =
            {"Referans", "Karşı Kurum", "Döviz", "Anapara", "Faiz%", "Valör", "Vade", "Kalan Gün", "Geri Ödeme"};

    private static final String[] BUCKETS = {"Vadesi Geçmiş", "0-7 gün", "8-30 gün", "31-90 gün", "90+ gün"};

    public static void writeExcel(File out, List<MoneyMarketBorrowing> active) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle head = wb.createCellStyle();
            Font bold = wb.createFont(); bold.setBold(true); head.setFont(bold);

            // ---- Sayfa 1: Açık Borçlanmalar ----
            Sheet s1 = wb.createSheet("Açık Borçlanmalar");
            Row h = s1.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) { Cell cell = h.createCell(c); cell.setCellValue(HEADERS[c]); cell.setCellStyle(head); }

            int r = 1;
            for (MoneyMarketBorrowing d : active) {
                long kalan = daysToMaturity(d);
                Row row = s1.createRow(r++);
                row.createCell(0).setCellValue(d.getReferenceNo());
                row.createCell(1).setCellValue(d.getCounterpartyNo() > 0
                        ? d.getCounterpartyNo() + " - " + d.getCounterpartyName() : "-");
                row.createCell(2).setCellValue(d.getCurrency());
                row.createCell(3).setCellValue(d.getPrincipal());
                row.createCell(4).setCellValue(d.getInterestRate());
                row.createCell(5).setCellValue(d.getValueDate());
                row.createCell(6).setCellValue(d.getMaturityDate());
                row.createCell(7).setCellValue(kalan);
                row.createCell(8).setCellValue(d.getRepaymentAmount());
            }
            for (int c = 0; c < HEADERS.length; c++) s1.autoSizeColumn(c);

            // ---- Sayfa 2: Likidite ----
            Sheet s2 = wb.createSheet("Likidite");
            // Döviz bazında açık anapara + geri ödeme
            Map<String, double[]> byCur = new LinkedHashMap<>();   // döviz -> {anapara, geriÖdeme, adet}
            Map<String, double[]> byBucket = new LinkedHashMap<>();// kova -> {anapara, adet}
            for (String b : BUCKETS) byBucket.put(b, new double[2]);
            for (MoneyMarketBorrowing d : active) {
                byCur.computeIfAbsent(d.getCurrency(), k -> new double[3]);
                double[] cv = byCur.get(d.getCurrency());
                cv[0] += d.getPrincipal(); cv[1] += d.getRepaymentAmount(); cv[2] += 1;
                double[] bv = byBucket.get(bucketOf(daysToMaturity(d)));
                bv[0] += d.getPrincipal(); bv[1] += 1;
            }

            int rr = 0;
            Row t1 = s2.createRow(rr++); Cell tc1 = t1.createCell(0); tc1.setCellValue("DÖVİZ BAZINDA AÇIK POZİSYON"); tc1.setCellStyle(head);
            Row ch = s2.createRow(rr++);
            String[] curCols = {"Döviz", "İşlem Adedi", "Açık Anapara", "Vade Sonu Geri Ödeme"};
            for (int c = 0; c < curCols.length; c++) { Cell cell = ch.createCell(c); cell.setCellValue(curCols[c]); cell.setCellStyle(head); }
            for (Map.Entry<String, double[]> e : byCur.entrySet()) {
                Row row = s2.createRow(rr++);
                row.createCell(0).setCellValue(e.getKey());
                row.createCell(1).setCellValue((long) e.getValue()[2]);
                row.createCell(2).setCellValue(e.getValue()[0]);
                row.createCell(3).setCellValue(e.getValue()[1]);
            }
            rr++; // boş satır
            Row t2 = s2.createRow(rr++); Cell tc2 = t2.createCell(0); tc2.setCellValue("VADE KOVALARINA GÖRE (LİKİDİTE)"); tc2.setCellStyle(head);
            Row bh = s2.createRow(rr++);
            String[] bCols = {"Vade Aralığı", "İşlem Adedi", "Açık Anapara (tüm dövizler)"};
            for (int c = 0; c < bCols.length; c++) { Cell cell = bh.createCell(c); cell.setCellValue(bCols[c]); cell.setCellStyle(head); }
            for (String b : BUCKETS) {
                double[] bv = byBucket.get(b);
                Row row = s2.createRow(rr++);
                row.createCell(0).setCellValue(b);
                row.createCell(1).setCellValue((long) bv[1]);
                row.createCell(2).setCellValue(bv[0]);
            }
            for (int c = 0; c < 4; c++) s2.autoSizeColumn(c);

            try (FileOutputStream fos = new FileOutputStream(out)) { wb.write(fos); }
        }
    }

    private static long daysToMaturity(MoneyMarketBorrowing d) {
        try { return ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(d.getMaturityDate().substring(0, 10))); }
        catch (Exception e) { return 0; }
    }

    /**
     * Para piyasası PLASMAN (borç verme) pozisyon raporu (.xlsx).
     *   Sayfa 1 "Açık Plasmanlar": aktif deal'ler.
     *   Sayfa 2 "Likidite": döviz bazında açık anapara + vade kovaları.
     */
    public static void writeExcelLending(File out, List<MoneyMarketLending> active) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle head = wb.createCellStyle();
            Font bold = wb.createFont(); bold.setBold(true); head.setFont(bold);

            Sheet s1 = wb.createSheet("Açık Plasmanlar");
            Row h = s1.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) { Cell cell = h.createCell(c); cell.setCellValue(HEADERS[c]); cell.setCellStyle(head); }

            int r = 1;
            for (MoneyMarketLending d : active) {
                long kalan = daysToMaturity(d.getMaturityDate());
                Row row = s1.createRow(r++);
                row.createCell(0).setCellValue(d.getReferenceNo());
                row.createCell(1).setCellValue(d.getCounterpartyNo() > 0
                        ? d.getCounterpartyNo() + " - " + d.getCounterpartyName() : "-");
                row.createCell(2).setCellValue(d.getCurrency());
                row.createCell(3).setCellValue(d.getPrincipal());
                row.createCell(4).setCellValue(d.getInterestRate());
                row.createCell(5).setCellValue(d.getValueDate());
                row.createCell(6).setCellValue(d.getMaturityDate());
                row.createCell(7).setCellValue(kalan);
                row.createCell(8).setCellValue(d.getRepaymentAmount());
            }
            for (int c = 0; c < HEADERS.length; c++) s1.autoSizeColumn(c);

            Sheet s2 = wb.createSheet("Likidite");
            Map<String, double[]> byCur = new LinkedHashMap<>();
            Map<String, double[]> byBucket = new LinkedHashMap<>();
            for (String b : BUCKETS) byBucket.put(b, new double[2]);
            for (MoneyMarketLending d : active) {
                byCur.computeIfAbsent(d.getCurrency(), k -> new double[3]);
                double[] cv = byCur.get(d.getCurrency());
                cv[0] += d.getPrincipal(); cv[1] += d.getRepaymentAmount(); cv[2] += 1;
                double[] bv = byBucket.get(bucketOf(daysToMaturity(d.getMaturityDate())));
                bv[0] += d.getPrincipal(); bv[1] += 1;
            }

            int rr = 0;
            Row t1 = s2.createRow(rr++); Cell tc1 = t1.createCell(0); tc1.setCellValue("DÖVİZ BAZINDA AÇIK PLASMAN"); tc1.setCellStyle(head);
            Row ch = s2.createRow(rr++);
            String[] curCols = {"Döviz", "İşlem Adedi", "Açık Anapara", "Vade Sonu Tahsil"};
            for (int c = 0; c < curCols.length; c++) { Cell cell = ch.createCell(c); cell.setCellValue(curCols[c]); cell.setCellStyle(head); }
            for (Map.Entry<String, double[]> e : byCur.entrySet()) {
                Row row = s2.createRow(rr++);
                row.createCell(0).setCellValue(e.getKey());
                row.createCell(1).setCellValue((long) e.getValue()[2]);
                row.createCell(2).setCellValue(e.getValue()[0]);
                row.createCell(3).setCellValue(e.getValue()[1]);
            }
            rr++;
            Row t2 = s2.createRow(rr++); Cell tc2 = t2.createCell(0); tc2.setCellValue("VADE KOVALARINA GÖRE (LİKİDİTE)"); tc2.setCellStyle(head);
            Row bh = s2.createRow(rr++);
            String[] bCols = {"Vade Aralığı", "İşlem Adedi", "Açık Anapara (tüm dövizler)"};
            for (int c = 0; c < bCols.length; c++) { Cell cell = bh.createCell(c); cell.setCellValue(bCols[c]); cell.setCellStyle(head); }
            for (String b : BUCKETS) {
                double[] bv = byBucket.get(b);
                Row row = s2.createRow(rr++);
                row.createCell(0).setCellValue(b);
                row.createCell(1).setCellValue((long) bv[1]);
                row.createCell(2).setCellValue(bv[0]);
            }
            for (int c = 0; c < 4; c++) s2.autoSizeColumn(c);

            try (FileOutputStream fos = new FileOutputStream(out)) { wb.write(fos); }
        }
    }

    private static long daysToMaturity(String maturity) {
        try { return ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(maturity.substring(0, 10))); }
        catch (Exception e) { return 0; }
    }

    private static String bucketOf(long days) {
        if (days < 0) return "Vadesi Geçmiş";
        if (days <= 7) return "0-7 gün";
        if (days <= 30) return "8-30 gün";
        if (days <= 90) return "31-90 gün";
        return "90+ gün";
    }
}
