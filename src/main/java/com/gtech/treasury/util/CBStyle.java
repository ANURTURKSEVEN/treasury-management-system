package com.gtech.treasury.util;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * CB SMG tarzı sorgu ekranı görünümü:
 *  - Alanlar soluk sarı arka plan + ince kenarlık
 *  - Alanın sağında küçük "liste/sorgu" ikon butonu
 *  - "Sorgu Kriterleri" başlıklı kenarlık
 */
public final class CBStyle {

    private CBStyle() { }

    /** Alanın sağına küçük "≡" (liste/sorgu) butonu ekleyip sarar. Alan görünümü değişmez. */
    public static JComponent withLookup(JComponent input, Runnable onClick) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(input, BorderLayout.CENTER);

        JButton b = new JButton("≡");
        b.setFocusable(false);
        b.setMargin(new Insets(0, 6, 0, 6));
        b.setToolTipText("Sorgula");
        if (onClick != null) b.addActionListener(e -> onClick.run());
        p.add(b, BorderLayout.EAST);
        return p;
    }

    /** "Sorgu Kriterleri" başlıklı kenarlık. */
    public static Border criteriaBorder() {
        TitledBorder tb = BorderFactory.createTitledBorder("Sorgu Kriterleri");
        tb.setTitleFont(tb.getTitleFont().deriveFont(Font.BOLD));
        return tb;
    }
}
