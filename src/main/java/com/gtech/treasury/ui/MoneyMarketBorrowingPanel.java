package com.gtech.treasury.ui;

import com.gtech.treasury.dao.AccountDAO;
import com.gtech.treasury.dao.CorrespondentBankDAO;
import com.gtech.treasury.dao.MoneyMarketBorrowingDAO;
import com.gtech.treasury.model.Account;
import com.gtech.treasury.model.CorrespondentBank;
import com.gtech.treasury.model.Customer;
import com.gtech.treasury.model.MoneyMarketBorrowing;
import com.gtech.treasury.model.MoneyMarketCharge;
import com.gtech.treasury.model.User;
import com.gtech.treasury.util.CustomerPicker;
import com.gtech.treasury.util.DatePicker;
import com.gtech.treasury.util.InterestCalculationService;
import com.gtech.treasury.util.Notify;
import com.gtech.treasury.util.Session;
import com.gtech.treasury.util.SwiftMessageService;
import com.gtech.treasury.util.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Para Piyasası Borçlanma Girişi (deal capture) — yalnız personel.
 * Bankanın bir karşı kurumdan fon almasını (borrowing) kayıt altına alır.
 * Retail vadeli mevduat ekranından (BorrowingPanel) bağımsızdır.
 */
public class MoneyMarketBorrowingPanel extends JPanel {

    private final AccountDAO accountDAO = new AccountDAO();
    private final MoneyMarketBorrowingDAO mmDAO = new MoneyMarketBorrowingDAO();
    private final User user;

    // Üst form
    private final CustomerPicker counterparty = new CustomerPicker();
    private final JComboBox<String> marketType = new JComboBox<>(new String[]{"MONEY_MARKET", "INTERBANK", "REPO", "OTHER"});
    private final JComboBox<String> purpose = new JComboBox<>(new String[]{"ALM", "LIQUIDITY", "FUNDING", "OTHER"});
    private final DatePicker dealDate = new DatePicker(LocalDate.now().toString());
    private final JTextField dealer = new JTextField(10);
    private final JTextField broker = new JTextField(14);
    private final JTextField comment = new JTextField(24);
    private final JCheckBox cbSwift = new JCheckBox("EFT/SWIFT Mesajı Gönderilsin", true);
    private final JCheckBox cbStopaj = new JCheckBox("Stopaj");
    private final JCheckBox cbMt320 = new JCheckBox("MT320 Oluştur", true);
    private final JCheckBox cbMt202 = new JCheckBox("MT202 Oluştur", true);

    // Borç alma
    private final JComboBox<String> currency = new JComboBox<>(new String[]{"TRY", "USD", "EUR", "GBP"});
    private final JTextField amount = new JTextField(14);
    private final DatePicker valueDate = new DatePicker(LocalDate.now().toString());
    private final JTextField rate = new JTextField(12);
    private final JComboBox<String> bcs = new JComboBox<>(new String[]{"BANKA", "CARİ", "SERBEST"});
    private final JComboBox<Account> receivingAcc = new JComboBox<>();
    private final JComboBox<String> dayCount = new JComboBox<>(InterestCalculationService.DAY_COUNTS);
    private final JTextField grossInterest = new JTextField(12);   // hesaplanır (read-only)

    // Geri ödeme
    private final JTextField repayAmount = new JTextField(14);     // hesaplanır (read-only)
    private final DatePicker maturityDate = new DatePicker(null);
    private final JComboBox<Account> repayAcc = new JComboBox<>();

    // Muhabir
    private final JComboBox<CorrespondentBank> corr1 = new JComboBox<>();
    private final JComboBox<CorrespondentBank> corr2 = new JComboBox<>();

    private final JLabel summary = new JLabel(" ");
    private final JTextArea swiftArea = new JTextArea();
    private MoneyMarketBorrowing computed;   // Hesapla ile üretilen son deal
    private int amendId = 0;                  // >0 ise değişiklik (amend) modu
    private String amendRef;                  // amend edilen deal referansı
    private Runnable onSaved;                 // kayıt/başarı sonrası (ör. diyaloğu kapat)

