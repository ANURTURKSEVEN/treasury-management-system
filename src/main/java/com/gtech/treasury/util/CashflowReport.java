package com.gtech.treasury.util;

import com.gtech.treasury.model.ActivityLog;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class CashflowReport {

    private static final String[] HEADERS =
            {"Tarih", "Saat", "İşlem Türü", "Müşteri No", "Tutar", "Döviz", "Açıklama"};

    /** Bir günün banka nakit hareketlerini Excel (.xlsx) olarak yazar. */
    public static void writeExcel(File out, List<ActivityLog> rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Nakit Akisi");

            // Başlık stili (kalın)
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
            for (ActivityLog a : rows) {
                if (a.getAmount() <= 0) continue;   // parasal olmayan kayıtları atla (ör. giriş/çıkış logu)

                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(a.getDatePart());
                row.createCell(1).setCellValue(a.getTimePart());
                row.createCell(2).setCellValue(a.getActionType());
                row.createCell(3).setCellValue(a.getCustomerNo());
                row.createCell(4).setCellValue(a.getAmount());
                row.createCell(5).setCellValue(a.getCurrency() == null ? "" : a.getCurrency());
                row.createCell(6).setCellValue(a.getDescription() == null ? "" : a.getDescription());
            }

            for (int c = 0; c < HEADERS.length; c++) sheet.autoSizeColumn(c);

            try (FileOutputStream fos = new FileOutputStream(out)) {
                wb.write(fos);
            }
        }
    }
}