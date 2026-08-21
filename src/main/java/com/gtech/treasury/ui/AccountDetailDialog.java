package com.gtech.treasury.ui;

import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.model.Account;
import com.gtech.treasury.model.ActivityLog;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/** Bir hesabın detayı: sahip + bakiye + açılış + durum + hesap hareketleri (personel görünümü). */
public class AccountDetailDialog extends JDialog {

    private final Account a;

    public AccountDetailDialog(Window owner, Account a) {
        super(owner, "Hesap Detayı — " + a.getAccountNo(), ModalityType.APPLICATION_MODAL);
        this.a = a;

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(14, 16, 14, 16));
        root.setBackground(new Color(0xF0F2F5));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildMovements(), BorderLayout.CENTER);

        JButton close = new JButton("Kapat");
        close.addActionListener(e -> dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.setOpaque(false); south.add(close);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(640, 540);
        setLocationRelativeTo(owner);
    }

    private JComponent buildHeader() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)), new EmptyBorder(14, 18, 14, 18)));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 8, 3, 8); g.anchor = GridBagConstraints.WEST;

        int row = 0;
        JLabel bal = new JLabel(String.format("%,.2f %s", a.getBalance(), a.getCurrency()));
        bal.setFont(bal.getFont().deriveFont(Font.BOLD, 24f));
        bal.setForeground(a.getBalance() < 0 ? new Color(0xC5221F) : new Color(0x1E8E3E));
        g.gridx = 0; g.gridy = row++; g.gridwidth = 2; card.add(bal, g); g.gridwidth = 1;

        row = kv(card, g, row, "Hesap No:", String.valueOf(a.getAccountNo()));
        row = kv(card, g, row, "Sahibi:", a.getCustomerNo() + " - " + nz(a.getCustomerName()));
        row = kv(card, g, row, "Hesap Türü:", a.getAccountType() + " Hesabı");
        row = kv(card, g, row, "Döviz:", a.getCurrency());
        row = kv(card, g, row, "Durum:", a.getStatus() == 1 ? "Açık" : "Kapalı");
        row = kv(card, g, row, "Açılış:", (a.getOpenedDate() + " " + a.getOpenedTime()).trim());
        return card;
    }

    private JComponent buildMovements() {
        DefaultTableModel m = new DefaultTableModel(
                new String[]{"Tarih", "İşlem", "Tutar", "Açıklama"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (ActivityLog al : new ActivityLogDAO().byAccountNo(a.getAccountNo())) {
            m.addRow(new Object[]{al.getDatePart() + " " + al.getTimePart(), al.getActionType(),
                    al.getAmount() > 0 ? String.format("%,.2f %s", al.getAmount(), al.getCurrency() == null ? "" : al.getCurrency()) : "-",
                    al.getDescription()});
        }
        JTable t = new JTable(m); t.setRowHeight(24);
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        JLabel h = new JLabel("Hesap Hareketleri");
        h.setFont(h.getFont().deriveFont(Font.BOLD, 14f));
        p.add(h, BorderLayout.NORTH);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        if (m.getRowCount() == 0) {
            JLabel empty = new JLabel("Bu hesapta hareket yok.");
            empty.setForeground(new Color(0x6B7280));
            empty.setBorder(new EmptyBorder(6, 2, 2, 2));
            p.add(empty, BorderLayout.SOUTH);
        }
        return p;
    }

    private int kv(JPanel p, GridBagConstraints g, int row, String k, String v) {
        g.gridx = 0; g.gridy = row; JLabel l = new JLabel(k); l.setFont(l.getFont().deriveFont(Font.BOLD)); p.add(l, g);
        g.gridx = 1; p.add(new JLabel(v), g);
        return row + 1;
    }
    private String nz(String s) { return (s == null || s.isBlank()) ? "-" : s; }
}
