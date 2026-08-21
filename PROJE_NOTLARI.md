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

## Bu sohbette yapılanlar (18–19 Ağustos 2026) — GÜNCEL DURUM

### Kredi & Mevduat (gerçek bankacılığa yaklaştırma)
- **Mevduat onay akışı:** `borrowing.status` 2=onay bekliyor, 3=reddedildi eklendi (`reject_reason` kolonu). Müşteri başvurusu para çekmeden bekler; bankacı onaylayınca tek transaction'da para çekilir. (`BorrowingDAO.apply/approve/reject`, sql/19)
- **Bankacı adına başvuru:** admin/trader müşteri seçip kredi/mevduat başvurusu açar (yeniden kullanılabilir `util/CustomerPicker` = No + ≡ pop-up arama; tüm müşteri seçim ekranlarında kullanılıyor).
- **Alt menülere bölme:** Krediler/Mevduat sol menüde Onay / Aktif / Kapanan / Reddedilen alt sayfaları (panellerde `view` parametresi: V_APPROVAL/ACTIVE/CLOSED/REJECTED/APPLY). Hem personel hem müşteri.
- **Otomatik/manuel ödeme talimatı:** `lending.auto_pay` (sql/20). `collectDue` yalnız auto_pay=1 kredileri otomatik tahsil eder.
- **Gecikme faizi:** `payInstallment` vadesi geçmiş taksite gecikme faizi (akdi faiz × 1.3 × gün) ekler.
- **Mevduat stopajı:** `settle` faiz getirisinden vadeye göre stopaj keser (`depositTaxRate`).
- **KRS/KDS karar desteği:** `util/CreditScoreService` — müşteri verisinden KRS notu + KDS önerisi (ONAYLA/İNCELE/REDDET). Başvuru anında `lending`e yazılır (krs_score/krs_band/kds_decision, sql/18). Değerlendirme ekranında gösterilir.
- **Kur fixleme:** `customer_fixed_rate` (sql/17), `FixedRatePanel` (Kur İşlemleri → Kur Fixleme). Müşteri spot işlemde güncel/fixli kur seçer (`SpotTradePanel`).

### PDF / Excel / Raporlama / Batch
- **PDF dekont + tarih aralıklı ekstre:** `util/PdfService` (OpenPDF; Türkçe için Arial IDENTITY_H+embed). `CustomerAccountsPanel`.
- **Geciken Krediler ekranı:** `model/OverdueInstallment` + `LendingDAO.getOverdue()` + `ui/OverduePanel` (menü: Krediler → Geciken Krediler). Gün + gecikme faizi hesaplanır.
- **Excel (Apache POI):** `pom.xml`'e `poi-ooxml 5.2.5` eklendi. `util/OverdueReport` (geciken) + `util/CashflowReport` (nakit akış) → gerçek .xlsx.
- **EOD batch:** `batch/EodOverdueJob` — geciken krediler + günlük nakit akışını Excel'e yazar, personele bildirim + activity_log. Çıktı **proje içi `reports/gecikenler` ve `reports/nakit_akis`** (önce masaüstündeydi, projeye alındı). `.gitignore`'da.
- **`.bat` + zamanlayıcı:** `run-eod.bat` POI için `lib/` klasöründeki jar'ları kullanır (`lib\*`). Windows görevi **TreasuryEODGecikme** her gün 18:00 (StartWhenAvailable=true). `run-*.bat` JDK yolu jdk-21'e düzeltildi. Kur güncelleme görevi: **TreasuryKurGuncelle** 15:30.