    public void setOnSaved(Runnable r) { this.onSaved = r; }

    // Masraf/komisyon kalemleri
    private final List<MoneyMarketCharge> charges = new ArrayList<>();
    private final DefaultTableModel chargeModel = new DefaultTableModel(
            new String[]{"Tip", "Tutar", "Döviz", "Ödeyen", "Açıklama"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JLabel chargeTotal = new JLabel("Banka'nın ödeyeceği toplam masraf: 0,00");

    public MoneyMarketBorrowingPanel(User user) {
        this.user = user;
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(14, 18, 14, 18));
        setBackground(new Color(0xF0F2F5));

        dealer.setText(Session.getCurrentUsername());
        dealer.setEditable(false);
        grossInterest.setEditable(false);
        repayAmount.setEditable(false);
        loadCorrespondents();
        currency.addActionListener(e -> reloadAccounts());
        reloadAccounts();

        com.gtech.treasury.util.VScrollContent content =
                new com.gtech.treasury.util.VScrollContent(new BorderLayout(0, 12));
        content.add(buildTopForm(), BorderLayout.NORTH);
        content.add(buildTabs(), BorderLayout.CENTER);

        JScrollPane sp = new JScrollPane(content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null); sp.getViewport().setOpaque(false); sp.setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        add(sp, BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);
    }

    // ---------- Üst form ----------
    private JComponent buildTopForm() {
        JPanel card = card();
        GridBagConstraints g = gbc();
        int r = 0;
        JLabel title = new JLabel("Para Piyasası Borçlanma Girişi");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        g.gridx = 0; g.gridy = r++; g.gridwidth = 4; card.add(title, g); g.gridwidth = 1;

        addRow(card, g, r++, "Karşı Kurum:", counterparty, "Piyasa Tipi:", marketType);
        addRow(card, g, r++, "Deal Tarihi:", dealDate, "Anlaşma Amacı:", purpose);
        addRow(card, g, r++, "Dealer:", dealer, "Broker (ops.):", broker);
        g.gridx = 0; g.gridy = r; g.weightx = 0; card.add(boldLabel("Yorum:"), g);
        g.gridx = 1; g.gridy = r++; g.gridwidth = 3; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; card.add(comment, g);
        g.gridwidth = 1;

        JPanel checks = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        checks.setOpaque(false);
        checks.add(cbSwift); checks.add(cbStopaj); checks.add(cbMt320); checks.add(cbMt202);
        g.gridx = 0; g.gridy = r++; g.gridwidth = 4; card.add(checks, g);
        return card;
    }

    // ---------- Sekmeler ----------
    private JComponent buildTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("TL/Döviz Borç Alma", buildBorrowTab());
        tabs.addTab("Komisyon / Masraf", buildChargesTab());
        tabs.addTab("SWIFT Mesaj", buildSwiftTab());
        return tabs;
    }

