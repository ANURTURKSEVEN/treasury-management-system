package com.gtech.treasury.util;

import com.gtech.treasury.dao.CustomerDAO;
import com.gtech.treasury.model.Customer;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Yeniden kullanılabilir müşteri seçim bileşeni:
 *   [Müşteri No alanı] [≡ arama pop-up'ı] [Temizle]  seçili müşteri
 * No yazıp Enter ile seçilir; ≡ ile Ad/Soyad'a göre aranıp tablodan seçilir.
 */
public class CustomerPicker extends JPanel {

    private final CustomerDAO customerDAO = new CustomerDAO();
    private final JTextField noField = new JTextField(12);
    private Customer selected;
    private Runnable onChange;

    public CustomerPicker() {
        setOpaque(false);
        setLayout(new FlowLayout(FlowLayout.LEFT, 6, 0));
        noField.addActionListener(e -> searchByNo());
        add(CBStyle.withLookup(noField, this::openSearch));   // No + ≡
        JButton clear = new JButton("Temizle");
        clear.addActionListener(e -> {
            noField.setText(""); selected = null;
            if (onChange != null) onChange.run();
        });
        add(clear);
    }

    /** Seçim değiştiğinde çağrılacak geri çağırım (hesapları yükle vb.). */
    public void setOnChange(Runnable r) { this.onChange = r; }

    public Customer getSelected() { return selected; }

    /** Dışarıdan programatik seçim (ör. tablo satırından). */
    public void setSelected(Customer c) { if (c != null) apply(c); }

    private void searchByNo() {
        String no = noField.getText().trim();
        if (no.isEmpty()) { openSearch(); return; }
        List<Customer> res = customerDAO.searchByCriteria(no, "", "", "", "");
        if (res.isEmpty()) {
            Notify.warning(this, "Bu numarayla müşteri bulunamadı: " + no);
        } else if (res.size() == 1) {
            apply(res.get(0));
        } else {
            openSearch();
        }
    }

    private void openSearch() {
        JTextField noF = new JTextField(9), nameF = new JTextField(9), surF = new JTextField(9);
        noF.setText(noField.getText().trim());
        CustModel model = new CustModel();
        JTable results = new JTable(model);
        results.setRowHeight(24);
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        Runnable doSearch = () -> model.setData(
                customerDAO.searchByCriteria(noF.getText(), nameF.getText(), surF.getText(), "", ""));

        JPanel crit = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        crit.setBorder(CBStyle.criteriaBorder());
        crit.add(new JLabel("Müşteri No")); crit.add(noF);
        crit.add(new JLabel("Ad"));         crit.add(nameF);
        crit.add(new JLabel("Soyad"));      crit.add(surF);
        JButton search = new JButton("Sorgula");
        search.addActionListener(e -> doSearch.run());
        crit.add(search);
        JButton clear = new JButton("Temizle");
        clear.addActionListener(e -> { noF.setText(""); nameF.setText(""); surF.setText(""); doSearch.run(); });
        crit.add(clear);
        for (JTextField f : new JTextField[]{noF, nameF, surF}) f.addActionListener(e -> doSearch.run());

        JLabel hint = new JLabel("Satıra çift tıklayarak müşteriyi seçin.");
        hint.setForeground(new Color(0x6B7280));

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setPreferredSize(new Dimension(760, 400));
        content.add(crit, BorderLayout.NORTH);
        content.add(new JScrollPane(results), BorderLayout.CENTER);
        content.add(hint, BorderLayout.SOUTH);

        doSearch.run();

        results.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && results.getSelectedRow() >= 0) {
                    apply(model.getAt(results.getSelectedRow()));
                    Window w = SwingUtilities.getWindowAncestor(results);
                    if (w != null) w.dispose();
                }
            }
        });

        int res = JOptionPane.showConfirmDialog(this, content, "Müşteri Sorgula / Seç",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION && results.getSelectedRow() >= 0) {
            apply(model.getAt(results.getSelectedRow()));
        }
    }

    private void apply(Customer c) {
        this.selected = c;
        noField.setText(String.valueOf(c.getCustomerNo()));
        if (onChange != null) onChange.run();
    }

    private static class CustModel extends AbstractTableModel {
        private final String[] cols = {"No", "Ad", "Soyad", "Tür"};
        private List<Customer> data = new java.util.ArrayList<>();
        void setData(List<Customer> d) { this.data = d; fireTableDataChanged(); }
        Customer getAt(int r) { return data.get(r); }
        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Object getValueAt(int r, int c) {
            Customer cu = data.get(r);
            switch (c) {
                case 0: return cu.getCustomerNo();
                case 1: return cu.getCustomerName();
                case 2: return cu.getSurname();
                case 3: return cu.getCustomerType();
                default: return "";
            }
        }
    }
}
