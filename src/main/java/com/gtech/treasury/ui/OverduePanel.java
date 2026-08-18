package com.gtech.treasury.ui;

import com.gtech.treasury.dao.LendingDAO;
import com.gtech.treasury.model.OverdueInstallment;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.gtech.treasury.util.OverdueReport;
import java.io.File;

/**
 * "Geciken Krediler" ekranı: vadesi geçmiş, ödenmemiş kredi taksitlerini
 * bir tabloda listeler (gecikilen gün + gecikme faizi hesaplanmış) ve
 * bu listeyi Excel (.xlsx) olarak indirmeyi sağlar.
 */
public class OverduePanel extends JPanel {

    private final LendingDAO dao = new LendingDAO();          // veriyi çeken DAO
    private final OverdueModel model = new OverdueModel();    // tablonun veri modeli (beyni)
    private final JTable table = new JTable(model);           // ekrandaki tablo (ızgara)

    /** Ekranı kurar: başlık + tablo + (Excel İndir / Yenile) butonları; açılışta veriyi yükler. */
    public OverduePanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel title = new JLabel("Geciken Krediler");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        add(title, BorderLayout.NORTH);

        table.setRowHeight(26);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JButton refresh = new JButton("Yenile");
        refresh.addActionListener(e -> reload());

        JButton excel = new JButton("Excel İndir");
        excel.addActionListener(e -> exportExcel());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(excel);
        south.add(refresh);
        add(south, BorderLayout.SOUTH);

        reload();
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) { reload(); }
        });
    }

    /** DAO'dan güncel geciken taksit listesini çeker ve tabloyu tazeler. */
    private void reload() {
        model.setData(dao.getOverdue());
    }

    /** "Farklı Kaydet" penceresi açar; seçilen yere geciken kredileri .xlsx olarak yazar. */
    private void exportExcel() {
        JFileChooser fc = new JFileChooser();                 // kaydetme penceresi
        fc.setSelectedFile(new File("geciken_krediler.xlsx")); // varsayılan dosya adı
        // Kullanıcı "Kaydet" demezse (iptal) hiçbir şey yapma
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File out = fc.getSelectedFile();                       // kullanıcının seçtiği dosya
        try {
            // Güncel listeyi çekip seçilen dosyaya Excel olarak yaz
            OverdueReport.writeExcel(out, dao.getOverdue());
            JOptionPane.showMessageDialog(this,
                    "Rapor kaydedildi:\n" + out.getAbsolutePath(),
                    "Başarılı", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            // writeExcel hata fırlatırsa burada yakalanır; kullanıcıya gösterilir
            JOptionPane.showMessageDialog(this,
                    "Excel üretilemedi: " + ex.getMessage(),
                    "Hata", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- Tablonun beyni: veri modeli ----
    private static class OverdueModel extends AbstractTableModel {
        private final String[] cols =
                {"Müşteri No", "Müşteri", "Kredi #", "Taksit", "Vade", "Gecikilen Gün", "Gecikme Faizi", "Tutar"};
        private List<OverdueInstallment> data = new java.util.ArrayList<>();

        /** Listeyi değiştirir ve tabloya "veri yenilendi, kendini çiz" sinyali verir. */
        void setData(List<OverdueInstallment> list) { this.data = list; fireTableDataChanged(); }

        @Override public int getRowCount() { return data.size(); }      // kaç satır (liste boyutu)
        @Override public int getColumnCount() { return cols.length; }   // kaç sütun
        @Override public String getColumnName(int c) { return cols[c]; } // c. sütunun başlığı

        /** Tablo her hücre için bunu sorar: (satır, sütun) -> o hücrede ne yazacak? */
        @Override public Object getValueAt(int row, int col) {
            OverdueInstallment o = data.get(row);   // o satırın verisi
            long gun = 0;
            // Gecikilen gün = vade tarihi ile bugün arasındaki gün farkı
            try { gun = ChronoUnit.DAYS.between(LocalDate.parse(o.getDueDate()), LocalDate.now()); }
            catch (Exception ignored) { }
            // Gecikme faizi = tutar × (yıllık faiz / 365) × 1,3 × gecikilen gün
            double faiz = o.getAmount() * (o.getInterestRate() / 100.0 / 365.0) * 1.3 * gun;
            switch (col) {
                case 0: return o.getCustomerNo();
                case 1: return o.getCustomerName();
                case 2: return o.getLendingId();
                case 3: return o.getSeqNo();
                case 4: return o.getDueDate();
                case 5: return gun;
                case 6: return String.format("%,.2f %s", faiz, o.getCurrency());
                case 7: return String.format("%,.2f %s", o.getAmount(), o.getCurrency());
                default: return "";
            }
        }
    }
}