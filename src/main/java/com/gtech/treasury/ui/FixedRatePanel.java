package com.gtech.treasury.ui;

import com.gtech.treasury.dao.CustomerDAO;
import com.gtech.treasury.dao.CustomerFXFixingDAO;
import com.gtech.treasury.dao.CustomerFixedRateDAO;
import com.gtech.treasury.dao.RateDAO;
import com.gtech.treasury.model.CurrencyRate;
import com.gtech.treasury.model.Customer;
import com.gtech.treasury.model.CustomerFXFixing;
import com.gtech.treasury.model.CustomerFixedRate;
import com.gtech.treasury.util.FxPricingService;
import com.gtech.treasury.util.Notify;
import com.gtech.treasury.util.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Müşteriye Özel Kur Fixleme — Treasury FX fiksasyon ekranı.
 * İşlem tipi (Banka Satış/Alış) + Kur Tipi (Döviz/Efektif) + Spread ile müşteri kuru ve P&L
 * hesaplanır; fixing bir deal olarak (customer_fx_fixing) kaydedilir ve ilgili yön mevcut
 * customer_fixed_rate'e köprülenir (SpotTradePanel bunu okumaya devam eder).
 */
public class FixedRatePanel extends JPanel {

    private static final Color OK_C = new Color(0x1E8E3E), BAD_C = new Color(0xC5221F), WARN_C = new Color(0xB45309);

    private final CustomerDAO customerDAO = new CustomerDAO();
    private final CustomerFixedRateDAO fixedDAO = new CustomerFixedRateDAO();
    private final CustomerFXFixingDAO fixingDAO = new CustomerFXFixingDAO();
    private final RateDAO rateDAO = new RateDAO();

    private Customer selected;
    private final JTextField noField = new JTextField(12);

    // İşlem tipi / kur tipi
    private final JRadioButton typeSatis = new JRadioButton("Banka Satış", true);
    private final JRadioButton typeAlis  = new JRadioButton("Banka Alış");
    private final JRadioButton typeParite = new JRadioButton("Parite");
    private final JRadioButton rateDoviz = new JRadioButton("Döviz", true);
    private final JRadioButton rateEfektif = new JRadioButton("Efektif");

    private final JComboBox<String> currencyCombo = new JComboBox<>();
    private final JLabel pairLabel = new JLabel("—");
    private final JTextField amountField = new JTextField(12);
    private final JCheckBox spreadCheck = new JCheckBox("Spread kullan");
    private final JTextField spreadField = new JTextField(8);
    private final JTextField descField = new JTextField(20);

    private final JLabel marketLabel = new JLabel(" ");
    private final JLabel updatedLabel = new JLabel(" ");

    private final JTextField buyField = new JTextField(12);    // Fix Alış (banka alış)
    private final JTextField sellField = new JTextField(12);   // Fix Satış (banka satış)
    private final JLabel buyRowLabel = new JLabel("Fix Alış Kuru:");
    private final JLabel sellRowLabel = new JLabel("Fix Satış Kuru:");

    // Maliyet / Fiyat kartı (referanstaki gibi salt-okunur kutular)
    private final JTextField cMarket = roField(), cTreasury = roField(),
            cKarZarar = roField(), cIptalKuru = roField(), cIptalPnl = roField();
    private final JLabel cMarketUpd = new JLabel("—"), cTreasuryUpd = new JLabel("—"),
            cMarketTitle = new JLabel("Anlık Kur:");
    private final JComboBox<String> pnlCurCombo = new JComboBox<>();
    private CustomerFXFixing shown;   // Maliyet/Fiyat kartında gösterilen fixing (hesap veya seçili geçmiş)

    private final FixTableModel fixModel = new FixTableModel();
    private final JTable fixTable = new JTable(fixModel);
    private final FixingTableModel histModel = new FixingTableModel();
    private final JTable histTable = new JTable(histModel);

    private CustomerFXFixing computed;   // Hesapla sonucu