### Banka mesajlaşma / gelen kutusu (Aşama 1-4 BİTTİ)
- **Aşama 4 (personel gönderim + müşteri birleşik kutu + görüşme thread):**
  - `ui/ComposeMessageDialog`: personel → müşteri (`CustomerPicker`) veya personele (`JComboBox`, `UserDAO.getStaffUsers`) normal mesaj **veya memnuniyet anketi** (kategori SURVEY). Yanıt kurucusu alıcıyı önden seçip **kilitler** (setEnabledDeep).
  - `ui/InboxPanel` 3 modlu (`JList<Object>` + `RowRenderer`): **Gelen Kutusu / Gönderilenler / Müşteri Görüşmeleri**. Okunmamış = ● + kalın; okunan ince. Tek mesajda "Yanıtla" (göndericiye) + onay mesajında "Değerlendir".
  - **Müşteri Görüşmeleri (thread):** `MessageDAO.customerConversations()` (müşteri başına son mesaj + okunmamış), `conversation(no)` (her iki yön kronolojik), `markCustomerThreadRead(no)`. Sağda mesaj balonları; her balonda **kim yazdı** (`Banka — trader` / `Müşteri 123`). Alttan yanıt → `sender='STAFF:kadi'`, `recipient='CUSTOMER:no'`. `model/Conversation` eklendi.
  - **Müşteri tarafı BİRLEŞİK kutu:** `ui/CustomerInboxPanel` — notification (işlemler) + message (banka) tek listede, tarih sırası. Satıra göre: işlem bildirimi→**İtiraz Et** (DisputeDAO.create + `recipient='STAFF'` mesaj), banka mesajı→**Yanıtla** / SURVEY→**Ankete Katıl** (1-5 + yorum → SURVEY_RESPONSE). Altta **Soru Sor** (→ `STAFF` ortak kutu). `CustomerDashboardFrame` tek "Gelen Kutusu" butonu (birleşik rozet); eski `openNotificationCenter` artık çağrılmıyor (dead code).
  - **Ortak okundu mantığı:** müşteri→banka mesajları `recipient='STAFF'` (tek satır) → personelden biri okuyunca herkeste okundu. Yeni kategoriler: QUESTION, DISPUTE, SURVEY, SURVEY_RESPONSE (şema değişmedi).

### (ESKİ) Banka mesajlaşma notları — Aşama 1-3
- **Amaç:** Banka bildirimlerini tek bir gelen kutusuna (mail gibi) topla. Kredi/mevduat başvuruları ekrana açılır kutu yerine buraya düşsün; tıklayınca detay + "Değerlendir" butonu o kaydı açsın.
- **Yapılan (Aşama 1-3):**
  - `message` tablosu (sql/21): sender/recipient ("SYSTEM"|"STAFF"|"STAFF:kadi"|"CUSTOMER:no"), subject, body, category (INFO/LOAN_APPROVAL/DEPOSIT_APPROVAL/SURVEY), ref_no, is_read.
  - `model/Message`, `dao/MessageDAO` (send/staffInbox/staffUnreadCount/markRead).
  - `ui/InboxPanel`: solda liste, sağda mail detayı, onay mesajında "Değerlendir" butonu (`Consumer<Message> onEvaluate`).
  - `DashboardFrame`: çan artık Gelen Kutusu'nu açıyor (`openInbox`, `case "INBOX"`), rozet = message okunmamış. Girişteki "bekleyen kredi" açılır kutusu KALDIRILDI. `openEvaluate(Message)` → doğru onay ekranını açıp `evaluate(id)` çağırır.
  - `LendingDAO.apply`/`BorrowingDAO.apply`: başvuruda `RETURN_GENERATED_KEYS` ile yeni id alınır, gelen kutusuna LOAN_APPROVAL/DEPOSIT_APPROVAL mesajı düşer (ref_no = id).
  - `LendingPanel`/`BorrowingPanel`: `doEvaluate(Lending)`/`doEvaluateDeposit(Deposit)` overload + `public evaluate(int id)`.
- **KALAN — Aşama 4:** admin/trader/viewer'ın müşteriye ve birbirlerine **mesaj + memnuniyet anketi** gönderebilmesi (bir "Yeni Mesaj" ekranı + müşteri gelen kutusunun da bu tabloyu kullanması). Anket biçimi netleşmedi (1-5 puan + yorum önerildi).
- **NOT:** Eski `openDisputeCenter` artık çana bağlı değil (kod duruyor, kullanılmıyor). İstenirse itirazlar da gelen kutusuna taşınabilir → gerçekten "tek kutu".

