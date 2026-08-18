# Treasury Management System — Proje Notları (staj projesi)

> Bu dosya, yeni bir sohbetin projeyi hızlıca anlaması için özet rehberdir.
> Yeni sohbette: **"Şu dosyayı oku ve projeyi öğren: C:\Users\gtstaj0079\treasury-management-system\PROJE_NOTLARI.md"** demen yeterli.

## Ne / Nerede
- **Tür:** Java **Swing** masaüstü bankacılık/hazine uygulaması (staj bitirme projesi).
- **Konum:** `C:\Users\gtstaj0079\treasury-management-system`
- Not: `C:\Users\gtstaj0079\treasury-management` AYRI bir projedir (Spring Boot + React web sürümü); karıştırma.

## Teknolojiler
- **Java 17** hedefi (pom: `maven.compiler.source/target = 17`). Kurulu JDK: **21** → `C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot` (21'de sorunsuz çalışır).
- **Maven** (Eclipse m2e). **Maven wrapper (mvnw) YOK.** Lombok **kullanılmıyor** (düz getter/setter).
- **Swing + FlatLaf 3.4.1**, **MySQL 8** (JDBC, DAO pattern), javax.mail 1.6.2, javax.activation 1.2.0.
- Bağımlılık jar'ları: `C:\Users\gtstaj0079\.m2\repository` altında.

## Veritabanı
- Servis: **MySQL80**, `localhost:3306`. Veritabanı: **`treasury_db`**.
- Bağlantı (`src/main/resources/db.properties`): kullanıcı **root**, parola **gt0079**.
- Şema/seed: `sql/00_database.sql … 15_sample_customers.sql` (sırayla). `sql/run_all.sql` yalnız **komut satırında** çalışır (`SOURCE` içerir; Workbench desteklemez → dosyaları 00→15 tek tek çalıştır).
- Faydalı sorgu: `sql/musteri_hesaplari.sql` (müşteri + hesapları).
- Temiz kurulum: root ile `sql/00→15`'i çalıştır.

## Çalıştırma
### Eclipse
1. `File → Import → Existing Maven Projects` → kök: bu klasör.
2. JDK 21 tanımlı olsun (`Window → Preferences → Installed JREs`).
3. Ana sınıf: **`com.gtech.treasury.Main`** → Run As → Java Application.
4. **ÖNEMLİ:** Dosyalar Eclipse dışından düzenlenirse, çalıştırmadan önce projeye **F5 (Refresh) → Project → Clean → Run** (yoksa eski `.class`'lar çalışır).

### Komut satırı (Eclipse'siz — dışardan derleme sonrası)
```
javac -encoding UTF-8 -cp target\classes -d target\classes <degisen .java dosyalari>
javaw -cp "target\classes;src\main\resources;<m2 jar'lari>" com.gtech.treasury.Main
```
Gerekli jar'lar: mysql-connector-j-8.4.0, flatlaf-3.4.1, javax.mail-1.6.2, javax.activation-1.2.0 (hepsi .m2 altında).

## Giriş bilgileri (seed)
| Kullanıcı | Parola | Rol |
|---|---|---|
| admin | admin123 | ADMIN |
| trader | trader123 | TRADER |
| viewer | viewer123 | VIEWER |
| 10000001 … 10000005 | 1234 | CUSTOMER |

## Yapı
```
src/main/java/com/gtech/treasury/
  Main.java              # giriş noktası (global hata yakalayıcı + LoginFrame)
  dao/     ...DAO.java    # JDBC erişim (Account, Customer, Rate, Spot, Lending, Borrowing, Notification, Dispute, ...)
  model/   ...            # POJO'lar (Account, Customer, CurrencyRate, Notification, Dispute, ...)
  ui/      ...Panel/Frame # DashboardFrame (personel), CustomerDashboardFrame (müşteri) + paneller
  util/    DBConnection, UITheme, Notify, Session, Icons, IconLoader, IconGen, VScrollContent, TcmbRateService
  batch/   ...Job.java    # DailyReport, DepositMaturity, Installment, RateUpdate
sql/                      # 00..15 şema+seed, run_all, sorgular
src/main/resources/       # db.properties, mail.properties, icons/*.png
run-*.bat                 # batch job çalıştırıcılar
```

## Roller / ekranlar (özet)
- **ADMIN:** her şey + Banka Kasası / Nakit Akışı işlemleri (yalnız admin).
- **TRADER/VIEWER:** anasayfada admin ile aynı bilgi (Banka Hazine Kasası, salt-okunur); kasa İŞLEMLERİ admin'de.
- **CUSTOMER:** kendi bilgileri/hesapları, transfer, spot FX, kredi/mevduat, bildirim çanı.

## Son eklenen özellikler (bu proje üzerinde yapılan işler)
1. **Müşteri soft-delete:** admin müşteriyi pasife alır; **hesapları da** tek transaction'da pasif olur. "Pasif Müşteriler" → **Geri Getir** (müşteri + hesapları reaktive). (`CustomerDAO`, `CustomerPanel`)
2. **İlk hesap kuralı:** bir müşterinin ilk hesabı **zorunlu Vadesiz/TRY**; sonra USD/Yatırım vb. açılır. (`AccountDAO.open`, `AccountsPanel`)
3. **UI kaydırma:** Kredi / Vadeli Mevduat / Spot FX panelleri dikey kaydırılabilir + alt tablolar büyütüldü. (`util/VScrollContent`, ilgili paneller)
4. **Kur tekrarı düzeltmesi:** `RateDAO.getAll` her para biriminden yalnız en yeni aktif satırı döndürür.
5. **Bildirim çanı + itiraz (dispute) akışı:** müşteride "Bildirimler", personelde "İtirazlar" butonu; `notification` tablosu genişletildi (type/ref_no/target_role), `dispute` tablosu eklendi; `NotificationDAO`, `DisputeDAO`, `Dispute` modeli. Müşteri bir bildirime itiraz eder → personele düşer → karar. **Bu akış eklendi; uçtan uca TEST edilip cilalanmalı.**
6. **Emoji → PNG ikon seti:** Swing renkli emoji çizemediği için ikonlar PNG. `util/IconGen` (üretici, tek sefer çalışır → `resources/icons/*.png`), `util/IconLoader` (yükle/ölçek/önbellek + emoji temizleme). Sidebar, üst bar (marka/çan/çıkış), sekme başlıkları ikonlu/temiz.

## Önemli notlar / tuzaklar
- **Swing renkli emoji çizemez** → emoji yerine `resources/icons/*.png` kullanılır (`IconLoader`). Yeni ikon gerekiyorsa `IconGen`'e ekleyip bir kez çalıştır.
- **Dış düzenleme sonrası Eclipse:** F5 + Clean şart, yoksa değişiklik görünmez.
- `run_all.sql` Workbench'te `SOURCE` hatası verir (normaldir) — komut satırında kullan.

## Sıradaki olası işler
- İtiraz (dispute) akışını uçtan uca test/cilala.
- Panel içi başlıklardaki kalan emojileri ikona/temiz metne çevir.
- İstenirse: rapor/dekont geliştirmeleri, kredi taksit ödeme akışı.