    public FixedRatePanel() {
        setLayout(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(16, 20, 16, 20));
        setBackground(new Color(0xF0F2F5));

        JLabel title = new JLabel("💱  Müşteriye Özel Kur Fixleme");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        add(title, BorderLayout.NORTH);

        JScrollPane sp = new JScrollPane(buildCenter());
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getViewport().setBackground(new Color(0xF0F2F5));
        add(sp, BorderLayout.CENTER);

        typeParite.setEnabled(false);
        typeParite.setToolTipText("Parite (FX/FX) Phase 3'te eklenecek — mevcut akış FX/TRY.");
        ButtonGroup tg = new ButtonGroup(); tg.add(typeSatis); tg.add(typeAlis); tg.add(typeParite);
        ButtonGroup rg = new ButtonGroup(); rg.add(rateDoviz); rg.add(rateEfektif);

        loadCurrencies();
        loadTables();
        currencyCombo.addActionListener(e -> { updatePair(); updateMarket(); });
        typeSatis.addActionListener(e -> { updateMarket(); updateFixVisibility(); });
        typeAlis.addActionListener(e -> { updateMarket(); updateFixVisibility(); });
        rateDoviz.addActionListener(e -> updateMarket());
        rateEfektif.addActionListener(e -> updateMarket());
        spreadCheck.addActionListener(e -> spreadField.setEnabled(spreadCheck.isSelected()));
        spreadField.setEnabled(false);
        updatePair(); updateMarket(); updateFixVisibility();
    }

    /** İşlem tipine göre yalnız ilgili Fix alanını göster (Banka Satış→Fix Satış, Banka Alış→Fix Alış). */
    private void updateFixVisibility() {
        boolean sell = typeSatis.isSelected();
        sellRowLabel.setVisible(sell); sellField.setVisible(sell);
        buyRowLabel.setVisible(!sell); buyField.setVisible(!sell);
    }

