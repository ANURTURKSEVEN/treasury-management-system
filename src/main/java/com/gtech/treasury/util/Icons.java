package com.gtech.treasury.util;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/**
 * Java2D ile çizilen basit vektör ikonlar.
 * (Swing renkli emoji çizemediği için 🔔 vb. yerine bunları kullanıyoruz.)
 */
public final class Icons {

    private Icons() {}

    /** Çan (bildirim) ikonu. */
    public static Icon bell(int size, Color color) {
        return new Icon() {
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
            @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.translate(x, y);
                g.setColor(color);

                double cx = size / 2.0;
                double top = size * 0.24, bot = size * 0.70, hw = size * 0.28;

                // gövde (çan)
                Path2D body = new Path2D.Double();
                body.moveTo(cx - hw, bot);
                body.curveTo(cx - hw, top + (bot - top) * 0.15, cx - hw * 0.65, top, cx, top);
                body.curveTo(cx + hw * 0.65, top, cx + hw, top + (bot - top) * 0.15, cx + hw, bot);
                body.closePath();
                g.fill(body);

                // alt kenar (ağız)
                g.setStroke(new BasicStroke(Math.max(1f, size * 0.06f)));
                g.drawLine((int) (cx - hw - size * 0.06), (int) bot, (int) (cx + hw + size * 0.06), (int) bot);

                // üst tutamak
                int k = Math.max(2, (int) (size * 0.12));
                g.fillOval((int) (cx - k / 2.0), (int) (top - k), k, k);
                // alt tokmak
                int cl = Math.max(2, (int) (size * 0.14));
                g.fillOval((int) (cx - cl / 2.0), (int) (bot + size * 0.04), cl, cl);

                g.dispose();
            }
        };
    }
}
