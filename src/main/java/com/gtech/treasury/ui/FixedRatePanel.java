package com.gtech.treasury.ui;

import com.gtech.treasury.dao.CustomerDAO;
import com.gtech.treasury.dao.CustomerFixedRateDAO;
import com.gtech.treasury.dao.RateDAO;
import com.gtech.treasury.model.CurrencyRate;
import com.gtech.treasury.model.Customer;
import com.gtech.treasury.model.CustomerFixedRate;
import com.gtech.treasury.util.Notify;
import com.gtech.treasury.util.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Banka (admin) tarafı — Müşteriye Özel Kur Fixleme.
 * Üstte müşteri sorgulama pop-up'ı; ortada seçili müşteriye döviz bazında
 * özel alış/satış kuru tanımlama; altta tanımlı fix kurların listesi.
 */
public class FixedRatePanel extends JPanel {

    private final CustomerDAO customerDAO = new CustomerDAO();
    private final CustomerFixedRateDAO fixedDAO = new CustomerFixedRateDAO();
    private final RateDAO rateDAO = new RateDAO();

    private Customer selected;                 // seçili müşteri
    private final JTextField noField = new JTextField(12);   // birincil kriter: Müşteri No
    private final JLabel selectedLabel = new JLabel("— (Müşteri No yazın veya ≡ ile arayın)");
    private final JComboBox<String> currencyCombo = new JComboBox<>();
    private final JTextField buyField = new JTextField(12);
    private final JTextField sellField = new JTextField(12);
    private final JLabel marketHint = new JLabel(" ");

    private final FixTableModel tableModel = new FixTableModel();
    private final JTable table = new JTable(tableModel);

    public FixedRatePanel() {
        setLayout(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(16, 20, 16, 20));
        setBackground(new Color(0xF0F2F5));

        add(buildHeader(), BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(buildCenter());
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getViewport().setBackground(new Color(0xF0F2F5));
        add(sp, BorderLayout.CENTER);

        loadCurrencies();
        loadTable();
        updateMarketHint();
    }

    // ---- Üst: sadece başlık (sorgu, formdaki "Müşteri:" satırında) ----
    private JComponent buildHeader() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        JLabel title = new JLabel("💱  Müşteriye Özel Kur Fixleme");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        bar.add(title, BorderLayout.WEST);
        return bar;
    }

