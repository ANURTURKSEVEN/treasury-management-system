package com.gtech.treasury.ui;

import com.gtech.treasury.dao.ErrorLogDAO;
import com.gtech.treasury.dao.MoneyMarketBorrowingDAO;
import com.gtech.treasury.model.MoneyMarketBorrowing;
import com.gtech.treasury.model.User;
import com.gtech.treasury.util.MmPositionReport;
import com.gtech.treasury.util.Notify;

import java.io.File;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.List;

/** Para piyasası borçlanma işlemleri — gözlem/liste ekranı (personel). */
public class MoneyMarketListPanel extends JPanel {

    private final MoneyMarketBorrowingDAO dao = new MoneyMarketBorrowingDAO();
    private final MMTableModel model = new MMTableModel();
    private final JTable table = new JTable(model);
    private final JComboBox<String> filter = new JComboBox<>(new String[]{"Tümü", "ACTIVE", "MATURED", "CANCELLED"});
    private final User user;

    public MoneyMarketListPanel(User user) {
        this.user = user;
        setLayout(new BorderLayout(0, 10));
        setBorder(new EmptyBorder(14, 18, 14, 18));
        setBackground(new Color(0xF0F2F5));

        JLabel title = new JLabel("Para Piyasası İşlemleri (Borçlanma)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(title, BorderLayout.WEST);
        JPanel fp = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        fp.setOpaque(false);
        fp.add(new JLabel("Durum:"));
        filter.addActionListener(e -> reload());
        fp.add(filter);
        top.add(fp, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        table.setRowHeight(26);
        table.getColumnModel().getColumn(model.findColumn("Durum")).setCellRenderer(new StatusRenderer());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) doDetail();
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JButton detail = new JButton("Detay");
        detail.addActionListener(e -> doDetail());
        JButton refresh = new JButton("Yenile");
        refresh.addActionListener(e -> reload());
        JButton mature = new JButton("Vadesi Gelenleri Kapat (Batch)");
        mature.addActionListener(e -> {
            int n = dao.matureDue();
            reload();
            Notify.info(this, n + " deal vade sonu kapatıldı (banka kasasından geri ödendi).");
        });
        JButton excel = new JButton("Excel (Pozisyon)");
        excel.addActionListener(e -> doExcel());
        JButton amend = new JButton("Değişiklik");
        amend.addActionListener(e -> doAmend());
        JButton cancel = new JButton("İptal Et");
        cancel.addActionListener(e -> doCancel());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        south.add(cancel); south.add(amend); south.add(excel); south.add(mature); south.add(detail); south.add(refresh);
        add(south, BorderLayout.SOUTH);

        reload();
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) { reload(); }
        });
    }

    private void reload() {
        String f = (String) filter.getSelectedItem();
        List<MoneyMarketBorrowing> data = "Tümü".equals(f) ? dao.getAll() : dao.getByStatus(f);
        model.setData(data);
    }

    private void doDetail() {
        int r = table.getSelectedRow();
        if (r < 0) { Notify.warning(this, "Detayını görmek için bir işlem seçin."); return; }
        MoneyMarketBorrowing d = model.getAt(table.convertRowIndexToModel(r));
        new MoneyMarketDetailDialog(SwingUtilities.getWindowAncestor(this), d).setVisible(true);
    }

    private void doExcel() {
        List<MoneyMarketBorrowing> active = dao.getByStatus("ACTIVE");
        if (active.isEmpty()) { Notify.info(this, "Açık (aktif) para piyasası işlemi yok."); return; }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("mm_pozisyon_" + java.time.LocalDate.now() + ".xlsx"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            MmPositionReport.writeExcel(fc.getSelectedFile(), active);
            Notify.info(this, "Pozisyon raporu kaydedildi:\n" + fc.getSelectedFile().getAbsolutePath());
        } catch (Exception ex) {
            ErrorLogDAO.log(ex, "MM pozisyon raporu");
            Notify.error(this, "Rapor üretilemedi: " + ex.getMessage());
        }
    }

    private void doAmend() {
        int r = table.getSelectedRow();
        if (r < 0) { Notify.warning(this, "Değiştirmek için bir işlem seçin."); return; }
        MoneyMarketBorrowing d = model.getAt(table.convertRowIndexToModel(r));
        if (!"ACTIVE".equals(d.getStatus())) { Notify.warning(this, "Sadece AKTİF işlem değiştirilebilir."); return; }

        MoneyMarketBorrowingPanel form = new MoneyMarketBorrowingPanel(user);
        form.prefill(d);
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this),
                "Deal Değişiklik — " + d.getReferenceNo(), Dialog.ModalityType.APPLICATION_MODAL);
        form.setOnSaved(() -> { dlg.dispose(); reload(); });
        dlg.setContentPane(form);
        dlg.setSize(900, 640);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void doCancel() {
        int r = table.getSelectedRow();
        if (r < 0) { Notify.warning(this, "İptal için bir işlem seçin."); return; }
        MoneyMarketBorrowing d = model.getAt(table.convertRowIndexToModel(r));
        if (!"ACTIVE".equals(d.getStatus())) { Notify.warning(this, "Sadece AKTİF işlem iptal edilebilir."); return; }
        int ans = JOptionPane.showConfirmDialog(this,
                d.getReferenceNo() + " iptal edilsin mi?\nKasaya giren "
                        + String.format("%,.2f %s", d.getPrincipal(), d.getCurrency()) + " geri alınacaktır.",
                "İşlem İptali", JOptionPane.YES_NO_OPTION);
        if (ans != JOptionPane.YES_OPTION) return;
        String err = dao.cancel(d.getId());
        if (err != null) { Notify.warning(this, err); return; }
        reload();
        Notify.info(this, "İşlem iptal edildi.");
    }

    // ---- Tablo ----
    private static class MMTableModel extends AbstractTableModel {
        private final String[] cols = {"Ref", "Karşı Kurum", "Döviz", "Anapara", "Faiz%", "Valör", "Vade", "Geri Ödeme", "Durum"};
        private List<MoneyMarketBorrowing> data = new java.util.ArrayList<>();
        void setData(List<MoneyMarketBorrowing> d) { this.data = d; fireTableDataChanged(); }
        MoneyMarketBorrowing getAt(int r) { return data.get(r); }
        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Object getValueAt(int row, int col) {
            MoneyMarketBorrowing d = data.get(row);
            switch (cols[col]) {
                case "Ref":         return d.getReferenceNo();
                case "Karşı Kurum": return d.getCounterpartyNo() > 0
                        ? d.getCounterpartyNo() + " - " + d.getCounterpartyName() : "-";
                case "Döviz":       return d.getCurrency();
                case "Anapara":     return String.format("%,.2f", d.getPrincipal());
                case "Faiz%":       return String.format("%.4f", d.getInterestRate());
                case "Valör":       return d.getValueDate();
                case "Vade":        return d.getMaturityDate();
                case "Geri Ödeme":  return String.format("%,.2f", d.getRepaymentAmount());
                case "Durum":       return d.getStatusText();
                default:            return "";
            }
        }
    }

    private class StatusRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int row, int col) {
            super.getTableCellRendererComponent(t, v, s, f, row, col);
            setFont(getFont().deriveFont(Font.BOLD));
            if (!s) {
                MoneyMarketBorrowing d = model.getAt(t.convertRowIndexToModel(row));
                String st = d.getStatus();
                setForeground("ACTIVE".equals(st) ? new Color(0x1E8E3E)
                        : "CANCELLED".equals(st) ? new Color(0xC5221F) : new Color(0x374151));
            }
            return this;
        }
    }
}
