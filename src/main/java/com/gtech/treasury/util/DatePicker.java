package com.gtech.treasury.util;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Yeniden kullanılabilir tarih seçici: [yyyy-MM-dd yazı alanı] + [takvim düğmesi].
 * Düğmeye tıklayınca aylık takvim açılır; gün seçilir. Kullanıcı elle de yazabilir.
 * Değer her zaman "yyyy-MM-dd" biçimindedir (boş olabilir).
 */
public class DatePicker extends JPanel {

    private static final String[] AYLAR = {
            "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
            "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};
    private static final String[] GUNLER = {"Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz"};

    private final JTextField field = new JTextField(10);

    public DatePicker() { this(null); }

    public DatePicker(String initial) {
        setLayout(new BorderLayout(4, 0));
        setOpaque(false);
        if (initial != null && initial.length() >= 10) field.setText(initial.substring(0, 10));

        JButton btn = new JButton(new CalendarIcon());
        btn.setFocusable(false);
        btn.setMargin(new Insets(2, 6, 2, 6));
        btn.setToolTipText("Takvimden seç");
        btn.addActionListener(e -> openCalendar());

        add(field, BorderLayout.CENTER);
        add(btn, BorderLayout.EAST);
    }

    /** Seçili/yazılı tarih (yyyy-MM-dd) — boş olabilir. */
    public String getText() { return field.getText().trim(); }
    public void setText(String s) { field.setText(s == null ? "" : s); }
    public JTextField getField() { return field; }

    // ---- Takvim penceresi ----
    private void openCalendar() {
        LocalDate cur = parseOrNull(field.getText().trim());
        final YearMonth[] ym = { cur != null ? YearMonth.from(cur) : YearMonth.now() };

        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Tarih Seç",
                Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Üst: ay gezinme
        JButton prev = new JButton("◀");
        JButton next = new JButton("▶");
        prev.setFocusable(false); next.setFocusable(false);
        JLabel head = new JLabel("", SwingConstants.CENTER);
        head.setFont(head.getFont().deriveFont(Font.BOLD, 14f));
        JPanel nav = new JPanel(new BorderLayout());
        nav.add(prev, BorderLayout.WEST); nav.add(head, BorderLayout.CENTER); nav.add(next, BorderLayout.EAST);
        root.add(nav, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 7, 2, 2));
        root.add(grid, BorderLayout.CENTER);

        JButton today = new JButton("Bugün");
        JButton clear = new JButton("Temizle");
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        south.add(clear); south.add(today);
        root.add(south, BorderLayout.SOUTH);

        Runnable rebuild = () -> {
            grid.removeAll();
            head.setText(AYLAR[ym[0].getMonthValue() - 1] + " " + ym[0].getYear());
            for (String d : GUNLER) {
                JLabel l = new JLabel(d, SwingConstants.CENTER);
                l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
                l.setForeground(new Color(0x6B7280));
                grid.add(l);
            }
            LocalDate first = ym[0].atDay(1);
            int offset = first.getDayOfWeek().getValue() - 1;   // Pzt=0
            for (int i = 0; i < offset; i++) grid.add(new JLabel(""));
            LocalDate sel = parseOrNull(field.getText().trim());
            int len = ym[0].lengthOfMonth();
            for (int day = 1; day <= len; day++) {
                LocalDate date = ym[0].atDay(day);
                JButton b = new JButton(String.valueOf(day));
                b.setFocusable(false);
                b.setMargin(new Insets(2, 2, 2, 2));
                if (date.equals(LocalDate.now())) b.setBorder(BorderFactory.createLineBorder(new Color(0x2D6CDF)));
                if (date.equals(sel)) { b.setBackground(new Color(0x2D6CDF)); b.setForeground(Color.WHITE); b.setOpaque(true); }
                b.addActionListener(ev -> { field.setText(date.toString()); dlg.dispose(); });
                grid.add(b);
            }
            grid.revalidate(); grid.repaint();
        };

        prev.addActionListener(e -> { ym[0] = ym[0].minusMonths(1); rebuild.run(); });
        next.addActionListener(e -> { ym[0] = ym[0].plusMonths(1); rebuild.run(); });
        today.addActionListener(e -> { field.setText(LocalDate.now().toString()); dlg.dispose(); });
        clear.addActionListener(e -> { field.setText(""); dlg.dispose(); });

        rebuild.run();
        dlg.setContentPane(root);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private static LocalDate parseOrNull(String s) {
        try { return (s == null || s.length() < 10) ? null : LocalDate.parse(s.substring(0, 10)); }
        catch (Exception e) { return null; }
    }

    /** Küçük takvim ikonu (renkli emoji Swing'de çizilemediği için elle çizilir). */
    private static class CalendarIcon implements Icon {
        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0x374151));
            g2.drawRoundRect(x + 1, y + 2, 13, 12, 3, 3);      // gövde
            g2.fillRect(x + 1, y + 2, 14, 4);                   // üst bant
            g2.setColor(new Color(0x374151));
            g2.fillRect(x + 4, y, 2, 4);                        // sol halka
            g2.fillRect(x + 10, y, 2, 4);                       // sağ halka
            g2.setColor(new Color(0x6B7280));
            for (int r = 0; r < 2; r++)                         // gün noktaları
                for (int col = 0; col < 3; col++)
                    g2.fillRect(x + 3 + col * 4, y + 8 + r * 3, 2, 2);
            g2.dispose();
        }
    }
}
