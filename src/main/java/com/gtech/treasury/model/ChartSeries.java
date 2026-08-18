package com.gtech.treasury.model;

/**
 * Çizgi grafik için tarih etiketleri + değerler (eskiden yeniye sıralı).
 * labels[i] i. noktanın gün etiketi (örn. "14.08"), values[i] o günün TL karşılığı.
 */
public class ChartSeries {
    private final String[] labels;
    private final double[] values;

    public ChartSeries(String[] labels, double[] values) {
        this.labels = labels;
        this.values = values;
    }

    public String[] getLabels() { return labels; }
    public double[] getValues() { return values; }
    public int size() { return values == null ? 0 : values.length; }
}
