package com.gtech.treasury.ui;

import com.gtech.treasury.dao.AccountDAO;
import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.dao.CustomerDAO;
import com.gtech.treasury.model.Account;
import com.gtech.treasury.model.Customer;
import com.gtech.treasury.model.User;
import com.gtech.treasury.util.Notify;
import com.gtech.treasury.util.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Hesaplar ekranı. Müşterinin bir/çok hesabı olabilir (tür + döviz).
 * Her sütun için ayrı arama kutusu (canlı filtre) vardır.
 *   - Personel: new AccountsPanel(user)     -> tüm hesaplar
 *   - Müşteri : new AccountsPanel(customer)  -> kendi hesapları
 */
public class AccountsPanel extends JPanel {

    private static final String[] CURRENCIES = {"TRY", "USD", "EUR", "GBP"};

    private final AccountDAO accountDAO = new AccountDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();

    private final boolean canManage;
    private final Customer fixedCustomer;

    private static final int COL_TYPE = 3;       // Tür
    private static final int COL_CURRENCY = 4;   // Döviz

    private final AccountTableModel tableModel = new AccountTableModel();
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<AccountTableModel> sorter = new TableRowSorter<>(tableModel);
    private JComponent[] filterComps;   // sütun başına arama bileşeni (metin ya da açılır liste)
    private JDialog detailDialog;       // "Detaylı Arama" pop-up'ı

    public AccountsPanel(User currentUser) {
        this(null, "ADMIN".equals(currentUser.getRole()) || "TRADER".equals(currentUser.getRole()));
    }

    public AccountsPanel(Customer customer) {
        this(customer, false);   // müşteri sadece görüntüler (hesap açma/işlem personelde)
    }

