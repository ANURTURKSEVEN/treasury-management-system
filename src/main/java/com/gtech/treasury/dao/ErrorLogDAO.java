package com.gtech.treasury.dao;

import com.gtech.treasury.model.ErrorLog;
import com.gtech.treasury.util.DBConnection;
import com.gtech.treasury.util.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Hataları error_log tablosuna kaydeder.
 * İki konum tutulur:
 *   error_source : hatanın oluştuğu metot : satır
 *   error_caller : o metodun çağrıldığı yer : satır (bir üst çağrı)
 *
 * NOT: Bu sınıfın kendi catch'i tekrar log yazmaya ÇALIŞMAZ (sonsuz döngü olmasın).
 */
public class ErrorLogDAO {

    public static void log(Throwable e) {
        log(e, null);
    }

    /**
     * Bir hatayı, isteğe bağlı bir açıklama ile loglar.
     * source ve caller konumları hatanın StackTrace'inden çıkarılır.
     */
    public static void log(Throwable e, String context) {
        String type = (context != null ? context + " | " : "") + e.getClass().getName();
        String[] loc = locations(e.getStackTrace(), null);
        save(type, loc[0], loc[1], e.getMessage());
    }

    /** Geriye uyumlu: caller olmadan kaydeder. */
    public static void save(String errorType, String errorSource, String errorMessage) {
        save(errorType, errorSource, "-", errorMessage);
    }

    /** Tam kayıt: tip, oluştuğu yer (source), çağıran yer (caller), mesaj. */
    public static void save(String errorType, String errorSource, String errorCaller, String errorMessage) {
        String sql = "INSERT INTO error_log "
                   + "(error_type, error_source, error_caller, error_message, username) "
                   + "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, cut(errorType, 200));
            ps.setString(2, cut(errorSource, 255));
            ps.setString(3, cut(errorCaller, 255));
            ps.setString(4, errorMessage);
            ps.setString(5, Session.getCurrentUsername());
            ps.executeUpdate();

        } catch (SQLException ex) {
            System.err.println("Hata loglanamadı: " + ex.getMessage());
        }
    }

    /** Tüm hata kayıtlarını (en yeni önce) getirir. */
    public static List<ErrorLog> getAll() {
        List<ErrorLog> list = new ArrayList<>();
        String sql = "SELECT id, error_type, error_source, error_caller, error_message, username, created_at "
                   + "FROM error_log ORDER BY id DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(new ErrorLog(
                        rs.getInt("id"),
                        rs.getString("error_type"),
                        rs.getString("error_source"),
                        rs.getString("error_caller"),
                        rs.getString("error_message"),
                        rs.getString("username"),
                        String.valueOf(rs.getTimestamp("created_at"))));
            }
        } catch (SQLException e) {
            System.err.println("Hata kayıtları getirilemedi: " + e.getMessage());
        }
        return list;
    }

    /** Tüm hata kayıtlarını siler. */
    public static boolean clearAll() {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM error_log")) {
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Hata kayıtları silinemedi: " + e.getMessage());
        }
        return false;
    }

    /**
     * StackTrace'ten iki konum döndürür: [source, caller].
     * source = KENDİ kodumuzdaki (com.gtech.treasury) ilk kare,
     * caller = ondan sonraki ilk kendi kod karesi (metodu çağıran).
     * skipClass verilirse o sınıfın kareleri atlanır (örn. Notify kendini atlar).
     */
    public static String[] locations(StackTraceElement[] trace, String skipClass) {
        String source = "-";
        String caller = "-";
        if (trace == null || trace.length == 0) {
            return new String[]{source, caller};
        }

        int foundIndex = -1;
        for (int i = 0; i < trace.length; i++) {
            if (isOwnFrame(trace[i], skipClass)) {
                source = format(trace[i]);
                foundIndex = i;
                break;
            }
        }
        if (foundIndex < 0) {
            source = format(trace[0]);   // kendi kodumuz yoksa en üst kareyi al
        }

        for (int j = foundIndex + 1; j < trace.length; j++) {
            if (isOwnFrame(trace[j], skipClass)) {
                caller = format(trace[j]);
                break;
            }
        }
        return new String[]{source, caller};
    }

    private static boolean isOwnFrame(StackTraceElement el, String skipClass) {
        String cls = el.getClassName();
        return cls.startsWith("com.gtech.treasury")
                && !cls.equals(ErrorLogDAO.class.getName())
                && (skipClass == null || !cls.equals(skipClass));
    }

    private static String format(StackTraceElement el) {
        return el.getClassName() + "." + el.getMethodName() + " : " + el.getLineNumber();
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