### Ortam / altyapı düzeltmeleri
- **MySQL bağlantısı:** `db.properties`'e `allowPublicKeyRetrieval=true` eklendi (yoksa MySQL yeniden başlayınca "Public Key Retrieval is not allowed" hatası). `db.properties` `.gitignore`'da.
- **GitHub:** proje artık `github.com/ANURTURKSEVEN/treasury-management-system` deposunda (git init + push edildi). `.gitignore`: target/, lib/, reports/, *.class, db.properties, mail.properties, logs/. (Not: `treasury-management` AYRI web projesidir.)

## Önemli notlar / tuzaklar (güncel)
- **Swing renkli emoji çizemez** → `resources/icons/*.png` + `IconLoader`.
- **Dış düzenleme sonrası Eclipse:** F5 + Clean şart.
- **Yeni SQL şema dosyaları elle çalıştırılır** (00→21 sırayla; en yenileri 17–21).
- **POI batch'i uygulama dışında çalışsın diye `lib/` klasörü var** (gerekli jar'lar orada; `.bat` `lib\*` kullanır).
- `MessageDAO` ve `LendingDAO`/`BorrowingDAO` aynı `dao` paketinde → birbirini import'suz görür.
- `return null;`'den sonra satır ekleme (unreachable statement); INSERT'te üretilen id için `Statement.RETURN_GENERATED_KEYS` + `getGeneratedKeys()`.

## GÜNCEL DURUM — Ağustos 2026 (Treasury gerçekçileştirme turu)

Mambu / Apache Fineract yaklaşımı referans alınarak modüller "deal-capture"a yaklaştırıldı. **Mevcut mimari korunur; DAO/model/panel desenleri aynı; her yeni işlem transaction + rollback + ActivityLog + Notification kullanır.** Yeni SQL dosyaları **elle** çalıştırılır: **22→28**.

### Kredi (Lending) & Vadeli Mevduat (Borrowing) yenilemesi
- **Kullandırım ayrımı:** Onay artık para vermez → `status=4 APPROVED`; ayrı **`disburse()`** → `status=1` (para + taksit planı). `sql/22` (`approved_by/approved_at/disbursed_at`). Menü: Krediler → **Kullandırım (Onaylanan)** = `LendingPanel.V_APPROVED`.
- **Detay ekranları:** `ui/LoanDetailDialog`, `ui/DepositDetailDialog` (özet + taksit/hareket + onay izi; `LendingDAO.loanActivity/krsKds`, `BorrowingDAO.depositActivity`).
- **Liste zenginleştirme:** Kalan Borç / Sıradaki Vade / Gecikme + renkli & türetilmiş durum (Aktif/Kısmen Ödendi/Gecikmiş). Mevduatta Net Getiri / Kalan Gün.
- **KDS override:** öneri ONAYLA değilken onay için zorunlu gerekçe (activity_log).
- **B2:** `sql/24` borrowing `approved_by/at`; demo "Test" butonları kaldırıldı; EOD `DepositMaturityJob` MM'i de kapatır.
- **Taksit ödeme onay/sonuç akışı:** "Taksiti Öde" ve Taksit Planı'ndaki "Sonraki Taksiti Öde" artık ortak `payNextWithConfirm()` — önce **onay** (taksit no/vade, taksit tutarı, gecikme günü + **gecikme faizi**, toplam, kalan borç), sonra **sonuç** (çekilen kırılımı + kalan borç önce→sonra). Gecikme faizi zaten vardı (`taksit × yıllık faiz/365 × 1,3 × gün`), artık görünür. Taksitte anapara/faiz ayrımı HÂLÂ saklanmıyor (istenirse ayrı aşama).

