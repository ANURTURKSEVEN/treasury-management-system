package com.gtech.treasury.ui;

import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.dao.CustomerDAO;
import com.gtech.treasury.dao.UserDAO;
import com.gtech.treasury.model.Customer;
import com.gtech.treasury.model.User;
import com.gtech.treasury.util.Notify;
import com.gtech.treasury.util.Session;
import com.gtech.treasury.util.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Giriş ekranı (Swing) — modern, tam ekran, ortada kart tasarımı.
 * Veritabanından kontrol eder, doğruysa Dashboard'u açar.
 */
public class LoginFrame extends JFrame {

    private final JTextField usernameField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final UserDAO userDAO = new UserDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();

    public LoginFrame() {
        setTitle("Treasury System - Giriş");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        UITheme.maximize(this);                 // tam ekran
        setLayout(new GridBagLayout());         // kartı ortalamak için
        getContentPane().setBackground(new Color(0xF0F2F5));

        add(buildCard());
    }

    /** Ekranın ortasındaki giriş kartı. */
    private JPanel buildCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(new EmptyBorder(36, 44, 36, 44));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 2;

        JLabel title = new JLabel("Treasury Management System", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        gbc.gridx = 0; gbc.gridy = 0;
        card.add(title, gbc);

        JLabel subtitle = new JLabel("Devam etmek için giriş yapın", SwingConstants.CENTER);
        subtitle.setForeground(new Color(0x6B7280));
        gbc.gridy = 1;
        card.add(subtitle, gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = 2;
        card.add(new JLabel("Kullanıcı Adı"), gbc);
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        card.add(usernameField, gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = 4;
        card.add(new JLabel("Şifre"), gbc);
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        card.add(passwordField, gbc);

        JButton loginButton = new JButton("GİRİŞ YAP");
        loginButton.setPreferredSize(new Dimension(0, 42));
        UITheme.stylePrimary(loginButton);
        loginButton.addActionListener(e -> doLogin());
        getRootPane().setDefaultButton(loginButton);   // Enter ile giriş
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        gbc.insets = new Insets(20, 8, 8, 8);
        card.add(loginButton, gbc);

        return card;
    }

    private void doLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            Notify.warning(this, "Kullanıcı adı ve şifre boş olamaz.");
            return;
        }

        // Tek giriş noktası: users tablosu (staff + müşteri; müşteri = MÜŞTERİ NO girer)
        boolean numeric = username.matches("\\d+");   // sayısalsa müşteri no denemesi
        User user = userDAO.login(username, password);

        // --- Giriş başarısız: sebebe göre spesifik mesaj + kategorili log ---
        if (user == null) {
            if (!userDAO.usernameExists(username)) {
                if (numeric) {
                    Notify.error(this,
                            "Böyle bir müşteri kaydı bulunamadı. (Müşteri No: " + username + ")",
                            "GIRIS HATASI | MUSTERI YOK (no=" + username + ")");
                } else {
                    Notify.error(this,
                            "Böyle bir kullanıcı bulunamadı: " + username,
                            "GIRIS HATASI | KULLANICI YOK (kadi=" + username + ")");
                }
            } else {
                Notify.error(this, "Şifre hatalı. Lütfen tekrar deneyin.",
                        "GIRIS HATASI | SIFRE HATALI (kadi=" + username + ")");
            }
            passwordField.setText("");
            return;
        }

        // --- Müşteri girişi: kayıt aktif mi? ---
        if ("CUSTOMER".equals(user.getRole())) {
            Customer customer = customerDAO.getCustomerById(user.getCustomerId());
            if (customer == null || customer.getStatus() == 0) {
                Notify.error(this,
                        "Böyle bir müşteri kaydı bulunamadı. (Hesap pasif veya silinmiş olabilir.)",
                        "GIRIS HATASI | MUSTERI PASIF/SILINMIS (no=" + username + ")");
                passwordField.setText("");
                return;
            }
            Session.setCurrentUsername(user.getUsername());
            ActivityLogDAO.log("LOGIN", customer.getCustomerNo(),
                    "Müşteri girişi: " + customer.getCustomerName() + " " + customer.getSurname(),
                    "Müşteri No: " + customer.getCustomerNo() + " | Tür: " + customer.getCustomerType());
            dispose();
            SwingUtilities.invokeLater(() -> new CustomerDashboardFrame(customer).setVisible(true));
            return;
        }

        // --- Personel girişi (ADMIN/TRADER/VIEWER) ---
        Session.setCurrentUsername(user.getUsername());
        ActivityLogDAO.log("LOGIN", 0,
                "Personel girişi: " + user.getUsername(),
                "Rol: " + user.getRole() + " | Ad: " + user.getFullName());
        dispose();
        SwingUtilities.invokeLater(() -> new DashboardFrame(user).setVisible(true));
    }
}