    private AccountsPanel(Customer fixedCustomer, boolean canManage) {
        this.fixedCustomer = fixedCustomer;
        this.canManage = canManage;

        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel header = new JLabel(fixedCustomer != null
                ? "Hesaplarım — " + fixedCustomer.getCustomerName() + " " + fixedCustomer.getSurname()
                : "Hesaplar");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 18f));
        add(header, BorderLayout.NORTH);

        table.setRowHeight(28);
        table.setRowSorter(sorter);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getClickCount() == 2) doDetail(); }
        });
        // Ana ekranda sadece Hesap No, Müşteri No, Açılış Tarihi görünür (gerisi pop-up'ta)
        hideColumn("Müşteri");
        hideColumn("Bakiye");
        hideColumn("Açılış Saati");

        JPanel center = new JPanel(new BorderLayout(0, 4));
        center.add(buildSearchRow(), BorderLayout.NORTH);   // sütun bazlı arama kutuları
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        add(buildBottomBar(), BorderLayout.SOUTH);

        loadAccounts();
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) { loadAccounts(); }
        });
    }

    /** Ana arama alanı: Hesap No, Müşteri No, Açılış Tarihi + Detaylı Arama pop-up butonu. */
    private JComponent buildSearchRow() {
        filterComps = new JComponent[tableModel.getColumnCount()];
        // Ana ekran kutuları (metin)
        makeText(0);   // Hesap No
        makeText(1);   // Müşteri No
        makeText(6);   // Açılış Tarihi
        // Pop-up bileşenleri
        makeText(2);                                    // Müşteri (ad)
        makeCombo(COL_TYPE, accountDAO.getAccountTypes());
        makeCombo(COL_CURRENCY, java.util.Arrays.asList(CURRENCIES));

        // Kompakt, sabit genişlikte kutular (tüm genişliği kaplamasın)
        ((JTextField) filterComps[0]).setColumns(14);
        ((JTextField) filterComps[1]).setColumns(14);
        ((JTextField) filterComps[6]).setColumns(14);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
                com.gtech.treasury.util.CBStyle.criteriaBorder(),
                new EmptyBorder(8, 10, 10, 10)));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        row.add(labeled("Hesap No", filterComps[0]));
        // Müşteri No yanında ≡ → kalan kriterlerin (ad/tür/döviz) detay pop-up'ı
        row.add(labeled("Müşteri No",
                com.gtech.treasury.util.CBStyle.withLookup(filterComps[1], this::openDetailSearch)));
        row.add(labeled("Açılış Tarihi", filterComps[6]));
        panel.add(row, BorderLayout.CENTER);

        JButton search = new JButton("Sorgula");
        UITheme.stylePrimary(search);
        search.addActionListener(e -> doSearch());
        JButton clear = new JButton("Temizle");
        clear.addActionListener(e -> clearFilters());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(clear);
        buttons.add(search);
        panel.add(buttons, BorderLayout.SOUTH);

        return panel;
    }

    /** "Detaylı Arama" pop-up'ı: Müşteri, Tür, Döviz (açılır liste) — ne olduğu etiketle yazılı. */
    private void openDetailSearch() {
        if (detailDialog == null) {
            detailDialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Detaylı Arama");
            detailDialog.setModal(false);   // arka planda tablo canlı süzülsün
            JPanel p = new JPanel(new GridLayout(0, 1, 6, 10));
            p.setBorder(BorderFactory.createCompoundBorder(
                    com.gtech.treasury.util.CBStyle.criteriaBorder(),
                    new EmptyBorder(6, 12, 12, 12)));
            p.add(labeled("Müşteri (ad)", com.gtech.treasury.util.CBStyle.withLookup(filterComps[2], this::doSearch)));
            p.add(labeled("Hesap Türü", com.gtech.treasury.util.CBStyle.withLookup(filterComps[COL_TYPE], this::doSearch)));
            p.add(labeled("Döviz", com.gtech.treasury.util.CBStyle.withLookup(filterComps[COL_CURRENCY], this::doSearch)));
            JButton search = new JButton("Sorgula");
            UITheme.stylePrimary(search);
            search.addActionListener(e -> doSearch());
            JButton close = new JButton("Kapat");
            close.addActionListener(e -> detailDialog.setVisible(false));
            JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            south.add(close);
            south.add(search);
            p.add(south);
            detailDialog.add(p);
            detailDialog.setSize(320, 260);
            detailDialog.setLocationRelativeTo(this);
        }
        detailDialog.setVisible(true);
        detailDialog.toFront();
    }

    // ---- Yardımcılar ----
    private void makeText(int col) {
        JTextField f = new JTextField();
        f.putClientProperty("JTextField.placeholderText", "🔍 " + tableModel.getColumnName(col));
        f.addActionListener(e -> doSearch());   // Enter ile sorgula (canlı süzme yok)
        filterComps[col] = f;
    }

    private void makeCombo(int col, java.util.List<String> items) {
        JComboBox<String> cb = new JComboBox<>();
        cb.addItem("");                          // boş = hepsi
        for (String it : items) cb.addItem(it);
        filterComps[col] = cb;                   // seçim canlı süzmez; Sorgula'da uygulanır
    }

    /** Bileşenin üstüne "ne olduğu"nu yazan etiket ekler. */
    private JComponent labeled(String text, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setOpaque(false);
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(11f));
        l.setForeground(new Color(0x6B7280));
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    /** Bir tablo kolonunu (başlık adıyla) görünümden gizler. */
    private void hideColumn(String columnName) {
        try {
            int vi = table.getColumnModel().getColumnIndex(columnName);
            table.getColumnModel().removeColumn(table.getColumnModel().getColumn(vi));
        } catch (IllegalArgumentException ignored) { }
    }

    private void doSearch() {
        loadAccounts();
        applyFilters();
    }

    private void clearFilters() {
        for (JComponent c : filterComps) {
            if (c == null) continue;
            if (c instanceof JComboBox) ((JComboBox<?>) c).setSelectedIndex(0);
            else ((JTextField) c).setText("");
        }
        sorter.setRowFilter(null);
        loadAccounts();
    }

    private String filterValue(int i) {
        JComponent c = filterComps[i];
        if (c == null) return "";
        if (c instanceof JComboBox) {
            Object v = ((JComboBox<?>) c).getSelectedItem();
            return v == null ? "" : v.toString().trim();
        }
        return ((JTextField) c).getText().trim();
    }

    /** Dolu filtreleri birleştirip (VE) tabloya uygular.
     *  Açılır liste kolonları (Tür/Döviz) tam eşleşme; diğerleri "içinde geçen". */
    private void applyFilters() {
        List<RowFilter<Object, Object>> filters = new ArrayList<>();
        for (int i = 0; i < filterComps.length; i++) {
            String v = filterValue(i);
            if (v.isEmpty()) continue;
            String regex = (i == COL_TYPE || i == COL_CURRENCY)
                    ? "(?i)^" + Pattern.quote(v) + "$"     // seçimden tam eşleşme
                    : "(?i)" + Pattern.quote(v);            // metinde içinde geçen
            filters.add(RowFilter.regexFilter(regex, i));
        }
        sorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
    }

    private JComponent buildBottomBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refresh = new JButton("Yenile");
        refresh.addActionListener(e -> loadAccounts());
        JButton detail = new JButton("Detay");
        detail.addActionListener(e -> doDetail());
        panel.add(detail);
        panel.add(refresh);
        if (canManage) {
            JButton balance = new JButton("Para Yatır / Çek");
            balance.addActionListener(e -> balanceDialog());
            JButton close = new JButton("Hesabı Kapat");
            close.addActionListener(e -> closeSelected());
            JButton open = new JButton("＋ Yeni Hesap Aç");
            open.addActionListener(e -> openAccountDialog());
            panel.add(balance);
            panel.add(close);
            panel.add(open);
        }
        return panel;
    }

    private void loadAccounts() {
        List<Account> list = (fixedCustomer != null)
                ? accountDAO.getByCustomer(fixedCustomer.getCustomerId())
                : accountDAO.getAll();
        tableModel.setData(list);
    }

    private Account selected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return null;
        return tableModel.getAt(table.convertRowIndexToModel(viewRow));
    }

    private void doDetail() {
        Account a = selected();
        if (a == null) { Notify.warning(this, "Detay için bir hesap seçin."); return; }
        new AccountDetailDialog(SwingUtilities.getWindowAncestor(this), a).setVisible(true);
    }

    // ---- Yeni hesap ----
    private void openAccountDialog() {
        com.gtech.treasury.util.CustomerPicker picker = new com.gtech.treasury.util.CustomerPicker();
        JComboBox<String> typeCombo = new JComboBox<>(accountDAO.getAccountTypes().toArray(new String[0]));
        JComboBox<String> curCombo = new JComboBox<>(CURRENCIES);

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        if (fixedCustomer == null) { form.add(new JLabel("Müşteri No")); form.add(picker); }
        form.add(new JLabel("Hesap Türü:")); form.add(typeCombo);
        form.add(new JLabel("Döviz:"));      form.add(curCombo);

        int res = JOptionPane.showConfirmDialog(this, form, "Yeni Hesap Aç",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        Customer c = fixedCustomer != null ? fixedCustomer : picker.getSelected();
        if (c == null) { Notify.warning(this, "Müşteri seçin."); return; }
        String type = (String) typeCombo.getSelectedItem();
        String cur = (String) curCombo.getSelectedItem();

        // KURAL: Müşterinin ilk hesabı zorunlu olarak Vadesiz / TRY olmalı.
        if (accountDAO.countActiveAccounts(c.getCustomerId()) == 0
                && !("Vadesiz".equals(type) && "TRY".equals(cur))) {
            Notify.warning(this,
                    "Bu müşterinin henüz hesabı yok.\n"
                  + "İlk hesap ZORUNLU olarak Vadesiz / TRY olmalıdır.\n"
                  + "Önce Vadesiz TL hesabı açın; sonra USD, Yatırım vb. açabilirsiniz.");
            return;
        }

        if (accountDAO.open(c.getCustomerId(), type, cur)) {
            ActivityLogDAO.log("ACCOUNT_OPEN", c.getCustomerNo(),
                    "Hesap açıldı: " + type + " / " + cur,
                    "Müşteri: " + c.getCustomerName() + " " + c.getSurname()
                            + " | Tür: " + type + " | Döviz: " + cur);
            loadAccounts();
        } else {
            Notify.error(this, "Hesap açılamadı.");
        }
    }

    // ---- Para yatır/çek ----
    private void balanceDialog() {
        Account a = selected();
        if (a == null) { Notify.warning(this, "Önce bir hesap seçin."); return; }

        JRadioButton dep = new JRadioButton("Para Yatır", true);
        JRadioButton wit = new JRadioButton("Para Çek");
        ButtonGroup g = new ButtonGroup(); g.add(dep); g.add(wit);
        JTextField amount = new JTextField(12);

        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        form.add(new JLabel(a.getAccountNo() + " (" + a.getCurrency() + ") — Bakiye: "
                + String.format("%,.2f", a.getBalance())));
        JPanel dir = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        dir.add(dep); dir.add(Box.createHorizontalStrut(12)); dir.add(wit);
        form.add(dir);
        form.add(new JLabel("Tutar (" + a.getCurrency() + "):"));
        form.add(amount);

        int res = JOptionPane.showConfirmDialog(this, form, "Para Yatır / Çek",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        double val;
        try { val = Double.parseDouble(amount.getText().trim().replace(',', '.')); }
        catch (Exception e) { Notify.warning(this, "Geçerli bir tutar girin."); return; }
        if (val <= 0) { Notify.warning(this, "Tutar sıfırdan büyük olmalı."); return; }

        boolean deposit = dep.isSelected();
        if (!deposit && val > a.getBalance()) { Notify.warning(this, "Yetersiz bakiye."); return; }
        double delta = deposit ? val : -val;
        if (accountDAO.changeBalance(a.getAccountId(), delta)) {
            ActivityLogDAO.log(deposit ? "ACCOUNT_DEPOSIT" : "ACCOUNT_WITHDRAW",
                    a.getCustomerNo(), val, a.getCurrency(),
                    (deposit ? "Para yatırma: " : "Para çekme: ") + val + " " + a.getCurrency(),
                    "Hesap: " + a.getAccountNo());
            loadAccounts();
        } else {
            Notify.error(this, "İşlem gerçekleştirilemedi.");
        }
    }

    private void closeSelected() {
        Account a = selected();
        if (a == null) { Notify.warning(this, "Önce bir hesap seçin."); return; }
        if (a.getBalance() != 0) {
            Notify.warning(this, "Bakiyesi olan hesap kapatılamaz. Önce bakiyeyi sıfırlayın.");
            return;
        }
        int ans = JOptionPane.showConfirmDialog(this,
                a.getAccountNo() + " numaralı hesap kapatılsın mı?", "Onay", JOptionPane.YES_NO_OPTION);
        if (ans == JOptionPane.YES_OPTION) {
            if (accountDAO.close(a.getAccountId())) {
                ActivityLogDAO.log("ACCOUNT_CLOSE", a.getCustomerNo(),
                        "Hesap kapatıldı: " + a.getAccountNo(), "Tür: " + a.getAccountType());
                loadAccounts();
            } else {
                Notify.error(this, "Hesap kapatılamadı.");
            }
        }
    }

    // ---- Tablo modeli ----
    private static class AccountTableModel extends AbstractTableModel {
        private final String[] columns =
                {"Hesap No", "Müşteri No", "Müşteri", "Tür", "Döviz", "Bakiye",
                 "Açılış Tarihi", "Açılış Saati"};
        private List<Account> data = new java.util.ArrayList<>();

        void setData(List<Account> list) { this.data = list; fireTableDataChanged(); }
        Account getAt(int row) { return data.get(row); }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int c) { return columns[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            Account a = data.get(row);
            switch (col) {
                case 0: return a.getAccountNo();
                case 1: return a.getCustomerNo();
                case 2: return a.getCustomerName();
                case 3: return a.getAccountType();
                case 4: return a.getCurrency();
                case 5: return String.format("%,.2f", a.getBalance());
                case 6: return a.getOpenedDate();
                case 7: return a.getOpenedTime();
                default: return "";
            }
        }
    }
}
