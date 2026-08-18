package com.gtech.treasury.ui;

import com.gtech.treasury.dao.AccountDAO;
import com.gtech.treasury.dao.BorrowingDAO;
import com.gtech.treasury.dao.BorrowingDAO.Contract;
import com.gtech.treasury.dao.CustomerDAO;
import com.gtech.treasury.model.Account;
import com.gtech.treasury.model.Customer;
import com.gtech.treasury.model.Deposit;
import com.gtech.treasury.model.User;
import com.gtech.treasury.util.Notify;
import com.gtech.treasury.util.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Vadeli Mevduat (Borrowing) ekranı.
 *   - Müşteri: mevduat açar (tutar, vade, sözleşme tipi), erken bozabilir.
 *   - Personel: tüm mevduatları görür; vadesi gelenleri toplu kapatır.
 */
public class BorrowingPanel extends JPanel {

    private final AccountDAO accountDAO = new AccountDAO();
    private final BorrowingDAO borrowingDAO = new BorrowingDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final Customer customer;
    private final boolean staffMode;

    private final com.gtech.treasury.util.CustomerPicker picker = new com.gtech.treasury.util.CustomerPicker();
    private final JComboBox<Account> accountCombo = new JComboBox<>();
    private final JComboBox<Integer> termCombo = new JComboBox<>(new Integer[]{3, 6, 12, 24, 36});
    private final JRadioButton sabit = new JRadioButton("Sabit", true);
    private final JRadioButton esnek = new JRadioButton("Esnek");
    private final JTextField amountField = new JTextField();
    private final JLabel previewLabel = new JLabel(" ");

    private final DepTableModel model;
    private final JTable table;
    private DepTableModel pendingModel;   // personel: bekleyen başvurular
    private JTable pendingTable;

    public static final String V_APPROVAL = "APPROVAL", V_ACTIVE = "ACTIVE",
            V_CLOSED = "CLOSED", V_REJECTED = "REJECTED", V_ALL = "ALL", V_APPLY = "APPLY";
    private final String view;

    public BorrowingPanel(User staff)              { this(null, true, V_APPROVAL); }
    public BorrowingPanel(User staff, String view) { this(null, true, view); }
    public BorrowingPanel(Customer c)              { this(c, false, V_ALL); }
    public BorrowingPanel(Customer c, String view) { this(c, false, view); }

    private BorrowingPanel(Customer customer, boolean staffMode, String view) {
        this.customer = customer;
        this.staffMode = staffMode;
        this.view = view;
        this.model = new DepTableModel(staffMode);
        this.table = new JTable(model);

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(16, 20, 16, 20));
        setBackground(new Color(0xF0F2F5));

