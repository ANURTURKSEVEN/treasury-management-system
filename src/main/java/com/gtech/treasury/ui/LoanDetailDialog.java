package com.gtech.treasury.ui;

import com.gtech.treasury.dao.LendingDAO;
import com.gtech.treasury.dao.LendingDAO.LoanType;
import com.gtech.treasury.model.ActivityLog;
import com.gtech.treasury.model.Installment;
import com.gtech.treasury.model.Lending;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Tek bir kredinin detay ekranı: özet + KRS/KDS + onay bilgisi + taksit planı + hareketler. */
public class LoanDetailDialog extends JDialog {

    private final LendingDAO dao;
    private final Lending l;

    public LoanDetailDialog(Window owner, Lending l, LendingDAO dao) {
        super(owner, "Kredi Detayı #" + l.getId(), ModalityType.APPLICATION_MODAL);
        this.l = l;
        this.dao = dao;

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(14, 16, 14, 16));
        root.setBackground(new Color(0xF0F2F5));

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildTabs(), BorderLayout.CENTER);

        JButton close = new JButton("Kapat");
        close.addActionListener(e -> dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.setOpaque(false);
        south.add(close);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(680, 600);
        setLocationRelativeTo(owner);
    }

    // ---- Üst: özet kartı ----
    private JComponent buildHeader() {
        List<Installment> insts = dao.getInstallments(l.getId());
        double outstanding = 0;
        String nextDue = "-";
        long daysPast = 0;
        for (Installment i : insts) {
            if (i.getStatus() == 0) {
                outstanding += i.getAmount();
                if ("-".equals(nextDue)) {
                    nextDue = i.getDueDate();
                    try {
                        LocalDate due = LocalDate.parse(i.getDueDate().substring(0, 10));
                        if (LocalDate.now().isAfter(due))
                            daysPast = ChronoUnit.DAYS.between(due, LocalDate.now());
                    } catch (Exception ignored) { }
                }
            }
        }
        String[] ks = dao.krsKds(l.getId());

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)), new EmptyBorder(14, 18, 14, 18)));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 8, 3, 8); g.anchor = GridBagConstraints.WEST;

        int row = 0;
        JLabel title = new JLabel(LoanType.labelOf(l.getLoanType()) + "  •  Kredi #" + l.getId());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
        g.gridx = 0; g.gridy = row++; g.gridwidth = 4; card.add(title, g); g.gridwidth = 1;

        JLabel st = new JLabel("Durum: " + l.getStatusText());
        st.setFont(st.getFont().deriveFont(Font.BOLD, 13f));
        st.setForeground(statusColor(l.getStatus()));
        g.gridx = 0; g.gridy = row++; g.gridwidth = 4; card.add(st, g); g.gridwidth = 1;

        // iki sütunlu anahtar/değer
        row = kv(card, g, row, 0, "Müşteri:", l.getCustomerNo() + " " + l.getCustomerName());
        kvRight(card, g, row - 1, "Hesap:", String.valueOf(l.getAccountNo()));
        row = kv(card, g, row, 0, "Anapara:", money(l.getAmount()));
        kvRight(card, g, row - 1, "Faiz / Vade:", String.format("%%%.2f  /  %d ay", l.getInterestRate(), l.getTermMonths()));
        row = kv(card, g, row, 0, "Aylık Taksit:", money(l.getMonthlyPayment()));
        kvRight(card, g, row - 1, "Toplam Geri Ödeme:", money(l.getTotalDue()));
        row = kv(card, g, row, 0, "Kalan Borç:", money(outstanding));
        kvRight(card, g, row - 1, "Sıradaki Vade:", nextDue);
        row = kv(card, g, row, 0, "Gecikme:", daysPast > 0 ? daysPast + " gün" : "yok");
        kvRight(card, g, row - 1, "KRS / KDS:", ks[0] + " (" + ks[1] + ")  •  " + ks[2]);

        // Onay / kullandırım izi
        g.gridx = 0; g.gridy = row++; g.gridwidth = 4; card.add(new JSeparator(), g); g.gridwidth = 1;
        row = kv(card, g, row, 0, "Onaylayan:", nz(l.getApprovedBy()));
        kvRight(card, g, row - 1, "Onay Tarihi:", dt(l.getApprovedAt()));
        row = kv(card, g, row, 0, "Kullandırım:", dt(l.getDisbursedAt()));
        kvRight(card, g, row - 1, "Vade Tarihi:", dt(l.getMaturityDate()));
        if (l.getStatus() == 2 && l.getRejectReason() != null) {
            row = kv(card, g, row, 0, "Red Sebebi:", l.getRejectReason());
        }
        return card;
    }

    private JComponent buildTabs() {
        JTabbedPane tabs = new JTabbedPane();

        // Taksit Planı
        DefaultTableModel im = new DefaultTableModel(
                new String[]{"Taksit", "Vade", "Tutar", "Durum", "Ödeme Tarihi"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (Installment i : dao.getInstallments(l.getId())) {
            im.addRow(new Object[]{i.getSeqNo(), i.getDueDate(), String.format("%,.2f", i.getAmount()),
                    i.getStatusText(),
                    (i.getPaidDate() == null || i.getPaidDate().startsWith("null")) ? "-" : i.getPaidDate()});
        }
        JTable it = new JTable(im); it.setRowHeight(24);
        tabs.addTab("Taksit Planı", new JScrollPane(it));

        // Hareketler
        DefaultTableModel am = new DefaultTableModel(
                new String[]{"Tarih", "İşlem", "Tutar", "Açıklama"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (ActivityLog a : dao.loanActivity(l.getId())) {
            am.addRow(new Object[]{a.getDatePart() + " " + a.getTimePart(),
                    a.getActionType(),
                    a.getAmount() > 0 ? String.format("%,.2f %s", a.getAmount(), a.getCurrency() == null ? "" : a.getCurrency()) : "-",
                    a.getDescription()});
        }
        JTable at = new JTable(am); at.setRowHeight(24);
        tabs.addTab("Hareketler", new JScrollPane(at));

        return tabs;
    }

    // ---- yardımcılar ----
    private int kv(JPanel p, GridBagConstraints g, int row, int col0, String k, String v) {
        g.gridx = col0; g.gridy = row;
        JLabel l = new JLabel(k); l.setFont(l.getFont().deriveFont(Font.BOLD)); p.add(l, g);
        g.gridx = col0 + 1; p.add(new JLabel(v), g);
        return row + 1;
    }
    private void kvRight(JPanel p, GridBagConstraints g, int row, String k, String v) {
        g.gridx = 2; g.gridy = row;
        JLabel l = new JLabel(k); l.setFont(l.getFont().deriveFont(Font.BOLD)); p.add(l, g);
        g.gridx = 3; p.add(new JLabel(v), g);
    }
    private String money(double v) { return String.format("%,.2f %s", v, l.getCurrency()); }
    private String nz(String s) { return (s == null || s.isBlank() || "null".equals(s)) ? "-" : s; }
    private String dt(String s) {
        if (s == null || s.startsWith("null") || s.isBlank()) return "-";
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }
    private Color statusColor(int st) {
        switch (st) {
            case 0: return new Color(0x6B7280);   // başvuru
            case 4: return new Color(0x2D6CDF);   // onaylandı
            case 1: return new Color(0x1E8E3E);   // aktif
            case 3: return new Color(0x374151);   // kapandı
            case 2: return new Color(0xC5221F);   // red
            default: return Color.DARK_GRAY;
        }
    }
}