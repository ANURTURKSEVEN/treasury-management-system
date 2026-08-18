package com.gtech.treasury.ui;

import com.gtech.treasury.dao.ErrorLogDAO;
import com.gtech.treasury.model.ErrorLog;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.List;

/**
 * Hata Kayıtları ekranı (yalnızca ADMIN).
 * error_log tablosundaki tüm hataları listeler; yenileme ve temizleme yapılabilir.
 */
public class ErrorLogPanel extends JPanel {

    private final LogTableModel tableModel = new LogTableModel();
    private final JTable table = new JTable(tableModel);

    public ErrorLogPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("Hata Kayıtları");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        add(title, BorderLayout.NORTH);

        table.setRowHeight(26);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);   // uzun mesajlar için yatay kaydırma
        add(new JScrollPane(table), BorderLayout.CENTER);

        add(buildBottomBar(), BorderLayout.SOUTH);

        loadLogs();
    }

    private JComponent buildBottomBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton refresh = new JButton("Yenile");
        refresh.addActionListener(e -> loadLogs());

        JButton clear = new JButton("Tümünü Temizle");
        clear.addActionListener(e -> {
            int ans = JOptionPane.showConfirmDialog(this,
                    "Tüm hata kayıtları silinsin mi? Bu işlem geri alınamaz.",
                    "Onay", JOptionPane.YES_NO_OPTION);
            if (ans == JOptionPane.YES_OPTION) {
                ErrorLogDAO.clearAll();
                loadLogs();
            }
        });

        panel.add(refresh);
        panel.add(clear);
        return panel;
    }

    private void loadLogs() {
        tableModel.setData(ErrorLogDAO.getAll());
        setColumnWidths();
    }

    private void setColumnWidths() {
        int[] widths = {50, 200, 240, 240, 280, 110, 150};
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    // ---- Tablo modeli ----
    private static class LogTableModel extends AbstractTableModel {
        private final String[] columns =
                {"ID", "Hata Tanımı", "Oluştuğu Yer (satır)", "Çağıran Yer (satır)",
                 "Mesaj", "Kullanıcı", "Tarih/Saat"};
        private List<ErrorLog> data = new java.util.ArrayList<>();

        void setData(List<ErrorLog> logs) {
            this.data = logs;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int c) { return columns[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            ErrorLog e = data.get(row);
            switch (col) {
                case 0: return e.getId();
                case 1: return e.getErrorType();
                case 2: return e.getErrorSource();
                case 3: return e.getErrorCaller();
                case 4: return e.getErrorMessage();
                case 5: return e.getUsername();
                case 6: return e.getCreatedAt();
                default: return "";
            }
        }
    }
}
