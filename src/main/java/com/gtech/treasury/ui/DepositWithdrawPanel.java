package com.gtech.treasury.ui;

import com.gtech.treasury.dao.AccountDAO;
import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.dao.CustomerDAO;
import com.gtech.treasury.dao.CustomerSnapshotDAO;
import com.gtech.treasury.dao.NotificationDAO;
import com.gtech.treasury.model.Account;
import com.gtech.treasury.model.Customer;
import com.gtech.treasury.model.User;
import com.gtech.treasury.util.Notify;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Para Yatır / Çek — modern kart tasarımı.
 *   - Müşteri modu (Customer): kendi hesabına yatırır/çeker.
 *   - Personel modu (User)   : bankacı; önce müşteri seçer, sonra hesabına yatırır/çeker
 *     (ör. müşteri dışarıdan nakit getirdi → hesabına eklenir). İşlem müşteriye bildirim düşer.
 */
public class DepositWithdrawPanel extends JPanel {

    private static final Color GREEN = new Color(0x1E8E3E);
    private static final Color RED   = new Color(0xC5221F);
    private static final Color INK   = new Color(0x1F2A44);

    private final AccountDAO accountDAO = new AccountDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();

    private final Customer fixedCustomer;   // müşteri modu
    private final boolean staffMode;        // personel modu

    private final com.gtech.treasury.util.CustomerPicker picker = new com.gtech.treasury.util.CustomerPicker();
    private final JComboBox<Account> accountCombo = new JComboBox<>();
    private final JToggleButton depBtn = new JToggleButton("＋  Para Yatır", true);
    private final JToggleButton witBtn = new JToggleButton("−  Para Çek");
    private final JTextField amountField = new JTextField();
    private final JLabel curSuffix = new JLabel(" ");
    private final JLabel previewLabel = new JLabel(" ");
    private JButton actionBtn;

    public DepositWithdrawPanel(Customer customer) {
        this.fixedCustomer = customer;
        this.staffMode = false;
        init();
    }

    public DepositWithdrawPanel(User staff) {
        this.fixedCustomer = null;
        this.staffMode = true;
        init();
    }

