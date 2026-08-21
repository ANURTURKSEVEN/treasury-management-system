# Kullanıcı Promptları (esas istekler)

## 2026-08-20 — Mesajlar/Bildirimler ayrımı + yan menü
> proje bulunan mesajlar ve bildirimler sistemini incele. sistemdeki müşteri ve banka mesajlarını ve işlem bildirimlerini ayır ve mesajları yandaki ana menü tarafına ekle. müşteriler ekranı içinde aynı şeyi yap.

**Yapılan:**
- `CustomerInboxPanel`: `mode` eklendi (`MODE_ALL/MODE_MESSAGES/MODE_NOTIFICATIONS`), üst başlık + moda göre kaynak filtresi; "Soru Sor" yalnız mesaj görünümünde.
- `CustomerDashboardFrame`: yan menüye "📬 Mesajlar" grubu (Mesajlar / Bildirimler). Çan artık yalnız **İşlem Bildirimleri**'ni açar (rozet = okunmamış bildirim).
- `DashboardFrame` (personel): yan menüye "📬 Mesajlar" satırı (mevcut `InboxPanel`).
- `IconLoader.forKey`: INBOX/CINBOX* → "bell" ikonu.

## 2026-08-20 — KDS müşteri araştırması (KRS'ye dokunmadan)
> kredi karar sistemini incele. krs notuna dokunma ama kds sisteminde müşterinin hesap hareketleri, ödenmeyen kredisinin olup olmadığı gibi müşteriye yönelik bir araştırma yapsın. ve buna yönelik bir sonuç dönsün. riskliye nedeni iyiyse de nedeni yazsın.

**Yapılan:**
- `CreditScoreService`: KRS skor formülü aynen korundu. `evaluate`'e `customerNo` overload'u eklendi. Yeni `loadAccountActivity` (activity_log son 90 gün: hareket sayısı, TL yaklaşık giriş/çıkış, son işlem gün farkı) + `overdueLoans` sayımı. KDS kararı artık ödenmeyen taksit + açık borç + hesap hareketliliği (dormant/cashDrain) faktörlerine bakıyor; her durumda ✓/⚠/• işaretli gerekçe listesi + "Sonuç:" başlığı üretiyor.
- `LendingPanel`: değerlendirme kartına "Gerekçeler (müşteri araştırması)" metin alanı eklendi (önceden reasons hiç gösterilmiyordu). `evaluate` çağrısı customerNo geçiyor.
- `LendingDAO.apply`: `evaluate` çağrısı customerNo geçiyor.

## 2026-08-20 — Money Market LENDING / Plasman modülü (Faz 1+2+3)
> Borrowing modülünün karşı tarafı Money Market Lending/Placement deal-entry ekranı. Önce analiz (kod yazma) → sonra tam uygulama. Ayrı mm_lending tablosu + tüm yaşam döngüsü (rollover, erken kapama).

**Yapılan (MM Borrowing'in aynası, ters nakit yönü):**
- SQL `29_mm_lending.sql`: `mm_lending` (+parent_deal_id/rolled_to_id/early_closed_at/penalty_amount, funding/collection account), `mm_lending_charge`, `screen`+`role_screen` `MM_LEND`. **Elle çalıştır (29).**
- `util/SwiftDealView` arayüzü → `SwiftMessageService.buildMT320/MT202` artık arayüz alıyor; `MoneyMarketBorrowing` ("B") ve `MoneyMarketLending` ("L") implement ediyor (17R yönü). Borrowing davranışı değişmedi.
- `model/MoneyMarketLending`, `model/MoneyMarketLendingCharge`.
- `dao/MoneyMarketLendingDAO`: create (kasadan −anapara), matureDue/settleOne (+geri ödeme), cancel (+anapara), amend, **rollover** (eski→ROLLED_OVER, yeni deal parent_deal_id ile), **earlyClose** (işleyen faiz + penalty, InterestCalculationService ile). Guard'lı debit + credit, transaction+rollback.
- `ui/MoneyMarketLendingPanel` (Plasman/Masraf/SWIFT; amend + rollover modları), `ui/MoneyMarketLendingListPanel` (Detay/Yenile/Batch/Excel/Değişiklik/RollOver/ErkenKapama/İptal), `ui/MoneyMarketLendingDetailDialog`.
- Entegrasyon: `MmPositionReport.writeExcelLending`, `DepositMaturityJob` plasman matureDue, `DashboardFrame` MM_LEND/MM_LEND_LIST menü+routing, `CashflowPanel.classify` + `BankTreasuryPanel` (MM_LEND_* yön eşleme).
- Belirsiz (uydurulmadı): B/C/S iş kuralı (placeholder), Broker lookup (yok), SWIFT yön alanları (domain önerisi). Stopaj oranı %15 (DEMO).

## 2026-08-20 — MM müşteri tarafı: bildirim + gözlem + ödeme + iki taraflı takas (Model B)
> Admin'deki para piyasası müşteride yok, bildirim müşteriye ulaşmıyor; alış-veriş müşteri tarafında nasıl olacak? Karar: deal'i bankacı açar; counterparty müşteriyse (a) bildirim gitsin (b) müşteri kendi işlemlerini gözlemlesin (c) borçlu olduğu deal'i kendi hesabından ödesin (d) müşteri hesabı gerçek ayna ayak olarak hareket etsin.

**Yapılan (MM = interbank; ama counterparty sistem müşterisi + deal dövizinde AKTİF hesabı varsa müşteri ayağı devreye girer, yoksa eski interbank davranışı korunur):**
- `MoneyMarketLendingDAO` & `MoneyMarketBorrowingDAO`: `custAcc(conn,custId,cur)` + `notifyCustomer(...)` + `getByCounterpartyNo(no)` eklendi. create/mature/cancel/amend (+ lending rollover/earlyClose) noktalarına müşteri ayağı guard'lı olarak eklendi. Lending: müşteri borçlu (valörde +anapara, vadede −geri ödeme). Borrowing: müşteri alacaklı (valörde −anapara, vadede +geri ödeme). Tüm olaylarda `NotificationDAO.add(counterpartyNo,...)`.
- Lending `payByCustomer(dealId, customerNo)` + ortak `settleByCustomerOrBank(...)`: müşteri kendi hesabından borcunu öder; vade/sonrası tam geri ödeme (MATURED), erken ise işleyen faiz (EARLY_CLOSED). Yetki: counterparty no doğrulanır.
- Yeni `ui/CustomerMoneyMarketPanel` (salt-okunur "Para Piyasası İşlemlerim": borçlanma+plasman birlikte, Detay + "Öde"). `CustomerDashboardFrame`'e MM_MINE menü + routing.
- Müşteri hesabı yoksa (harici/interbank kurum) müşteri ayağı atlanır → eski davranış. Money conservation: iki ayak birlikte (banka kasası ↔ müşteri hesabı).
