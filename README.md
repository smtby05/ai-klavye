# 🤖 AI Klavye

Yapay zeka destekli mesajlaşma asistanı — Android APK + iPhone web uygulaması.

## 🔗 Linkler

| Ne | Adres |
|---|---|
| 📱 **Web uygulaması (iPhone/Android)** | https://smtby05.github.io/ai-klavye/ |
| 📦 **Android APK (doğrudan indir)** | https://github.com/smtby05/ai-klavye/raw/main/android/AI-Klavye.apk |
| 💾 Kaynak kod | Bu repo |

## ✨ Neler yapar?

- 💬 **Kişiye göre cevap**: Sohbeti yapıştır → sevgiline romantik, arkadaşına esprili cevaplar. En uygunu üstte, dokun → gönder
- 🛠 **Otomatik düzeltme**: Yanlışları AI düzeltir
- 🌐 **Çeviri**: 8 dile ton koruyarak çevirir
- ❓ **Soru-cevap**: Anlamını bilmediğini direkt sor
- 🎙️ **Sesli yazışma**: Konuşmanı düzgün mesaja çevirir
- ⚙️ **Kişiselleştirme**: Adın ve yazım tarzınla senin adına yazar

## 🚀 Hızlı başlangıç

**Android:** APK'yı indir → kur → uygulamayı aç → klavyeyi etkinleştir.
Detay: [android/README.md](android/README.md)

**iPhone:** Siteyi Safari'de aç → Paylaş (⬆︎) → Ana Ekrana Ekle.
Sesli otomasyon için Kestrol tarifi: [web/README özeti aşağıda](#iphone-kestrol-otomasyonu)

## 🔑 Yapay zeka bağlantısı

Ücretsiz seçenek — **Groq**: console.groq.com → ücretsiz API anahtarı al →
uygulamada Sağlayıcı: Groq seç, anahtarı gir. Model: `llama-3.3-70b-versatile`.

OpenAI / OpenRouter / Ollama da desteklenir (OpenAI uyumlu her servis çalışır).

## 🔒 Gizlilik

API anahtarın sadece kendi cihazında saklanır; hiçbir sunucuya gönderilmez.
İstekler doğrudan seçtiğin AI sağlayıcısına gider.

## iPhone Kestrol Otomasyonu

Kısayollar uygulamasında yeni kısayol:
1. Metni Dikte Et
2. URL'nin İçeriğini Al → POST `https://api.groq.com/openai/v1/chat/completions`
   - Başlık: `Authorization` = `Bearer ANAHTARIN`, `Content-Type` = `application/json`
   - Gövde: model + messages JSON (bkz. android/README.md)
3. Sözlük Değeri Al → yol: `choices.1.message.content`
4. Panoya Kopyala

Artık her yerden: dikte et → kısayolu çalıştır → yapıştır ✅
