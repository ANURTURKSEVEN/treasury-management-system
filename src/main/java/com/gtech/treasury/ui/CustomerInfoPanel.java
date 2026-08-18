package com.gtech.treasury.ui;

import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.dao.CustomerDAO;
import com.gtech.treasury.model.Customer;
import com.gtech.treasury.util.Notify;
import com.gtech.treasury.util.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Müşteri "Bilgileri Düzenle" ekranı (Ayarlar altında).
 * Kimlik alanları (No/Ad/TC/Tür) salt okunur; telefon ve adres düzenlenebilir.
 */
public class CustomerInfoPanel extends JPanel {

    private final CustomerDAO customerDAO = new CustomerDAO();
    private final Customer customer;

    public CustomerInfoPanel(Customer customer) {
        this.customer = customer;
        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(20, 20, 20, 20));
        add(buildCard());
    }

    private JComponent buildCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)),
                new EmptyBorder(24, 32, 24, 32)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(7, 8, 7, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField phone = new JTextField(customer.getPhone(), 20);
        JTextField address = new JTextField(customer.getAddress(), 20);

        int r = 0;
        JLabel title = new JLabel("Bilgileri Düzenle");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        gbc.gridx = 0; gbc.gridy = r++; gbc.gridwidth = 2;
        card.add(title, gbc);
        gbc.gridwidth = 1;

        ro(card, gbc, r++, "Müşteri No:", String.valueOf(customer.getCustomerNo()));
        ro(card, gbc, r++, "Ad Soyad:", customer.getCustomerName() + " " + customer.getSurname());
        ro(card, gbc, r++, "TC:", customer.getTc());
        ro(card, gbc, r++, "Tür:", customer.getCustomerType());
        rw(card, gbc, r++, "Telefon:", phone);
        rw(card, gbc, r++, "Adres:", address);

        JButton save = new JButton("Kaydet");
        UITheme.stylePrimary(save);
        save.addActionListener(e -> {
            Customer upd = new Customer(customer.getCustomerId(), customer.getCustomerNo(),
                    customer.getCustomerType(), customer.getCustomerName(), customer.getSurname(),
                    customer.getTc(), phone.getText().trim(), address.getText().trim());
            if (customerDAO.updateCustomer(upd)) {
                customer.setPhone(phone.getText().trim());
                customer.setAddress(address.getText().trim());
                ActivityLogDAO.log("CUSTOMER_UPDATE", customer.getCustomerNo(),
                        "Müşteri kendi bilgilerini güncelledi",
                        "Tel: " + customer.getPhone() + " | Adres: " + customer.getAddress());
                Notify.info(this, "Bilgileriniz güncellendi.");
            } else {
                Notify.error(this, "Güncelleme başarısız.");
            }
        });
        gbc.gridx = 1; gbc.gridy = r; gbc.fill = GridBagConstraints.NONE;
        card.add(save, gbc);
        return card;
    }

    private void ro(JPanel c, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel l = new JLabel(label); l.setFont(l.getFont().deriveFont(Font.BOLD));
        c.add(l, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        c.add(new JLabel(value == null ? "-" : value), gbc);
    }

    private void rw(JPanel c, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel l = new JLabel(label); l.setFont(l.getFont().deriveFont(Font.BOLD));
        c.add(l, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        c.add(field, gbc);
    }
}
