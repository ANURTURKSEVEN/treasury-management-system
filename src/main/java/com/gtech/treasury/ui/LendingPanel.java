package com.gtech.treasury.ui;

import com.gtech.treasury.dao.AccountDAO;
import com.gtech.treasury.dao.CustomerDAO;
import com.gtech.treasury.dao.LendingDAO;
import com.gtech.treasury.dao.LendingDAO.LoanType;
import com.gtech.treasury.model.Account;
import com.gtech.treasury.model.Customer;
import com.gtech.treasury.model.Installment;
import com.gtech.treasury.model.Lending;
import com.gtech.treasury.model.User;
import com.gtech.treasury.util.Notify;
import com.gtech.treasury.util.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Kredi ekranı.
 *   - Müşteri: kredi türü seçip BAŞVURU yapar; kendi kredilerini görür.
 *   - Personel: bekleyen başvuruları DEĞERLENDİRİR (uygunluk puanı) → onaylar/reddeder;
 *     aktif kredileri geri aldırır.
 */
public class LendingPanel extends JPanel {

    private final AccountDAO accountDAO = new AccountDAO();
    private final LendingDAO lendingDAO = new LendingDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final Customer customer;
    private final boolean staffMode;

    // Başvuru formu
    private final com.gtech.treasury.util.CustomerPicker picker = new com.gtech.treasury.util.CustomerPicker();
    private final JComboBox<LoanType> typeCombo = new JComboBox<>(LoanType.values());
    private final JComboBox<Account> accountCombo = new JComboBox<>();
    private final JTextField amountField = new JTextField();
    private final JLabel rateLabel = new JLabel();
    private final JComboBox<Integer> termCombo = new JComboBox<>();
    private final JLabel previewLabel = new JLabel(" ");

    private final LoanTableModel loansModel;
    private final JTable loansTable;
    private LoanTableModel pendingModel;   // personel
    private JTable pendingTable;

    public static final String V_APPROVAL = "APPROVAL", V_ACTIVE = "ACTIVE",
            V_CLOSED = "CLOSED", V_REJECTED = "REJECTED", V_ALL = "ALL", V_APPLY = "APPLY",
            V_APPROVED = "APPROVED";   // onaylandı, kullandırım bekliyor
    private final String view;

    public LendingPanel(User staff)              { this(null, true, V_APPROVAL); }
    public LendingPanel(User staff, String view) { this(null, true, view); }
    public LendingPanel(Customer c)              { this(c, false, V_ALL); }
    public LendingPanel(Customer c, String view) { this(c, false, view); }

    private LendingPanel(Customer customer, boolean staffMode, String view) {
        this.customer = customer;
        this.staffMode = staffMode;
        this.view = view;
        this.loansModel = new LoanTableModel(staffMode);
        this.loansTable = new JTable(loansModel);

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(16, 20, 16, 20));
        setBackground(new Color(0xF0F2F5));