    private JComponent buildBorrowTab() {
        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));

        // BORÇLANMA
        JPanel b = card();
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints g = gbc();
        int r = 0;
        g.gridx = 0; g.gridy = r++; g.gridwidth = 4; b.add(section("BORÇLANMA BİLGİLERİ"), g); g.gridwidth = 1;
        addRow(b, g, r++, "Döviz:", currency, "Tutar:", amount);
        addRow(b, g, r++, "Valör Tarihi:", valueDate, "Faiz Oranı (%):", rate);
        addRow(b, g, r++, "Faiz Yöntemi:", dayCount, "B/C/S:", bcs);
        addRow(b, g, r++, "Alacak Hesap:", receivingAcc, "Brüt Faiz:", grossInterest);
        wrap.add(b);
        wrap.add(Box.createVerticalStrut(12));

        // GERİ ÖDEME
        JPanel p = card();
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints g2 = gbc();
        r = 0;
        g2.gridx = 0; g2.gridy = r++; g2.gridwidth = 4; p.add(section("GERİ ÖDEME BİLGİLERİ"), g2); g2.gridwidth = 1;
        addRow(p, g2, r++, "Vade Tarihi:", maturityDate, "Geri Ödeme Tutarı:", repayAmount);
        addRow(p, g2, r++, "Ödeme Hesabı:", repayAcc, "", new JLabel(" "));
        wrap.add(p);
        wrap.add(Box.createVerticalStrut(12));

        // MUHABİR
        JPanel m = card();
        m.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints g3 = gbc();
        r = 0;
        g3.gridx = 0; g3.gridy = r++; g3.gridwidth = 4; m.add(section("KARŞI KURUM MUHABİR BİLGİLERİ"), g3); g3.gridwidth = 1;
        addRow(m, g3, r++, "Muhabir 1:", corr1, "Muhabir 2:", corr2);
        wrap.add(m);
        wrap.add(Box.createVerticalStrut(10));

        // Hesapla + özet
        JButton calc = new JButton("Hesapla");
        UITheme.stylePrimary(calc);
        calc.addActionListener(e -> doCalculate());
        JPanel prev = new JPanel(new BorderLayout());
        prev.setBackground(new Color(0xF0FDF4));
        prev.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xBBF7D0)), new EmptyBorder(10, 14, 10, 14)));
        summary.setFont(summary.getFont().deriveFont(Font.BOLD, 13f));
        prev.add(summary);
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel calcWrap = new JPanel(new FlowLayout(FlowLayout.LEFT)); calcWrap.setOpaque(false); calcWrap.add(calc);
        row.add(calcWrap, BorderLayout.WEST);
        row.add(prev, BorderLayout.CENTER);
        wrap.add(row);
        return wrap;
    }

    private JComponent buildChargesTab() {
        JPanel c = card();
        c.setLayout(new BorderLayout(0, 10));

        // Ekleme formu
        JComboBox<String> typeCb = new JComboBox<>(MoneyMarketCharge.LABELS);
        JTextField amtF = new JTextField(10);
        JComboBox<String> payerCb = new JComboBox<>(new String[]{"Banka", "Karşı Kurum"});
        JTextField noteF = new JTextField(16);
        JButton add = new JButton("Ekle");
        add.addActionListener(e -> {
            Double a = parse(amtF.getText());
            if (a == null || a <= 0) { Notify.warning(this, "Geçerli bir masraf tutarı girin."); return; }
            String type = MoneyMarketCharge.TYPES[typeCb.getSelectedIndex()];
            String payer = payerCb.getSelectedIndex() == 0 ? "BANKA" : "KARSI_KURUM";
            String cur = (String) currency.getSelectedItem();
            charges.add(new MoneyMarketCharge(type, a, cur, payer, noteF.getText().trim()));
            amtF.setText(""); noteF.setText("");
            refreshCharges();
        });
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        form.setOpaque(false);
        form.add(new JLabel("Tip:")); form.add(typeCb);
        form.add(new JLabel("Tutar:")); form.add(amtF);
        form.add(new JLabel("Ödeyen:")); form.add(payerCb);
        form.add(new JLabel("Açıklama:")); form.add(noteF);
        form.add(add);
        c.add(form, BorderLayout.NORTH);

        JTable table = new JTable(chargeModel);
        table.setRowHeight(24);
        c.add(new JScrollPane(table), BorderLayout.CENTER);

        JButton del = new JButton("Seçiliyi Sil");
        del.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r < 0) { Notify.warning(this, "Silinecek kalemi seçin."); return; }
            charges.remove(r);
            refreshCharges();
        });
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        chargeTotal.setFont(chargeTotal.getFont().deriveFont(Font.BOLD));
        south.add(chargeTotal, BorderLayout.WEST);
        JPanel delWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0)); delWrap.setOpaque(false); delWrap.add(del);
        south.add(delWrap, BorderLayout.EAST);
        c.add(south, BorderLayout.SOUTH);
        return c;
    }

    /** Masraf tablosunu ve BANKA'nın ödeyeceği toplamı tazeler. */
    private void refreshCharges() {
        chargeModel.setRowCount(0);
        double bankTotal = 0;
        for (MoneyMarketCharge ch : charges) {
            chargeModel.addRow(new Object[]{ch.getTypeLabel(),
                    String.format("%,.2f", ch.getAmount()), ch.getCurrency(), ch.getPayerLabel(),
                    ch.getNote() == null ? "" : ch.getNote()});
            if ("BANKA".equals(ch.getPayer())) bankTotal += ch.getAmount();
        }
        chargeTotal.setText("Banka'nın ödeyeceği toplam masraf: " + String.format("%,.2f", bankTotal));
    }

    private JComponent buildSwiftTab() {
        JPanel c = card();
        c.setLayout(new BorderLayout(0, 8));
        JButton preview = new JButton("SWIFT Önizle (MT320 / MT202)");
        preview.addActionListener(e -> doSwiftPreview());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT)); top.setOpaque(false); top.add(preview);
        c.add(top, BorderLayout.NORTH);
        swiftArea.setEditable(false);
        swiftArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        swiftArea.setRows(16);
        c.add(new JScrollPane(swiftArea), BorderLayout.CENTER);
        return c;
    }

    private JComponent buildButtons() {
        JButton ok = new JButton("Tamam");
        UITheme.stylePrimary(ok);
        ok.addActionListener(e -> doSave());
        JButton clear = new JButton("Temizle");
        clear.addActionListener(e -> doClear());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        south.add(clear); south.add(ok);
        return south;
    }

    // ---------- İş mantığı ----------
    /** Formu okuyup bir deal nesnesi kurar; hata varsa mesaj döner (null = geçerli). */
    private String buildFromForm(MoneyMarketBorrowing d) {
        Double amt = parse(amount.getText());
        Double r = parse(rate.getText());
        if (amt == null || amt <= 0) return "Geçerli bir borçlanma tutarı girin.";
        if (r == null || r < 0) return "Geçerli bir faiz oranı girin.";
        LocalDate deal, value, mat;
        try { deal = LocalDate.parse(dealDate.getText().trim()); } catch (Exception e) { return "Deal tarihi geçersiz (yyyy-MM-dd)."; }
        try { value = LocalDate.parse(valueDate.getText().trim()); } catch (Exception e) { return "Valör tarihi geçersiz (yyyy-MM-dd)."; }
        try { mat = LocalDate.parse(maturityDate.getText().trim()); } catch (Exception e) { return "Vade tarihi geçersiz (yyyy-MM-dd)."; }
        if (value.isBefore(deal)) return "Valör tarihi, deal tarihinden önce olamaz.";
        if (!mat.isAfter(value)) return "Vade tarihi, valör tarihinden sonra olmalı.";

        Account recv = (Account) receivingAcc.getSelectedItem();
        if (recv == null) return "Alacak hesabı seçin (borçlanma dövizinde banka kasası).";
        String cur = (String) currency.getSelectedItem();
        if (!cur.equals(recv.getCurrency())) return "Alacak hesabı dövizi borçlanma dövizi ile uyuşmuyor.";
        Account rep = (Account) repayAcc.getSelectedItem();
        if (rep != null && !cur.equals(rep.getCurrency())) return "Ödeme hesabı dövizi borçlanma dövizi ile uyuşmuyor.";

        InterestCalculationService.Result res =
                InterestCalculationService.calculate(amt, r, value, mat, (String) dayCount.getSelectedItem(), cbStopaj.isSelected());

        d.setCounterpartyId(counterparty.getSelected() != null ? counterparty.getSelected().getCustomerId() : 0);
        d.setCounterpartyNo(counterparty.getSelected() != null ? counterparty.getSelected().getCustomerNo() : 0);
        d.setCounterpartyName(counterparty.getSelected() != null
                ? counterparty.getSelected().getCustomerName() + " " + counterparty.getSelected().getSurname() : "");
        d.setMarketType((String) marketType.getSelectedItem());
        d.setPurpose((String) purpose.getSelectedItem());
        d.setDealer(dealer.getText().trim());
        d.setBroker(broker.getText().trim().isEmpty() ? null : broker.getText().trim());
        d.setComment(comment.getText().trim().isEmpty() ? null : comment.getText().trim());
        d.setBcs((String) bcs.getSelectedItem());
        d.setCurrency(cur);
        d.setPrincipal(amt);
        d.setInterestRate(r);
        d.setDayCount((String) dayCount.getSelectedItem());
        d.setDealDate(deal.toString());
        d.setValueDate(value.toString());
        d.setMaturityDate(mat.toString());
        d.setInterestAmount(res.interest);
        d.setTaxAmount(res.tax);
        d.setRepaymentAmount(res.repayment);
        d.setStopaj(cbStopaj.isSelected());
        d.setReceivingAccountId(recv.getAccountId());
        d.setRepaymentAccountId(rep != null ? rep.getAccountId() : recv.getAccountId());
        CorrespondentBank c1 = (CorrespondentBank) corr1.getSelectedItem();
        CorrespondentBank c2 = (CorrespondentBank) corr2.getSelectedItem();
        d.setCorrespondent1Bic(c1 != null ? c1.getBic() : null);
        d.setCorrespondent2Bic(c2 != null ? c2.getBic() : null);
        d.setCreateSwift(cbSwift.isSelected());
        d.setCreateMt320(cbMt320.isSelected());
        d.setCreateMt202(cbMt202.isSelected());
        for (MoneyMarketCharge ch : charges) ch.setCurrency(cur);   // deal döviziyle eşitle
        d.setCharges(new ArrayList<>(charges));
        return null;
    }

    private void doCalculate() {
        MoneyMarketBorrowing d = new MoneyMarketBorrowing();
        String err = buildFromForm(d);
        if (err != null) { Notify.warning(this, err); return; }
        computed = d;
        grossInterest.setText(String.format("%,.2f", d.getInterestAmount()));
        repayAmount.setText(String.format("%,.2f", d.getRepaymentAmount()));
        long days = InterestCalculationService.days(
                LocalDate.parse(d.getValueDate()), LocalDate.parse(d.getMaturityDate()), d.getDayCount());
        summary.setText(String.format(
                "<html>BORÇLANMA: <b>%,.2f %s</b> &nbsp;|&nbsp; FAİZ: <b>%,.2f %s</b>%s &nbsp;|&nbsp; "
                + "GERİ ÖDEME: <b>%,.2f %s</b> &nbsp;|&nbsp; GÜN: <b>%d</b> (%s) &nbsp;|&nbsp; VADE: <b>%s</b></html>",
                d.getPrincipal(), d.getCurrency(), d.getInterestAmount(), d.getCurrency(),
                d.isStopaj() ? String.format(" (stopaj -%,.2f)", d.getTaxAmount()) : "",
                d.getRepaymentAmount(), d.getCurrency(), days, d.getDayCount(), d.getMaturityDate()));
    }

    private void doSwiftPreview() {
        MoneyMarketBorrowing d = new MoneyMarketBorrowing();
        String err = buildFromForm(d);
        if (err != null) { Notify.warning(this, err); return; }
        d.setReferenceNo("(önizleme)");
        StringBuilder sb = new StringBuilder();
        if (cbMt320.isSelected() || cbSwift.isSelected())
            sb.append("===== MT320 (Borçlanma Teyidi) =====\n").append(SwiftMessageService.buildMT320(d)).append("\n\n");
        if (cbMt202.isSelected() || cbSwift.isSelected())
            sb.append("===== MT202 (Geri Ödeme Transferi) =====\n").append(SwiftMessageService.buildMT202(d)).append("\n");
        if (sb.length() == 0) sb.append("SWIFT/MT320/MT202 seçili değil.");
        swiftArea.setText(sb.toString());
        swiftArea.setCaretPosition(0);
    }

    private void doSave() {
        MoneyMarketBorrowing d = new MoneyMarketBorrowing();
        String err = buildFromForm(d);
        if (err != null) { Notify.warning(this, err); return; }

        if (amendId > 0) {                       // DEĞİŞİKLİK (amend) modu
            d.setId(amendId);
            d.setReferenceNo(amendRef);
            String res = mmDAO.amend(d);
            if (res != null) { Notify.warning(this, res); return; }
            Notify.info(this, "İşlem güncellendi.\n\nReferans: " + amendRef
                    + "\nBorçlanma: " + String.format("%,.2f %s", d.getPrincipal(), d.getCurrency())
                    + "\nGeri Ödeme: " + String.format("%,.2f %s", d.getRepaymentAmount(), d.getCurrency()));
            if (onSaved != null) onSaved.run(); else doClear();
            return;
        }

        String res = mmDAO.create(d);
        if (res != null) { Notify.warning(this, res); return; }
        Notify.info(this, "Para piyasası borçlanma deal'i kaydedildi.\n\nReferans: " + d.getReferenceNo()
                + "\nBorçlanma: " + String.format("%,.2f %s", d.getPrincipal(), d.getCurrency())
                + "\nGeri Ödeme: " + String.format("%,.2f %s", d.getRepaymentAmount(), d.getCurrency())
                + "\nVade: " + d.getMaturityDate()
                + (d.isCreateSwift() || d.isCreateMt320() || d.isCreateMt202()
                    ? "\n\nSWIFT mesaj(lar)ı üretildi ve personel gelen kutusuna düştü." : ""));
        if (onSaved != null) onSaved.run(); else doClear();
    }

    private void doClear() {
        counterparty.setSelected(null);
        amount.setText(""); rate.setText(""); grossInterest.setText(""); repayAmount.setText("");
        maturityDate.setText(""); broker.setText(""); comment.setText("");
        dealDate.setText(LocalDate.now().toString());
        valueDate.setText(LocalDate.now().toString());
        summary.setText(" "); swiftArea.setText(""); computed = null;
        charges.clear(); refreshCharges();
    }

    /** Mevcut bir deal'i forma yükler ve DEĞİŞİKLİK (amend) moduna geçer. */
    public void prefill(MoneyMarketBorrowing d) {
        amendId = d.getId();
        amendRef = d.getReferenceNo();
        currency.setSelectedItem(d.getCurrency());   // reloadAccounts tetiklenir
        reloadAccounts();
        if (d.getCounterpartyNo() > 0) counterparty.selectByNo(d.getCounterpartyNo());
        marketType.setSelectedItem(d.getMarketType());
        purpose.setSelectedItem(d.getPurpose());
        dealDate.setText(d.getDealDate());
        valueDate.setText(d.getValueDate());
        maturityDate.setText(d.getMaturityDate());
        broker.setText(nz(d.getBroker()));
        comment.setText(nz(d.getComment()));
        bcs.setSelectedItem(d.getBcs());
        amount.setText(String.format("%.2f", d.getPrincipal()));
        rate.setText(String.valueOf(d.getInterestRate()));
        dayCount.setSelectedItem(d.getDayCount());
        cbStopaj.setSelected(d.isStopaj());
        cbSwift.setSelected(d.isCreateSwift());
        cbMt320.setSelected(d.isCreateMt320());
        cbMt202.setSelected(d.isCreateMt202());
        selectAccount(receivingAcc, d.getReceivingAccountId());
        selectAccount(repayAcc, d.getRepaymentAccountId());
        selectCorr(corr1, d.getCorrespondent1Bic());
        selectCorr(corr2, d.getCorrespondent2Bic());
        charges.clear();
        charges.addAll(mmDAO.getCharges(d.getId()));
        refreshCharges();
        doCalculate();
    }

    private void selectAccount(JComboBox<Account> box, int id) {
        for (int i = 0; i < box.getItemCount(); i++) {
            Account a = box.getItemAt(i);
            if (a != null && a.getAccountId() == id) { box.setSelectedIndex(i); return; }
        }
    }
    private void selectCorr(JComboBox<CorrespondentBank> box, String bic) {
        if (bic == null || bic.isBlank()) { box.setSelectedItem(null); return; }
        for (int i = 0; i < box.getItemCount(); i++) {
            CorrespondentBank c = box.getItemAt(i);
            if (c != null && bic.equals(c.getBic())) { box.setSelectedIndex(i); return; }
        }
    }
    private String nz(String s) { return s == null ? "" : s; }

    private void reloadAccounts() {
        String cur = (String) currency.getSelectedItem();
        receivingAcc.removeAllItems();
        repayAcc.removeAllItems();
        for (Account a : accountDAO.getBankAccounts()) {
            if (cur == null || cur.equals(a.getCurrency())) {
                receivingAcc.addItem(a);
                repayAcc.addItem(a);
            }
        }
        AccountRenderer ren = new AccountRenderer();
        receivingAcc.setRenderer(ren);
        repayAcc.setRenderer(ren);
    }

    private void loadCorrespondents() {
        corr1.addItem(null);   // opsiyonel: boş seçim
        corr2.addItem(null);
        for (CorrespondentBank cb : new CorrespondentBankDAO().getActive()) {
            corr1.addItem(cb); corr2.addItem(cb);
        }
        DefaultListCellRenderer ren = new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                setText(v == null ? "(seçilmedi)" : v.toString());
                return this;
            }
        };
        corr1.setRenderer(ren); corr2.setRenderer(ren);
    }

    // ---------- UI yardımcıları ----------
    private static class AccountRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
            super.getListCellRendererComponent(l, v, i, s, f);
            if (v instanceof Account) {
                Account a = (Account) v;
                setText(a.getCurrency() + " Kasası  •  " + a.getAccountNo() + "  •  " + String.format("%,.2f", a.getBalance()));
            } else setText("(hesap yok)");
            return this;
        }
    }

    private JPanel card() {
        JPanel c = new JPanel(new GridBagLayout());
        c.setBackground(Color.WHITE);
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)), new EmptyBorder(14, 18, 14, 18)));
        return c;
    }
    private GridBagConstraints gbc() {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 8, 6, 8); g.anchor = GridBagConstraints.WEST; g.fill = GridBagConstraints.HORIZONTAL;
        return g;
    }
    private JLabel boldLabel(String t) { JLabel l = new JLabel(t); l.setFont(l.getFont().deriveFont(Font.BOLD)); return l; }
    private JLabel section(String t) { JLabel l = new JLabel(t); l.setFont(l.getFont().deriveFont(Font.BOLD, 14f)); return l; }

    /** İki sütunlu satır: label1|field1  label2|field2. */
    private void addRow(JPanel card, GridBagConstraints g, int row, String l1, JComponent f1, String l2, JComponent f2) {
        g.gridy = row;
        g.gridx = 0; g.weightx = 0; g.fill = GridBagConstraints.NONE; card.add(boldLabel(l1), g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; card.add(f1, g);
        g.gridx = 2; g.weightx = 0; g.fill = GridBagConstraints.NONE; card.add(boldLabel(l2), g);
        g.gridx = 3; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; card.add(f2, g);
    }

    /** Hem "100.000,00" (binlik . / ondalık ,) hem "5.49" (ondalık .) girişini doğru okur. */
    private Double parse(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        if (t.contains(",") && t.contains(".")) t = t.replace(".", "").replace(',', '.');  // 100.000,00
        else if (t.contains(",")) t = t.replace(',', '.');                                   // 5,49
        // yalnız '.' varsa ondalık kabul edilir (dokunma)
        try { return Double.parseDouble(t); } catch (Exception e) { return null; }
    }
}
