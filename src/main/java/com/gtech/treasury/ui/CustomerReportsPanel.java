package com.gtech.treasury.ui;

import com.gtech.treasury.dao.SpotDAO;
import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.dao.ErrorLogDAO;
import com.gtech.treasury.model.Customer;
import com.gtech.treasury.model.SpotTransaction;
import com.gtech.treasury.model.ActivityLog;
import com.gtech.treasury.util.PdfService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.LocalDate;
import java.util.List;

/**
 * Müşteri Raporları — müşterinin KENDİ tüm işlemlerini (spot_transaction) listeler.
 * Bu tablo activity_log'dan bağımsızdır; rapor özelliğinden önce yapılan
 * işlemler de burada görünür. Satıra çift tıklayınca dekont açılır.
 */
public class CustomerReportsPanel extends JPanel {

    private final SpotDAO spotDAO = new SpotDAO();
    private final Customer customer;

    private final TxTableModel tableModel = new TxTableModel();
    private final JTable table = new JTable(tableModel);

    public CustomerReportsPanel(Customer customer) {
        this.customer = customer;

        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("İşlemlerim  (satıra çift tıklayarak dekontu görün)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        add(title, BorderLayout.NORTH);

        table.setRowHeight(28);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openDetail();
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Tarih aralığı kutuları (format: yyyy-MM-dd — search() bunu bekliyor)
        com.gtech.treasury.util.DatePicker baslangicField =
                new com.gtech.treasury.util.DatePicker(LocalDate.now().withDayOfMonth(1).toString());
        com.gtech.treasury.util.DatePicker bitisField =
                new com.gtech.treasury.util.DatePicker(LocalDate.now().toString());

        JButton refresh = new JButton("Yenile");
        refresh.addActionListener(e -> loadData());

        JButton pdfBtn = new JButton("PDF Ekstre İndir");
        pdfBtn.addActionListener(e -> exportEkstrePdf(baslangicField.getText(), bitisField.getText()));

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(new JLabel("Başlangıç:"));
        south.add(baslangicField);
        south.add(new JLabel("Bitiş:"));
        south.add(bitisField);
        south.add(refresh);
        south.add(pdfBtn);
        add(south, BorderLayout.SOUTH);

        loadData();
    }

    /** Tarih aralığındaki hareketleri activity_log'dan çekip PDF ekstre üretir. */
    private void exportEkstrePdf(String baslangic, String bitis) {
        // 1) Veriyi çek — search: (customerNo, username, actionType, minAmount, currency, startDate, endDate)
        List<ActivityLog> hareketler = new ActivityLogDAO().search(
                String.valueOf(customer.getCustomerNo()),
                null, null, null, null,
                baslangic, bitis);

        if (hareketler.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Seçilen tarih aralığında hareket bulunamadı.",
                    "Bilgi", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // 2) Kaydetme yerini sor
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("ekstre_" + customer.getCustomerNo()
                + "_" + baslangic + "_" + bitis + ".pdf"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File out = fc.getSelectedFile();

        // 3) PDF'i üret
        try {
            PdfService.ekstreUret(out, customer, baslangic, bitis, hareketler);
            JOptionPane.showMessageDialog(this,
                    "Ekstre kaydedildi:\n" + out.getAbsolutePath(),
                    "Başarılı", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            ErrorLogDAO.log(ex, "Ekstre PDF");
            JOptionPane.showMessageDialog(this,
                    "PDF üretilemedi: " + ex.getMessage(),
                    "Hata", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadData() {
        tableModel.setData(spotDAO.getByCustomer(customer.getCustomerId()));
    }

    private void openDetail() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        SpotTransaction t = tableModel.getAt(row);
        boolean alis = !"TRY".equals(t.getBuyCurrency());
        String fxCur = alis ? t.getBuyCurrency() : t.getSellCurrency();
        double fxAmt = alis ? t.getBuyAmount() : t.getSellAmount();
        double tryAmt = alis ? t.getSellAmount() : t.getBuyAmount();

        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(new EmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 8, 5, 8);
        gbc.anchor = GridBagConstraints.WEST;

        int r = 0;
        JLabel header = new JLabel("İşlem Dekontu");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 18f));
        gbc.gridx = 0; gbc.gridy = r++; gbc.gridwidth = 2; card.add(header, gbc);
        gbc.gridwidth = 1;

        r = addRow(card, gbc, r, "İşlem No:", String.valueOf(t.getId()));
        r = addRow(card, gbc, r, "Tarih / Saat:", t.getTransactionDate());
        r = addRow(card, gbc, r, "Müşteri:", customer.getCustomerName() + " " + customer.getSurname()
                + " (No: " + customer.getCustomerNo() + ")");
        r = addRow(card, gbc, r, "İşlem Yönü:", alis ? "Döviz Alış" : "Döviz Satış");
        r = addRow(card, gbc, r, "Döviz:", fxCur);
        r = addRow(card, gbc, r, "Miktar:", String.format("%,.2f %s", fxAmt, fxCur));
        r = addRow(card, gbc, r, "Kur:", String.format("%,.4f", t.getRate()));
        r = addRow(card, gbc, r, "Karşılık:", String.format("%,.2f TRY", tryAmt));

        Object[] secenekler = {"PDF İndir", "Kapat"};
        int secim = JOptionPane.showOptionDialog(this, card,
                "Dekont - İşlem #" + t.getId(),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, secenekler, secenekler[1]);

        if (secim == 0) { // "PDF İndir" seçildi
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("dekont_" + t.getId() + ".pdf"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            try {
                String aciklama = (alis ? "Döviz Alış" : "Döviz Satış")
                        + " | Kur: " + String.format("%,.4f", t.getRate())
                        + " | Karşılık: " + String.format("%,.2f TRY", tryAmt);
                PdfService.dekontUret(fc.getSelectedFile(),
                        alis ? "Döviz Alış" : "Döviz Satış",
                        customer.getCustomerNo(),
                        fxAmt, fxCur, aciklama,
                        t.getTransactionDate());
                JOptionPane.showMessageDialog(this, "Dekont kaydedildi.",
                        "Başarılı", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                ErrorLogDAO.log(ex, "Dekont PDF");
                JOptionPane.showMessageDialog(this, "PDF üretilemedi: " + ex.getMessage(),
                        "Hata", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private int addRow(JPanel card, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0; gbc.gridy = row;
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        card.add(l, gbc);
        gbc.gridx = 1;
        card.add(new JLabel(value), gbc);
        return row + 1;
    }

    // ---- Tablo modeli ----
    private static class TxTableModel extends AbstractTableModel {
        private final String[] columns = {"Tarih", "Yön", "Döviz", "Miktar", "Kur", "Karşılık (TRY)"};
        private List<SpotTransaction> data = new java.util.ArrayList<>();

        void setData(List<SpotTransaction> list) { this.data = list; fireTableDataChanged(); }
        SpotTransaction getAt(int row) { return data.get(row); }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int c) { return columns[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            SpotTransaction t = data.get(row);
            boolean alis = !"TRY".equals(t.getBuyCurrency());
            String fxCur = alis ? t.getBuyCurrency() : t.getSellCurrency();
            double fxAmt = alis ? t.getBuyAmount() : t.getSellAmount();
            double tryAmt = alis ? t.getSellAmount() : t.getBuyAmount();
            switch (col) {
                case 0: return t.getTransactionDate();
                case 1: return alis ? "Alış" : "Satış";
                case 2: return fxCur;
                case 3: return String.format("%,.2f", fxAmt);
                case 4: return String.format("%,.4f", t.getRate());
                case 5: return String.format("%,.2f", tryAmt);
                default: return "";
            }
        }
    }
}
