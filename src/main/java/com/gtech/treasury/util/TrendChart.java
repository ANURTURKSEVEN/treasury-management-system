package com.gtech.treasury.util;

import com.gtech.treasury.model.ChartSeries;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;

/**
 * Gerçek eksenli trend (çizgi) grafiği:
 *  - Y ekseni: ızgara çizgileri + kısa tutar etiketleri (1,2 mn ₺ / 340 b ₺)
 *  - X ekseni: gün etiketleri (kalabalık olmasın diye en çok ~6 tarih)
 *  - Fare ile üzerine gelince o noktanın gün + tam tutar bilgisi (tooltip) çıkar.
 */
public class TrendChart extends JPanel {

    private final double[] v;
    private final String[] labels;

    // paint sırasında hesaplanır; hover ve tooltip için saklanır
    private int[] xs, ys;
    private int hoverIdx = -1;

    public TrendChart(ChartSeries s) {
        this.v = s.getValues();
        this.labels = s.getLabels();
        setPreferredSize(new Dimension(600, 300));   // daha uzun grafik
        setBackground(Color.WHITE);

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { updateHover(e.getX()); }
            @Override public void mouseExited(MouseEvent e) { setHover(-1); }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    private void setHover(int idx) {
        if (idx != hoverIdx) { hoverIdx = idx; repaint(); }
    }

    /** Fare x'ine en yakın noktayı bul ve hover olarak işaretle. */
    private void updateHover(int mx) {
        if (xs == null || xs.length == 0) return;
        int best = -1, bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < xs.length; i++) {
            int d = Math.abs(mx - xs[i]);
            if (d < bestDist) { bestDist = d; best = i; }
        }
        setHover(best);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (v == null || v.length < 2) {
            g2.setColor(new Color(0x6B7280));
            g2.drawString("Yeterli veri yok — işlem oldukça trend birikir.", 12, 24);
            xs = null; ys = null;
            return;
        }
        int w = getWidth(), h = getHeight(), padL = 74, padR = 18, padT = 14, padB = 28;
        double min = Arrays.stream(v).min().getAsDouble();
        double max = Arrays.stream(v).max().getAsDouble();
        double span = max - min;
        if (span <= 0) { double d = Math.max(1, Math.abs(max)) * 0.1; min -= d; max += d; }
        else { min -= span * 0.10; max += span * 0.10; }
        double range = max - min; if (range <= 0) range = 1;
        int n = v.length, plotW = w - padL - padR, plotH = h - padT - padB, baseY = padT + plotH;

        // Y ekseni: ızgara + tutar etiketleri
        g2.setFont(g2.getFont().deriveFont(10f));
        int gridN = 4;
        for (int gi = 0; gi <= gridN; gi++) {
            int gy = baseY - (int) (plotH * gi / (double) gridN);
            g2.setColor(new Color(0xEEEEEE));
            g2.drawLine(padL, gy, w - padR, gy);
            g2.setColor(new Color(0x6B7280));
            String lbl = money(min + range * gi / gridN);
            g2.drawString(lbl, padL - 6 - g2.getFontMetrics().stringWidth(lbl), gy + 3);
        }
        g2.setColor(new Color(0xCCCCCC));
        g2.drawLine(padL, padT, padL, baseY);
        g2.drawLine(padL, baseY, w - padR, baseY);

        xs = new int[n]; ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = padL + (n == 1 ? plotW / 2 : (int) (plotW * (i / (double) (n - 1))));
            ys[i] = padT + (int) (plotH * (1 - (v[i] - min) / range));
        }

        // Alan dolgusu
        Polygon area = new Polygon();
        area.addPoint(xs[0], baseY);
        for (int i = 0; i < n; i++) area.addPoint(xs[i], ys[i]);
        area.addPoint(xs[n - 1], baseY);
        g2.setColor(new Color(45, 108, 223, 30));
        g2.fillPolygon(area);

        // Çizgi + noktalar
        g2.setStroke(new BasicStroke(2.2f));
        g2.setColor(new Color(0x2D6CDF));
        for (int i = 0; i < n - 1; i++) g2.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1]);
        for (int i = 0; i < n; i++) g2.fillOval(xs[i] - 3, ys[i] - 3, 6, 6);

        // X ekseni: gün etiketleri (en çok ~6)
        g2.setColor(new Color(0x374151));
        g2.setFont(g2.getFont().deriveFont(10f));
        int step = Math.max(1, (int) Math.ceil(n / 6.0));
        for (int i = 0; i < n; i += step) {
            String lb = (labels != null && i < labels.length) ? labels[i] : String.valueOf(i + 1);
            int lw = g2.getFontMetrics().stringWidth(lb);
            int lx = Math.max(padL, Math.min(xs[i] - lw / 2, w - padR - lw));
            g2.drawString(lb, lx, baseY + 16);
        }

        // Hover: dikey kılavuz + vurgulu nokta + tooltip kutusu
        if (hoverIdx >= 0 && hoverIdx < n) {
            int hx = xs[hoverIdx], hy = ys[hoverIdx];
            g2.setColor(new Color(0x9CA3AF));
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[]{4f, 4f}, 0f));
            g2.drawLine(hx, padT, hx, baseY);
            g2.setColor(new Color(0x2D6CDF));
            g2.fillOval(hx - 5, hy - 5, 10, 10);
            g2.setColor(Color.WHITE);
            g2.fillOval(hx - 2, hy - 2, 4, 4);

            String gun = (labels != null && hoverIdx < labels.length) ? labels[hoverIdx] : ("#" + (hoverIdx + 1));
            String tut = String.format("%,.0f ₺", v[hoverIdx]);
            drawTooltip(g2, hx, hy, "Gün: " + gun, tut, w, padR);
        }
    }

    private void drawTooltip(Graphics2D g2, int px, int py, String line1, String line2, int w, int padR) {
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 11f));
        FontMetrics fm = g2.getFontMetrics();
        int tw = Math.max(fm.stringWidth(line1), fm.stringWidth(line2)) + 16;
        int th = fm.getHeight() * 2 + 12;
        int bx = px + 10, by = py - th - 8;
        if (bx + tw > w - padR) bx = px - tw - 10;   // sağa taşarsa sola koy
        if (bx < 4) bx = 4;
        if (by < 4) by = py + 12;                     // yukarı taşarsa aşağı koy

        g2.setColor(new Color(17, 24, 39, 235));      // koyu yarı saydam
        g2.fillRoundRect(bx, by, tw, th, 8, 8);
        g2.setColor(Color.WHITE);
        g2.drawString(line1, bx + 8, by + fm.getAscent() + 5);
        g2.drawString(line2, bx + 8, by + fm.getAscent() + 5 + fm.getHeight());
    }

    /** Y ekseni için kısa tutar biçimi. */
    private static String money(double val) {
        double a = Math.abs(val);
        if (a >= 1_000_000) return String.format("%,.1f mn ₺", val / 1_000_000);
        if (a >= 1_000)     return String.format("%,.0f b ₺", val / 1_000);
        return String.format("%,.0f ₺", val);
    }
}
