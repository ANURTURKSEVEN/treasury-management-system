package com.gtech.treasury.model;

/**
 * Sistemdeki bir ekranı (menüyü) temsil eder.
 * screen tablosundaki bir satıra karşılık gelir.
 */
public class Screen {

    private int screenId;
    private String screenKey;    // kodun tanıdığı anahtar (CUSTOMER, SPOT, ...)
    private String screenName;   // menüde görünen ad
    private String forType;      // USER / CUSTOMER / BOTH

    public Screen(int screenId, String screenKey, String screenName, String forType) {
        this.screenId = screenId;
        this.screenKey = screenKey;
        this.screenName = screenName;
        this.forType = forType;
    }

    public int getScreenId() {
        return screenId;
    }

    public String getScreenKey() {
        return screenKey;
    }

    public String getScreenName() {
        return screenName;
    }

    public String getForType() {
        return forType;
    }
}