        // İçerik dikey kaydırılabilir panele konur (form uzun olsa da mevduat listesine inilebilir).
        com.gtech.treasury.util.VScrollContent content =
                new com.gtech.treasury.util.VScrollContent(new BorderLayout(0, 12));
        if (staffMode && V_APPROVAL.equals(view)) {
            JPanel top = new JPanel();
            top.setOpaque(false);
            top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
            top.add(buildForm());
            top.add(Box.createVerticalStrut(12));
            top.add(buildStaffTop());
            content.add(top, BorderLayout.NORTH);
        } else if (staffMode) {
            content.add(buildList(headingFor(view)), BorderLayout.CENTER);
        } else if (V_APPLY.equals(view)) {          // müşteri: başvuru + bekleyenler
            content.add(buildForm(), BorderLayout.NORTH);
            content.add(buildList("Başvurularım (Onay Bekleyen)"), BorderLayout.CENTER);
        } else if (V_ACTIVE.equals(view) || V_CLOSED.equals(view) || V_REJECTED.equals(view)) {
            content.add(buildList(headingFor(view)), BorderLayout.CENTER);
        } else {                                    // V_ALL
            content.add(buildForm(), BorderLayout.NORTH);
            content.add(buildList("Mevduatlarım"), BorderLayout.CENTER);
        }
        JScrollPane outer = new JScrollPane(content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        outer.setBorder(null);
        outer.getViewport().setOpaque(false);
        outer.setOpaque(false);
        outer.getVerticalScrollBar().setUnitIncrement(16);
        add(outer, BorderLayout.CENTER);
        reloadAll();
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) { reloadAll(); }
        });
    }

    // ---- Müşteri: mevduat açma formu ----
    private JComponent buildForm() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)), new EmptyBorder(18, 24, 18, 24)));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(7, 8, 7, 8); g.anchor = GridBagConstraints.WEST; g.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        JLabel title = new JLabel(staffMode ? "Müşteri Adına Vadeli Mevduat" : "Vadeli Mevduat Aç");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        g.gridx = 0; g.gridy = row++; g.gridwidth = 2; card.add(title, g); g.gridwidth = 1;

        if (staffMode) {
            picker.setOnChange(() -> { loadAccounts(); refreshPreview(); });
            addRow(card, g, row++, "Müşteri No", picker);
        }

        accountCombo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof Account) { Account a = (Account) v;
                    setText(a.getAccountNo() + "  •  " + a.getAccountType() + "  •  " + String.format("%,.2f %s", a.getBalance(), a.getCurrency())); }
                return this;
            }
        });
        accountCombo.addActionListener(e -> refreshPreview());
        addRow(card, g, row++, "Hesap:", accountCombo);
        addRow(card, g, row++, "Tutar:", amountField);
        termCombo.setSelectedItem(12);
        termCombo.addActionListener(e -> refreshPreview());
        addRow(card, g, row++, "Vade (ay):", termCombo);

        ButtonGroup grp = new ButtonGroup(); grp.add(sabit); grp.add(esnek);
        JPanel cp = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); cp.setOpaque(false);
        cp.add(sabit); cp.add(Box.createHorizontalStrut(16)); cp.add(esnek);
        addRow(card, g, row++, "Sözleşme:", cp);
        JLabel hint = new JLabel("Sabit: vade sonunda tam faiz, erken bozmada faiz yok.  Esnek: erken bozmada geçen süreye kısmi faiz.");
        hint.setForeground(new Color(0x6B7280)); hint.setFont(hint.getFont().deriveFont(11f));
        g.gridx = 0; g.gridy = row++; g.gridwidth = 2; card.add(hint, g); g.gridwidth = 1;

        amountField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshPreview(); }
            public void removeUpdate(DocumentEvent e) { refreshPreview(); }
            public void changedUpdate(DocumentEvent e) { refreshPreview(); }
        });

        JPanel prev = new JPanel(new BorderLayout());
        prev.setBackground(new Color(0xF0FDF4));
        prev.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xBBF7D0)), new EmptyBorder(10, 14, 10, 14)));
        previewLabel.setFont(previewLabel.getFont().deriveFont(Font.BOLD, 13f));
        prev.add(previewLabel);
        g.gridx = 0; g.gridy = row++; g.gridwidth = 2; card.add(prev, g);

        JButton open = new JButton(staffMode ? "Müşteri Adına Başvuru Yap" : "Vadeli Mevduat Başvurusu Yap");
        UITheme.stylePrimary(open);
        open.setPreferredSize(new Dimension(0, 42));
        open.addActionListener(e -> doOpen());
        g.gridy = row++; card.add(open, g);

        loadAccounts();
        refreshPreview();
        return card;
    }

    private void refreshPreview() {
        Account a = (Account) accountCombo.getSelectedItem();
        Double amt = parse(amountField.getText());
        Integer months = (Integer) termCombo.getSelectedItem();
        if (months == null) return;
        double rate = BorrowingDAO.depositRate(months);
        if (a == null || amt == null || amt <= 0) {
            previewLabel.setText("Faiz: %" + String.format("%.0f", rate) + "  (vade: " + months + " ay) — tutar girin.");
            return;
        }
        double interest = BorrowingDAO.interestFor(amt, months);
        double taxRate = BorrowingDAO.depositTaxRate(months);
        double tax = interest * taxRate;
        double net = amt + (interest - tax);
        String cur = a.getCurrency();
        previewLabel.setText(String.format(
                "<html>Faiz: <b>%%%.0f</b> &nbsp;|&nbsp; Brüt getiri: <b>%,.2f %s</b><br>"
                + "Stopaj (%%%.0f): <font color='#C5221F'>-%,.2f %s</font> &nbsp;|&nbsp; "
                + "Net vade sonu getiri: <font color='#1E8E3E'><b>%,.2f %s</b></font><br>"
                + "Vade: %s</html>",
                rate, interest, cur, taxRate * 100, tax, cur, net, cur, LocalDate.now().plusMonths(months)));
    }

    private void doOpen() {
        Account a = (Account) accountCombo.getSelectedItem();
        Double amt = parse(amountField.getText());
        Integer months = (Integer) termCombo.getSelectedItem();
        if (staffMode && applicant() == null) { Notify.warning(this, "Müşteri seçin."); return; }
        if (a == null) { Notify.warning(this, "Hesap seçin. (Seçili müşterinin hesabı olmayabilir.)"); return; }
        if (amt == null || amt <= 0) { Notify.warning(this, "Geçerli bir tutar girin."); return; }
        Contract c = sabit.isSelected() ? Contract.SABIT : Contract.ESNEK;
        String err = borrowingDAO.apply(a, months, c, amt);
        if (err != null) { Notify.warning(this, err); return; }
        amountField.setText("");
        reloadAll();
        Notify.info(this, (staffMode ? "Müşteri adına mevduat başvurusu alındı, onay bekliyor."
                : "Mevduat başvurunuz alındı, banka onayı bekliyor.") + "\n\n"
                + String.format("%,.2f %s / %d ay (%s)", amt, a.getCurrency(), months, c == Contract.SABIT ? "Sabit" : "Esnek"));
    }

    // ---- Personel: bekleyen başvurular + değerlendirme ----
    private JComponent buildStaffTop() {
        pendingModel = new DepTableModel(true);
        pendingTable = new JTable(pendingModel);
        pendingTable.setRowHeight(26);
        pendingTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel t = new JLabel("Bekleyen Mevduat Başvuruları");
        t.setFont(t.getFont().deriveFont(Font.BOLD, 15f));
        panel.add(t, BorderLayout.NORTH);
        pendingTable.setPreferredScrollableViewportSize(new Dimension(900, 150));
        panel.add(new JScrollPane(pendingTable), BorderLayout.CENTER);

        JButton eval = new JButton("Değerlendir / Onayla-Reddet");
        UITheme.stylePrimary(eval);
        eval.addActionListener(e -> doEvaluateDeposit());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        south.add(eval);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private void doEvaluateDeposit() {
        int r = pendingTable.getSelectedRow();
        if (r < 0) { Notify.warning(this, "Değerlendirmek için bir başvuru seçin."); return; }
        Deposit d = pendingModel.getAt(pendingTable.convertRowIndexToModel(r));

        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(new EmptyBorder(8, 8, 8, 8));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 8, 4, 8); g.anchor = GridBagConstraints.WEST;
        int row = 0;
        JLabel h = new JLabel("Mevduat Başvurusu — " + d.getCustomerNo() + " " + d.getCustomerName());
        h.setFont(h.getFont().deriveFont(Font.BOLD, 15f));
        g.gridx = 0; g.gridy = row++; g.gridwidth = 2; card.add(h, g); g.gridwidth = 1;
        row = kv(card, g, row, "Sözleşme:", d.getContractLabel());
        row = kv(card, g, row, "Tutar:", String.format("%,.2f %s", d.getAmount(), d.getCurrency()));
        row = kv(card, g, row, "Vade:", d.getTermMonths() + " ay  (Faiz %" + String.format("%.0f", d.getInterestRate()) + ")");
        row = kv(card, g, row, "Vade Sonu Getiri:", String.format("%,.2f %s", d.getTotalReturn(), d.getCurrency()));
        JLabel info = new JLabel("Onaylanırsa müşteri hesabından tutar çekilir; vade onay tarihinden başlar.");
        info.setForeground(new Color(0x6B7280));
        g.gridx = 0; g.gridy = row++; g.gridwidth = 2; card.add(info, g);

        Object[] opts = {"Onayla", "Reddet", "Vazgeç"};
        int res = JOptionPane.showOptionDialog(this, card, "Mevduat Başvurusu #" + d.getId(),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[2]);
        if (res == 0) {
            String err = borrowingDAO.approve(d.getId());
            if (err != null) { Notify.warning(this, err); return; }
            Notify.info(this, "Mevduat onaylandı ve açıldı.");
            reloadAll();
        } else if (res == 1) {
            String reason = JOptionPane.showInputDialog(this, "Red sebebi:", "Uygun bulunmadı");
            if (reason == null) return;
            String err = borrowingDAO.reject(d.getId(), reason);
            if (err != null) { Notify.warning(this, err); return; }
            Notify.info(this, "Başvuru reddedildi.");
            reloadAll();
        }
    }

    private int kv(JPanel p, GridBagConstraints g, int row, String k, String v) {
        g.gridx = 0; g.gridy = row; JLabel l = new JLabel(k); l.setFont(l.getFont().deriveFont(Font.BOLD)); p.add(l, g);
        g.gridx = 1; p.add(new JLabel(v), g);
        return row + 1;
    }

    // ---- Ortak: mevduat listesi ----
    private JComponent buildList(String heading) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel t = new JLabel(heading);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 15f));
        panel.add(t, BorderLayout.NORTH);
        table.setRowHeight(26);
        table.setPreferredScrollableViewportSize(new Dimension(900, 190)); // kısa; içinde kaydırılır
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        if (staffMode) {
            JButton editDate = new JButton("Vade Tarihi Düzenle");
            editDate.addActionListener(e -> doEditMaturity());
            JButton batch = new JButton("Vadesi Gelenleri Kapat (Batch)");
            batch.addActionListener(e -> { int n = borrowingDAO.matureDue(); reloadAll(); Notify.info(this, n + " mevduat vade sonu kapatıldı."); });
            JButton test = new JButton("Test: Vadeyi Bugüne Çek + Kapat");
            test.addActionListener(e -> doTestMature());
            south.add(editDate); south.add(batch); south.add(test);
        }
        JButton brk = new JButton("Erken Boz");
        brk.addActionListener(e -> doBreak());
        JButton refresh = new JButton("Yenile");
        refresh.addActionListener(e -> reloadAll());
        south.add(refresh); south.add(brk);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private Deposit selected() {
        int r = table.getSelectedRow();
        if (r < 0) return null;
        return model.getAt(table.convertRowIndexToModel(r));
    }

    private void doBreak() {
        Deposit d = selected();
        if (d == null) { Notify.warning(this, "Bir mevduat seçin."); return; }
        if (d.getStatus() != 1) { Notify.warning(this, "Sadece AKTİF mevduat bozulabilir."); return; }
        String msg = "SABIT".equals(d.getContractType())
                ? "Sabit sözleşme: erken bozarsanız FAİZ İŞLEMEZ, sadece anapara iade edilir. Devam?"
                : "Esnek sözleşme: geçen süreye kısmi faiz ödenir. Devam?";
        if (JOptionPane.showConfirmDialog(this, msg, "Erken Bozma", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        String err = borrowingDAO.breakEarly(d.getId());
        if (err != null) { Notify.warning(this, err); return; }
        reloadAll();
        Notify.info(this, "Mevduat erken bozuldu, tutar hesabınıza yatırıldı.");
    }

    private void doEditMaturity() {
        Deposit d = selected();
        if (d == null) { Notify.warning(this, "Bir mevduat seçin."); return; }
        if (d.getStatus() != 1) { Notify.warning(this, "Sadece AKTİF mevduatın vade tarihi değiştirilebilir."); return; }
        String cur = d.getMaturityDate() != null && d.getMaturityDate().length() >= 10
                ? d.getMaturityDate().substring(0, 10) : "";
        while (true) {
            String s = JOptionPane.showInputDialog(this, "Yeni vade tarihi (yyyy-MM-dd):", cur);
            if (s == null) return;
            s = s.trim();
            try { LocalDate.parse(s); } catch (Exception ex) { Notify.warning(this, "Geçersiz tarih. Örnek: 2026-12-31"); continue; }
            String err = borrowingDAO.updateMaturity(d.getId(), s);
            if (err != null) { Notify.warning(this, err); return; }
            reloadAll();
            Notify.info(this, "Vade tarihi güncellendi: " + s);
            return;
        }
    }

    private void doTestMature() {
        Deposit d = selected();
        if (d == null) { Notify.warning(this, "Bir mevduat seçin."); return; }
        if (d.getStatus() != 1) { Notify.warning(this, "Aktif mevduat seçin."); return; }
        borrowingDAO.pullMaturityToday(d.getId());
        int n = borrowingDAO.matureDue();
        reloadAll();
        Notify.info(this, n + " mevduat vade sonu kapatıldı (anapara+faiz ödendi).");
    }

    // ---- yardımcılar ----
    /** Başvuru sahibi müşteri: personelde seçiciden, müşteride sabit. */
    private Customer applicant() {
        return staffMode ? picker.getSelected() : customer;
    }

    private void loadAccounts() {
        accountCombo.removeAllItems();
        Customer c = applicant();
        if (c == null) return;
        for (Account a : accountDAO.getByCustomer(c.getCustomerId())) accountCombo.addItem(a);
    }
    private void reloadAll() {
        if (staffMode) {
            if (V_APPROVAL.equals(view)) {
                if (pendingModel != null) pendingModel.setData(borrowingDAO.getPending());
            } else {
                int st = statusFor(view);
                java.util.List<Deposit> filtered = new java.util.ArrayList<>();
                for (Deposit d : borrowingDAO.getAll()) if (d.getStatus() == st) filtered.add(d);
                model.setData(filtered);
            }
        } else {
            loadAccounts();
            java.util.List<Deposit> mine = customer == null ? java.util.Collections.emptyList()
                    : borrowingDAO.getByCustomer(customer.getCustomerId());
            if (V_APPLY.equals(view)) {
                java.util.List<Deposit> f = new java.util.ArrayList<>();
                for (Deposit d : mine) if (d.getStatus() == 2) f.add(d);   // onay bekleyen
                model.setData(f);
            } else if (V_ACTIVE.equals(view) || V_CLOSED.equals(view) || V_REJECTED.equals(view)) {
                int st = statusFor(view);
                java.util.List<Deposit> f = new java.util.ArrayList<>();
                for (Deposit d : mine) if (d.getStatus() == st) f.add(d);
                model.setData(f);
            } else {
                model.setData(mine);
            }
        }
    }

    /** Görünüm -> mevduat durumu (1 aktif, 0 kapandı, 3 red). */
    private int statusFor(String v) {
        if (V_ACTIVE.equals(v)) return 1;
        if (V_CLOSED.equals(v)) return 0;
        if (V_REJECTED.equals(v)) return 3;
        return 1;
    }

    private String headingFor(String v) {
        if (V_ACTIVE.equals(v)) return "Aktif Mevduatlar";
        if (V_CLOSED.equals(v)) return "Kapanan Mevduatlar";
        if (V_REJECTED.equals(v)) return "Reddedilen Başvurular";
        return "Mevduatlar";
    }
    private Double parse(String s) { try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (Exception e) { return null; } }
    private void addRow(JPanel card, GridBagConstraints g, int row, String label, JComponent field) {
        g.gridx = 0; g.gridy = row; g.gridwidth = 1; g.weightx = 0;
        JLabel l = new JLabel(label); l.setFont(l.getFont().deriveFont(Font.BOLD)); card.add(l, g);
        g.gridx = 1; g.weightx = 1; card.add(field, g);
    }

    // ---- Tablo modeli ----
    private static class DepTableModel extends AbstractTableModel {
        private final boolean showCustomer;
        private final String[] cols;
        private List<Deposit> data = new java.util.ArrayList<>();
        DepTableModel(boolean showCustomer) {
            this.showCustomer = showCustomer;
            this.cols = showCustomer
                    ? new String[]{"#", "Müşteri", "Sözleşme", "Tutar", "Faiz%", "Vade", "Getiri", "Vade Sonu", "Durum"}
                    : new String[]{"#", "Sözleşme", "Tutar", "Faiz%", "Vade", "Getiri", "Vade Sonu", "Durum"};
        }
        void setData(List<Deposit> l) { this.data = l; fireTableDataChanged(); }
        Deposit getAt(int r) { return data.get(r); }
        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Object getValueAt(int row, int col) {
            Deposit d = data.get(row);
            int i = col;
            if (showCustomer) {
                if (i == 0) return d.getId();
                if (i == 1) return d.getCustomerNo() + " - " + d.getCustomerName();
                i -= 1;
            } else if (i == 0) return d.getId();
            switch (i) {
                case 1: return d.getContractLabel();
                case 2: return String.format("%,.2f %s", d.getAmount(), d.getCurrency());
                case 3: return String.format("%.0f", d.getInterestRate());
                case 4: return d.getTermMonths() + " ay";
                case 5: return String.format("%,.2f", d.getTotalReturn());
                case 6: return d.getMaturityDate();
                case 7: return d.getStatusText();
                default: return "";
            }
        }
    }
}
