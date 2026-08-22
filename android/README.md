# 🤖 AI Klavye — Android Uygulaması (APK)

Gerçek sistem klavyesi: WhatsApp, Instagram, SMS — **her uygulamada** çalışır.
Yazarken yapay zeka seninle birlikte yazar.

## ✨ Özellikler

| Tuş | Ne yapar? |
|---|---|
| ⌨️ | Türkçe Q klavye (ğ ü ş ı ö ç), shift, rakam/sembol sayfası |
| ✨ **Öner** | Sohbeti yapıştır → kişiye göre (Sevgili/Arkadaş/Aile/İş) 3 cevap üretir. **EN UYGUN olan üstte**, dokun → mesaj olarak yazılır |
| 🛠 **Düzelt** | Yazdığın son cümleyi AI ile düzeltir (yanlışlar gider, anlam korunur) |
| 🌐 **Çevir** | Yazdığını seçilen dile çevirip yerine yazar (8 dil) |
| ❓ **Sor** | Anlamını bilmediğin şeyi direkt sor → cevabı mesaja ekleyebilirsin |
| 🎙️ **Sesli** | Konuş → AI dağınık konuşmayı düzgün mesaja çevirir ve yazar |

Üst şeritteki yeşil çiplere dokununca o metin doğrudan yazma alanına eklenir.

---

## 📲 Kurulum (2 dakika)

### 1. APK'yı telefona at ve kur
- `AI-Klavye.apk` dosyasını telefona gönder (WhatsApp'tan kendine, USB, Drive...)
- Dosyaya dokun → *"Bilinmeyen kaynaklara izin ver" → Yine de kur*
- **Not:** Bu bir debug imzalı apk; Play Koruması uyarı verebilir → "Yine de kur"

### 2. Klavyeyi etkinleştir
1. **AI Klavye** uygulamasını aç
2. **⌨️ Klavyeyi Etkinleştir** düğmesine bas → listede **AI Klavye**'yi aç (aç/kapa)
3. Geri dön → telefonun klavye ayarından **varsayılan klavye** olarak seç
   (Ayarlar → Sistem → Diller ve Giriş → Ekran Klavyesi / Varsayılan Klavye)

### 3. Yapay zekayı bağla
Uygulama içinde:
1. Sağlayıcı seç → önerilen: **Groq (ücretsiz)** — console.groq.com'dan anahtar al
2. API Anahtarı'nı yapıştır → **💾 Kaydet** → **🔌 Test Et**
3. **🎙️ Mikrofon İzni Ver** (sesli giriş için)
4. Adını ve nasıl yazdığını gir ("kısa yazarım, espriliyim") → AI senin adına böyle yazar

Hepsi bu ✅ Artık her uygulamada klavyenin üstünde AI tuşlarını göreceksin.

---

## 🕹 Kullanım örneği

> Sevgilin yazdı: *"bugün çok özledim seni :("*
> 1. Klavyede **✨ Öner** → kişi: Sevgili
> 2. Sohbeti yapıştır → **Cevap Üret**
> 3. En üstteki (EN UYGUN) cevaba dokun → mesaj hazır, sadece gönder 💌

---

## ⬆️ GitHub'a Yükleme (yedek + otomatik derleme)

Bu klasörü GitHub'a yüklersen herhangi bir bilgisayardan tekrar APK derleyebilirsin:

```powershell
cd "C:\Users\bayra\OneDrive\Documents\Default Project\ai-klavye-android"
git init
git add .
git commit -m "AI Klavye v1"
git branch -M main
git remote add origin https://github.com/KULLANICI_ADIN/ai-klavye.git
git push -u origin main
```

> Not: `local.properties` ve `AI-Klavye.apk` yükleme (`.gitignore` ile hariç tutulur).

Otomatik derleme istersen `.github/workflows/build.yml` ekle:

```yaml
name: Build APK
on: [push, workflow_dispatch]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v3
        with: { gradle-version: '8.7' }
      - run: gradle assembleDebug --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: ai-klavye-apk
          path: app/build/outputs/apk/debug/app-debug.apk
```

Derlenen APK: repo → **Actions** → son iş → **Artifacts** → indir.

## ❓ Sorun Giderme

| Sorun | Çözüm |
|---|---|
| Klavye listesinde görünmüyor | Uygulamayı açtıktan sonra telefonu yeniden başlat |
| "API hatası 401" | Anahtar yanlış — Ayarlar'dan yeniden gir |
| Sesli giriş çalışmıyor | Mikrofon izni verildi mi? Google uygulaması güncel mi? |
| Cevaplar gelmiyor | 🔌 Test Et ile bağlantıyı kontrol et; internet açık mı? |

---

*Teknik not: Saf Java + sıfır harici bağımlılık. minSdk 24 (Android 7.0+), hedef Android 14.*
