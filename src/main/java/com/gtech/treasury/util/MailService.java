package com.gtech.treasury.util;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.Properties;

/**
 * Gmail SMTP ile e-posta gönderir (en basit yöntem: ayrı servis yok).
 * Ayarlar classpath'teki mail.properties'ten okunur:
 *   mail.username / mail.password -> Gmail adresi + UYGULAMA ŞİFRESİ (16 hane)
 *   mail.from                     -> gönderen (genelde mail.username ile aynı)
 *   mail.to                       -> alıcı(lar), virgülle ayrılır
 *
 * NOT: mail.password Gmail'in normal şifresi DEĞİL, "Uygulama Şifresi"dir
 * (Google Hesabı -> Güvenlik -> Uygulama şifreleri). 2 Adımlı Doğrulama açık olmalı.
 */
public class MailService {

    private final Properties cfg = new Properties();

    public MailService() {
        try (InputStream in = MailService.class.getClassLoader().getResourceAsStream("mail.properties")) {
            if (in == null) {
                throw new IllegalStateException("mail.properties classpath'te bulunamadı.");
            }
            cfg.load(in);
        } catch (Exception e) {
            throw new RuntimeException("mail.properties okunamadı: " + e.getMessage(), e);
        }
    }

    /** HTML gövdeli e-posta gönderir. Başarısızsa açıklamalı bir istisna fırlatır. */
    public void sendHtml(String subject, String htmlBody) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.host", cfg.getProperty("mail.smtp.host", "smtp.gmail.com"));
        props.put("mail.smtp.port", cfg.getProperty("mail.smtp.port", "587"));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        final String user = req("mail.username");
        final String pass = req("mail.password");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, pass);
            }
        });

        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(cfg.getProperty("mail.from", user)));
        for (String to : req("mail.to").split(",")) {
            if (!to.trim().isEmpty()) {
                msg.addRecipient(Message.RecipientType.TO, new InternetAddress(to.trim()));
            }
        }
        msg.setSubject(subject, "UTF-8");
        msg.setContent(htmlBody, "text/html; charset=UTF-8");

        Transport.send(msg);
    }

    /** Raporun gideceği adres(ler) — loglama için. */
    public String recipient() {
        return cfg.getProperty("mail.to");
    }

    private String req(String key) {
        String v = cfg.getProperty(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("mail.properties içinde '" + key + "' tanımlı değil.");
        }
        return v.trim();
    }
}
