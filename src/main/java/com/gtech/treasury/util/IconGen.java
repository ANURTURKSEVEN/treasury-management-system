package com.gtech.treasury.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Tek seferlik ikon ÜRETİCİ (dev aracı): Java2D ile renkli düz PNG ikonlar çizip
 * src/main/resources/icons/ altına yazar. Bir kez çalıştırılır; üretilen PNG'ler kaynağa girer.
 *   Çalıştırma: java -cp target/classes com.gtech.treasury.util.IconGen
 */
public final class IconGen {

    private static final int S = 64;                 // ikon boyutu
    private static final Color BG = new Color(0x1F2A44);
    private static final Color FG = Color.WHITE;

    public static void main(String[] args) throws Exception {
        File dir = new File("src/main/resources/icons");
        dir.mkdirs();

        write(dir, "home",       new Color(0x2563EB), IconGen::glyphHome);
        write(dir, "customers",  new Color(0x0D9488), IconGen::glyphPeople);
        write(dir, "accounts",   new Color(0x4F46E5), IconGen::glyphCard);
        write(dir, "transfer",   new Color(0x059669), IconGen::glyphArrows);
        write(dir, "deposit",    new Color(0x16A34A), IconGen::glyphBanknote);
        write(dir, "cashflow",   new Color(0xF59E0B), IconGen::glyphUpDown);
        write(dir, "lending",    new Color(0x7C3AED), IconGen::glyphUp);
        write(dir, "borrowing",  new Color(0x9A3412), IconGen::glyphDown);
        write(dir, "fx",         new Color(0x0EA5E9), IconGen::glyphArrows);
        write(dir, "fxwatch",    new Color(0x06B6D4), IconGen::glyphChartLine);
        write(dir, "reports",    new Color(0xEA580C), IconGen::glyphBars);
        write(dir, "users",      new Color(0x0D9488), IconGen::glyphPeople);
        write(dir, "roleperm",   new Color(0x475569), IconGen::glyphShield);
        write(dir, "errorlog",   new Color(0xDC2626), IconGen::glyphWarning);
        write(dir, "bank",       new Color(0x1F2A44), IconGen::glyphBank);
        write(dir, "brand",      new Color(0x1D4ED8), IconGen::glyphBank);
        write(dir, "bell",       new Color(0xF59E0B), IconGen::glyphBell);
        write(dir, "logout",     new Color(0xDC2626), IconGen::glyphLogout);
        write(dir, "settings",   new Color(0x475569), IconGen::glyphGear);
        write(dir, "transactions", new Color(0x2563EB), IconGen::glyphArrows);

        System.out.println("Ikonlar yazildi -> " + dir.getAbsolutePath());
    }

    private interface Glyph { void draw(Graphics2D g); }

    private static void write(File dir, String name, Color bg, Glyph glyph) throws Exception {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        // yuvarlak köşeli renkli arka plan
        g.setColor(bg);
        g.fill(new RoundRectangle2D.Double(2, 2, S - 4, S - 4, 16, 16));
        g.setColor(FG);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        glyph.draw(g);
        g.dispose();
        ImageIO.write(img, "png", new File(dir, name + ".png"));
    }

