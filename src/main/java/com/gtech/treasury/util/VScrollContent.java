package com.gtech.treasury.util;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;

/**
 * Dikey kaydırma için içerik paneli.
 * Genişliği viewport'a uyar (yatay kaydırma olmaz), yüksekliği tercih ettiği kadardır;
 * içerik pencereden uzunsa JScrollPane dikey kaydırma çubuğu gösterir.
 */
public class VScrollContent extends JPanel implements Scrollable {

    public VScrollContent(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle vr, int orientation, int dir) { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle vr, int orientation, int dir) { return 120; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }   // genişlik viewport'a uysun
    @Override public boolean getScrollableTracksViewportHeight() { return false; } // yükseklik serbest -> kaydırılır
}
