package com.gtech.treasury.ui;

import com.gtech.treasury.dao.ErrorLogDAO;
import com.gtech.treasury.dao.MoneyMarketLendingDAO;
import com.gtech.treasury.model.MoneyMarketLending;
import com.gtech.treasury.model.User;
import com.gtech.treasury.util.DatePicker;
import com.gtech.treasury.util.MmPositionReport;
import com.gtech.treasury.util.Notify;

import java.io.File;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.List;

/** Para piyasası PLASMAN işlemleri — gözlem/liste ekranı (personel). */
public class MoneyMarketLendingListPanel extends JPanel {

    private final MoneyMarketLendingDAO dao = new MoneyMarketLendingDAO();
    private final MMTableModel model = new MMTableModel();
    private final JTable table = new JTable(model);
    private final JComboBox<String> filter = new JComboBox<>(
            new String[]{"Tümü", "ACTIVE", "MATURED", "CANCELLED", "ROLLED_OVER", "EARLY_CLOSED"});
    private final User user;

    public MoneyMarketLendingListPanel(User user) {
        this.user = user;
        setLayout(new BorderLayout(0, 10));
        setBorder(new EmptyBorder(14, 18, 14, 18));
        setBackground(new Color(0xF0F2F5));

        JLabel title = new JLabel("Para Piyasası İşlemleri (Plasman / Borç Verme)");
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
            Notify.info(this, n + " plasman vade sonu tahsil edildi (banka kasasına geri girdi).");
        });
        JButton excel = new JButton("Excel (Pozisyon)");
        excel.addActionListener(e -> doExcel());
        JButton amend = new JButton("Değişiklik");
        amend.addActionListener(e -> doAmend());
        JButton rollover = new JButton("Roll Over");
        rollover.addActionListener(e -> doRollover());
        JButton early = new JButton("Erken Kapama");
        early.addActionListener(e -> doEarlyClose());
        JButton cancel = new JButton("İptal Et");
        cancel.addActionListener(e -> doCancel());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        south.add(cancel); south.add(early); south.add(rollover); south.add(amend);
        south.add(excel); south.add(mature); south.add(detail); south.add(refresh);
        add(south, BorderLayout.SOUTH);

        reload();
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) { reload(); }
        });
    }

    private void reload() {
        String f = (String) filter.getSelectedItem();
        List<MoneyMarketLending> data = "Tümü".equals(f) ? dao.getAll() : dao.getByStatus(f);
        model.setData(data);
    }

    private MoneyMarketLending selected() {
        int r = table.getSelectedRow();
        if (r < 0) return null;
        return model.getAt(table.convertRowIndexToModel(r));
    }

    private void doDetail() {
        MoneyMarketLending d = selected();
        if (d == null) { Notify.warning(this, "Detayını görmek için bir işlem seçin."); return; }
        new MoneyMarketLendingDetailDialog(SwingUtilities.getWindowAncestor(this), d).setVisible(true);
    }

    private void doExcel() {
        List<MoneyMarketLending> active = dao.getByStatus("ACTIVE");
        if (active.isEmpty()) { Notify.info(this, "Açık (aktif) plasman yok."); return; }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("mm_plasman_pozisyon_" + java.time.LocalDate.now() + ".xlsx"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            MmPositionReport.writeExcelLending(fc.getSelectedFile(), active);
            Notify.info(this, "Pozisyon raporu kaydedildi:\n" + fc.getSelectedFile().getAbsolutePath());
        } catch (Exception ex) {
            ErrorLogDAO.log(ex, "MM plasman pozisyon raporu");
            Notify.error(this, "Rapor üretilemedi: " + ex.getMessage());
        }
    }

    private void doAmend() {
        MoneyMarketLending d = selected();
        if (d == null) { Notify.warning(this, "Değiştirmek için bir işlem seçin."); return; }
        if (!"ACTIVE".equals(d.getStatus())) { Notify.warning(this, "Sadece AKTİF işlem değiştirilebilir."); return; }
        openForm("Plasman Değişiklik — " + d.getReferenceNo(), form -> form.prefill(d));
    }

    private void doRollover() {
        MoneyMarketLending d = selected();
        if (d == null) { Notify.warning(this, "Rollover için bir işlem seçin."); return; }
        if (!"ACTIVE".equals(d.getStatus())) { Notify.warning(this, "Sadece AKTİF işlem rollover edilebilir."); return; }
        openForm("Plasman Roll Over — " + d.getReferenceNo(), form -> form.prefillForRollover(d));
    }

    private void openForm(String titleText, java.util.function.Consumer<MoneyMarketLendingPanel> prep) {
        MoneyMarketLendingPanel form = new MoneyMarketLendingPanel(user);
        prep.accept(form);
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), titleText, Dialog.ModalityType.APPLICATION_MODAL);
        form.setOnSaved(() -> { dlg.dispose(); reload(); });
        dlg.setContentPane(form);
        dlg.setSize(900, 660);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void doEarlyClose() {
        MoneyMarketLending d = selected();
        if (d == null) { Notify.warning(this, "Erken kapatmak için bir işlem seçin."); return; }
        if (!"ACTIVE".equals(d.getStatus())) { Notify.warning(this, "Sadece AKTİF işlem erken kapatılabilir."); return; }

        DatePicker closeDate = new DatePicker(java.time.LocalDate.now().toString());
        JTextField penalty = new JTextField("0", 10);
        JPanel p = new JPanel(new GridLayout(0, 2, 6, 6));
        p.add(new JLabel("Kapanış Tarihi:")); p.add(closeDate);
        p.add(new JLabel("Penalty (ops.):")); p.add(penalty);
        int r = JOptionPane.showConfirmDialog(this, p, "Erken Kapama — " + d.getReferenceNo(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        double pen;
        try { pen = Double.parseDouble(penalty.getText().trim().replace(',', '.')); }
        catch (Exception ex) { Notify.warning(this, "Geçerli bir penalty tutarı girin."); return; }
        String err = dao.earlyClose(d.getId(), closeDate.getText().trim(), pen);
        if (err != null) { Notify.warning(this, err); return; }
        reload();
        Notify.info(this, "Plasman erken kapatıldı; işleyen faiz + varsa penalty kasaya tahsil edildi.");
    }

    private void doCancel() {
        MoneyMarketLending d = selected();
        if (d == null) { Notify.warning(this, "İptal için bir işlem seçin."); return; }
        if (!"ACTIVE".equals(d.getStatus())) { Notify.warning(this, "Sadece AKTİF işlem iptal edilebilir."); return; }
        int ans = JOptionPane.showConfirmDialog(this,
                d.getReferenceNo() + " iptal edilsin mi?\nKarşı tarafa verilen "
                        + String.format("%,.2f %s", d.getPrincipal(), d.getCurrency()) + " kasaya geri alınacaktır.",
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
        private List<MoneyMarketLending> data = new java.util.ArrayList<>();
        void setData(List<MoneyMarketLending> d) { this.data = d; fireTableDataChanged(); }
        MoneyMarketLending getAt(int r) { return data.get(r); }
        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Object getValueAt(int row, int col) {
            MoneyMarketLending d = data.get(row);
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
                MoneyMarketLending d = model.getAt(t.convertRowIndexToModel(row));
                String st = d.getStatus();
                setForeground("ACTIVE".equals(st) ? new Color(0x1E8E3E)
                        : ("CANCELLED".equals(st) || "EARLY_CLOSED".equals(st)) ? new Color(0xC5221F)
                        : "ROLLED_OVER".equals(st) ? new Color(0xB45309) : new Color(0x374151));
            }
            return this;
        }
    }
}
