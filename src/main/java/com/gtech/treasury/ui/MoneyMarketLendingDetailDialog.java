package com.gtech.treasury.ui;

import com.gtech.treasury.dao.MoneyMarketLendingDAO;
import com.gtech.treasury.model.MoneyMarketLending;
import com.gtech.treasury.model.MoneyMarketLendingCharge;
import com.gtech.treasury.util.SwiftMessageService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/** Bir para piyasası PLASMAN deal'inin detayı: özet + masraflar + SWIFT (MT320/MT202). */
public class MoneyMarketLendingDetailDialog extends JDialog {

    private final MoneyMarketLending d;

    public MoneyMarketLendingDetailDialog(Window owner, MoneyMarketLending d) {
        super(owner, "Para Piyasası Plasman — " + d.getReferenceNo(), ModalityType.APPLICATION_MODAL);
        this.d = d;

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(14, 16, 14, 16));
        root.setBackground(new Color(0xF0F2F5));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Özet", buildSummary());
        tabs.addTab("Masraflar", buildCharges());
        tabs.addTab("SWIFT", buildSwift());
        root.add(tabs, BorderLayout.CENTER);

        JButton close = new JButton("Kapat");
        close.addActionListener(e -> dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.setOpaque(false); south.add(close);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(700, 580);
        setLocationRelativeTo(owner);
    }

