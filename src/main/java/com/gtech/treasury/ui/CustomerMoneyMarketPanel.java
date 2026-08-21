package com.gtech.treasury.ui;

import com.gtech.treasury.dao.MoneyMarketBorrowingDAO;
import com.gtech.treasury.dao.MoneyMarketLendingDAO;
import com.gtech.treasury.model.Customer;
import com.gtech.treasury.model.MoneyMarketBorrowing;
import com.gtech.treasury.model.MoneyMarketLending;
import com.gtech.treasury.util.Notify;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Müşteri "Para Piyasası İşlemlerim" — SALT OKUNUR gözlem + borçlu olunan plasmanlar için ÖDEME.
 * Deal'i banka açar; müşteri burada taraf olduğu işlemleri izler ve borçlu olduğu (banka plasmanı)
 * işlemi kendi hesabından "Öde" ile kapatabilir.
 */
public class CustomerMoneyMarketPanel extends JPanel {

    private final MoneyMarketLendingDAO lendDAO = new MoneyMarketLendingDAO();
    private final MoneyMarketBorrowingDAO borrowDAO = new MoneyMarketBorrowingDAO();
    private final Customer customer;
    private final Runnable onChange;

    private final RowModel model = new RowModel();
    private final JTable table = new JTable(model);

    public CustomerMoneyMarketPanel(Customer customer, Runnable onChange) {
        this.customer = customer;
        this.onChange = onChange;
        setLayout(new BorderLayout(0, 10));
        setBorder(new EmptyBorder(14, 18, 14, 18));
        setBackground(new Color(0xF0F2F5));

        JLabel title = new JLabel("Para Piyasası İşlemlerim");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        JLabel sub = new JLabel("Bankayla yaptığınız para piyasası işlemleri (gözlem). Borçlu olduğunuz işlemi 'Öde' ile kapatabilirsiniz.");
        sub.setForeground(new Color(0x6B7280));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(title, BorderLayout.NORTH);
        head.add(sub, BorderLayout.SOUTH);
        add(head, BorderLayout.NORTH);

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
        JButton pay = new JButton("Öde (borçlu olduğum işlem)");
        pay.addActionListener(e -> doPay());
        JButton refresh = new JButton("Yenile");
        refresh.addActionListener(e -> reload());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        south.add(pay); south.add(detail); south.add(refresh);
        add(south, BorderLayout.SOUTH);

        reload();
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) { reload(); }
        });
    }

    private void reload() {
        List<Row> rows = new ArrayList<>();
        for (MoneyMarketLending d : lendDAO.getByCounterpartyNo(customer.getCustomerNo())) rows.add(new Row(d));
        for (MoneyMarketBorrowing d : borrowDAO.getByCounterpartyNo(customer.getCustomerNo())) rows.add(new Row(d));
        model.setData(rows);
        if (onChange != null) onChange.run();
    }

    private Row selected() {
        int r = table.getSelectedRow();
        return r < 0 ? null : model.getAt(table.convertRowIndexToModel(r));
    }

    private void doDetail() {
        Row row = selected();
        if (row == null) { Notify.warning(this, "Detay için bir işlem seçin."); return; }
        Window w = SwingUtilities.getWindowAncestor(this);
        if (row.lending != null) new MoneyMarketLendingDetailDialog(w, row.lending).setVisible(true);
        else new MoneyMarketDetailDialog(w, row.borrowing).setVisible(true);
    }

    private void doPay() {
        Row row = selected();
        if (row == null) { Notify.warning(this, "Ödemek için bir işlem seçin."); return; }
        if (row.lending == null) { Notify.warning(this, "Bu işlemde borçlu değilsiniz (alacaklısınız); ödeme yapılmaz."); return; }
        if (!"ACTIVE".equals(row.lending.getStatus())) { Notify.warning(this, "Sadece aktif borcunuzu ödeyebilirsiniz."); return; }

        MoneyMarketLending d = row.lending;
        int ans = JOptionPane.showConfirmDialog(this,
                d.getReferenceNo() + " numaralı borcunuzu şimdi ödemek istiyor musunuz?\n"
                        + "Bugüne kadar işleyen faiz dahil tutar hesabınızdan tahsil edilecektir.\n"
                        + "(Vade: " + d.getMaturityDate() + " — tam geri ödeme: "
                        + String.format("%,.2f %s", d.getRepaymentAmount(), d.getCurrency()) + ")",
                "Para Piyasası Borç Ödeme", JOptionPane.YES_NO_OPTION);
        if (ans != JOptionPane.YES_OPTION) return;
        String err = lendDAO.payByCustomer(d.getId(), customer.getCustomerNo());
        if (err != null) { Notify.warning(this, err); return; }
        reload();
        Notify.info(this, "Ödemeniz alındı ve borcunuz kapandı.");
    }

    // ---- Birleşik satır: ya lending ya borrowing ----
    private static class Row {
        final MoneyMarketLending lending;
        final MoneyMarketBorrowing borrowing;
        Row(MoneyMarketLending l) { this.lending = l; this.borrowing = null; }
        Row(MoneyMarketBorrowing b) { this.lending = null; this.borrowing = b; }

        String kind()      { return lending != null ? "Plasman (borçluyum)" : "Borçlanma (alacaklıyım)"; }
        String ref()       { return lending != null ? lending.getReferenceNo() : borrowing.getReferenceNo(); }
        String currency()  { return lending != null ? lending.getCurrency() : borrowing.getCurrency(); }
        double principal() { return lending != null ? lending.getPrincipal() : borrowing.getPrincipal(); }
        double rate()      { return lending != null ? lending.getInterestRate() : borrowing.getInterestRate(); }
        String value()     { return lending != null ? lending.getValueDate() : borrowing.getValueDate(); }
        String maturity()  { return lending != null ? lending.getMaturityDate() : borrowing.getMaturityDate(); }
        double repay()     { return lending != null ? lending.getRepaymentAmount() : borrowing.getRepaymentAmount(); }
        String status()    { return lending != null ? lending.getStatus() : borrowing.getStatus(); }
        String statusText(){ return lending != null ? lending.getStatusText() : borrowing.getStatusText(); }
    }

    private static class RowModel extends AbstractTableModel {
        private final String[] cols = {"Tür", "Ref", "Döviz", "Anapara", "Faiz%", "Valör", "Vade", "Geri Ödeme", "Durum"};
        private List<Row> data = new ArrayList<>();
        void setData(List<Row> d) { this.data = d; fireTableDataChanged(); }
        Row getAt(int r) { return data.get(r); }
        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Object getValueAt(int row, int col) {
            Row d = data.get(row);
            switch (cols[col]) {
                case "Tür":        return d.kind();
                case "Ref":        return d.ref();
                case "Döviz":      return d.currency();
                case "Anapara":    return String.format("%,.2f", d.principal());
                case "Faiz%":      return String.format("%.4f", d.rate());
                case "Valör":      return d.value();
                case "Vade":       return d.maturity();
                case "Geri Ödeme": return String.format("%,.2f", d.repay());
                case "Durum":      return d.statusText();
                default:           return "";
            }
        }
    }

    private class StatusRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int row, int col) {
            super.getTableCellRendererComponent(t, v, s, f, row, col);
            setFont(getFont().deriveFont(Font.BOLD));
            if (!s) {
                String st = model.getAt(t.convertRowIndexToModel(row)).status();
                setForeground("ACTIVE".equals(st) ? new Color(0x1E8E3E)
                        : ("CANCELLED".equals(st) || "EARLY_CLOSED".equals(st)) ? new Color(0xC5221F)
                        : "ROLLED_OVER".equals(st) ? new Color(0xB45309) : new Color(0x374151));
            }
            return this;
        }
    }
}