    private JComponent buildCenter() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.add(buildDealCard());
        col.add(Box.createVerticalStrut(12));
        col.add(buildCostCard());
        col.add(Box.createVerticalStrut(12));
        col.add(buildActionBar());
        col.add(Box.createVerticalStrut(12));
        col.add(cardWrap("FX Fiksasyon Geçmişi  (referans + P&L; satır seçip 'Referansla İşle' / 'İptal Et')", buildHistTable()));
        return col;
    }

    // ---- İşlem Bilgileri ----
    private JComponent buildDealCard() {
        JPanel card = whiteCard();
        card.setLayout(new GridBagLayout());
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 430));
        GridBagConstraints g = gbc();
        int r = 0;

        addRow(card, g, r++, "Müşteri No", customerQueryRow());
        addRow(card, g, r++, "Açıklama:", descField);

        JPanel types = flow(); types.add(typeSatis); types.add(typeAlis); types.add(typeParite);
        addRow(card, g, r++, "İşlem Tipi:", types);
        JPanel rts = flow(); rts.add(rateDoviz); rts.add(rateEfektif);
        addRow(card, g, r++, "Kur Tipi:", rts);

        JPanel curRow = flow(); curRow.add(currencyCombo);
        curRow.add(new JLabel("   Pair:")); curRow.add(pairLabel);
        addRow(card, g, r++, "Döviz:", curRow);
        addRow(card, g, r++, "Tutar:", amountField);

        JPanel spr = flow(); spr.add(spreadCheck); spr.add(new JLabel("Değer:")); spr.add(spreadField);
        addRow(card, g, r++, "Spread:", spr);

        addRow(card, g, r++, "Anlık Kur:", marketLabel);
        updatedLabel.setFont(updatedLabel.getFont().deriveFont(11f));
        addRow(card, g, r++, "Son Güncelleme:", updatedLabel);

        buyRowLabel.setFont(buyRowLabel.getFont().deriveFont(Font.BOLD));
        sellRowLabel.setFont(sellRowLabel.getFont().deriveFont(Font.BOLD));
        g.gridx = 0; g.gridy = r; g.weightx = 0; g.fill = GridBagConstraints.NONE; card.add(buyRowLabel, g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; card.add(buyField, g); r++;
        g.gridx = 0; g.gridy = r; g.weightx = 0; g.fill = GridBagConstraints.NONE; card.add(sellRowLabel, g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; card.add(sellField, g); r++;

        JButton calc = new JButton("Hesapla");
        UITheme.stylePrimary(calc);
        calc.addActionListener(e -> doCalculate());
        g.gridx = 0; g.gridy = r++; g.gridwidth = 2; card.add(calc, g); g.gridwidth = 1;
        return card;
    }

    // ---- Maliyet / Fiyat kartı (referans düzeni) ----
    private JComponent buildCostCard() {
        JPanel card = whiteCard();
        card.setLayout(new GridBagLayout());
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        GridBagConstraints g = gbc();
        JLabel h = new JLabel("MALİYET / FİYAT");
        h.setFont(h.getFont().deriveFont(Font.BOLD, 14f));
        g.gridx = 0; g.gridy = 0; g.gridwidth = 4; card.add(h, g); g.gridwidth = 1;

        cKarZarar.setFont(cKarZarar.getFont().deriveFont(Font.BOLD, 13f));
        pnlCurCombo.addActionListener(e -> refreshPnlDisplay());

        int r = 1;
        r = costRow(card, g, r, cMarketTitle, cMarket, "Son Güncelleme:", cMarketUpd);
        r = costRow(card, g, r, new JLabel("Hazine Satış Maliyeti:"), cTreasury, "Son Güncelleme:", cTreasuryUpd);
        r = costRow(card, g, r, new JLabel("Kâr / Zarar:"), cKarZarar, null, pnlCurCombo);
        r = costRow(card, g, r, new JLabel("İptal Kuru:"), cIptalKuru, null, null);
        r = costRow(card, g, r, new JLabel("İptal Kâr / Zarar:"), cIptalPnl, null, null);
        return card;
    }

    /** label | value-field | (opsiyonel) ekLabel | ekBileşen(combo/label) satırı. */
    private int costRow(JPanel card, GridBagConstraints g, int row, JLabel label, JComponent field,
                        String extraLabel, JComponent extra) {
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        g.gridx = 0; g.gridy = row; g.weightx = 0; g.fill = GridBagConstraints.NONE; card.add(label, g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; card.add(field, g);
        if (extraLabel != null) { g.gridx = 2; g.weightx = 0; g.fill = GridBagConstraints.NONE;
            JLabel el = new JLabel(extraLabel); el.setForeground(new Color(0x6B7280)); card.add(el, g); }
        if (extra != null) { g.gridx = 3; g.weightx = 0; g.fill = GridBagConstraints.NONE; card.add(extra, g); }
        return row + 1;
    }

    private JComponent buildActionBar() {
        JPanel p = flow();
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton save = new JButton("Kuru Fixle / Kaydet");
        UITheme.stylePrimary(save);
        save.addActionListener(e -> doFix());
        JButton clear = new JButton("Temizle");
        clear.addActionListener(e -> doClear());
        p.add(save); p.add(clear);
        return p;
    }

    // ---- Tablolar ----
    private JComponent buildFixTable() {
        fixTable.setRowHeight(26);
        fixTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane sp = new JScrollPane(fixTable);
        sp.setPreferredSize(new Dimension(900, 120));
        JButton del = new JButton("Seçili Fixi Kaldır");
        del.addActionListener(e -> deleteStanding());
        JPanel wrap = new JPanel(new BorderLayout(0, 6));
        wrap.setOpaque(false);
        wrap.add(sp, BorderLayout.CENTER);
        JPanel s = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); s.setOpaque(false); s.add(del);
        wrap.add(s, BorderLayout.SOUTH);
        return wrap;
    }

    private JComponent buildHistTable() {
        histTable.setRowHeight(26);
        histTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        histTable.getColumnModel().getColumn(histModel.findColumn("Durum")).setCellRenderer(new StatusRenderer());
        histTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int r = histTable.getSelectedRow();
                if (r >= 0) showInCostCard(histModel.getAt(histTable.convertRowIndexToModel(r)));
            }
        });
        JScrollPane sp = new JScrollPane(histTable, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sp.setPreferredSize(new Dimension(900, 150));
        histTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) doDetail(); }
        });
        JButton detail = new JButton("Detay");
        detail.addActionListener(e -> doDetail());
        JButton exec = new JButton("Referansla İşle (Al/Sat)");
        UITheme.stylePrimary(exec);
        exec.addActionListener(e -> doExecute());
        JButton cancel = new JButton("İptal Et");
        cancel.addActionListener(e -> doCancel());
        JButton refresh = new JButton("Yenile");
        refresh.addActionListener(e -> loadTables());
        JPanel wrap = new JPanel(new BorderLayout(0, 6));
        wrap.setOpaque(false);
        wrap.add(sp, BorderLayout.CENTER);
        JPanel s = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); s.setOpaque(false); s.add(refresh); s.add(detail); s.add(exec); s.add(cancel);
        wrap.add(s, BorderLayout.SOUTH);
        return wrap;
    }

    // ---- Müşteri seçimi ----
    private JComponent customerQueryRow() {
        JPanel p = flow();
        noField.addActionListener(e -> searchByNo());
        p.add(com.gtech.treasury.util.CBStyle.withLookup(noField, this::openSearch));
        JButton clear = new JButton("Temizle");
        clear.addActionListener(e -> { noField.setText(""); selected = null; });
        p.add(clear);
        return p;
    }
    private void searchByNo() {
        String no = noField.getText().trim();
        if (no.isEmpty()) { openSearch(); return; }
        List<Customer> res = customerDAO.searchByCriteria(no, "", "", "", "");
        if (res.isEmpty()) Notify.warning(this, "Bu numarayla müşteri bulunamadı: " + no);
        else if (res.size() == 1) selected = res.get(0);
        else openSearch();
    }
    private void openSearch() {
        JTextField noF = new JTextField(12), nameF = new JTextField(12), surF = new JTextField(12);
        noF.setText(noField.getText().trim());
        CustSearchModel model = new CustSearchModel();
        JTable results = new JTable(model);
        results.setRowHeight(24);
        Runnable doSearch = () -> model.setData(customerDAO.searchByCriteria(noF.getText(), nameF.getText(), surF.getText(), "", ""));
        JPanel crit = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        crit.setBorder(com.gtech.treasury.util.CBStyle.criteriaBorder());
        crit.add(new JLabel("Müşteri No")); crit.add(noF);
        crit.add(new JLabel("Ad")); crit.add(nameF);
        crit.add(new JLabel("Soyad")); crit.add(surF);
        JButton s = new JButton("Sorgula"); s.addActionListener(e -> doSearch.run()); crit.add(s);
        for (JTextField f : new JTextField[]{noF, nameF, surF}) f.addActionListener(e -> doSearch.run());
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setPreferredSize(new Dimension(580, 380));
        content.add(crit, BorderLayout.NORTH);
        content.add(new JScrollPane(results), BorderLayout.CENTER);
        doSearch.run();
        results.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && results.getSelectedRow() >= 0) {
                    apply(model.getAt(results.getSelectedRow()));
                    Window w = SwingUtilities.getWindowAncestor(results); if (w != null) w.dispose();
                }
            }
        });
        int res = JOptionPane.showConfirmDialog(this, content, "Müşteri Sorgula / Seç",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION && results.getSelectedRow() >= 0) apply(model.getAt(results.getSelectedRow()));
    }
    private void apply(Customer c) {
        this.selected = c;
        noField.setText(String.valueOf(c.getCustomerNo()));
    }

    // ---- Kur / hesaplama ----
    private void loadCurrencies() {
        currencyCombo.removeAllItems();
        for (CurrencyRate cr : rateDAO.getAll()) currencyCombo.addItem(cr.getCurrency());
    }
    private void updatePair() {
        String cur = (String) currencyCombo.getSelectedItem();
        pairLabel.setText(cur == null ? "—" : cur + "/TRY");
    }
    /** Seçili tip/kur tipine göre anlık piyasa kurunu ve güncelleme/stale bilgisini gösterir. */
    private double currentMarket() {
        String cur = (String) currencyCombo.getSelectedItem();
        if (cur == null) return 0;
        CurrencyRate cr = rateDAO.getByCurrency(cur);
        if (cr == null) return 0;
        boolean sell = typeSatis.isSelected();
        if (rateEfektif.isSelected()) return sell ? cr.getEffectiveSell() : cr.getEffectiveBuy();
        return sell ? cr.getSellRate() : cr.getBuyRate();
    }
    private void updateMarket() {
        String cur = (String) currencyCombo.getSelectedItem();
        if (cur == null) { marketLabel.setText("—"); updatedLabel.setText(" "); return; }
        CurrencyRate cr = rateDAO.getByCurrency(cur);
        if (cr == null) { marketLabel.setText("—"); updatedLabel.setText(" "); return; }
        double m = currentMarket();
        marketLabel.setText(String.format("%,.6f  (%s)", m, typeSatis.isSelected() ? "satış yönü" : "alış yönü"));
        long mins = minutesSince(cr.getUpdatedAt());
        if (mins < 0) { updatedLabel.setText(cr.getUpdatedAt()); updatedLabel.setForeground(new Color(0x6B7280)); }
        else {
            updatedLabel.setText(shortTs(cr.getUpdatedAt()) + "  (" + mins + " dk önce)");
            updatedLabel.setForeground(mins > 60 ? WARN_C : new Color(0x6B7280));
        }
    }

    private void doCalculate() {
        computed = null;   // başarısız hesaplamada eski sonuç kalmasın
        if (selected == null) { Notify.warning(this, "Önce müşteri seçin."); return; }
        String cur = (String) currencyCombo.getSelectedItem();
        if (cur == null) { Notify.warning(this, "Döviz seçin."); return; }
        Double amt = parseNum(amountField.getText());
        if (amt == null || amt <= 0) { Notify.warning(this, "Geçerli bir tutar girin."); return; }
        double market = currentMarket();
        if (market <= 0) { Notify.warning(this, "Bu döviz/kur tipi için anlık kur yok."); return; }
        boolean bankSell = typeSatis.isSelected();
        // Öncelik: kullanıcının ELLE girdiği fix kuru (yön'e göre ilgili alan).
        // Yoksa spread'ten (market ± spread), o da yoksa maliyet kuru (market) kullanılır.
        Double manual = parseNum(bankSell ? sellField.getText() : buyField.getText());
        double spread, customerRate;
        if (manual != null && manual > 0) {
            customerRate = manual;
            spread = bankSell ? (customerRate - market) : (market - customerRate);   // ima edilen marj
        } else if (spreadCheck.isSelected()) {
            Double s = parseNum(spreadField.getText());
            if (s == null || s < 0) { Notify.warning(this, "Geçerli bir spread girin."); return; }
            spread = s;
            customerRate = bankSell ? market + spread : market - spread;
        } else {
            customerRate = market; spread = 0;   // ne elle kur ne spread → maliyetle fix
        }
        if (customerRate <= 0) {
            Notify.warning(this, "Müşteri kuru sıfır/negatif (" + fmt6(customerRate) + "). Girdiğiniz kuru/spread'i kontrol edin.\nAnlık kur: " + fmt6(market));
            return;
        }
        // Yönlü P&L: Banka Satış → (müşteri kuru − maliyet)×tutar ; Banka Alış → (maliyet − müşteri kuru)×tutar
        double diff = bankSell ? (customerRate - market) : (market - customerRate);
        double pnl = Math.round(diff * amt * 100.0) / 100.0;

        CustomerFXFixing d = new CustomerFXFixing();
        d.setCustomerNo(selected.getCustomerNo());
        d.setCustomerId(selected.getCustomerId());
        d.setCustomerName(selected.getCustomerName() + " " + selected.getSurname());
        d.setTransactionType(bankSell ? CustomerFXFixing.T_SATIS : CustomerFXFixing.T_ALIS);
        d.setRateType(rateEfektif.isSelected() ? "EFEKTIF" : "DOVIZ");
        d.setCurrency(cur);
        d.setPair(cur + "/TRY");
        d.setAmount(amt);
        d.setMarketRate(market);
        d.setTreasuryCost(market);
        d.setSpread(spread);
        d.setPnl(pnl);
        d.setPnlCurrency("TRY");
        d.setDescription(descField.getText().trim());
        if (bankSell) { d.setCustomerSellRate(customerRate); sellField.setText(fmt6(customerRate)); }
        else          { d.setCustomerBuyRate(customerRate);  buyField.setText(fmt6(customerRate)); }
        computed = d;
        showInCostCard(d);   // sonuçlar Maliyet/Fiyat kartında + alanlarda görünür (ayrı popup yok)
    }

    private void doFix() {
        doCalculate();                 // her zaman güncel formdan hesapla (tek tıkla fix)
        if (computed == null) return;  // hesaplama başarısızsa uyarı verilmiştir
        String err = fixingDAO.create(computed);
        if (err != null) { Notify.warning(this, err); return; }
        Notify.info(this, "Fix kaydedildi.\nReferans: " + computed.getReferenceNo()
                + "\nMüşteri Kuru: " + fmt6(computed.isBankSell() ? computed.getCustomerSellRate() : computed.getCustomerBuyRate()));
        computed = null;
        loadTables();
    }

    private void doClear() {
        selected = null; noField.setText(""); amountField.setText("");
        buyField.setText(""); sellField.setText(""); descField.setText("");
        spreadCheck.setSelected(false); spreadField.setText(""); spreadField.setEnabled(false);
        cMarket.setText(""); cTreasury.setText(""); cKarZarar.setText(""); cKarZarar.setForeground(new Color(0x374151));
        cIptalKuru.setText(""); cIptalPnl.setText(""); cIptalPnl.setForeground(new Color(0x374151));
        cMarketUpd.setText("—"); cTreasuryUpd.setText("—");
        computed = null; shown = null;
    }

    private boolean populating = false;

    /** Maliyet/Fiyat kartını verilen fixing ile doldurur (hesap sonucu veya seçili geçmiş). */
    private void showInCostCard(CustomerFXFixing d) {
        shown = d;
        cMarketTitle.setText((d.getPair() == null ? "" : d.getPair() + " ") + "Anlık Kur:");
        cMarket.setText(fmt6(d.getMarketRate()));
        cTreasury.setText(fmt6(d.getTreasuryCost()));
        CurrencyRate cr = d.getCurrency() != null ? rateDAO.getByCurrency(d.getCurrency()) : null;
        String ts = cr != null ? cr.getUpdatedAt() : null;
        cMarketUpd.setText(ts != null && ts.length() >= 19 ? ts.substring(11, 19) : "—");
        cTreasuryUpd.setText(ts != null && ts.length() >= 10 ? ts.substring(0, 10) : "—");

        populating = true;
        pnlCurCombo.removeAllItems();
        pnlCurCombo.addItem("TRY");
        if (d.getCurrency() != null && !"TRY".equals(d.getCurrency())) pnlCurCombo.addItem(d.getCurrency());
        pnlCurCombo.setSelectedItem("TRY");
        populating = false;

        if ("CANCELLED".equals(d.getStatus())) {
            cIptalKuru.setText(fmt6(d.getCancellationRate()));
            setMoney(cIptalPnl, d.getCancellationPnl());
        } else {
            cIptalKuru.setText("—"); cIptalPnl.setText("—"); cIptalPnl.setForeground(new Color(0x374151));
        }
        refreshPnlDisplay();
    }

    /** P&L'yi seçili para biriminde gösterir (TRY veya işlem dövizi — market kuruyla çevrilir). */
    private void refreshPnlDisplay() {
        if (populating || shown == null) return;
        double pnlTry = shown.getPnl();
        String sel = (String) pnlCurCombo.getSelectedItem();
        double val = pnlTry;
        if (sel != null && !"TRY".equals(sel)) {
            double m = shown.getMarketRate();
            val = m > 0 ? pnlTry / m : 0;
        }
        setMoney(cKarZarar, val);
    }

    private void setMoney(JTextField f, double v) {
        f.setText(String.format("%+,.2f", v));
        f.setForeground(v > 0 ? OK_C : v < 0 ? BAD_C : new Color(0x374151));
    }

    private void deleteStanding() {
        int row = fixTable.getSelectedRow();
        if (row < 0) { Notify.warning(this, "Kaldırmak için bir satır seçin."); return; }
        CustomerFixedRate f = fixModel.getAt(row);
        if (JOptionPane.showConfirmDialog(this, f.getCustomerNo() + " - " + f.getCurrency()
                + " standing fix kaldırılsın mı?", "Onay", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        if (fixedDAO.deactivate(f.getCustomerNo(), f.getCurrency())) loadTables();
        else Notify.error(this, "Kaldırılamadı.");
    }

    private void doDetail() {
        int row = histTable.getSelectedRow();
        if (row < 0) { Notify.warning(this, "Detay için bir fixing seçin."); return; }
        new FxFixingDetailDialog(SwingUtilities.getWindowAncestor(this), histModel.getAt(row)).setVisible(true);
    }

    private void doExecute() {
        int row = histTable.getSelectedRow();
        if (row < 0) { Notify.warning(this, "İşleme almak için bir fixing seçin."); return; }
        CustomerFXFixing d = histModel.getAt(row);
        if (!"FIXED".equals(d.getStatus())) { Notify.warning(this, "Sadece FIXED referans işleme alınabilir."); return; }
        double rate = d.isBankSell() ? d.getCustomerSellRate() : d.getCustomerBuyRate();
        String yon = d.isBankSell() ? "Banka Satış (müşteri döviz ALIR)" : "Banka Alış (müşteri döviz SATAR)";
        int ans = JOptionPane.showConfirmDialog(this,
                d.getReferenceNo() + " referansı ile işlem yapılacak:\n\n"
                        + "Müşteri: " + d.getCustomerNo() + " - " + d.getCustomerName() + "\n"
                        + yon + "\n"
                        + "Tutar: " + String.format("%,.2f %s", d.getAmount(), d.getCurrency()) + "\n"
                        + "Kur: " + fmt6(rate) + "\n"
                        + "TRY karşılığı: " + String.format("%,.2f TRY", d.getAmount() * rate) + "\n\nOnaylıyor musunuz?",
                "Referansla FX İşlemi", JOptionPane.YES_NO_OPTION);
        if (ans != JOptionPane.YES_OPTION) return;
        String err = fixingDAO.execute(d.getId());
        if (err != null) { Notify.warning(this, err); return; }
        loadTables();
        Notify.info(this, "İşlem gerçekleşti (" + d.getReferenceNo() + ").\nMüşteri ve banka hesap hareketleri oluştu.");
    }

    private void doCancel() {
        int row = histTable.getSelectedRow();
        if (row < 0) { Notify.warning(this, "İptal için bir fixing seçin."); return; }
        CustomerFXFixing d = histModel.getAt(row);
        if (!"FIXED".equals(d.getStatus())) { Notify.warning(this, "Sadece FIXED fixing iptal edilebilir."); return; }
        String def = fmt6(d.getMarketRate());
        String s = JOptionPane.showInputDialog(this, "İptal (karşı) kuru:", def);
        if (s == null) return;
        Double cr = parseNum(s);
        if (cr == null || cr <= 0) { Notify.warning(this, "Geçerli bir iptal kuru girin."); return; }
        String err = fixingDAO.cancel(d.getId(), cr);
        if (err != null) { Notify.warning(this, err); return; }
        loadTables();
        Notify.info(this, "Fixing iptal edildi (" + d.getReferenceNo() + ").");
    }

    private void loadTables() {
        histModel.setData(fixingDAO.getAll());
    }

    // ---- yardımcılar ----
    private long minutesSince(String ts) {
        LocalDateTime t = parseTs(ts);
        if (t == null) return -1;
        return ChronoUnit.MINUTES.between(t, LocalDateTime.now());
    }
    private LocalDateTime parseTs(String ts) {
        if (ts == null || ts.length() < 16 || ts.startsWith("null")) return null;
        try {
            String s = ts.substring(0, 19).replace(' ', 'T');   // yyyy-MM-ddTHH:mm:ss
            return LocalDateTime.parse(s);
        } catch (Exception e) { return null; }
    }
    private String shortTs(String ts) { return (ts != null && ts.length() >= 19) ? ts.substring(0, 19) : ts; }
    private String fmt6(double v) { return String.format("%,.6f", v); }
    private static JTextField roField() {
        JTextField t = new JTextField(12);
        t.setEditable(false);
        t.setBackground(new Color(0xF3F4F6));
        t.setHorizontalAlignment(SwingConstants.RIGHT);
        return t;
    }
    private Double parseNum(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try { return Double.parseDouble(s.trim().replace(".", "").replace(',', '.')); }
        catch (Exception e) { try { return Double.parseDouble(s.trim()); } catch (Exception e2) { return null; } }
    }

    private JPanel whiteCard() {
        JPanel c = new JPanel();
        c.setBackground(Color.WHITE);
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)), new EmptyBorder(16, 20, 16, 20)));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }
    private JComponent cardWrap(String heading, JComponent inner) {
        JPanel c = new JPanel(new BorderLayout(0, 8));
        c.setBackground(Color.WHITE);
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)), new EmptyBorder(12, 16, 12, 16)));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel h = new JLabel(heading);
        h.setFont(h.getFont().deriveFont(Font.BOLD, 14f));
        c.add(h, BorderLayout.NORTH);
        c.add(inner, BorderLayout.CENTER);
        return c;
    }
    private GridBagConstraints gbc() {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 8, 6, 8); g.anchor = GridBagConstraints.WEST; g.fill = GridBagConstraints.HORIZONTAL;
        return g;
    }
    private JPanel flow() { JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); p.setOpaque(false); return p; }
    private void addRow(JPanel card, GridBagConstraints g, int row, String label, JComponent field) {
        g.gridx = 0; g.gridy = row; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        JLabel l = new JLabel(label); l.setFont(l.getFont().deriveFont(Font.BOLD)); card.add(l, g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; card.add(field, g);
    }
    private int kv(JPanel card, GridBagConstraints g, int row, String label, JLabel val) {
        g.gridx = 0; g.gridy = row; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        JLabel l = new JLabel(label); l.setFont(l.getFont().deriveFont(Font.BOLD)); card.add(l, g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; card.add(val, g);
        return row + 1;
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

    private static class FixingTableModel extends AbstractTableModel {
        private final String[] cols = {"Referans", "Müşteri", "Tip", "Pair", "Tutar", "Market", "Müşteri Kuru", "Spread", "P&L", "Durum", "Tarih"};
        private List<CustomerFXFixing> data = new java.util.ArrayList<>();
        void setData(List<CustomerFXFixing> d) { this.data = d; fireTableDataChanged(); }
        CustomerFXFixing getAt(int r) { return data.get(r); }
        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Object getValueAt(int r, int c) {
            CustomerFXFixing d = data.get(r);
            double custRate = d.getCustomerSellRate() > 0 ? d.getCustomerSellRate() : d.getCustomerBuyRate();
            switch (cols[c]) {
                case "Referans":     return d.getReferenceNo();
                case "Müşteri":      return d.getCustomerNo() + " - " + d.getCustomerName();
                case "Tip":          return d.getTypeLabel();
                case "Pair":         return d.getPair();
                case "Tutar":        return String.format("%,.2f", d.getAmount());
                case "Market":       return String.format("%,.6f", d.getMarketRate());
                case "Müşteri Kuru": return String.format("%,.6f", custRate);
                case "Spread":       return d.getSpread() > 0 ? String.format("%,.6f", d.getSpread()) : "—";
                case "P&L":          return String.format("%+,.2f %s", d.getPnl(), d.getPnlCurrency());
                case "Durum":        return d.getStatusText();
                case "Tarih":        return d.getCreatedAt();
                default:             return "";
            }
        }
    }

    private class StatusRenderer extends javax.swing.table.DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int row, int col) {
            super.getTableCellRendererComponent(t, v, s, f, row, col);
            setFont(getFont().deriveFont(Font.BOLD));
            if (!s) {
                String st = String.valueOf(v);
                setForeground("İptal".equals(st) ? BAD_C : "İşlendi".equals(st) ? new Color(0x2D6CDF) : OK_C);
            }
            return this;
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
            switch (c) { case 0: return cu.getCustomerNo(); case 1: return cu.getCustomerName();
                case 2: return cu.getSurname(); case 3: return cu.getCustomerType(); default: return ""; }
        }
    }
}