    // ---- glyph'ler (beyaz, 64x64 tuval, ~16..48 alanı) ----
    private static void glyphHome(Graphics2D g) {
        Path2D roof = new Path2D.Double();
        roof.moveTo(14, 34); roof.lineTo(32, 18); roof.lineTo(50, 34); roof.closePath();
        g.fill(roof);
        g.fillRect(20, 32, 24, 18);
        g.setColor(new Color(0x1F2A44));
        g.fillRect(28, 40, 8, 10);
    }
    private static void glyphPeople(Graphics2D g) {
        g.fillOval(20, 18, 12, 12); g.fillOval(34, 20, 10, 10);
        g.fillRoundRect(16, 32, 20, 16, 8, 8);
        g.fillRoundRect(34, 34, 16, 14, 8, 8);
    }
    private static void glyphCard(Graphics2D g) {
        g.fillRoundRect(14, 22, 36, 22, 6, 6);
        g.setColor(new Color(0x1F2A44)); g.fillRect(14, 28, 36, 4);
        g.setColor(FG); g.fillRect(20, 38, 12, 3);
    }
    private static void glyphArrows(Graphics2D g) {
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(18, 26, 46, 26); g.drawLine(46, 26, 40, 20); g.drawLine(46, 26, 40, 32);
        g.drawLine(46, 40, 18, 40); g.drawLine(18, 40, 24, 34); g.drawLine(18, 40, 24, 46);
    }
    private static void glyphBanknote(Graphics2D g) {
        g.fillRoundRect(14, 24, 36, 20, 5, 5);
        g.setColor(new Color(0x16A34A)); g.fillOval(28, 28, 12, 12);
        g.setColor(FG); g.setFont(new Font("SansSerif", Font.BOLD, 12)); g.drawString("₺", 30, 38);
    }
    private static void glyphUpDown(Graphics2D g) {
        g.drawLine(24, 18, 24, 46); g.drawLine(24, 18, 19, 24); g.drawLine(24, 18, 29, 24);
        g.drawLine(40, 46, 40, 18); g.drawLine(40, 46, 35, 40); g.drawLine(40, 46, 45, 40);
    }
    private static void glyphUp(Graphics2D g) {
        g.drawLine(32, 46, 32, 20); g.drawLine(32, 20, 24, 28); g.drawLine(32, 20, 40, 28);
    }
    private static void glyphDown(Graphics2D g) {
        g.drawLine(32, 18, 32, 44); g.drawLine(32, 44, 24, 36); g.drawLine(32, 44, 40, 36);
    }
    private static void glyphChartLine(Graphics2D g) {
        g.drawLine(16, 44, 26, 34); g.drawLine(26, 34, 34, 40); g.drawLine(34, 40, 48, 22);
    }
    private static void glyphBars(Graphics2D g) {
        g.fillRect(18, 34, 7, 14); g.fillRect(29, 26, 7, 22); g.fillRect(40, 20, 7, 28);
    }
    private static void glyphShield(Graphics2D g) {
        Path2D s = new Path2D.Double();
        s.moveTo(32, 16); s.lineTo(48, 22); s.lineTo(48, 34);
        s.curveTo(48, 44, 40, 48, 32, 50); s.curveTo(24, 48, 16, 44, 16, 34);
        s.lineTo(16, 22); s.closePath();
        g.fill(s);
    }
    private static void glyphWarning(Graphics2D g) {
        Path2D t = new Path2D.Double();
        t.moveTo(32, 16); t.lineTo(50, 48); t.lineTo(14, 48); t.closePath();
        g.fill(t);
        g.setColor(new Color(0xDC2626)); g.fillRect(30, 28, 4, 12); g.fillRect(30, 42, 4, 4);
    }
    private static void glyphBank(Graphics2D g) {
        Path2D roof = new Path2D.Double();
        roof.moveTo(14, 26); roof.lineTo(32, 16); roof.lineTo(50, 26); roof.closePath();
        g.fill(roof);
        g.fillRect(18, 28, 4, 16); g.fillRect(26, 28, 4, 16); g.fillRect(34, 28, 4, 16); g.fillRect(42, 28, 4, 16);
        g.fillRect(14, 46, 36, 4);
    }
    private static void glyphBell(Graphics2D g) {
        Path2D b = new Path2D.Double();
        b.moveTo(20, 42); b.curveTo(20, 26, 24, 20, 32, 20); b.curveTo(40, 20, 44, 26, 44, 42);
        b.closePath(); g.fill(b);
        g.drawLine(16, 44, 48, 44);
        g.fillOval(29, 46, 6, 6); g.fillOval(30, 14, 4, 4);
    }
    private static void glyphLogout(Graphics2D g) {
        g.drawRect(18, 18, 16, 28);
        g.drawLine(30, 32, 48, 32); g.drawLine(48, 32, 42, 26); g.drawLine(48, 32, 42, 38);
    }
    private static void glyphGear(Graphics2D g) {
        g.fillOval(22, 22, 20, 20);
        g.setColor(new Color(0x475569)); g.fillOval(28, 28, 8, 8);
        g.setColor(FG);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            int x = (int) (32 + Math.cos(a) * 16), y = (int) (32 + Math.sin(a) * 16);
            g.fillRect(x - 2, y - 2, 4, 4);
        }
    }
}
