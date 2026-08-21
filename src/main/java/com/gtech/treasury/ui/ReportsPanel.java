package com.gtech.treasury.ui;

import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.model.ActivityLog;
import com.gtech.treasury.model.User;
import com.gtech.treasury.util.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Raporlar ekranı — sistemdeki işlemleri (activity_log) listeler.
 * Hesaplar ekranıyla aynı arama deseni: her sütun için arama kutusu,
 * İşlem Türü ve Döviz açılır liste, canlı filtre + Sorgula/Temizle.
 * (Giriş/çıkış raporda gösterilmez.)
 */
public class ReportsPanel extends JPanel {

    private static final int COL_TYPE = 2;       // İşlem Türü
    private static final int COL_CURRENCY = 6;   // Döviz
    private static final String[] CURRENCIES = {"USD", "EUR", "GBP", "TRY"};

    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put("CUSTOMER_ADD", "Müşteri Kaydı");
        LABELS.put("CUSTOMER_UPDATE", "Müşteri Güncelleme");
        LABELS.put("CUSTOMER_DELETE", "Müşteri Silme");
        LABELS.put("ACCOUNT_OPEN", "Hesap Açma");
        LABELS.put("ACCOUNT_CLOSE", "Hesap Kapatma");
        LABELS.put("ACCOUNT_DEPOSIT", "Para Yatırma");
        LABELS.put("ACCOUNT_WITHDRAW", "Para Çekme");
        LABELS.put("TRANSFER", "Havale");
        LABELS.put("EFT", "EFT (Başka Banka)");
        LABELS.put("FAST", "FAST (Anlık)");
        LABELS.put("BANK_ADJUST", "Banka Kasası Düzenleme");
        LABELS.put("LOAN_APPLIED", "Kredi Başvurusu");
        LABELS.put("LOAN_GIVEN", "Kredi Onay/Kullanım");
        LABELS.put("LOAN_APPROVED", "Kredi Onayı");
        LABELS.put("LOAN_DISBURSED", "Kredi Kullandırım");
        LABELS.put("MM_BORROW_CREATE", "PP Borçlanma");
        LABELS.put("MM_BORROW_MATURE", "PP Borçlanma Vade");
        LABELS.put("MM_BORROW_CANCEL", "PP Borçlanma İptal");
        LABELS.put("LOAN_REJECTED", "Kredi Reddi");
        LABELS.put("LOAN_INSTALLMENT", "Kredi Taksit Ödemesi");
        LABELS.put("LOAN_REPAID", "Kredi Geri Ödeme");
        LABELS.put("DEPOSIT_OPEN", "Mevduat Açılışı");
        LABELS.put("DEPOSIT_CLOSE", "Mevduat Vade Sonu");
        LABELS.put("DEPOSIT_BREAK", "Mevduat Erken Bozma");
        LABELS.put("SPOT_BUY", "Döviz Alış");
        LABELS.put("SPOT_SELL", "Döviz Satış");
        LABELS.put("RATE_UPDATE", "Kur Güncelleme");
        LABELS.put("USER_ADD", "Kullanıcı Ekleme");
        LABELS.put("USER_DELETE", "Kullanıcı Silme");
        LABELS.put("ROLE_CHANGE", "Rol Değiştirme");
        LABELS.put("PERMISSION_UPDATE", "Yetki Güncelleme");
    }

    private final ActivityLogDAO dao = new ActivityLogDAO();
    private final ReportTableModel tableModel = new ReportTableModel();
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<ReportTableModel> sorter = new TableRowSorter<>(tableModel);
    private static final int COL_CUSTOMER = 4;   // "Müşteri No" sütunu (birincil arama)

    private JComponent[] filterComps;
    private JDialog searchDialog;    // detaylı arama pop-u'ı

    public ReportsPanel(User currentUser) {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(16, 16, 16, 16));

        // Tüm filtre bileşenlerini oluştur (bir sütunu inline, gerisi pop-up'ta gösterilir)
        filterComps = new JComponent[tableModel.getColumnCount()];
        for (int i = 0; i < filterComps.length; i++) filterComps[i] = makeFilter(i);

        add(buildTopBar(), BorderLayout.NORTH);

        table.setRowHeight(28);
        table.setRowSorter(sorter);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openDetail();
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);

        loadData();
    }

    /** Bir sütun için filtre bileşeni (İşlem Türü/Döviz açılır liste, gerisi metin — canlı filtre). */
    private JComponent makeFilter(int i) {
        if (i == COL_TYPE) {
            JComboBox<String> cb = new JComboBox<>();
            cb.addItem("");
            for (String label : LABELS.values()) cb.addItem(label);
            return cb;   // seçim canlı süzmez; Sorgula'da uygulanır
        }
        if (i == COL_CURRENCY) {
            JComboBox<String> cb = new JComboBox<>();
            cb.addItem("");
            for (String c : CURRENCIES) cb.addItem(c);
            return cb;
        }
        JTextField f = new JTextField(14);
        f.putClientProperty("JTextField.placeholderText", tableModel.getColumnName(i));
        f.addActionListener(e -> runQuery());   // Enter ile sorgula (canlı süzme yok)
        return f;
    }

    /** Kriterleri uygula (Sorgula). Veriyi tazeleyip filtreyi kurar. */
    private void runQuery() {
        loadData();
        applyFilters();
    }

    // ================= ÜST: Başlık + "Sorgu Kriterleri" (birincil Müşteri No + ≡) =================
    private JComponent buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(0, 8));
        JLabel title = new JLabel("İşlem Raporu  (satıra çift tıklayarak dekontu görün)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        bar.add(title, BorderLayout.NORTH);

        JPanel crit = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        crit.setBorder(com.gtech.treasury.util.CBStyle.criteriaBorder());
        crit.add(new JLabel("Müşteri No"));
        JTextField primary = (JTextField) filterComps[COL_CUSTOMER];
        primary.setColumns(12);
        // ≡ butonu detaylı arama pop-up'ını açar
        crit.add(com.gtech.treasury.util.CBStyle.withLookup(primary, this::openSearchDialog));
        JButton searchBtn = new JButton("Sorgula");
        UITheme.stylePrimary(searchBtn);
        searchBtn.addActionListener(e -> runQuery());
        crit.add(searchBtn);
        JButton clear = new JButton("Temizle");
        clear.addActionListener(e -> clearFilters());
        crit.add(clear);
        bar.add(crit, BorderLayout.WEST);
        return bar;
    }

    /** Detaylı arama pop-up'ı: Müşteri No hariç diğer kriterler. Modeless. */
    private void openSearchDialog() {
        if (searchDialog == null) {
            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(com.gtech.treasury.util.CBStyle.criteriaBorder());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 6, 5, 6);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            int row = 0;
            for (int i = 0; i < filterComps.length; i++) {
                if (i == COL_CUSTOMER) continue;   // birincil alan üstte inline
                gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
                form.add(new JLabel(tableModel.getColumnName(i)), gbc);
                gbc.gridx = 1; gbc.weightx = 1;
                form.add(filterComps[i], gbc);
                row++;
            }

            JButton searchBtn = new JButton("Sorgula");
            UITheme.stylePrimary(searchBtn);
            searchBtn.addActionListener(e -> runQuery());
            JButton clearBtn = new JButton("Temizle");
            clearBtn.addActionListener(e -> clearFilters());
            JButton closeBtn = new JButton("Kapat");
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            actions.add(clearBtn);
            actions.add(closeBtn);
            actions.add(searchBtn);

            JPanel content = new JPanel(new BorderLayout(8, 8));
            content.setBorder(new EmptyBorder(12, 14, 12, 14));
            content.add(form, BorderLayout.CENTER);
            content.add(actions, BorderLayout.SOUTH);

            searchDialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Rapor Ara (Detaylı)");
            searchDialog.setModal(false);
            closeBtn.addActionListener(e -> searchDialog.setVisible(false));
            searchDialog.setContentPane(content);
            searchDialog.pack();
            searchDialog.setLocationRelativeTo(this);
        }
        searchDialog.setVisible(true);
        searchDialog.toFront();
    }

    private String filterValue(int i) {
        JComponent c = filterComps[i];
        if (c instanceof JComboBox) {
            Object v = ((JComboBox<?>) c).getSelectedItem();
            return v == null ? "" : v.toString().trim();
        }
        return ((JTextField) c).getText().trim();
    }

    private void applyFilters() {
        List<RowFilter<Object, Object>> filters = new ArrayList<>();
        for (int i = 0; i < filterComps.length; i++) {
            String v = filterValue(i);
            if (v.isEmpty()) continue;
            String regex = (i == COL_TYPE || i == COL_CURRENCY)
                    ? "(?i)^" + Pattern.quote(v) + "$"
                    : "(?i)" + Pattern.quote(v);
            filters.add(RowFilter.regexFilter(regex, i));
        }
        sorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
    }

    private void clearFilters() {
        for (JComponent c : filterComps) {
            if (c instanceof JComboBox) ((JComboBox<?>) c).setSelectedIndex(0);
            else ((JTextField) c).setText("");
        }
        sorter.setRowFilter(null);
        loadData();
    }

    /** Tüm işlemleri (giriş/çıkış hariç) DB'den yükler. */
    private void loadData() {
        List<ActivityLog> list = dao.search("", "", "", "", "", "", "");
        list.removeIf(a -> "LOGIN".equals(a.getActionType()));
        tableModel.setData(list);
    }

    private void openDetail() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return;
        ActivityLog a = tableModel.getAt(table.convertRowIndexToModel(viewRow));

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

        r = addRow(card, gbc, r, "İşlem No:", String.valueOf(a.getId()));
        r = addRow(card, gbc, r, "Tarih:", a.getDatePart());
        r = addRow(card, gbc, r, "Saat:", a.getTimePart());
        r = addRow(card, gbc, r, "İşlem Türü:", labelOf(a.getActionType()));
        r = addRow(card, gbc, r, "Yapan Kullanıcı:", a.getUsername());
        r = addRow(card, gbc, r, "Müşteri No:", a.getCustomerNo() > 0 ? String.valueOf(a.getCustomerNo()) : "-");
        r = addRow(card, gbc, r, "Tutar:", a.getAmount() > 0
                ? String.format("%,.2f %s", a.getAmount(), a.getCurrency() == null ? "" : a.getCurrency()) : "-");
        r = addRow(card, gbc, r, "Açıklama:", a.getDescription());

        gbc.gridx = 0; gbc.gridy = r++; gbc.gridwidth = 2;
        card.add(new JSeparator(), gbc);
        JLabel dt = new JLabel("Detaylar");
        dt.setFont(dt.getFont().deriveFont(Font.BOLD));
        gbc.gridy = r++;
        card.add(dt, gbc);
        JTextArea details = new JTextArea(a.getDetails() == null ? "-" : a.getDetails(), 4, 42);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setBackground(new Color(0xF3F4F6));
        gbc.gridy = r++;
        card.add(new JScrollPane(details), gbc);

        JOptionPane.showMessageDialog(this, card, "Dekont - İşlem #" + a.getId(),
                JOptionPane.PLAIN_MESSAGE);
    }

    private int addRow(JPanel card, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0; gbc.gridy = row;
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        card.add(l, gbc);
        gbc.gridx = 1;
        card.add(new JLabel(value == null ? "-" : value), gbc);
        return row + 1;
    }

    private static String labelOf(String code) {
        return LABELS.getOrDefault(code, code);
    }

    // ---- Tablo modeli ----
    private static class ReportTableModel extends AbstractTableModel {
        private final String[] columns =
                {"Tarih", "Saat", "İşlem Türü", "Kullanıcı", "Müşteri No", "Tutar", "Döviz", "Açıklama"};
        private List<ActivityLog> data = new java.util.ArrayList<>();

        void setData(List<ActivityLog> list) { this.data = list; fireTableDataChanged(); }
        ActivityLog getAt(int row) { return data.get(row); }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int c) { return columns[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            ActivityLog a = data.get(row);
            switch (col) {
                case 0: return a.getDatePart();
                case 1: return a.getTimePart();
                case 2: return labelOf(a.getActionType());
                case 3: return a.getUsername();
                case 4: return a.getCustomerNo() > 0 ? a.getCustomerNo() : "-";
                case 5: return a.getAmount() > 0 ? String.format("%,.2f", a.getAmount()) : "-";
                case 6: return a.getCurrency() == null ? "-" : a.getCurrency();
                case 7: return a.getDescription();
                default: return "";
            }
        }
    }
}