    /** "Müşteri:" satırındaki sorgu bileşeni: No alanı + ≡ + Temizle + seçili müşteri. */
    private JComponent buildCustomerQueryRow() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);
        noField.addActionListener(e -> searchByNo());     // Enter ile seç
        p.add(com.gtech.treasury.util.CBStyle.withLookup(noField, this::openDetailedSearch)); // No + ≡
        JButton clear = new JButton("Temizle");
        clear.addActionListener(e -> { noField.setText(""); selected = null;
                selectedLabel.setText("— (No yazın veya ≡ ile arayın)");
                buyField.setText(""); sellField.setText(""); });
        p.add(clear);
        return p;
    }

    /** Müşteri No alanına yazılan no ile doğrudan seçim. */
    private void searchByNo() {
        String no = noField.getText().trim();
        if (no.isEmpty()) { openDetailedSearch(); return; }
        List<Customer> res = customerDAO.searchByCriteria(no, "", "", "", "");
        if (res.isEmpty()) {
            Notify.warning(this, "Bu numarayla müşteri bulunamadı: " + no);
        } else if (res.size() == 1) {
            applySelection(res.get(0));
        } else {
            openDetailedSearch();   // birden fazla → pop-up ile seç
        }
    }

    private JComponent buildCenter() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        col.add(buildForm());
        col.add(Box.createVerticalStrut(12));
        col.add(buildTableCard());
        return col;
    }

    // ---- Fix kur tanımlama formu ----
    private JComponent buildForm() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)),
                new EmptyBorder(16, 20, 16, 20)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 240));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(7, 8, 7, 8);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        int r = 0;
        addRow(card, g, r++, "Müşteri No", buildCustomerQueryRow());
        addRow(card, g, r++, "Döviz:", currencyCombo);

        marketHint.setForeground(new Color(0x6B7280));
        marketHint.setFont(marketHint.getFont().deriveFont(12f));
        addRow(card, g, r++, "Güncel Kur:", marketHint);

        addRow(card, g, r++, "Fix Alış Kuru:", buyField);
        addRow(card, g, r++, "Fix Satış Kuru:", sellField);

        JLabel note = new JLabel("<html><font color='#9CA3AF'>Boş bıraktığınız yön için fix uygulanmaz "
                + "(o yönde güncel kur geçerli olur).</font></html>");
        note.setFont(note.getFont().deriveFont(11f));
        g.gridx = 0; g.gridy = r++; g.gridwidth = 2;
        card.add(note, g);
        g.gridwidth = 1;

        JButton save = new JButton("Kuru Fixle / Kaydet");
        UITheme.stylePrimary(save);
        save.setPreferredSize(new Dimension(0, 40));
        save.addActionListener(e -> save());
        g.gridx = 0; g.gridy = r++; g.gridwidth = 2;
        card.add(save, g);

        currencyCombo.addActionListener(e -> { updateMarketHint(); prefillFromExisting(); });
        return card;
    }

    private JComponent buildTableCard() {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)),
                new EmptyBorder(12, 16, 12, 16)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel h = new JLabel("Tanımlı Fix Kurlar  (satır seçip 'Seçili Fixi Kaldır')");
        h.setFont(h.getFont().deriveFont(Font.BOLD, 14f));
        card.add(h, BorderLayout.NORTH);

        table.setRowHeight(26);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane sp = new JScrollPane(table,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.setPreferredSize(new Dimension(900, 150));   // daha kısa; içinde kaydırılır
        card.add(sp, BorderLayout.CENTER);

        JButton update = new JButton("Fix Güncelle");
        update.addActionListener(e -> updateSelected());
        JButton del = new JButton("Seçili Fixi Kaldır");
        del.addActionListener(e -> deleteSelected());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        south.setOpaque(false);
        south.add(update);
        south.add(del);
        card.add(south, BorderLayout.SOUTH);
        return card;
    }

    private void addRow(JPanel card, GridBagConstraints g, int row, String label, JComponent field) {
        g.gridx = 0; g.gridy = row; g.weightx = 0;
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        card.add(l, g);
        g.gridx = 1; g.weightx = 1;
        card.add(field, g);
    }

    // ---- Veri ----
    private void loadCurrencies() {
        currencyCombo.removeAllItems();
        for (CurrencyRate cr : rateDAO.getAll()) currencyCombo.addItem(cr.getCurrency());
    }

    private void loadTable() {
        tableModel.setData(fixedDAO.getAllActive());
    }

    private void updateMarketHint() {
        String cur = (String) currencyCombo.getSelectedItem();
        if (cur == null) { marketHint.setText(" "); return; }
        CurrencyRate cr = rateDAO.getByCurrency(cur);
        if (cr == null) { marketHint.setText("—"); return; }
        marketHint.setText(String.format("Alış %,.4f   •   Satış %,.4f", cr.getBuyRate(), cr.getSellRate()));
    }

    /** Seçili müşteri + döviz için mevcut fix varsa alanlara doldur. */
    private void prefillFromExisting() {
        if (selected == null) return;
        String cur = (String) currencyCombo.getSelectedItem();
        if (cur == null) return;
        CustomerFixedRate f = fixedDAO.get(selected.getCustomerNo(), cur);
        buyField.setText(f != null && f.getBuyRate() > 0 ? String.valueOf(f.getBuyRate()) : "");
        sellField.setText(f != null && f.getSellRate() > 0 ? String.valueOf(f.getSellRate()) : "");
    }

    private Double parse(String s) {
        if (s == null || s.trim().isEmpty()) return 0.0;   // boş = fix yok
        try { return Double.parseDouble(s.trim().replace(',', '.')); }
        catch (Exception e) { return null; }
    }

    private void save() {
        if (selected == null) { Notify.warning(this, "Önce 'Müşteri Sorgula' ile bir müşteri seçin."); return; }
        String cur = (String) currencyCombo.getSelectedItem();
        if (cur == null) { Notify.warning(this, "Döviz seçin."); return; }
        Double buy = parse(buyField.getText());
        Double sell = parse(sellField.getText());
        if (buy == null || sell == null) { Notify.warning(this, "Geçerli kur değerleri girin."); return; }
        if (buy <= 0 && sell <= 0) { Notify.warning(this, "En az bir yön (alış/satış) için kur girin."); return; }

        if (fixedDAO.upsert(selected.getCustomerNo(), cur, buy, sell)) {
            Notify.info(this, "Fix kur kaydedildi.\n"
                    + selected.getCustomerNo() + " - " + selected.getCustomerName() + " " + selected.getSurname()
                    + "\n" + cur + "  Alış: " + (buy > 0 ? buy : "—") + "  Satış: " + (sell > 0 ? sell : "—"));
            loadTable();
        } else {
            Notify.error(this, "Fix kur kaydedilemedi.");
        }
    }

    /** Seçili fix satırını forma yükler; kullanıcı düzenleyip "Kaydet" ile günceller. */
    private void updateSelected() {
        int row = table.getSelectedRow();
        if (row < 0) { Notify.warning(this, "Güncellemek için bir satır seçin."); return; }
        CustomerFixedRate f = tableModel.getAt(row);

        // Müşteriyi no ile bul ve seç
        List<Customer> res = customerDAO.searchByCriteria(String.valueOf(f.getCustomerNo()), "", "", "", "");
        if (!res.isEmpty()) applySelection(res.get(0));
        else {
            noField.setText(String.valueOf(f.getCustomerNo()));
            selectedLabel.setText(String.valueOf(f.getCustomerNo()));
        }

        currencyCombo.setSelectedItem(f.getCurrency());
        buyField.setText(f.getBuyRate() > 0 ? String.valueOf(f.getBuyRate()) : "");
        sellField.setText(f.getSellRate() > 0 ? String.valueOf(f.getSellRate()) : "");
        updateMarketHint();
        Notify.info(this, "Fix forma yüklendi. Değerleri düzenleyip 'Kuru Fixle / Kaydet' ile güncelleyin.");
    }

    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0) { Notify.warning(this, "Kaldırmak için bir satır seçin."); return; }
        CustomerFixedRate f = tableModel.getAt(row);
        int ok = JOptionPane.showConfirmDialog(this,
                f.getCustomerNo() + " - " + f.getCurrency() + " fix kuru kaldırılsın mı?",
                "Onay", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;
        if (fixedDAO.deactivate(f.getCustomerNo(), f.getCurrency())) { loadTable(); }
        else Notify.error(this, "Kaldırılamadı.");
    }

    // ---- Detaylı müşteri arama pop-up'ı (Ad/Soyad ile) ----
    private void openDetailedSearch() {
        JTextField noF = new JTextField(12), nameF = new JTextField(12), surF = new JTextField(12);
        noF.setText(noField.getText().trim());   // paneldeki no'yu taşı
        CustSearchModel model = new CustSearchModel();
        JTable results = new JTable(model);
        results.setRowHeight(24);
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        Runnable doSearch = () -> model.setData(
                customerDAO.searchByCriteria(noF.getText(), nameF.getText(), surF.getText(), "", ""));

        // "Sorgu Kriterleri" başlıklı kriter paneli: No + Ad + Soyad + Sorgula + Temizle
        JPanel crit = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        crit.setBorder(com.gtech.treasury.util.CBStyle.criteriaBorder());
        crit.add(new JLabel("Müşteri No")); crit.add(noF);
        crit.add(new JLabel("Ad"));         crit.add(nameF);
        crit.add(new JLabel("Soyad"));      crit.add(surF);
        JButton search = new JButton("Sorgula");
        search.addActionListener(e -> doSearch.run());
        crit.add(search);
        JButton clear = new JButton("Temizle");
        clear.addActionListener(e -> { noF.setText(""); nameF.setText(""); surF.setText(""); doSearch.run(); });
        crit.add(clear);

        // Enter ile ara
        for (JTextField f : new JTextField[]{noF, nameF, surF}) f.addActionListener(e -> doSearch.run());

        JPanel north = new JPanel(new BorderLayout());
        north.add(crit, BorderLayout.NORTH);

        JLabel hint = new JLabel("Satıra çift tıklayarak müşteriyi seçin.");
        hint.setForeground(new Color(0x6B7280));

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setPreferredSize(new Dimension(580, 380));
        content.add(north, BorderLayout.NORTH);
        content.add(new JScrollPane(results), BorderLayout.CENTER);
        content.add(hint, BorderLayout.SOUTH);

        doSearch.run();   // açılışta tümünü getir

        results.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && results.getSelectedRow() >= 0) {
                    applySelection(model.getAt(results.getSelectedRow()));
                    Window w = SwingUtilities.getWindowAncestor(results);
                    if (w != null) w.dispose();
                }
            }
        });

        int res = JOptionPane.showConfirmDialog(this, content, "Müşteri Sorgula / Seç",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION && results.getSelectedRow() >= 0) {
            applySelection(model.getAt(results.getSelectedRow()));
        }
    }

    private void applySelection(Customer c) {
        this.selected = c;
        noField.setText(String.valueOf(c.getCustomerNo()));
        selectedLabel.setText(c.getCustomerNo() + " - " + c.getCustomerName() + " " + c.getSurname());
        prefillFromExisting();
    }

    // ---- Tablo modelleri ----
    private static class FixTableModel extends AbstractTableModel {
        private final String[] cols = {"Müşteri No", "Müşteri", "Döviz", "Fix Alış", "Fix Satış", "Tarih"};
        private List<CustomerFixedRate> data = new java.util.ArrayList<>();
        void setData(List<CustomerFixedRate> d) { this.data = d; fireTableDataChanged(); }
        CustomerFixedRate getAt(int r) { return data.get(r); }
        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Object getValueAt(int r, int c) {
            CustomerFixedRate f = data.get(r);
            switch (c) {
                case 0: return f.getCustomerNo();
                case 1: return f.getCustomerName();
                case 2: return f.getCurrency();
                case 3: return f.getBuyRate() > 0 ? String.format("%,.4f", f.getBuyRate()) : "—";
                case 4: return f.getSellRate() > 0 ? String.format("%,.4f", f.getSellRate()) : "—";
                case 5: return f.getCreatedAt();
                default: return "";
            }
        }
    }

    private static class CustSearchModel extends AbstractTableModel {
        private final String[] cols = {"No", "Ad", "Soyad", "Tür"};
        private List<Customer> data = new java.util.ArrayList<>();
        void setData(List<Customer> d) { this.data = d; fireTableDataChanged(); }
        Customer getAt(int r) { return data.get(r); }
        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Object getValueAt(int r, int c) {
            Customer cu = data.get(r);
            switch (c) {
                case 0: return cu.getCustomerNo();
                case 1: return cu.getCustomerName();
                case 2: return cu.getSurname();
                case 3: return cu.getCustomerType();
                default: return "";
            }
        }
    }
}
