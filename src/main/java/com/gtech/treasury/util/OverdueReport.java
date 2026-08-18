package com.gtech.treasury.util;

import com.gtech.treasury.model.OverdueInstallment;

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
import java.util.List;

public class OverdueReport {

    private static final String[] HEADERS =
            {"Müşteri No", "Müşteri", "Kredi No", "Taksit", "Vade", "Gecikilen Gün", "Gecikme Faizi", "Tutar", "Döviz"};

    /** Geciken taksit listesini gerçek Excel (.xlsx) dosyasına yazar. */
    public static void writeExcel(File out, List<OverdueInstallment> rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Geciken Krediler");

            // Başlık satırı için kalın yazı stili
            CellStyle headStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headStyle.setFont(bold);

            // 0. satır = başlıklar
            Row head = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = head.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(headStyle);
            }

            // 1. satırdan itibaren veriler
            int r = 1;
            for (OverdueInstallment o : rows) {
                long gun = 0;
                try { gun = ChronoUnit.DAYS.between(LocalDate.parse(o.getDueDate()), LocalDate.now()); }
                catch (Exception ignored) { }
                double faiz = o.getAmount() * (o.getInterestRate() / 100.0 / 365.0) * 1.3 * gun;

                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(o.getCustomerNo());
                row.createCell(1).setCellValue(o.getCustomerName());
                row.createCell(2).setCellValue(o.getLendingId());
                row.createCell(3).setCellValue(o.getSeqNo());
                row.createCell(4).setCellValue(o.getDueDate());
                row.createCell(5).setCellValue(gun);
                row.createCell(6).setCellValue(faiz);
                row.createCell(7).setCellValue(o.getAmount());
                row.createCell(8).setCellValue(o.getCurrency());
            }

            // Sütun genişliklerini içeriğe göre ayarla
            for (int c = 0; c < HEADERS.length; c++) sheet.autoSizeColumn(c);

            // Dosyaya kaydet
            try (FileOutputStream fos = new FileOutputStream(out)) {
                wb.write(fos);
            }
        }
    }
}