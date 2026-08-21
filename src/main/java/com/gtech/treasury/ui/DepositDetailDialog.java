package com.gtech.treasury.ui;

import com.gtech.treasury.dao.BorrowingDAO;
import com.gtech.treasury.model.ActivityLog;
import com.gtech.treasury.model.Deposit;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Tek bir vadeli mevduatın detayı: özet (brüt faiz / stopaj / net getiri / kalan gün) + hareketler. */
public class DepositDetailDialog extends JDialog {

    private final BorrowingDAO dao;
    private final Deposit d;

    public DepositDetailDialog(Window owner, Deposit d, BorrowingDAO dao) {
        super(owner, "Mevduat Detayı #" + d.getId(), ModalityType.APPLICATION_MODAL);
        this.d = d;
        this.dao = dao;

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(14, 16, 14, 16));
        root.setBackground(new Color(0xF0F2F5));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildActivity(), BorderLayout.CENTER);

        JButton close = new JButton("Kapat");
        close.addActionListener(e -> dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.setOpaque(false);
        south.add(close);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(660, 540);
        setLocationRelativeTo(owner);
    }

    private JComponent buildHeader() {
        double gross = d.getInterestAmount();
        double taxRate = BorrowingDAO.depositTaxRate(d.getTermMonths());
        double tax = Math.round(gross * taxRate * 100.0) / 100.0;
        double net = Math.round((d.getAmount() + gross - tax) * 100.0) / 100.0;

        String kalanGun = "-";
        if (d.getStatus() == 1) {
            try {
                LocalDate m = LocalDate.parse(d.getMaturityDate().substring(0, 10));
                long g = ChronoUnit.DAYS.between(LocalDate.now(), m);
                kalanGun = g >= 0 ? g + " gün" : "vadesi doldu";
            } catch (Exception ignored) { }
        }

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)), new EmptyBorder(14, 18, 14, 18)));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 8, 3, 8); g.anchor = GridBagConstraints.WEST;

        int row = 0;
        JLabel title = new JLabel("Vadeli Mevduat  •  #" + d.getId() + "  (" + d.getContractLabel() + ")");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
        g.gridx = 0; g.gridy = row++; g.gridwidth = 4; card.add(title, g); g.gridwidth = 1;

        JLabel st = new JLabel("Durum: " + d.getStatusText());
        st.setFont(st.getFont().deriveFont(Font.BOLD, 13f));
        st.setForeground(statusColor(d));
        g.gridx = 0; g.gridy = row++; g.gridwidth = 4; card.add(st, g); g.gridwidth = 1;

        row = kv(card, g, row, "Müşteri:", d.getCustomerNo() + " " + d.getCustomerName());
        kvRight(card, g, row - 1, "Hesap:", String.valueOf(d.getAccountNo()));
        row = kv(card, g, row, "Anapara:", money(d.getAmount()));
        kvRight(card, g, row - 1, "Faiz / Vade:", String.format("%%%.0f  /  %d ay", d.getInterestRate(), d.getTermMonths()));
        row = kv(card, g, row, "Brüt Faiz:", money(gross));
        kvRight(card, g, row - 1, "Stopaj (%" + String.format("%.0f", taxRate * 100) + "):", "-" + money(tax));
        row = kv(card, g, row, "Net Getiri:", money(net));
        kvRight(card, g, row - 1, "Kalan Gün:", kalanGun);
        row = kv(card, g, row, "Başlangıç:", dt(d.getStartDate()));
        kvRight(card, g, row - 1, "Vade Tarihi:", dt(d.getMaturityDate()));
        row = kv(card, g, row, "Onaylayan:", nz(d.getApprovedBy()));
        kvRight(card, g, row - 1, "Onay Tarihi:", dt(d.getApprovedAt()));
        if (d.getStatus() == 0 && d.getCloseType() != null) {
            row = kv(card, g, row, "Kapanış:", "ERKEN".equals(d.getCloseType()) ? "Erken Bozuldu" : "Vade Sonu");
        }
        return card;
    }

    private JComponent buildActivity() {
        DefaultTableModel am = new DefaultTableModel(
                new String[]{"Tarih", "İşlem", "Tutar", "Açıklama"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (ActivityLog a : dao.depositActivity(d.getId())) {
            am.addRow(new Object[]{a.getDatePart() + " " + a.getTimePart(), a.getActionType(),
                    a.getAmount() > 0 ? String.format("%,.2f %s", a.getAmount(), a.getCurrency() == null ? "" : a.getCurrency()) : "-",
                    a.getDescription()});
        }
        JTable t = new JTable(am); t.setRowHeight(24);
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        JLabel h = new JLabel("Hareketler");
        h.setFont(h.getFont().deriveFont(Font.BOLD, 14f));
        p.add(h, BorderLayout.NORTH);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        return p;
    }

    // ---- yardımcılar ----
    private int kv(JPanel p, GridBagConstraints g, int row, String k, String v) {
        g.gridx = 0; g.gridy = row;
        JLabel l = new JLabel(k); l.setFont(l.getFont().deriveFont(Font.BOLD)); p.add(l, g);
        g.gridx = 1; p.add(new JLabel(v), g);
        return row + 1;
    }
    private void kvRight(JPanel p, GridBagConstraints g, int row, String k, String v) {
        g.gridx = 2; g.gridy = row;
        JLabel l = new JLabel(k); l.setFont(l.getFont().deriveFont(Font.BOLD)); p.add(l, g);
        g.gridx = 3; p.add(new JLabel(v), g);
    }
    private String money(double v) { return String.format("%,.2f %s", v, d.getCurrency()); }
    private String dt(String s) {
        if (s == null || s.startsWith("null") || s.isBlank()) return "-";
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }
    private String nz(String s) { return (s == null || s.isBlank() || "null".equals(s)) ? "-" : s; }
    private Color statusColor(Deposit dep) {
        if (dep.getStatus() == 2) return new Color(0x6B7280);   // onay bekliyor
        if (dep.getStatus() == 3) return new Color(0xC5221F);   // red
        if (dep.getStatus() == 1) return new Color(0x1E8E3E);   // aktif
        return new Color(0x374151);                              // kapandı
    }
}
