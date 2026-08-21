package com.gtech.treasury.ui;

import com.gtech.treasury.model.CustomerFXFixing;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Bir FX kur fiksasyonunun detayı: tüm alanlar + P&L + iptal/işlem bilgisi. */
public class FxFixingDetailDialog extends JDialog {

    private final CustomerFXFixing d;

    public FxFixingDetailDialog(Window owner, CustomerFXFixing d) {
        super(owner, "FX Fiksasyon — " + d.getReferenceNo(), ModalityType.APPLICATION_MODAL);
        this.d = d;

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(14, 16, 14, 16));
        root.setBackground(new Color(0xF0F2F5));
        root.add(buildSummary(), BorderLayout.CENTER);

        JButton close = new JButton("Kapat");
        close.addActionListener(e -> dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.setOpaque(false); south.add(close);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(560, 560);
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
        JLabel t = new JLabel(d.getReferenceNo() + "  •  " + d.getTypeLabel());
        t.setFont(t.getFont().deriveFont(Font.BOLD, 16f));
        g.gridx = 0; g.gridy = row++; g.gridwidth = 2; card.add(t, g); g.gridwidth = 1;

        JLabel st = new JLabel("Durum: " + d.getStatusText());
        st.setFont(st.getFont().deriveFont(Font.BOLD, 13f));
        st.setForeground("İptal".equals(d.getStatusText()) ? new Color(0xC5221F)
                : "İşlendi".equals(d.getStatusText()) ? new Color(0x2D6CDF) : new Color(0x1E8E3E));
        g.gridx = 0; g.gridy = row++; g.gridwidth = 2; card.add(st, g); g.gridwidth = 1;

        double custRate = d.getCustomerSellRate() > 0 ? d.getCustomerSellRate() : d.getCustomerBuyRate();
        row = kv(card, g, row, "Müşteri:", d.getCustomerNo() + " - " + nz(d.getCustomerName()));
        row = kv(card, g, row, "Pair:", nz(d.getPair()));
        row = kv(card, g, row, "Kur Tipi:", "EFEKTIF".equals(d.getRateType()) ? "Efektif" : "Döviz");
        row = kv(card, g, row, "Tutar:", String.format("%,.2f %s", d.getAmount(), d.getCurrency()));
        row = kv(card, g, row, "Anlık Kur (market):", fmt6(d.getMarketRate()));
        row = kv(card, g, row, "Hazine Maliyeti:", fmt6(d.getTreasuryCost()));
        row = kv(card, g, row, "Spread:", d.getSpread() > 0 ? fmt6(d.getSpread()) : "—");
        row = kv(card, g, row, "Müşteri Kuru:", fmt6(custRate));
        row = kv(card, g, row, "TRY Karşılığı:", String.format("%,.2f TRY", d.getAmount() * custRate));
        row = money(card, g, row, "Kâr / Zarar:", d.getPnl(), d.getPnlCurrency());
        row = kv(card, g, row, "Açıklama:", nz(d.getDescription()));

        g.gridx = 0; g.gridy = row++; g.gridwidth = 2; card.add(new JSeparator(), g); g.gridwidth = 1;
        row = kv(card, g, row, "Oluşturan:", nz(d.getCreatedBy()));
        row = kv(card, g, row, "Oluşturma:", dt(d.getCreatedAt()));
        if ("CANCELLED".equals(d.getStatus())) {
            row = kv(card, g, row, "İptal Kuru:", fmt6(d.getCancellationRate()));
            row = money(card, g, row, "İptal Kâr/Zarar:", d.getCancellationPnl(), d.getPnlCurrency());
            row = kv(card, g, row, "İptal Zamanı:", dt(d.getCancelledAt()));
        }
        if ("EXECUTED".equals(d.getStatus())) {
            row = kv(card, g, row, "İşleyen:", nz(d.getExecutedBy()));
            row = kv(card, g, row, "İşlem Zamanı:", dt(d.getExecutedAt()));
        }

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(card, BorderLayout.NORTH);
        return new JScrollPane(wrap);
    }

    private int kv(JPanel p, GridBagConstraints g, int row, String k, String v) {
        g.gridx = 0; g.gridy = row; JLabel l = new JLabel(k); l.setFont(l.getFont().deriveFont(Font.BOLD)); p.add(l, g);
        g.gridx = 1; p.add(new JLabel(v), g);
        return row + 1;
    }
    private int money(JPanel p, GridBagConstraints g, int row, String k, double v, String cur) {
        g.gridx = 0; g.gridy = row; JLabel l = new JLabel(k); l.setFont(l.getFont().deriveFont(Font.BOLD)); p.add(l, g);
        JLabel val = new JLabel(String.format("%+,.2f %s", v, cur == null ? "" : cur));
        val.setFont(val.getFont().deriveFont(Font.BOLD));
        val.setForeground(v > 0 ? new Color(0x1E8E3E) : v < 0 ? new Color(0xC5221F) : new Color(0x374151));
        g.gridx = 1; p.add(val, g);
        return row + 1;
    }
    private String fmt6(double v) { return String.format("%,.6f", v); }
    private String nz(String s) { return (s == null || s.isBlank() || "null".equals(s)) ? "-" : s; }
    private String dt(String s) { return (s == null || s.startsWith("null") || s.isBlank()) ? "-"
            : (s.length() >= 19 ? s.substring(0, 19) : s); }
}
