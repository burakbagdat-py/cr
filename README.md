# Kripto Radar (Android)

Kripto piyasasını **saniyelik** takip eden, bildirim gönderen ve **yalnızca istatistiklere dayanarak** coin öne çıkaran Android uygulaması.

> ⚠️ Uygulamadaki skorlar geçmiş fiyat/hacim verisinden otomatik hesaplanır. **Yatırım tavsiyesi değildir.**

## Özellikler

**Canlı veri (zaman kaybı yok)**
- Binance `!miniTicker@arr` WebSocket akışıyla tüm piyasa (2000+ çift) tek bağlantıdan **her saniye** güncellenir.
- Kopmada üstel bekleme ile otomatik yeniden bağlanma, yedek sunuculara geçiş, 30 sn veri gelmezse bağlantı yenileme.
- REST anlık görüntüsüyle periyodik tam senkron (yeni listelenen coinler dahil).

**Arka planda sürekli çalışma**
- Ön plan servisi (uygulama kapalıyken de çalışır) + kısmi wake lock: ekran kapalıyken bile akış sürer.
- Telefon yeniden başlatılınca otomatik başlar; sistem servisi öldürürse WorkManager bekçisi 15 dk'da bir yeniden başlatır.
- Kalıcı bildirimde favori coinlerin canlı fiyatı.
- Ayarlar'dan "Pil kısıtlamasını kaldır" ile kesintisiz çalışma.

**Bildirimler**
- Fiyat hedefi (yukarı/aşağı yön otomatik), 24s % değişim, "X dakikada ±%Y" ani hareket alarmları; tek sefer veya tekrarlı.
- Otomatik **ani hareket** tespiti: favoriler + hacimli coinlerde ayarlanabilir eşik/pencere (varsayılan 5 dk'da %3).
- **Hacim patlaması** (son 6 saat / önceki 48 saat ≥ 4x).
- **Dip dönüş adayı** bildirimi (gerekçeleriyle birlikte).
- Teknik skoru eşiği **yeni geçen** coinler için sinyal bildirimi.
- Aynı olay için tekrar tekrar bildirim gelmesini önleyen bekleme süresi.

**🔄 Dip Dönüş Dedektörü** (`ReversalDetector`)
Çok düşmüş **ama** grafiği yukarı dönmeye başlamış coinleri otomatik bulur ve bildirir.

*Derinlik – ne kadar düşük?* 30 günlük zirveden düşüş, 30 günlük aralıktaki konum, son 7 günün en düşük günlük RSI'ı.

*Dönüş – yükselmeye meyilli mi?* Saatlik RSI'ın aşırı satımdan toparlanması, EMA9'un EMA21'i yukarı kesmesi, MACD'nin pozitife dönmesi,
dipten tepki + daha yüksek dip (dip tekrar test edilmedi), pozitif RSI uyumsuzluğu, son 24 saatin regresyon eğimi (R² ile tutarlılık), alış yönlü hacim baskınlığı.

Aday = derinlik ≥ 0.45, 30g zirveden ≥ %15 aşağıda, dönüş ≥ 0.5 ve son 6 saatte çöküş yok ("düşen bıçak" elenir).
Her aday için bu desenin hem o coinin kendi geçmişindeki hem de tüm taranan coinlerdeki 24 saat sonraki isabet oranı gösterilir.
Tarama en hacimli coinlerin yanında 24 saatin en çok düşen likit coinlerini de kapsar. Tam teyit edilmemiş "çok düşük" coinler izleme listesinde gösterilir.

**📈 Öneri Takibi**: Bildirilen her öneri, bildirim anındaki fiyatıyla kaydedilir ve canlı getirisi izlenir; yöntemin gerçekte ne kadar tuttuğunu görürsünüz.

**İstatistiksel öneri motoru** (`SignalEngine`)
En hacimli N coin (varsayılan 100) + en çok düşen 25 likit coin için 500 saatlik + 60 günlük mum verisi çekilir ve 0-100 skor üretilir:

| Bileşen | Ağırlık | Ölçüt |
|---|---|---|
| Trend (1s) | 20% | Fiyat / EMA20 / EMA50 / EMA200 dizilimi |
| Günlük trend | 15% | Günlük EMA20, 7 günlük değişim |
| MACD | 15% | Histogram işareti ve eğimi |
| RSI(14) | 15% | 55-72 güçlü, >72 aşırı alım cezası, <30 tepki potansiyeli |
| Hacim | 12% | Son 6s hacmi / önceki 48s ortalaması, fiyat yönüyle birlikte |
| BTC'ye göre güç | 13% | 24s performans farkı |
| Bollinger | 5% | %B konumu, bant sıkışması |
| Oynaklık | 5% | Saatlik ATR (yüksek oynaklık cezası) |

Ek olarak **geçmiş isabet istatistiği**: son ~500 saatte aynı teknik koşul (RSI dilimi, EMA20'ye göre konum, MACD işareti) kaç kez oluştu ve 12 saat sonra fiyat yüzde kaç oranında daha yüksekti.

Ayrıca: Korku & Açgözlülük endeksi, BTC dominansı, toplam piyasa değeri değişimi.

**Ekranlar**: Piyasa (arama, favoriler, yükselen/düşen, hacim, 5 dk ani hareket), Sinyaller (Dip Dönüş / Teknik Skor / Öneri Takibi), Coin detayı (mum grafiği + EMA20, 1/5/15/60 dk değişim, faktör dökümü), Alarmlar, Ayarlar. USDT / TRY / FDUSD / BTC çiftleri.

## Kurulum

### Hazır APK
Her push'ta GitHub Actions APK üretir: **Actions → APK Derle → son çalıştırma → Artifacts → KriptoRadar-apk**. Zip'i açıp `app-release.apk` dosyasını telefona yükleyin (bilinmeyen kaynaklara izin verin).

### Kendiniz derleyin
```bash
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest # testler (gerçek Binance verisiyle uçtan uca tarama dahil)
```
Gereksinimler: JDK 17+, Android SDK 35. Android 8.0 (API 26) ve üzeri.

### İlk açılışta
1. Bildirim iznini verin.
2. Ayarlar → **Pil kısıtlamasını kaldır** (arka planda kesintisiz çalışma için önemli; Xiaomi/Huawei/Samsung'da ayrıca "otomatik başlatma"ya izin verin).
3. Piyasa listesinde ☆ ile favorilerinizi seçin.

## Mimari
```
data/      Binance REST + WebSocket, canlı depo (MarketRepository), fiyat geçmişi halka tamponu, ayarlar
analysis/  İndikatörler (EMA, RSI, MACD, Bollinger, ATR) ve skor motoru
alerts/    Alarm motoru ve bildirimler
service/   Ön plan servisi, açılışta başlatma, WorkManager bekçisi
ui/        Jetpack Compose ekranları
```
Kotlin, Jetpack Compose (Material 3), OkHttp, kotlinx.serialization, WorkManager. API anahtarı gerekmez.