    private JComponent buildSummary() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)), new EmptyBorder(14, 18, 14, 18)));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 8, 3, 8); g.anchor = GridBagConstraints.WEST;

        int row = 0;
        JLabel t = new JLabel(d.getReferenceNo() + "  •  " + d.getMarketType());
        t.setFont(t.getFont().deriveFont(Font.BOLD, 16f));
        g.gridx = 0; g.gridy = row++; g.gridwidth = 4; card.add(t, g); g.gridwidth = 1;

        JLabel st = new JLabel("Durum: " + d.getStatusText());
        st.setFont(st.getFont().deriveFont(Font.BOLD, 13f));
        st.setForeground("ACTIVE".equals(d.getStatus()) ? new Color(0x1E8E3E)
                : ("CANCELLED".equals(d.getStatus()) || "EARLY_CLOSED".equals(d.getStatus())) ? new Color(0xC5221F)
                : new Color(0x374151));
        g.gridx = 0; g.gridy = row++; g.gridwidth = 4; card.add(st, g); g.gridwidth = 1;

        row = kv(card, g, row, "Karşı Kurum:", d.getCounterpartyNo() > 0
                ? d.getCounterpartyNo() + " " + d.getCounterpartyName() : "-");
        kvR(card, g, row - 1, "Amaç:", nz(d.getPurpose()));
        row = kv(card, g, row, "Döviz:", d.getCurrency());
        kvR(card, g, row - 1, "Anapara:", money(d.getPrincipal()));
        row = kv(card, g, row, "Faiz Oranı:", String.format("%%%.6f", d.getInterestRate()));
        kvR(card, g, row - 1, "Faiz Yöntemi:", nz(d.getDayCount()));
        row = kv(card, g, row, "Brüt Faiz:", money(d.getInterestAmount()));
        kvR(card, g, row - 1, "Stopaj:", d.isStopaj() ? money(d.getTaxAmount()) : "-");
        row = kv(card, g, row, "Geri Ödeme (tahsil):", money(d.getRepaymentAmount()));
        kvR(card, g, row - 1, "B/C/S:", nz(d.getBcs()));
        row = kv(card, g, row, "Deal Tarihi:", nz(d.getDealDate()));
        kvR(card, g, row - 1, "Valör:", nz(d.getValueDate()));
        row = kv(card, g, row, "Vade:", nz(d.getMaturityDate()));
        kvR(card, g, row - 1, "Dealer:", nz(d.getDealer()));
        row = kv(card, g, row, "Broker:", nz(d.getBroker()));
        kvR(card, g, row - 1, "Kayıt:", nz(d.getCreatedAt()));
        row = kv(card, g, row, "Muhabir 1:", nz(d.getCorrespondent1Bic()));
        kvR(card, g, row - 1, "Muhabir 2:", nz(d.getCorrespondent2Bic()));
        if (d.getParentDealId() > 0)
            row = kv(card, g, row, "Kaynak Deal (rollover):", "#" + d.getParentDealId());
        if (d.getRolledToId() > 0)
            row = kv(card, g, row, "Devredilen Deal:", "#" + d.getRolledToId());
        if (d.getPenaltyAmount() != null)
            row = kv(card, g, row, "Erken Kapama Penalty:", money(d.getPenaltyAmount()));
        if (d.getEarlyClosedAt() != null && !d.getEarlyClosedAt().startsWith("null"))
            row = kv(card, g, row, "Erken Kapanış:", d.getEarlyClosedAt());
        if (d.getComment() != null && !d.getComment().isBlank())
            row = kv(card, g, row, "Yorum:", d.getComment());
        if (d.getMaturedAt() != null && !d.getMaturedAt().startsWith("null"))
            row = kv(card, g, row, "Kapanış:", d.getMaturedAt());

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(card, BorderLayout.NORTH);
        return new JScrollPane(wrap);
    }

    private JComponent buildCharges() {
        DefaultTableModel m = new DefaultTableModel(
                new String[]{"Tip", "Tutar", "Döviz", "Ödeyen", "Açıklama"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        double bankTotal = 0;
        for (MoneyMarketLendingCharge ch : new MoneyMarketLendingDAO().getCharges(d.getId())) {
            m.addRow(new Object[]{ch.getTypeLabel(), String.format("%,.2f", ch.getAmount()),
                    ch.getCurrency(), ch.getPayerLabel(), ch.getNote() == null ? "" : ch.getNote()});
            if ("BANKA".equals(ch.getPayer())) bankTotal += ch.getAmount();
        }
        JTable t = new JTable(m); t.setRowHeight(24);
        JPanel p = new JPanel(new BorderLayout(0, 6));
        JLabel tot = new JLabel("Banka'nın ödediği toplam masraf: " + String.format("%,.2f %s", bankTotal, d.getCurrency()));
        tot.setFont(tot.getFont().deriveFont(Font.BOLD));
        tot.setBorder(new EmptyBorder(6, 4, 6, 4));
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        p.add(tot, BorderLayout.SOUTH);
        return p;
    }

    private JComponent buildSwift() {
        JTextArea ta = new JTextArea();
        ta.setEditable(false);
        ta.setFont(new Font("Consolas", Font.PLAIN, 12));
        StringBuilder sb = new StringBuilder();
        sb.append("===== MT320 (Plasman Teyidi) =====\n").append(SwiftMessageService.buildMT320(d)).append("\n\n");
        sb.append("===== MT202 (Tahsil / Transfer) =====\n").append(SwiftMessageService.buildMT202(d)).append("\n");
        ta.setText(sb.toString());
        ta.setCaretPosition(0);
        return new JScrollPane(ta);
    }

    private int kv(JPanel p, GridBagConstraints g, int row, String k, String v) {
        g.gridx = 0; g.gridy = row;
        JLabel l = new JLabel(k); l.setFont(l.getFont().deriveFont(Font.BOLD)); p.add(l, g);
        g.gridx = 1; p.add(new JLabel(v), g);
        return row + 1;
    }
    private void kvR(JPanel p, GridBagConstraints g, int row, String k, String v) {
        g.gridx = 2; g.gridy = row;
        JLabel l = new JLabel(k); l.setFont(l.getFont().deriveFont(Font.BOLD)); p.add(l, g);
        g.gridx = 3; p.add(new JLabel(v), g);
    }
    private String money(double v) { return String.format("%,.2f %s", v, d.getCurrency()); }
    private String nz(String s) { return (s == null || s.isBlank() || "null".equals(s)) ? "-" : s; }
}
