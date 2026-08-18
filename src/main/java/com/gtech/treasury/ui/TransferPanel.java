package com.gtech.treasury.ui;

import com.gtech.treasury.dao.AccountDAO;
import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.model.Account;
import com.gtech.treasury.model.Customer;
import com.gtech.treasury.model.User;
import com.gtech.treasury.util.Notify;
import com.gtech.treasury.util.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Para Transferi — Havale (aynı banka, hesaplar arası) veya EFT (başka banka, IBAN'a).
 * Her ikisinde masraf (komisyon) kaynaktan kesilir ve BANKA kasasına girer.
 * Farklı dövizde havale arka planda kur çevrimi yapar.
 *   - Personel: tüm hesaplar. Müşteri: sadece kendi hesapları.
 */
public class TransferPanel extends JPanel {

    private final AccountDAO accountDAO = new AccountDAO();
    private final Customer fixedCustomer;

    private final JRadioButton havaleRadio = new JRadioButton("Havale (aynı banka)", true);
    private final JRadioButton eftRadio = new JRadioButton("EFT (başka banka)");
    private final JRadioButton fastRadio = new JRadioButton("FAST (anlık)");
    private final JComboBox<Account> sourceCombo = new JComboBox<>();
    private final JComboBox<Account> targetCombo = new JComboBox<>();
    private final JTextField ibanField = new JTextField(18);
    private final JTextField amountField = new JTextField(16);
    private final JLabel feeLabel = new JLabel(" ");
    private final JLabel resultLabel = new JLabel(" ");
    private final CardLayout targetCards = new CardLayout();
    private final JPanel targetContainer = new JPanel(targetCards);
    private final JLabel targetLabel = new JLabel("Alan Hesap:");

    public TransferPanel(User currentUser) {
        this((Customer) null);
    }

    public TransferPanel(Customer customer) {
        this.fixedCustomer = customer;
        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(20, 20, 20, 20));
        add(buildCard());
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) { loadAccounts(); }
        });
    }

    private JComponent buildCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)),
                new EmptyBorder(24, 32, 24, 32)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        DefaultListCellRenderer renderer = new DefaultListCellRenderer() {
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
        };
        sourceCombo.setRenderer(renderer);
        targetCombo.setRenderer(renderer);

        int row = 0;
        JLabel title = new JLabel("Para Transferi (Havale / EFT)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        card.add(title, gbc);
        gbc.gridwidth = 1;

        // İşlem tipi
        ButtonGroup group = new ButtonGroup();
        group.add(havaleRadio);
        group.add(eftRadio);
        group.add(fastRadio);
        JPanel typeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        typeRow.setOpaque(false);
        typeRow.add(havaleRadio);
        typeRow.add(Box.createHorizontalStrut(16));
        typeRow.add(eftRadio);
        typeRow.add(Box.createHorizontalStrut(16));
        typeRow.add(fastRadio);
        gbc.gridx = 0; gbc.gridy = row; card.add(bold("İşlem Tipi:"), gbc);
        gbc.gridx = 1; card.add(typeRow, gbc);
        row++;

        gbc.gridx = 0; gbc.gridy = row; card.add(bold("Gönderen Hesap:"), gbc);
        gbc.gridx = 1; card.add(sourceCombo, gbc);
        row++;

        // Alan: Havale -> hesap listesi, EFT -> IBAN
        targetContainer.setOpaque(false);
        targetContainer.add(targetCombo, "HAVALE");
        targetContainer.add(ibanField, "EXTERNAL");
        targetLabel.setFont(targetLabel.getFont().deriveFont(Font.BOLD));
        gbc.gridx = 0; gbc.gridy = row; card.add(targetLabel, gbc);
        gbc.gridx = 1; card.add(targetContainer, gbc);
        row++;

        gbc.gridx = 0; gbc.gridy = row; card.add(bold("Tutar (gönderen döviz):"), gbc);
        gbc.gridx = 1; card.add(amountField, gbc);
        row++;

        feeLabel.setForeground(new Color(0x6B7280));
        gbc.gridx = 1; gbc.gridy = row++; card.add(feeLabel, gbc);

        JButton transfer = new JButton("Gönder");
        UITheme.stylePrimary(transfer);
        transfer.setPreferredSize(new Dimension(0, 42));
        transfer.addActionListener(e -> doTransfer());
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        gbc.insets = new Insets(16, 8, 8, 8);
        card.add(transfer, gbc);

        resultLabel.setForeground(new Color(0x1E8E3E));
        resultLabel.setFont(resultLabel.getFont().deriveFont(Font.BOLD, 14f));
        gbc.gridy = row++;
        card.add(resultLabel, gbc);

        // Etkileşim
        havaleRadio.addActionListener(e -> onTypeChange());
        eftRadio.addActionListener(e -> onTypeChange());
        fastRadio.addActionListener(e -> onTypeChange());
        sourceCombo.addActionListener(e -> updateFee());
        amountField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateFee(); }
            @Override public void removeUpdate(DocumentEvent e) { updateFee(); }
            @Override public void changedUpdate(DocumentEvent e) { updateFee(); }
        });

        loadAccounts();
        onTypeChange();
        return card;
    }

    private AccountDAO.TransferKind selectedKind() {
        if (eftRadio.isSelected())  return AccountDAO.TransferKind.EFT;
        if (fastRadio.isSelected()) return AccountDAO.TransferKind.FAST;
        return AccountDAO.TransferKind.HAVALE;
    }

    private void onTypeChange() {
        boolean havale = havaleRadio.isSelected();
        targetCards.show(targetContainer, havale ? "HAVALE" : "EXTERNAL");
        targetLabel.setText(havale ? "Alan Hesap:" : "Alıcı IBAN / Hesap No:");
        updateFee();
    }

    private void updateFee() {
        Double amt = parse(amountField.getText());
        Account src = (Account) sourceCombo.getSelectedItem();
        String cur = src != null ? src.getCurrency() : "";
        AccountDAO.TransferKind kind = selectedKind();

        if (kind == AccountDAO.TransferKind.HAVALE) {
            feeLabel.setText("Havale (aynı banka) ücretsizdir.");
        } else if (kind == AccountDAO.TransferKind.FAST) {
            feeLabel.setText("FAST anlık ve ücretsizdir (işlem başı en fazla "
                    + String.format("%,.0f TL", AccountDAO.FAST_LIMIT_TRY) + " karşılığı).");
        } else if (amt == null || amt <= 0) {
            feeLabel.setText("EFT masrafı tutara göre kademelidir (başka banka).");
        } else {
            double fee = accountDAO.calcFee(amt, cur, kind);
            feeLabel.setText("EFT masrafı: " + String.format("%,.2f %s", fee, cur) + " → banka"
                    + "   |   Toplam kesinti: " + String.format("%,.2f %s", amt + fee, cur));
        }
    }

    private Double parse(String s) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }

    private JLabel bold(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    private void loadAccounts() {
        sourceCombo.removeAllItems();
        targetCombo.removeAllItems();
        java.util.List<Account> list = (fixedCustomer != null)
                ? accountDAO.getByCustomer(fixedCustomer.getCustomerId())
                : accountDAO.getAll();
        for (Account a : list) {
            sourceCombo.addItem(a);
            targetCombo.addItem(a);
        }
    }

    private void doTransfer() {
        Account src = (Account) sourceCombo.getSelectedItem();
        if (src == null) { Notify.warning(this, "Gönderen hesabı seçin."); return; }
        Double amount = parse(amountField.getText());
        if (amount == null || amount <= 0) { Notify.warning(this, "Geçerli bir tutar girin."); return; }
        if (fixedCustomer != null && src.getCustomerId() != fixedCustomer.getCustomerId()) {
            Notify.warning(this, "Sadece kendi hesabınızdan transfer yapabilirsiniz.");
            return;
        }

        AccountDAO.TransferKind kind = selectedKind();
        boolean external = (kind != AccountDAO.TransferKind.HAVALE);
        long targetNo;
        String targetText;
        if (external) {
            String iban = ibanField.getText().trim();
            if (iban.isEmpty()) { Notify.warning(this, "Alıcı IBAN / hesap no girin."); return; }
            // Girilen numara bizim sistemdeki bir hesapsa ona teslim edilir; değilse dışarı gider.
            // (10 haneli hesap no'ya uyan sayı; uzun/aşırı büyük IBAN'lar dış hesap sayılır.)
            long tn = 0;
            if (iban.matches("\\d{6,15}")) {
                try { tn = Long.parseLong(iban); } catch (NumberFormatException ex) { tn = 0; }
            }
            targetNo = tn;
            targetText = iban;
        } else {
            Account tgt = (Account) targetCombo.getSelectedItem();
            if (tgt == null) { Notify.warning(this, "Alan hesabı seçin."); return; }
            if (src.getAccountId() == tgt.getAccountId()) {
                Notify.warning(this, "Gönderen ve alan hesap aynı olamaz."); return;
            }
            targetNo = tgt.getAccountNo();
            targetText = String.valueOf(tgt.getAccountNo());
        }

        AccountDAO.TransferResult r = accountDAO.transferWithFee(src.getAccountNo(), targetNo, amount, kind);
        if (!r.ok) { Notify.warning(this, r.error); return; }

        String tipTr = kind == AccountDAO.TransferKind.EFT ? "EFT"
                     : kind == AccountDAO.TransferKind.FAST ? "FAST" : "Havale";
        String action = kind == AccountDAO.TransferKind.EFT ? "EFT"
                      : kind == AccountDAO.TransferKind.FAST ? "FAST" : "TRANSFER";
        String details = "Tip: " + tipTr
                + " | Gönderen: " + src.getAccountNo() + " (" + src.getCurrency() + ")"
                + " | Alıcı: " + targetText
                + " | Tutar: " + String.format("%,.2f %s", amount, src.getCurrency())
                + " | Masraf: " + String.format("%,.2f %s", r.fee, src.getCurrency())
                + (external ? "" : " | Alan hesaba geçen: " + String.format("%,.2f %s", r.credited, r.tgtCurrency));
        ActivityLogDAO.log(action, src.getCustomerNo(), amount, src.getCurrency(),
                tipTr + ": " + src.getAccountNo() + " → " + targetText, details);

        resultLabel.setText("✓ " + tipTr + " tamam"
                + (r.fee > 0 ? " — masraf " + String.format("%,.2f %s", r.fee, src.getCurrency()) + " bankaya geçti." : "."));
        amountField.setText("");
        ibanField.setText("");
        loadAccounts();
        updateFee();

        boolean delivered = r.credited > 0;   // hedef sistemde bulunup teslim edildiyse
        String msg = tipTr + " tamamlandı.\n\n"
                + "Tutar: " + String.format("%,.2f %s", amount, src.getCurrency()) + "\n"
                + "Masraf (bankaya): " + String.format("%,.2f %s", r.fee, src.getCurrency()) + "\n"
                + "Toplam kesinti: " + String.format("%,.2f %s", amount + r.fee, src.getCurrency())
                + (delivered
                    ? "\n\n" + targetText + " hesabına "
                        + String.format("%,.2f %s", r.credited, r.tgtCurrency) + " geçti."
                    : "\n\nAna para " + targetText + " adresine gönderildi (başka banka).");
        Notify.info(this, msg);
    }
}
