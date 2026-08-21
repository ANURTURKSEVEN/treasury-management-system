package com.gtech.treasury.ui;

import com.gtech.treasury.dao.AccountDAO;
import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.dao.CustomerDAO;
import com.gtech.treasury.dao.CustomerFixedRateDAO;
import com.gtech.treasury.dao.RateDAO;
import com.gtech.treasury.dao.SpotDAO;
import com.gtech.treasury.model.CurrencyRate;
import com.gtech.treasury.model.Customer;
import com.gtech.treasury.model.CustomerFixedRate;
import com.gtech.treasury.model.SpotTransaction;
import com.gtech.treasury.util.Notify;
import com.gtech.treasury.util.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.List;

/**
 * Döviz Al/Sat ekranı. Alış ve satış aynı ekranda (işlem yönü seçilir).
 * Altta işlem geçmişi tablosu (personel: tümü, müşteri: kendi işlemleri).
 *
 * fixedCustomer != null ise (müşteri girişi): müşteri seçimi yoktur,
 * işlemler o müşteri adına yapılır ve sadece kendi geçmişi listelenir.
 */
public class SpotTradePanel extends JPanel {

    private static final Color RESULT_COLOR = new Color(0x1E8E3E);

    private final RateDAO rateDAO = new RateDAO();
    private final SpotDAO spotDAO = new SpotDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final AccountDAO accountDAO = new AccountDAO();
    private final CustomerFixedRateDAO fixedRateDAO = new CustomerFixedRateDAO();
    private final com.gtech.treasury.dao.CustomerFXFixingDAO fixingDAO = new com.gtech.treasury.dao.CustomerFXFixingDAO();

    private final boolean canTrade;
    private final Customer fixedCustomer;   // müşteri girişiyse dolu
    private final Runnable backToRate;

    private final com.gtech.treasury.util.CustomerPicker picker = new com.gtech.treasury.util.CustomerPicker();
    private final JRadioButton buyRadio = new JRadioButton("Alış  (döviz alıyoruz)", true);
    private final JRadioButton sellRadio = new JRadioButton("Satış (döviz satıyoruz)");
    private final JComboBox<String> currencyCombo = new JComboBox<>();
    private final JTextField amountField = new JTextField(14);
    private final JTextField rateField = new JTextField(14);
    private final JLabel resultLabel = new JLabel("—");

    // Kur kaynağı seçimi: güncel kur mu, müşteriye fixlenmiş kur mu
    private final JComboBox<String> rateSourceCombo =
            new JComboBox<>(new String[]{"Güncel Kur", "Fixlenmiş Kur"});
    private final JLabel rateInfo = new JLabel(" ");
    private boolean refreshingRate = false;

    private final HistoryTableModel historyModel = new HistoryTableModel();
    private final JTable historyTable = new JTable(historyModel);

    private List<CurrencyRate> rates;