### Para Piyasası (Money Market) Borçlanma — YENİ, retail'den bağımsız
`sql/25` (`mm_borrowing` + `correspondent_bank` + `MM_BORROW` menü izni), `sql/26` (`mm_charge`). `model/MoneyMarketBorrowing`, `MoneyMarketCharge`, `CorrespondentBank`; `dao/MoneyMarketBorrowingDAO` (create/amend/cancel/matureDue/getCharges, hepsi transaction'lı, banka kasası `customer_no=99999999`), `dao/CorrespondentBankDAO`; `util/InterestCalculationService` (A/360-A/365-30/360 + stopaj), `util/ReferenceGenerator` (MM-/FX- YYYYMMDD-000001), `util/SwiftMessageService` (MT320/MT202 → mevcut `message` tablosuna), `util/MmPositionReport` (POI Excel: açık borçlanmalar + likidite kovaları). UI: `ui/MoneyMarketBorrowingPanel` (deal giriş + Komisyon/Masraf + SWIFT sekmeleri, amend modu), `ui/MoneyMarketListPanel` (gözlem + detay/amend/iptal/excel), `ui/MoneyMarketDetailDialog`. Menü: İşlemler → **Para Piyasası → Borçlanma Girişi / İşlemler(Gözlem)**. EOD: `EodOverdueJob` MM pozisyon Excel'i de üretir. MM nakit hareketleri `CashflowPanel` + `BankTreasuryPanel` kasa detayına işlendi.

### FX Kur Fixleme yenilemesi (`ui/FixedRatePanel`)
`sql/27` (`customer_fx_fixing`: deal+P&L+status+iptal), `sql/28` (`executed_at/by` + EXECUTED). `model/CustomerFXFixing`, `util/FxPricingService`, `dao/CustomerFXFixingDAO`, `ui/FxFixingDetailDialog`. Mevcut `customer_fixed_rate` (standing fix, SpotTradePanel okur) **BOZULMADI** — bir fixing FIXED olunca ilgili yön oraya köprülenir. Ekran: İşlem Tipi (Banka Satış/Alış; Parite pasif), Kur Tipi (Döviz/Efektif — `currency_rate` efektif taşıyor), Spread, Maliyet/Fiyat kartı (referans düzeni: anlık kur/hazine/spread/**Kâr-Zarar + para birimi combosu**/iptal kuru/iptal P&L). **Hesapla/Kuru Fixle tek tıkla** ve **elle girilen Fix kuru esas alınır** (P&L = yönlü (müşteri kuru − maliyet)×tutar). **Referans akışı:** fix kaydedilince müşteriye **bildirim** (Gelen Kutusu) düşer; **Referansla İşle** (bankacı: geçmiş tablosu / müşteri: SpotTradePanel "Fix Referansı ile İşle") gerçek spot alım-satıma çevirir (`AccountDAO.spotTrade`, Banka Satış→isBuy=true), fix **İşlendi** olur. Geçmiş tablosunda **Detay** butonu var. Standing "Tanımlı Fix Kurlar" tablosu ekrandan kaldırıldı (köprü arka planda çalışır).

### Diğer UI iyileştirmeleri
- **`util/DatePicker`** (ortak takvim bileşeni): MM tarihleri, kredi taksit vade düzenleme, mevduat vade düzenleme, müşteri rapor/ekstre tarih alanları.
- **Rol Yetkileri**'ne BANK + CASHFLOW eklendi (`sql/23`), DashboardFrame izne göre gösterir.
- **`ui/AccountDetailDialog`**: Hesaplar ekranında (admin) **Detay** butonu (bakiye/sahip/tür/döviz/açılış/durum + hareketler). Hesaplar tablosunda Tür + Döviz kolonları görünür.
- **Nakit Akışı**'na "Bugün" dönemi; CustomerHomePanel karşılama kartı yükseklik fix.

## Önemli teknik notlar (güncel)
- **Derleme kontrolü (Claude):** GUI çalıştırılamadığında `javac -cp "target/classes;lib/*"` ile derleme; POI için `lib/*`, DB testleri için `.m2` mysql-connector-j-8.4.0 + `src/main/resources`. `java` PATH'te 8, gerçek çalıştırma **JDK 21** (`C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`).
- **spotTrade yönü (KESİN):** `isBuy=true` → müşteri döviz ALIR = **Banka Satış** (kur=sell); `isBuy=false` → müşteri döviz SATAR = **Banka Alış** (kur=buy).
- **Spread küçük bir kur değeridir** (ör. 0,05) — büyük girilirse müşteri kuru negatife düşer (artık doğrulama engelliyor).
- Belirsiz/uydurulmadı: B/C/S kesin tanımı, MM Piyasa Tipi/Amaç enum'ları, hazine maliyeti ayrı besleme (şu an = market).

### Para Piyasası PLASMAN / Borç Verme (Money Market Lending) — YENİ (Faz 1+2+3)
MM Borrowing'in aynası; nakit yönü ters. `sql/29_mm_lending.sql` (`mm_lending` + `mm_lending_charge` + `MM_LEND` menü izni; yaşam döngüsü kolonları parent_deal_id/rolled_to_id/early_closed_at/penalty_amount). `model/MoneyMarketLending`, `MoneyMarketLendingCharge`; `dao/MoneyMarketLendingDAO` (create=kasadan −anapara, matureDue=+geri ödeme, cancel=+anapara, amend, **rollover**, **earlyClose**; guard'lı debit+credit, transaction'lı). UI: `ui/MoneyMarketLendingPanel` (Plasman/Masraf/SWIFT + amend/rollover modu), `ui/MoneyMarketLendingListPanel` (Detay/Batch/Excel/Değişiklik/RollOver/ErkenKapama/İptal), `ui/MoneyMarketLendingDetailDialog`. SWIFT için `util/SwiftDealView` arayüzü (MT320 17R yönü: Borrowing="B", Lending="L"); `SwiftMessageService` arayüz alıyor. Menü: İşlemler → Para Piyasası → **Borç Verme (Plasman) Girişi / Plasman İşlemleri**. EOD: `DepositMaturityJob` plasman vadesini de kapatır. Cashflow/BankTreasury `MM_LEND_*` eşlemeli. `MmPositionReport.writeExcelLending` plasman Excel'i. **Belirsiz:** B/C/S iş kuralı yok (placeholder), Broker lookup yok, SWIFT yön alanları domain önerisi, stopaj %15 DEMO.

### MM müşteri tarafı — bildirim + gözlem + ödeme + iki taraflı takas (Model B)
Para piyasası deal'ini **bankacı açar**. Counterparty bir **sistem müşterisi** ve **deal dövizinde AKTİF hesabı** varsa müşteri ayağı devreye girer (yoksa eski interbank davranışı: yalnız banka kasası). **Lending**: müşteri borçlu → valörde müşteri hesabına +anapara, vadede −geri ödeme. **Borrowing**: müşteri alacaklı → valörde −anapara, vadede +geri ödeme. Her olayda müşteriye **bildirim** (`NotificationDAO.add`, Gelen Kutusu → Bildirimler). DAO'lara `custAcc()/notifyCustomer()/getByCounterpartyNo()` eklendi; create/mature/cancel/amend (+lending rollover/earlyClose) müşteri ayağı guard'lı. Lending `payByCustomer(dealId,customerNo)` + ortak `settleByCustomerOrBank()`: müşteri kendi hesabından borcunu öder (vade/sonrası tam=MATURED, erken=işleyen faiz+EARLY_CLOSED; counterparty no doğrulanır). Müşteri ekranı: `ui/CustomerMoneyMarketPanel` (salt-okunur "Para Piyasası İşlemlerim" + "Öde"); `CustomerDashboardFrame` MM_MINE menü. **Önemli:** artık MM (in-system counterparty için) çift ayaklı gerçek transfer; para korunur.

