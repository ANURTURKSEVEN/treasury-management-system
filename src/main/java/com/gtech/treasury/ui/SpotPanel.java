package com.gtech.treasury.ui;

import com.gtech.treasury.model.Customer;
import com.gtech.treasury.model.User;

import javax.swing.*;
import java.awt.*;

/**
 * Spot FX ana paneli — sekme yok, butonlarla geçiş.
 *
 * İki kullanım:
 *   - Personel: new SpotPanel(user)     → kur düzenlenebilir (ADMIN/TRADER), tüm müşteriler
 *   - Müşteri : new SpotPanel(customer)  → kur SALT görüntüleme, işlem kendi adına, kendi geçmişi
 */
public class SpotPanel extends JPanel {

    private final CardLayout cards = new CardLayout();
    private final JPanel container = new JPanel(cards);

    /** Personel görünümü. */
    public SpotPanel(User currentUser) {
        String role = currentUser.getRole();
        boolean canEditRates = "ADMIN".equals(role);                       // kur güncelleme: sadece ADMIN
        boolean canTrade = "ADMIN".equals(role) || "TRADER".equals(role);  // al/sat: ADMIN + TRADER
        build(canEditRates, canTrade, null);
    }

    /** Müşteri görünümü: kur değiştiremez, ama kendi adına işlem yapabilir. */
    public SpotPanel(Customer customer) {
        build(false, true, customer);
    }

    private void build(boolean canEditRates, boolean canTrade, Customer fixedCustomer) {
        setLayout(new BorderLayout());
        container.add(new SpotRatePanel(canEditRates, () -> cards.show(container, "TRADE")), "RATE");
        container.add(new SpotTradePanel(canTrade, fixedCustomer, () -> cards.show(container, "RATE")), "TRADE");
        add(container, BorderLayout.CENTER);
        cards.show(container, "RATE");
    }
}