    public SpotTradePanel(boolean canTrade, Customer fixedCustomer, Runnable backToRate) {
        this.canTrade = canTrade;
        this.fixedCustomer = fixedCustomer;
        this.backToRate = backToRate;

        setLayout(new BorderLayout(0, 10));
        setBorder(new EmptyBorder(16, 20, 20, 20));

        // Kur alanı değiştirilemez: değer Kur Ekranı'ndan otomatik gelir
        rateField.setEditable(false);
        rateField.setBackground(new Color(0xF3F4F6));
        rateField.setToolTipText("Kur sabittir; güncelleme Kur Ekranı'ndan yapılır.");

        // İşlem geçmişi tablosu SADECE müşteri girişinde gösterilir.
        // Tek dikey kaydırma: üstte Al/Sat kartı, altta "İşlemlerim" — aşağı kaydırılır.
        if (fixedCustomer != null) {
            com.gtech.treasury.util.VScrollContent content =
                    new com.gtech.treasury.util.VScrollContent(new BorderLayout(0, 10));
            content.add(buildTop(), BorderLayout.NORTH);
            content.add(buildHistory(), BorderLayout.CENTER);
            JScrollPane outer = new JScrollPane(content,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            outer.setBorder(null);
            outer.getViewport().setOpaque(false);
            outer.setOpaque(false);
            outer.getVerticalScrollBar().setUnitIncrement(16);
            add(outer, BorderLayout.CENTER);
        } else {
            add(buildTop(), BorderLayout.NORTH);
        }

        loadData();
        fillRateFromSelection();
        loadHistory();
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) {
                loadData(); fillRateFromSelection(); loadHistory();
            }
        });
    }

    private JComponent buildTop() {
        JPanel top = new JPanel(new BorderLayout());
        // "Kur Ekranına Dön" yalnızca eski birleşik ekranda; ayrı menü ekranında gizli
        if (backToRate != null) top.add(buildBackBar(), BorderLayout.NORTH);
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
        wrap.add(buildCard());
        top.add(wrap, BorderLayout.CENTER);
        return top;
    }

    private JComponent buildBackBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton back = new JButton("←  Kur Ekranına Dön");
        back.addActionListener(e -> { if (backToRate != null) backToRate.run(); });
        bar.add(back);
        return bar;
    }

    private JComponent buildCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)),
                new EmptyBorder(20, 28, 20, 28)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(7, 8, 7, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        JLabel title = new JLabel("Döviz Al / Sat");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        card.add(title, gbc);
        row++;
        gbc.gridwidth = 1;

        // Müşteri satırı: sabit müşteri varsa etiket, yoksa açılır liste
        if (fixedCustomer != null) {
            JLabel who = new JLabel(fixedCustomer.getCustomerNo() + " - "
                    + fixedCustomer.getCustomerName() + " " + fixedCustomer.getSurname());
            who.setFont(who.getFont().deriveFont(Font.BOLD));
            addRow(card, gbc, row++, "Müşteri:", who);
        } else {
            addRow(card, gbc, row++, "Müşteri No", picker);
        }

        ButtonGroup group = new ButtonGroup();
        group.add(buyRadio);
        group.add(sellRadio);
        JPanel dir = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        dir.setOpaque(false);
        dir.add(buyRadio);
        dir.add(Box.createHorizontalStrut(16));
        dir.add(sellRadio);
        addRow(card, gbc, row++, "İşlem Yönü:", dir);

        addRow(card, gbc, row++, "Döviz:", currencyCombo);
        addRow(card, gbc, row++, "Miktar:", amountField);
        addRow(card, gbc, row++, "Kur Türü:", rateSourceCombo);
        rateInfo.setForeground(new Color(0x6B7280));
        rateInfo.setFont(rateInfo.getFont().deriveFont(12f));
        addRow(card, gbc, row++, "", rateInfo);
        addRow(card, gbc, row++, "Kur:", rateField);

        JButton calc = new JButton("Hesapla");
        calc.addActionListener(e -> calculate());
        gbc.gridx = 1; gbc.gridy = row++; gbc.fill = GridBagConstraints.NONE;
        card.add(calc, gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPanel resultBox = new JPanel(new BorderLayout());
        resultBox.setBackground(new Color(0xF0FDF4));
        resultBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xBBF7D0)),
                new EmptyBorder(10, 14, 10, 14)));
        JLabel resTitle = new JLabel("Karşılık (TRY)");
        resTitle.setForeground(new Color(0x6B7280));
        resultLabel.setFont(resultLabel.getFont().deriveFont(Font.BOLD, 22f));
        resultLabel.setForeground(RESULT_COLOR);
        resultBox.add(resTitle, BorderLayout.NORTH);
        resultBox.add(resultLabel, BorderLayout.CENTER);
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        gbc.insets = new Insets(14, 8, 8, 8);
        card.add(resultBox, gbc);
        gbc.insets = new Insets(7, 8, 7, 8);

        if (canTrade) {
            JButton save = new JButton("İşlemi Kaydet");
            UITheme.stylePrimary(save);
            save.setPreferredSize(new Dimension(0, 40));
            save.addActionListener(e -> saveTrade());
            gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
            card.add(save, gbc);

            JButton byRef = new JButton("Fix Referansı ile İşle");
            byRef.addActionListener(e -> executeByReference());
            gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
            card.add(byRef, gbc);
        }

        currencyCombo.addActionListener(e -> fillRateFromSelection());
        buyRadio.addActionListener(e -> fillRateFromSelection());
        sellRadio.addActionListener(e -> fillRateFromSelection());
        picker.setOnChange(this::fillRateFromSelection);
        rateSourceCombo.addActionListener(e -> { if (!refreshingRate) fillRateFromSelection(); });

        return card;
    }

    /** Bir FX fix referansıyla, o referanstaki kur/tutar/yön ile spot işlemi gerçekleştirir. */
    private void executeByReference() {
        String ref = JOptionPane.showInputDialog(this,
                "Fix referans numarası (ör. FX-20260820-000001):", "Fix Referansı ile İşle",
                JOptionPane.PLAIN_MESSAGE);
        if (ref == null || ref.trim().isEmpty()) return;
        com.gtech.treasury.model.CustomerFXFixing d = fixingDAO.getByReference(ref.trim());
        if (d == null) { Notify.warning(this, "Bu referansla bir fix bulunamadı: " + ref.trim()); return; }
        if (!"FIXED".equals(d.getStatus())) {
            Notify.warning(this, "Bu referans işleme uygun değil (durum: " + d.getStatusText() + ")."); return;
        }
        if (fixedCustomer != null && d.getCustomerNo() != fixedCustomer.getCustomerNo()) {
            Notify.warning(this, "Bu referans size ait değil."); return;
        }
        double rate = d.isBankSell() ? d.getCustomerSellRate() : d.getCustomerBuyRate();
        String yon = d.isBankSell() ? "Döviz ALIŞ (size döviz satılır)" : "Döviz SATIŞ (sizden döviz alınır)";
        int ans = JOptionPane.showConfirmDialog(this,
                d.getReferenceNo() + " referansı ile işlem:\n\n"
                        + yon + "\n"
                        + "Tutar: " + String.format("%,.2f %s", d.getAmount(), d.getCurrency()) + "\n"
                        + "Kur: " + String.format("%,.6f", rate) + "\n"
                        + "TRY karşılığı: " + String.format("%,.2f TRY", d.getAmount() * rate) + "\n\nOnaylıyor musunuz?",
                "Referansla İşlem", JOptionPane.YES_NO_OPTION);
        if (ans != JOptionPane.YES_OPTION) return;
        String err = fixingDAO.execute(d.getId());
        if (err != null) { Notify.warning(this, err); return; }
        Notify.info(this, "İşlem gerçekleşti (" + d.getReferenceNo() + "). Hesap bakiyeleriniz güncellendi.");
        loadData(); fillRateFromSelection(); loadHistory();
    }

    private JComponent buildHistory() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(new EmptyBorder(6, 0, 0, 0));

        JLabel title = new JLabel("İşlemlerim");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(title, BorderLayout.NORTH);

        historyTable.setRowHeight(30);
        historyTable.setFont(historyTable.getFont().deriveFont(14f));
        historyTable.getTableHeader().setFont(historyTable.getTableHeader().getFont().deriveFont(Font.BOLD, 13f));
        historyTable.setFillsViewportHeight(true);
        JScrollPane sp = new JScrollPane(historyTable);
        // Daha büyük: ~10 satır görünecek yükseklik
        sp.setPreferredSize(new Dimension(1000, 200));
        sp.setMinimumSize(new Dimension(500, 280));
        panel.add(sp, BorderLayout.CENTER);

        JButton refresh = new JButton("Yenile");
        refresh.addActionListener(e -> loadHistory());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(refresh);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private void addRow(JPanel card, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        card.add(l, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        card.add(field, gbc);
    }

    private void loadData() {
        rates = rateDAO.getAll();
        currencyCombo.removeAllItems();
        for (CurrencyRate r : rates) {
            currencyCombo.addItem(r.getCurrency());
        }
    }

    private void loadHistory() {
        if (fixedCustomer == null) return;   // geçmiş yalnızca müşteride
        historyModel.setData(spotDAO.getByCustomer(fixedCustomer.getCustomerId()));
    }

    /** Aktif müşteri: müşteri girişinde sabit, personelde açılır listedeki. */
    private Customer activeCustomer() {
        return fixedCustomer != null ? fixedCustomer : picker.getSelected();
    }

    private void fillRateFromSelection() {
        String cur = (String) currencyCombo.getSelectedItem();
        if (cur == null || rates == null) return;
        boolean isBuy = buyRadio.isSelected();

        // Güncel (market) kur
        double market = 0;
        for (CurrencyRate r : rates) {
            if (r.getCurrency().equals(cur)) { market = isBuy ? r.getBuyRate() : r.getSellRate(); break; }
        }

        // Müşteriye fixlenmiş kur var mı?
        CustomerFixedRate fx = null;
        Customer ac = activeCustomer();
        if (ac != null) fx = fixedRateDAO.get(ac.getCustomerNo(), cur);
        boolean hasFix = fx != null && fx.hasRate(isBuy);
        double fixRate = hasFix ? fx.rateFor(isBuy) : 0;

        refreshingRate = true;
        rateSourceCombo.setEnabled(hasFix);
        if (!hasFix) rateSourceCombo.setSelectedIndex(0);   // fix yoksa güncel
        refreshingRate = false;

        boolean useFix = hasFix && rateSourceCombo.getSelectedIndex() == 1;
        double rate = useFix ? fixRate : market;
        rateField.setText(String.valueOf(rate));

        if (hasFix) {
            rateInfo.setText(String.format("<html>Güncel: <b>%,.4f</b>  •  "
                    + "<font color='#1E8E3E'>Fixli: <b>%,.4f</b></font></html>", market, fixRate));
        } else {
            rateInfo.setText("Bu müşteri/döviz için fix kur tanımlı değil.");
        }
        resultLabel.setText("—");
    }

    private Double parse(String s) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }

    private void calculate() {
        Double amount = parse(amountField.getText());
        Double rate = parse(rateField.getText());
        if (amount == null || amount <= 0) { Notify.warning(this, "Geçerli bir miktar girin."); return; }
        if (rate == null || rate <= 0)     { Notify.warning(this, "Geçerli bir kur girin."); return; }
        resultLabel.setText(String.format("%,.2f ₺", amount * rate));
    }

    private void saveTrade() {
        if (!canTrade) return;

        Customer customer = activeCustomer();
        String cur = (String) currencyCombo.getSelectedItem();
        Double amount = parse(amountField.getText());
        Double rate = parse(rateField.getText());

        if (customer == null) { Notify.warning(this, "Müşteri seçin."); return; }
        if (cur == null)      { Notify.warning(this, "Döviz seçin."); return; }
        if (amount == null || amount <= 0) { Notify.warning(this, "Geçerli bir miktar girin."); return; }
        if (rate == null || rate <= 0)     { Notify.warning(this, "Geçerli bir kur girin."); return; }

        boolean isBuy = buyRadio.isSelected();
        double tryAmount = amount * rate;
        boolean fixUsed = rateSourceCombo.isEnabled() && rateSourceCombo.getSelectedIndex() == 1;

        // Hesap-tabanlı işlem: müşteri hesabı <-> banka hazine hesabı (tek transaction)
        String err = accountDAO.spotTrade(customer.getCustomerId(), cur, amount, rate, isBuy);
        if (err != null) { Notify.warning(this, err); return; }

        String yon = isBuy ? "Alış" : "Satış";
        ActivityLogDAO.log(isBuy ? "SPOT_BUY" : "SPOT_SELL",
                customer.getCustomerNo(), amount, cur,
                "Döviz " + yon + ": " + amount + " " + cur,
                "Müşteri: " + customer.getCustomerName() + " " + customer.getSurname()
                        + " (No: " + customer.getCustomerNo() + ")"
                        + " | " + (isBuy ? "TL ödendi" : "TL alındı") + ": " + String.format("%,.2f TRY", tryAmount)
                        + " | Miktar: " + amount + " " + cur
                        + " | Kur: " + rate + (fixUsed ? " (fixlenmiş kur)" : " (güncel kur)"));
        Notify.info(this, "İşlem tamamlandı.\n"
                + yon + " " + amount + " " + cur + " @ " + rate + "\n"
                + (isBuy
                    ? "Hesabınızdan " + String.format("%,.2f TRY", tryAmount) + " düşüldü, "
                        + String.format("%,.2f %s", amount, cur) + " eklendi."
                    : String.format("%,.2f %s", amount, cur) + " düşüldü, hesabınıza "
                        + String.format("%,.2f TRY", tryAmount) + " eklendi."));
        amountField.setText("");
        resultLabel.setText("—");
        loadHistory();   // geçmiş tablosunu tazele
    }

    // ---- İşlem geçmişi tablo modeli ----
    private static class HistoryTableModel extends AbstractTableModel {
        private final String[] columns =
                {"Tarih", "Müşteri No", "Müşteri", "Alınan", "Verilen", "Kur"};
        private List<SpotTransaction> data = new java.util.ArrayList<>();

        void setData(List<SpotTransaction> list) {
            this.data = list;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int c) { return columns[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            SpotTransaction t = data.get(row);
            switch (col) {
                case 0: return t.getTransactionDate();
                case 1: return t.getCustomerNo();
                case 2: return t.getCustomerName();
                case 3: return String.format("%,.2f %s", t.getBuyAmount(), t.getBuyCurrency());
                case 4: return String.format("%,.2f %s", t.getSellAmount(), t.getSellCurrency());
                case 5: return String.format("%,.4f", t.getRate());
                default: return "";
            }
        }
    }
}