        // İçerik dikey kaydırılabilir panele konur (form uzun olsa da tabloya inilebilir).
        com.gtech.treasury.util.VScrollContent content =
                new com.gtech.treasury.util.VScrollContent(new BorderLayout(0, 12));
        if (staffMode && V_APPROVAL.equals(view)) {
            JPanel top = new JPanel();
            top.setOpaque(false);
            top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
            top.add(buildApplyForm());
            top.add(Box.createVerticalStrut(12));
            top.add(buildStaffTop());
            content.add(top, BorderLayout.NORTH);
        } else if (staffMode) {
            content.add(buildLoans(headingFor(view)), BorderLayout.CENTER);
        } else if (V_APPLY.equals(view)) {          // müşteri: başvuru + bekleyenler
            content.add(buildApplyForm(), BorderLayout.NORTH);
            content.add(buildLoans("Başvurularım (Onay Bekleyen)"), BorderLayout.CENTER);
        } else if (V_ACTIVE.equals(view) || V_CLOSED.equals(view) || V_REJECTED.equals(view)) {
            content.add(buildLoans(headingFor(view)), BorderLayout.CENTER);
        } else {                                    // V_ALL (tek sayfa)
            content.add(buildApplyForm(), BorderLayout.NORTH);
            content.add(buildLoans("Kredilerim"), BorderLayout.CENTER);
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

    // ============ MÜŞTERİ: başvuru formu ============
    private JComponent buildApplyForm() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)), new EmptyBorder(18, 24, 18, 24)));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(7, 8, 7, 8); g.anchor = GridBagConstraints.WEST; g.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        JLabel title = new JLabel(staffMode ? "Müşteri Adına Kredi Başvurusu" : "Kredi Başvurusu");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        g.gridx = 0; g.gridy = row++; g.gridwidth = 2; card.add(title, g); g.gridwidth = 1;

        if (staffMode) {
            picker.setOnChange(() -> { loadAccounts(); refreshPreview(); });
            addRow(card, g, row++, "Müşteri No", picker);
        }

        typeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof LoanType) setText(((LoanType) v).label);
                return this;
            }
        });
        typeCombo.addActionListener(e -> onTypeChange());
        addRow(card, g, row++, "Kredi Türü:", typeCombo);

        accountCombo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof Account) { Account a = (Account) v;
                    setText(a.getAccountNo() + "  •  " + a.getAccountType() + "  •  " + String.format("%,.2f %s", a.getBalance(), a.getCurrency())); }
                return this;
            }
        });
        addRow(card, g, row++, "Yatırılacak Hesap:", accountCombo);

        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshPreview(); }
            public void removeUpdate(DocumentEvent e) { refreshPreview(); }
            public void changedUpdate(DocumentEvent e) { refreshPreview(); }
        };
        amountField.getDocument().addDocumentListener(dl);
        termCombo.addActionListener(e -> refreshPreview());
        addRow(card, g, row++, "Anapara:", amountField);
        addRow(card, g, row++, "Yıllık Faiz:", rateLabel);
        addRow(card, g, row++, "Vade (ay):", termCombo);

        JPanel prev = new JPanel(new BorderLayout());
        prev.setBackground(new Color(0xF0FDF4));
        prev.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xBBF7D0)), new EmptyBorder(10, 14, 10, 14)));
        previewLabel.setFont(previewLabel.getFont().deriveFont(Font.BOLD, 13f));
        prev.add(previewLabel);
        g.gridx = 0; g.gridy = row++; g.gridwidth = 2; card.add(prev, g);

        JButton apply = new JButton(staffMode ? "Müşteri Adına Başvuru Yap" : "Kredi Başvurusu Yap");
        UITheme.stylePrimary(apply);
        apply.setPreferredSize(new Dimension(0, 42));
        apply.addActionListener(e -> doApply());
        g.gridy = row++; g.gridwidth = 2; card.add(apply, g); g.gridwidth = 1;

        loadAccounts();
        onTypeChange();
        return card;
    }

    private void onTypeChange() {
        LoanType t = (LoanType) typeCombo.getSelectedItem();
        if (t == null) return;
        rateLabel.setText("%" + String.format("%.2f", t.rate) + "  (sabit)");
        Integer prev = (Integer) termCombo.getSelectedItem();
        termCombo.removeAllItems();
        for (int m : new int[]{6, 12, 24, 36, 48, 60, 120}) if (m <= t.maxTerm) termCombo.addItem(m);
        termCombo.setSelectedItem(prev != null && prev <= t.maxTerm ? prev : 12);
        refreshPreview();
    }

    private void refreshPreview() {
        LoanType t = (LoanType) typeCombo.getSelectedItem();
        Account a = (Account) accountCombo.getSelectedItem();
        Double amt = parse(amountField.getText());
        Integer months = (Integer) termCombo.getSelectedItem();
        if (t == null || a == null || amt == null || amt <= 0 || months == null) {
            previewLabel.setText("Üst limit: " + (t == null ? "-" : String.format("%,.0f ₺", t.maxCap)) + "  |  Anapara ve vade girin.");
            return;
        }
        double monthly = LendingDAO.monthlyPayment(amt, t.rate, months);
        double total = LendingDAO.totalDue(amt, t.rate, months);
        previewLabel.setText(String.format(
                "<html>Aylık taksit: <b>%,.2f %s</b> &nbsp;|&nbsp; Toplam geri ödeme: <b>%,.2f %s</b> "
                + "&nbsp;|&nbsp; Üst limit: %,.0f ₺<br>"
                + "<font color='#6B7280'>Ödeme talimatı varsayılan <b>Otomatik</b>; vadesinde ödenmeyen taksite "
                + "gecikme faizi işler.</font></html>",
                monthly, a.getCurrency(), total, a.getCurrency(), t.maxCap));
    }

    private void doApply() {
        Account a = (Account) accountCombo.getSelectedItem();
        LoanType t = (LoanType) typeCombo.getSelectedItem();
        Double amt = parse(amountField.getText());
        Integer months = (Integer) termCombo.getSelectedItem();
        if (staffMode && applicant() == null) { Notify.warning(this, "Müşteri seçin."); return; }
        if (a == null) { Notify.warning(this, "Hesap seçin. (Seçili müşterinin hesabı olmayabilir.)"); return; }
        if (amt == null || amt <= 0) { Notify.warning(this, "Geçerli bir anapara girin."); return; }
        String err = lendingDAO.apply(a, t, amt, t.rate, months);
        if (err != null) { Notify.warning(this, err); return; }
        amountField.setText("");
        reloadAll();
        Notify.info(this, (staffMode ? "Müşteri adına kredi başvurusu alındı." : "Kredi başvurunuz alındı.")
                + "\n\n" + t.label + " — " + String.format("%,.2f %s / %d ay", amt, a.getCurrency(), months)
                + (staffMode ? "\nBekleyen başvurular listesinden değerlendirebilirsiniz." : "\nBanka değerlendirmesi sonrası bilgilendirileceksiniz."));
    }

    // ============ PERSONEL: bekleyen başvurular ============
    private JComponent buildStaffTop() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel t = new JLabel("Bekleyen Kredi Başvuruları");
        t.setFont(t.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(t, BorderLayout.NORTH);

        pendingModel = new LoanTableModel(true);
        pendingTable = new JTable(pendingModel);
        pendingTable.setRowHeight(26);
        pendingTable.getColumnModel().getColumn(pendingModel.findColumn("Durum")).setCellRenderer(new LoanCellRenderer(pendingModel));
        JScrollPane sp = new JScrollPane(pendingTable);
        sp.setPreferredSize(new Dimension(1000, 160));
        panel.add(sp, BorderLayout.CENTER);

        JButton eval = new JButton("Değerlendir / Onayla-Reddet");
        UITheme.stylePrimary(eval);
        eval.addActionListener(e -> doEvaluate());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(eval);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private void doEvaluate() {
        int r = pendingTable.getSelectedRow();
        if (r < 0) { Notify.warning(this, "Değerlendirmek için bir başvuru seçin."); return; }
        doEvaluate(pendingModel.getAt(pendingTable.convertRowIndexToModel(r)));
    }

    /** Gelen kutusundan çağrı: verilen id'li bekleyen başvuruyu doğrudan değerlendirir. */
    public void evaluate(int id) {
        for (Lending l : lendingDAO.getPending()) if (l.getId() == id) { doEvaluate(l); return; }
        Notify.info(this, "Bu başvuru bekleyenler arasında değil (zaten sonuçlanmış olabilir).");
    }

    private void doEvaluate(Lending l) {
        LoanType t = LoanType.of(l.getLoanType());
        LendingDAO.Evaluation ev = lendingDAO.evaluate(l.getCustomerId(), t, l.getAmount(), l.getInterestRate(), l.getTermMonths());

        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(new EmptyBorder(8, 8, 8, 8));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 8, 4, 8); g.anchor = GridBagConstraints.WEST;
        int row = 0;
        JLabel h = new JLabel("Kredi Değerlendirmesi — " + l.getCustomerNo() + " " + l.getCustomerName());
        h.setFont(h.getFont().deriveFont(Font.BOLD, 15f));
        g.gridx = 0; g.gridy = row++; g.gridwidth = 2; card.add(h, g); g.gridwidth = 1;
        row = kv(card, g, row, "Kredi Türü:", t.label);
        row = kv(card, g, row, "Talep:", String.format("%,.2f %s / %d ay", l.getAmount(), l.getCurrency(), l.getTermMonths()));
        row = kv(card, g, row, "Aylık Taksit:", String.format("%,.2f %s", l.getMonthlyPayment(), l.getCurrency()));
        row = kv(card, g, row, "Aylık Gelir (tahmini):", String.format("%,.2f ₺", ev.monthlyIncome));
        row = kv(card, g, row, "Toplam Bakiye (TL):", String.format("%,.2f ₺", ev.totalBalance));
        row = kv(card, g, row, "Uygun Üst Limit:", String.format("%,.2f ₺", ev.maxEligible));
        row = kv(card, g, row, "Risk:", ev.risk);
        JLabel note = new JLabel(ev.note);
        note.setForeground(ev.eligible ? new Color(0x1E8E3E) : new Color(0xC5221F));
        note.setFont(note.getFont().deriveFont(Font.BOLD));
        g.gridx = 0; g.gridy = row++; g.gridwidth = 2; card.add(note, g);

        // ---- KRS (Kredi Referans Sistemi) + KDS (Karar Destek Sistemi) ----
        com.gtech.treasury.util.CreditScoreService.Result cs =
                com.gtech.treasury.util.CreditScoreService.evaluate(
                        l.getCustomerId(), l.getCustomerNo(), ev, l.getAmount());
        g.gridwidth = 2;
        g.gridx = 0; g.gridy = row++; card.add(new JSeparator(), g);
        g.gridwidth = 1;

        JLabel krsH = new JLabel("KRS — Kredi Referans Sistemi");
        krsH.setFont(krsH.getFont().deriveFont(Font.BOLD, 13f));
        g.gridx = 0; g.gridy = row++; g.gridwidth = 2; card.add(krsH, g); g.gridwidth = 1;
        row = kv(card, g, row, "KRS Notu:", cs.krsScore + " / 1900   (" + cs.krsBand + ")");
        row = kv(card, g, row, "Aktif Kredi / Kalan Borç:",
                cs.activeLoans + " adet  •  " + String.format("%,.2f ₺", cs.outstanding));
        row = kv(card, g, row, "Taksit Geçmişi:",
                cs.paidInstallments + " ödendi, " + cs.overdueInstallments + " gecikmiş / "
                        + cs.totalInstallments + " toplam");

        Color kdsColor = com.gtech.treasury.util.CreditScoreService.ONAYLA.equals(cs.kdsDecision)
                ? new Color(0x1E8E3E)
                : com.gtech.treasury.util.CreditScoreService.REDDET.equals(cs.kdsDecision)
                    ? new Color(0xC5221F) : new Color(0xB45309);
        JLabel kdsH = new JLabel("KDS Kararı (öneri):  " + cs.kdsDecision);
        kdsH.setFont(kdsH.getFont().deriveFont(Font.BOLD, 14f));
        kdsH.setForeground(kdsColor);
        g.gridx = 0; g.gridy = row++; g.gridwidth = 2; card.add(kdsH, g);
        g.gridwidth = 1;

        // KDS gerekçeleri (riskli ya da iyi — her durumda neden yazılır)
        JLabel gH = new JLabel("Gerekçeler (müşteri araştırması):");
        gH.setFont(gH.getFont().deriveFont(Font.BOLD, 12f));
        g.gridx = 0; g.gridy = row++; g.gridwidth = 2; card.add(gH, g);
        StringBuilder rb = new StringBuilder();
        for (String reason : cs.reasons) rb.append(reason).append("\n");
        JTextArea reasonsArea = new JTextArea(rb.toString().trim());
        reasonsArea.setEditable(false); reasonsArea.setLineWrap(true); reasonsArea.setWrapStyleWord(true);
        reasonsArea.setBackground(new Color(0xF7F8FA));
        reasonsArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JScrollPane rsp = new JScrollPane(reasonsArea);
        rsp.setPreferredSize(new Dimension(420, 120));
        g.gridx = 0; g.gridy = row++; g.gridwidth = 2; card.add(rsp, g);
        g.gridwidth = 1;

        Object[] opts = {"Onayla", "Reddet", "Vazgeç"};
        int res = JOptionPane.showOptionDialog(this, card, "Kredi Başvurusu #" + l.getId(),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[2]);
        if (res == 0) {          // Onayla
            String overrideNote = null;
            if (!com.gtech.treasury.util.CreditScoreService.ONAYLA.equals(cs.kdsDecision)) {
                overrideNote = (String) JOptionPane.showInputDialog(this,
                        "KDS önerisi: " + cs.kdsDecision + " — sistem bu talebi doğrudan onaylamıyor.\n"
                                + "Onaya devam etmek için GEREKÇE girin (zorunlu, denetim izine kaydedilir):",
                        "KDS Override — Kredi #" + l.getId(), JOptionPane.WARNING_MESSAGE, null, null, "");
                if (overrideNote == null) return;                       // vazgeçildi
                overrideNote = overrideNote.trim();
                if (overrideNote.isEmpty()) {
                    Notify.warning(this, "Onaya devam etmek için gerekçe zorunludur.");
                    return;
                }
            }
            String err = lendingDAO.approve(l.getId(), overrideNote);
            if (err != null) { Notify.warning(this, err); return; }
            Notify.info(this, "Kredi ONAYLANDI. Para henüz kullandırılmadı.\n\n"
                    + "\"Kullandırım (Onaylanan)\" ekranından 'Kullandır' ile müşteri hesabına aktarabilirsiniz.");
            reloadAll();
        } else if (res == 1) {   // Reddet
            String reason = JOptionPane.showInputDialog(this, "Red sebebi:", "Yetersiz gelir / bakiye");
            if (reason == null) return;
            String err = lendingDAO.reject(l.getId(), reason);
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

    /** yyyy-MM-dd biçiminde tarih ister, doğrular; iptalde null. */
    private String askDate(java.awt.Component parent, String prompt, String def) {
        com.gtech.treasury.util.DatePicker dp = new com.gtech.treasury.util.DatePicker(def);
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.add(new JLabel(prompt), BorderLayout.NORTH);
        p.add(dp, BorderLayout.CENTER);
        int r = JOptionPane.showConfirmDialog(parent, p, "Tarih Seç",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return null;
        String s = dp.getText().trim();
        try { LocalDate.parse(s); return s; }
        catch (Exception e) { Notify.warning(parent, "Geçersiz tarih. Örnek: 2026-09-15"); return null; }
    }

    // ============ Ortak: kredi listesi ============
    private JComponent buildLoans(String heading) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel t = new JLabel(heading);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 15f));
        panel.add(t, BorderLayout.NORTH);
        loansTable.setRowHeight(26);
        loansTable.getColumnModel().getColumn(loansModel.findColumn("Durum")).setCellRenderer(new LoanCellRenderer(loansModel));
        loansTable.getColumnModel().getColumn(loansModel.findColumn("Gecikme")).setCellRenderer(new LoanCellRenderer(loansModel));
        loansTable.setPreferredScrollableViewportSize(new Dimension(900, 190)); // kısa; içinde kaydırılır
        panel.add(new JScrollPane(loansTable), BorderLayout.CENTER);

        // Görünüme göre hangi işlemler anlamlı?
        boolean activeView   = V_ACTIVE.equals(view) || V_ALL.equals(view);   // taksit ödeme, geri ödeme
        boolean approvedView = V_APPROVED.equals(view);                        // kullandırım
        boolean closedView   = V_CLOSED.equals(view);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        // Detay + Yenile: her ekranda
        JButton detail = new JButton("Detay");
        detail.addActionListener(e -> doShowDetail());
        JButton refresh = new JButton("Yenile");
        refresh.addActionListener(e -> reloadAll());
        south.add(detail);
        south.add(refresh);

        // Taksit planı: aktif veya kapanan kredilerde
        if (activeView || closedView) {
            JButton plan = new JButton("Taksit Planı");
            plan.addActionListener(e -> doShowInstallments());
            south.add(plan);
        }

        // Kullandırım: yalnız "Onaylanan" ekranında (personel)
        if (staffMode && approvedView) {
            JButton disburse = new JButton("Kullandır");
            disburse.addActionListener(e -> doDisburse());
            south.add(disburse);
        }

        // Aktif kredi işlemleri: taksit öde / tümünü geri öde (bankacı müşteri adına da yapabilir)
        if (activeView) {
            JButton payNext = new JButton("Taksiti Öde");
            payNext.addActionListener(e -> doPayNext());
            JButton repay = new JButton("Geri Öde (Tümü)");
            repay.addActionListener(e -> doRepay());
            south.add(payNext);
            south.add(repay);

            // Ödeme talimatı (Oto/Manuel): yalnız MÜŞTERİ kendi kredisinde
            if (!staffMode) {
                JButton autopay = new JButton("Ödeme Talimatı (Oto/Manuel)");
                autopay.addActionListener(e -> doToggleAutoPay());
                south.add(autopay);
            }
            // Toplu tahsilat (batch): yalnız personel, aktif krediler ekranı
            if (staffMode) {
                JButton collect = new JButton("Vadesi Gelenleri Tahsil Et (Batch)");
                collect.addActionListener(e -> doCollectDue());
                south.add(collect);
            }
        }

        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private Lending selectedLoan() {
        int r = loansTable.getSelectedRow();
        if (r < 0) return null;
        return loansModel.getAt(loansTable.convertRowIndexToModel(r));
    }

    private void doPayNext() {
        Lending l = selectedLoan();
        if (l == null) { Notify.warning(this, "Bir kredi seçin."); return; }
        if (l.getStatus() != 1) { Notify.warning(this, "Sadece AKTİF kredinin taksiti ödenir."); return; }
        payNextWithConfirm(l, this, null);
    }

    /** Onay + ödeme + sonuç akışı — hem ana ekrandan hem Taksit Planı penceresinden kullanılır. */
    private void payNextWithConfirm(Lending l, java.awt.Component parent, Runnable extraRefresh) {
        LendingDAO.PayPreview p = lendingDAO.previewNextInstallment(l.getId());
        if (p == null || p.none) { Notify.info(parent, "Ödenecek taksit yok."); return; }
        String cur = p.currency;

        // ---- Onay ekranı ----
        JPanel c = kvPanel();
        addKv(c, "Kredi:", "#" + l.getId() + "  " + LoanType.labelOf(l.getLoanType()));
        addKv(c, "Ödenecek Taksit:", p.seqNo + ". taksit  (vade: " + p.dueDate + ")");
        addKv(c, "Taksit Tutarı (akdi):", pmoney(p.baseAmount, cur));
        if (p.daysLate > 0) {
            addKv(c, "Gecikme:", p.daysLate + " gün");
            addKv(c, "Gecikme Faizi (ek):", pmoney(p.lateFee, cur));
        } else {
            addKv(c, "Gecikme:", "yok");
        }
        addKvBold(c, "Toplam Ödenecek:", pmoney(p.totalCharge, cur));
        addKv(c, "Ödeme Hesabı:", String.valueOf(p.accountNo));
        addKv(c, "Kalan Borç (şu an):", pmoney(p.remainingBefore, cur));
        int ans = JOptionPane.showConfirmDialog(parent, c, "Taksit Ödeme Onayı",
                JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ans != JOptionPane.YES_OPTION) return;

        double balBefore = lendingDAO.accountBalanceOf(l.getId());
        String err = lendingDAO.payNextInstallment(l.getId());
        if (err != null) { Notify.warning(parent, err); return; }
        double remAfter = lendingDAO.remainingDebt(l.getId());
        double balAfter = lendingDAO.accountBalanceOf(l.getId());
        reloadAll();
        if (extraRefresh != null) extraRefresh.run();

        // ---- Sonuç ekranı ----
        JPanel r = kvPanel();
        addKvBold(r, "Hesaptan Çekilen:", pmoney(p.totalCharge, cur));
        addKv(r, "   • Taksit (akdi):", pmoney(p.baseAmount, cur));
        addKv(r, "   • Gecikme Faizi:", p.lateFee > 0 ? pmoney(p.lateFee, cur) : "—");
        addKv(r, "Hesap Bakiyesi (önce):", pmoney(balBefore, cur));
        addKvBold(r, "Hesap Bakiyesi (sonra):", pmoney(balAfter, cur));
        addKv(r, "Kalan Borç (önce):", pmoney(p.remainingBefore, cur));
        addKvBold(r, "Kalan Borç (sonra):", pmoney(remAfter, cur));
        addKv(r, "Durum:", remAfter <= 0 ? "Kredi tamamlandı ✓" : "Ödeme alındı");
        JOptionPane.showMessageDialog(parent, r, "Ödeme Sonucu", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---- Onay/sonuç diyaloğu için basit anahtar-değer paneli ----
    private JPanel kvPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.putClientProperty("row", 0);
        p.setBorder(new EmptyBorder(4, 4, 4, 4));
        return p;
    }
    private void addKv(JPanel p, String k, String v) { addKvImpl(p, k, v, false); }
    private void addKvBold(JPanel p, String k, String v) { addKvImpl(p, k, v, true); }
    private void addKvImpl(JPanel p, String k, String v, boolean bold) {
        int row = (int) p.getClientProperty("row");
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 8, 3, 8); g.anchor = GridBagConstraints.WEST;
        g.gridx = 0; g.gridy = row;
        JLabel lk = new JLabel(k); lk.setFont(lk.getFont().deriveFont(Font.BOLD)); p.add(lk, g);
        g.gridx = 1;
        JLabel lv = new JLabel(v); if (bold) lv.setFont(lv.getFont().deriveFont(Font.BOLD, 14f)); p.add(lv, g);
        p.putClientProperty("row", row + 1);
    }
    private String pmoney(double v, String cur) { return String.format("%,.2f %s", v, cur); }

    private void doDisburse() {
        Lending l = selectedLoan();
        if (l == null) { Notify.warning(this, "Kullandırmak için bir kredi seçin."); return; }
        if (l.getStatus() != 4) { Notify.warning(this, "Sadece ONAYLANMIŞ (kullandırım bekleyen) kredi kullandırılabilir."); return; }
        int ans = JOptionPane.showConfirmDialog(this,
                "Kredi #" + l.getId() + " — " + String.format("%,.2f %s", l.getAmount(), l.getCurrency())
                        + " müşteri hesabına (" + l.getAccountNo() + ") kullandırılsın mı?\n"
                        + "Banka kasasından çıkış yapılacak ve taksit planı oluşturulacaktır.",
                "Kullandırım Onayı", JOptionPane.YES_NO_OPTION);
        if (ans != JOptionPane.YES_OPTION) return;
        String err = lendingDAO.disburse(l.getId());
        if (err != null) { Notify.warning(this, err); return; }
        reloadAll();
        Notify.info(this, "Kredi kullandırıldı ve aktifleşti. Taksit planı oluşturuldu.");
    }

    private void doCollectDue() {
        int n = lendingDAO.collectDue();
        reloadAll();
        Notify.info(this, n + " taksit tahsil edildi (vadesi bugün veya geçmiş olanlar).");
    }
    
    private void doShowDetail() {
        Lending l = selectedLoan();
        if (l == null) { Notify.warning(this, "Detayını görmek için bir kredi seçin."); return; }
        new LoanDetailDialog(SwingUtilities.getWindowAncestor(this), l, lendingDAO).setVisible(true);
    }

    private void doShowInstallments() {
        Lending l = selectedLoan();
        if (l == null) { Notify.warning(this, "Taksit planını görmek için bir kredi seçin."); return; }
        if (l.getStatus() == 0) {
            Notify.info(this, "Kredi başvurunuz henüz onaylanmadı.\nTaksit planı, kredi onaylandıktan sonra oluşturulur.");
            return;
        }
        if (l.getStatus() == 2) {
            Notify.info(this, "Bu kredi başvurusu reddedildi; taksit planı bulunmuyor.");
            return;
        }

        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this),
                "Taksit Planı — Kredi #" + l.getId(), Dialog.ModalityType.APPLICATION_MODAL);
        InstallmentTableModel im = new InstallmentTableModel();
        JTable t = new JTable(im);
        t.setRowHeight(24);
        Runnable refresh = () -> im.setData(lendingDAO.getInstallments(l.getId()));
        refresh.run();

        JButton pay = new JButton("Sonraki Taksiti Öde");
        pay.addActionListener(e -> payNextWithConfirm(l, dlg, refresh));
        JButton close = new JButton("Kapat");
        close.addActionListener(e -> dlg.dispose());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        if (staffMode) {
            JButton editDate = new JButton("Vade Tarihi Düzenle");
            editDate.addActionListener(e -> {
                int r = t.getSelectedRow();
                if (r < 0) { Notify.warning(dlg, "Bir taksit seçin."); return; }
                Installment ins = im.getAt(t.convertRowIndexToModel(r));
                if (ins.getStatus() != 0) { Notify.warning(dlg, "Sadece ödenmemiş taksitin tarihi değiştirilebilir."); return; }
                String nd = askDate(dlg, "Yeni vade tarihi (yyyy-MM-dd):", ins.getDueDate());
                if (nd == null) return;
                String err = lendingDAO.updateInstallmentDate(ins.getId(), nd);
                if (err != null) { Notify.warning(dlg, err); return; }
                refresh.run(); reloadAll();
                Notify.info(dlg, "Taksit vade tarihi güncellendi.");
            });
            south.add(editDate);
        }
        south.add(pay); south.add(close);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        content.add(new JScrollPane(t), BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        dlg.setContentPane(content);
        dlg.setSize(560, 420);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // ---- Taksit tablosu modeli ----
    private static class InstallmentTableModel extends AbstractTableModel {
        private final String[] cols = {"Taksit", "Vade", "Tutar", "Durum", "Ödeme Tarihi"};
        private List<Installment> data = new java.util.ArrayList<>();
        void setData(List<Installment> l) { this.data = l; fireTableDataChanged(); }
        Installment getAt(int r) { return data.get(r); }
        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Object getValueAt(int row, int col) {
            Installment i = data.get(row);
            switch (col) {
                case 0: return i.getSeqNo();
                case 1: return i.getDueDate();
                case 2: return String.format("%,.2f", i.getAmount());
                case 3: return i.getStatusText();
                case 4: return (i.getPaidDate() == null || i.getPaidDate().startsWith("null")) ? "-" : i.getPaidDate();
                default: return "";
            }
        }
    }

    private void doRepay() {
        int r = loansTable.getSelectedRow();
        if (r < 0) { Notify.warning(this, "Bir kredi seçin."); return; }
        Lending l = loansModel.getAt(loansTable.convertRowIndexToModel(r));
        if (l.getStatus() != 1) { Notify.warning(this, "Sadece AKTİF kredi geri ödenebilir."); return; }
        int ans = JOptionPane.showConfirmDialog(this,
                "Kredi #" + l.getId() + " için toplam " + String.format("%,.2f %s", l.getTotalDue(), l.getCurrency())
                        + " geri ödensin mi? (" + l.getAccountNo() + " hesabından)", "Onay", JOptionPane.YES_NO_OPTION);
        if (ans != JOptionPane.YES_OPTION) return;
        String err = lendingDAO.repay(l.getId());
        if (err != null) { Notify.warning(this, err); return; }
        reloadAll();
        Notify.info(this, "Kredi geri ödendi ve kapatıldı.");
    }

    private void doToggleAutoPay() {
        Lending l = selectedLoan();
        if (l == null) { Notify.warning(this, "Bir kredi seçin."); return; }
        if (l.getStatus() != 1) { Notify.warning(this, "Ödeme talimatı sadece AKTİF kredide değiştirilebilir."); return; }
        boolean currentlyAuto = l.isAutoPay();
        String[] opts = {"Otomatik", "Manuel", "Vazgeç"};
        int res = JOptionPane.showOptionDialog(this,
                "Kredi #" + l.getId() + " ödeme talimatı\n\n"
                        + "Otomatik: taksitler vadesinde hesabınızdan otomatik tahsil edilir.\n"
                        + "Manuel: taksitleri 'Taksiti Öde' ile kendiniz ödersiniz.\n\n"
                        + "Şu an: " + (currentlyAuto ? "Otomatik" : "Manuel"),
                "Ödeme Talimatı", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[currentlyAuto ? 0 : 1]);
        if (res != 0 && res != 1) return;
        boolean auto = (res == 0);
        String err = lendingDAO.setAutoPay(l.getId(), auto);
        if (err != null) { Notify.warning(this, err); return; }
        reloadAll();
        Notify.info(this, "Ödeme talimatı güncellendi: " + (auto ? "Otomatik" : "Manuel"));
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
                if (pendingModel != null) pendingModel.setData(lendingDAO.getPending());
            } else {
                int st = statusFor(view);
                java.util.List<Lending> filtered = new java.util.ArrayList<>();
                for (Lending l : lendingDAO.getAll()) if (l.getStatus() == st) filtered.add(l);
                loansModel.setData(filtered);
            }
        } else {
            loadAccounts();
            java.util.List<Lending> mine = customer == null ? java.util.Collections.emptyList()
                    : lendingDAO.getByCustomer(customer.getCustomerId());
            if (V_APPLY.equals(view)) {
                java.util.List<Lending> f = new java.util.ArrayList<>();
                for (Lending l : mine) if (l.getStatus() == 0 || l.getStatus() == 4) f.add(l);   // başvuru + onaylandı(kullandırım bekliyor)
                loansModel.setData(f);
            } else if (V_ACTIVE.equals(view) || V_CLOSED.equals(view) || V_REJECTED.equals(view)) {
                int st = statusFor(view);
                java.util.List<Lending> f = new java.util.ArrayList<>();
                for (Lending l : mine) if (l.getStatus() == st) f.add(l);
                loansModel.setData(f);
            } else {
                loansModel.setData(mine);
            }
        }
    }

    /** Görünüm -> kredi durumu (1 aktif, 2 red, 3 kapandı). */
    private int statusFor(String v) {
        if (V_ACTIVE.equals(v)) return 1;
        if (V_REJECTED.equals(v)) return 2;
        if (V_CLOSED.equals(v)) return 3;
        if (V_APPROVED.equals(v)) return 4;
        return 1;
    }

    private String headingFor(String v) {
        if (V_ACTIVE.equals(v)) return "Aktif Krediler";
        if (V_REJECTED.equals(v)) return "Reddedilen Krediler";
        if (V_CLOSED.equals(v)) return "Ödenen / Kapanan Krediler";
        if (V_APPROVED.equals(v)) return "Onaylanan Krediler (Kullandırım Bekliyor)";
        return "Krediler";
    }

    private Double parse(String s) {
        try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (Exception e) { return null; }
    }

    private void addRow(JPanel card, GridBagConstraints g, int row, String label, JComponent field) {
        g.gridx = 0; g.gridy = row; g.gridwidth = 1; g.weightx = 0;
        JLabel l = new JLabel(label); l.setFont(l.getFont().deriveFont(Font.BOLD)); card.add(l, g);
        g.gridx = 1; g.weightx = 1; card.add(field, g);
    }

    // ---- Tablo modeli ----
    private static class LoanTableModel extends AbstractTableModel {
        private final boolean showCustomer;
        private final String[] cols;
        private final LendingDAO dao = new LendingDAO();
        private List<Lending> data = new java.util.ArrayList<>();
        // id -> {Double kalan (null=ilgisiz), String sıradakiVade, String gecikme}
        private final java.util.Map<Integer, Object[]> extras = new java.util.HashMap<>();

        LoanTableModel(boolean showCustomer) {
            this.showCustomer = showCustomer;
            this.cols = showCustomer
                    ? new String[]{"#", "Müşteri", "Tür", "Talep", "Kalan Borç", "Aylık", "Sıradaki Vade", "Gecikme", "Durum"}
                    : new String[]{"#", "Tür", "Talep", "Kalan Borç", "Aylık", "Sıradaki Vade", "Gecikme", "Durum"};
        }
        void setData(List<Lending> list) {
            this.data = list;
            extras.clear();
            for (Lending l : list) extras.put(l.getId(), computeExtras(l));
            fireTableDataChanged();
        }
        Lending getAt(int row) { return data.get(row); }

        /** Aktif kredide kalan borç / sıradaki vade / gecikme; değilse "-". */
        private Object[] computeExtras(Lending l) {
            if (l.getStatus() != 1) return new Object[]{null, "-", "-", "-"};
            double outstanding = 0; String nextDue = "-"; String gecikme = "yok";
            boolean overdue = false; int paid = 0;
            for (Installment i : dao.getInstallments(l.getId())) {
                if (i.getStatus() == 1) { paid++; continue; }
                outstanding += i.getAmount();
                if ("-".equals(nextDue)) {
                    nextDue = i.getDueDate();
                    try {
                        LocalDate due = LocalDate.parse(i.getDueDate().substring(0, 10));
                        if (LocalDate.now().isAfter(due)) {
                            overdue = true;
                            gecikme = java.time.temporal.ChronoUnit.DAYS.between(due, LocalDate.now()) + " gün";
                        }
                    } catch (Exception ignored) { }
                }
            }
            String disp = overdue ? "Gecikmiş" : (paid > 0 ? "Kısmen Ödendi" : "Aktif");
            return new Object[]{outstanding, nextDue, gecikme, disp};
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Object getValueAt(int row, int col) {
            Lending l = data.get(row);
            Object[] ex = extras.getOrDefault(l.getId(), new Object[]{null, "-", "-", "-"});
            switch (cols[col]) {
                case "#":            return l.getId();
                case "Müşteri":      return l.getCustomerNo() + " - " + l.getCustomerName();
                case "Tür":          return LoanType.labelOf(l.getLoanType());
                case "Talep":        return String.format("%,.2f %s", l.getAmount(), l.getCurrency());
                case "Kalan Borç":   return ex[0] == null ? "-" : String.format("%,.2f %s", (Double) ex[0], l.getCurrency());
                case "Aylık":        return String.format("%,.2f", l.getMonthlyPayment());
                case "Sıradaki Vade":return ex[1];
                case "Gecikme":      return ex[2];
                case "Durum":        return l.getStatus() == 1 ? (String) ex[3] : l.getStatusText();
                default:             return "";
            }
        }
    }

    /** Durum'u statüye göre, Gecikme'yi (varsa) kırmızı renklendiren hücre çizici. */
    private static class LoanCellRenderer extends DefaultTableCellRenderer {
        private final LoanTableModel m;
        LoanCellRenderer(LoanTableModel m) { this.m = m; }
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int row, int col) {
            super.getTableCellRendererComponent(t, v, s, f, row, col);
            setFont(getFont().deriveFont(Font.PLAIN));
            if (!s) setForeground(Color.BLACK);
            String colName = t.getColumnName(col);
            Lending l = m.getAt(t.convertRowIndexToModel(row));
            if ("Durum".equals(colName)) {
                Color c;
                if (l.getStatus() == 1) {
                    String txt = String.valueOf(v);
                    c = "Gecikmiş".equals(txt)      ? new Color(0xC5221F)   // kırmızı
                      : "Kısmen Ödendi".equals(txt) ? new Color(0xB45309)   // amber
                      :                                new Color(0x1E8E3E);  // yeşil (Aktif)
                } else {
                    c = statusColor(l.getStatus());
                }
                if (!s) setForeground(c);
                setFont(getFont().deriveFont(Font.BOLD));
            } else if ("Gecikme".equals(colName)) {
                boolean overdue = v != null && !"yok".equals(v) && !"-".equals(v);
                if (overdue) {
                    if (!s) setForeground(new Color(0xC5221F));
                    setFont(getFont().deriveFont(Font.BOLD));
                }
            }
            return this;
        }
        static Color statusColor(int st) {
            switch (st) {
                case 0: return new Color(0x6B7280);  // başvuru
                case 4: return new Color(0x2D6CDF);  // onaylandı
                case 1: return new Color(0x1E8E3E);  // aktif
                case 3: return new Color(0x374151);  // kapandı
                case 2: return new Color(0xC5221F);  // red
                default: return Color.DARK_GRAY;
            }
        }
    }
}