    private void init() {
        setLayout(new GridBagLayout());
        setBackground(new Color(0xF0F2F5));
        add(buildCard());
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) { loadAccounts(); refreshPreview(); }
        });
    }

    private JComponent buildCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)),
                new EmptyBorder(28, 34, 28, 34)));
        card.setPreferredSize(new Dimension(480, staffMode ? 520 : 460));

        JLabel title = new JLabel("Para Yatır / Çek");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(title);
        JLabel sub = new JLabel(staffMode
                ? "Bankacı işlemi — müşteri hesabına para ekle/çıkar"
                : "Kendi hesabınıza para yatırın veya çekin");
        sub.setForeground(new Color(0x6B7280));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(sub);
        card.add(Box.createVerticalStrut(20));

        // Personel modunda müşteri seçimi (No + ≡ arama)
        if (staffMode) {
            card.add(fieldLabel("Müşteri No"));
            picker.setOnChange(() -> { loadAccounts(); refreshPreview(); });
            picker.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(picker);
            card.add(Box.createVerticalStrut(14));
        }

        // Yön: iki büyük toggle
        ButtonGroup g = new ButtonGroup();
        g.add(depBtn); g.add(witBtn);
        styleToggle(depBtn); styleToggle(witBtn);
        depBtn.addActionListener(e -> onDirChange());
        witBtn.addActionListener(e -> onDirChange());
        JPanel dir = new JPanel(new GridLayout(1, 2, 10, 0));
        dir.setOpaque(false);
        dir.setAlignmentX(Component.LEFT_ALIGNMENT);
        dir.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        dir.add(depBtn); dir.add(witBtn);
        card.add(dir);
        card.add(Box.createVerticalStrut(16));

        // Hesap
        card.add(fieldLabel("Hesap"));
        accountCombo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v,
                    int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof Account) {
                    Account a = (Account) v;
                    setText(a.getAccountNo() + "  •  " + a.getAccountType() + "  •  "
                            + String.format("%,.2f %s", a.getBalance(), a.getCurrency()));
                }
                return this;
            }
        });
        accountCombo.addActionListener(e -> refreshPreview());
        sizeFull(accountCombo);
        card.add(accountCombo);
        card.add(Box.createVerticalStrut(16));

        // Tutar
        card.add(fieldLabel("Tutar"));
        JPanel amtRow = new JPanel(new BorderLayout(8, 0));
        amtRow.setOpaque(false);
        amtRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        amtRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        amountField.setFont(amountField.getFont().deriveFont(Font.BOLD, 20f));
        amountField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refreshPreview(); }
            @Override public void removeUpdate(DocumentEvent e) { refreshPreview(); }
            @Override public void changedUpdate(DocumentEvent e) { refreshPreview(); }
        });
        curSuffix.setFont(curSuffix.getFont().deriveFont(Font.BOLD, 16f));
        curSuffix.setForeground(new Color(0x6B7280));
        amtRow.add(amountField, BorderLayout.CENTER);
        amtRow.add(curSuffix, BorderLayout.EAST);
        card.add(amtRow);
        card.add(Box.createVerticalStrut(8));

        previewLabel.setForeground(new Color(0x6B7280));
        previewLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(previewLabel);
        card.add(Box.createVerticalStrut(18));

        actionBtn = new JButton("Para Yatır");
        actionBtn.setForeground(Color.WHITE);
        actionBtn.setFont(actionBtn.getFont().deriveFont(Font.BOLD, 15f));
        actionBtn.setFocusPainted(false);
        actionBtn.setBorderPainted(false);
        actionBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        actionBtn.addActionListener(e -> doOperation());
        card.add(actionBtn);

        loadAccounts();
        onDirChange();
        return card;
    }

    // ---- Görsel yardımcılar ----
    private JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 12f));
        l.setForeground(new Color(0x374151));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 4, 0));
        return l;
    }

    private void sizeFull(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
    }

    private void styleToggle(JToggleButton b) {
        b.setFocusPainted(false);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 14f));
    }

    private void onDirChange() {
        boolean dep = depBtn.isSelected();
        actionBtn.setText(dep ? "Para Yatır" : "Para Çek");
        actionBtn.setBackground(dep ? GREEN : RED);
        refreshPreview();
    }

    private Account selectedAccount() {
        return (Account) accountCombo.getSelectedItem();
    }

    private void refreshPreview() {
        Account a = selectedAccount();
        curSuffix.setText(a != null ? a.getCurrency() : "");
        Double amt = parse(amountField.getText());
        if (a == null || amt == null || amt <= 0) { previewLabel.setText(" "); return; }
        double delta = depBtn.isSelected() ? amt : -amt;
        double newBal = a.getBalance() + delta;
        previewLabel.setText(String.format("Mevcut: %,.2f %s   →   Yeni bakiye: %,.2f %s",
                a.getBalance(), a.getCurrency(), newBal, a.getCurrency()));
    }

    private Double parse(String s) {
        try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (Exception e) { return null; }
    }

    private Customer currentCustomer() {
        return staffMode ? picker.getSelected() : fixedCustomer;
    }

    private void loadAccounts() {
        accountCombo.removeAllItems();
        Customer c = currentCustomer();
        if (c == null) return;
        for (Account a : accountDAO.getByCustomer(c.getCustomerId())) accountCombo.addItem(a);
    }

    private void doOperation() {
        Customer c = currentCustomer();
        if (c == null) { Notify.warning(this, "Önce müşteri seçin."); return; }
        Account a = selectedAccount();
        if (a == null) { Notify.warning(this, "Bir hesap seçin."); return; }

        Double amount = parse(amountField.getText());
        if (amount == null || amount <= 0) { Notify.warning(this, "Geçerli bir tutar girin."); return; }

        boolean deposit = depBtn.isSelected();
        if (!deposit && amount > a.getBalance()) {
            Notify.warning(this, "Yetersiz bakiye. Mevcut: " + String.format("%,.2f %s", a.getBalance(), a.getCurrency()));
            return;
        }

        double delta = deposit ? amount : -amount;
        if (!accountDAO.changeBalance(a.getAccountId(), delta)) {
            Notify.error(this, "İşlem gerçekleştirilemedi.");
            return;
        }

        ActivityLogDAO.log(deposit ? "ACCOUNT_DEPOSIT" : "ACCOUNT_WITHDRAW",
                c.getCustomerNo(), amount, a.getCurrency(),
                (deposit ? "Para yatırma: " : "Para çekme: ") + String.format("%,.2f %s", amount, a.getCurrency()),
                "Hesap: " + a.getAccountNo() + " (" + a.getCurrency() + ")"
                        + (staffMode ? " | Bankacı işlemi" : ""));
        CustomerSnapshotDAO.record(c.getCustomerId());

        // Personel yaptıysa müşteriye bildirim
        if (staffMode) {
            new NotificationDAO().add(c.getCustomerNo(),
                    (deposit ? "Hesabınıza " : "Hesabınızdan ")
                            + String.format("%,.2f %s", amount, a.getCurrency())
                            + (deposit ? " yatırıldı" : " çekildi"),
                    "Şube işlemi | Hesap: " + a.getAccountNo() + " (" + a.getCurrency() + ")");
        }

        amountField.setText("");
        loadAccounts();
        refreshPreview();
        Notify.info(this, (deposit ? "Para yatırma" : "Para çekme") + " tamamlandı.\n\n"
                + c.getCustomerName() + " " + c.getSurname() + " — " + a.getAccountNo() + "\n"
                + String.format("%,.2f %s", amount, a.getCurrency()) + (deposit ? " eklendi." : " çekildi."));
    }
}
