"use strict";

const AI = (() => {

  function getSettings() {
    try {
      return JSON.parse(localStorage.getItem("ak_settings")) || {};
    } catch {
      return {};
    }
  }

  function baseUrl() {
    const s = getSettings();
    let u = (s.apiBase || "https://api.openai.com/v1").trim().replace(/\/+$/, "");
    if (!/\/v\d+$/.test(u)) u += "/v1";
    return u;
  }

  function systemPrompt() {
    const s = getSettings();
    const name = s.userName || "Kullanıcı";
    const persona = s.userPersona || "Doğal, samimi ve kısa mesajlar yazar.";
    return [
      `Sen ${name} adlı kullanıcının kişisel klavye asistanısın.`,
      `Kullanıcı profili: ${persona}`,
      "Görevin: kullanıcının adına, karşı taraf ile ilişkisine uygun gerçekçi mesajlar üretmek.",
      "KURALLAR:",
      "- Gerçek bir insan gibi yaz; robotik veya klişe kalıplar kullanma.",
      "- Karşı tarafın üslubunu ve sohbetin tonunu birebir takip et.",
      "- Emoji kullanımını karşı tarafın tarzına göre ayarla; o kullanmıyorsa sen de kullanma.",
      "- Mesajlar kısa olsun, günlük mesajlaşma gibi görünsün.",
      "- Asla 'Yapay zeka olarak' gibi ifadeler kullanma."
    ].join("\n");
  }

  async function chat(messages, { temperature = 0.8, timeoutMs = 45000 } = {}) {
    const s = getSettings();
    if (!s.apiKey) {
      throw new Error("API anahtarı yok. Ayarlar sekmesinden anahtarınızı girin.");
    }
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    let res;
    try {
      res = await fetch(baseUrl() + "/chat/completions", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": "Bearer " + s.apiKey
        },
        body: JSON.stringify({
          model: s.model || "gpt-4o-mini",
          messages,
          temperature
        }),
        signal: controller.signal
      });
    } catch (e) {
      clearTimeout(timer);
      if (e.name === "AbortError") throw new Error("İstek zaman aşımına uğradı.");
      throw new Error("Bağlantı hatası: " + e.message + " (CORS engeli olabilir; farklı sağlayıcı deneyin)");
    }
    clearTimeout(timer);

    if (!res.ok) {
      let detail = "";
      try {
        const j = await res.json();
        detail = j.error && j.error.message ? j.error.message : JSON.stringify(j).slice(0, 200);
      } catch { detail = res.statusText; }
      throw new Error(`API hatası (${res.status}): ${detail}`);
    }
    const data = await res.json();
    const text = data.choices?.[0]?.message?.content;
    if (!text) throw new Error("API boş yanıt döndürdü.");
    return text.trim();
  }

  function extractJSON(text) {
    let t = text.replace(/```json\s*|```/g, "").trim();
    const start = t.indexOf("{");
    const end = t.lastIndexOf("}");
    if (start !== -1 && end > start) {
      t = t.slice(start, end + 1);
      try { return JSON.parse(t); } catch {}
    }
    const arrStart = t.indexOf("[");
    const arrEnd = t.lastIndexOf("]");
    if (arrStart !== -1 && arrEnd > arrStart) {
      try { return JSON.parse(t.slice(arrStart, arrEnd + 1)); } catch {}
    }
    return null;
  }

  async function suggestReplies({ personLabel, relationNote, history }) {
    const sys = systemPrompt();
    const user = [
      `Karşı taraf: ${personLabel}${relationNote ? " (" + relationNote + ")" : ""}`,
      "",
      "Son konuşma:",
      history || "(boş - ilk mesaj yazılacak)",
      "",
      "Görev:",
      "1. Sohbeti ve ilişkiyi analiz et (karşı tarafın ruh hali, tonu, ne beklediği).",
      "2. Kullanıcı adına gönderilmek üzere 3 alternatif mesaj üret. EN UYGUN olan İLK sırada olsun.",
      `3. Yanıtlar karşı tarafın üslubuna ve ilişkinin sıcaklığına birebir uysun.`,
      "",
      'SADECE şu JSON formatında yanıtla: {"analiz":"tek cümlelik ton analizi","oneriler":["mesaj1","mesaj2","mesaj3"]}'
    ].join("\n");
    const raw = await chat([
      { role: "system", content: sys },
      { role: "user", content: user }
    ], { temperature: 0.9 });
    const parsed = extractJSON(raw);
    if (parsed && Array.isArray(parsed.oneriler) && parsed.oneriler.length) {
      return {
        analiz: parsed.analiz || "",
        oneriler: parsed.oneriler.filter(x => typeof x === "string" && x.trim()).slice(0, 4)
      };
    }
    return { analiz: "", oneriler: [raw] };
  }

  async function fixText(text) {
    const raw = await chat([
      { role: "system", content: systemPrompt() },
      {
        role: "user",
        content:
          "Aşağıdaki metni düzelt: yazım hatalarını gider, eksik harfleri tamamla, " +
          "anlamı ve dilini KORU, anlamına hiçbir şey ekleme veya çıkarma. " +
          "Sadece düzeltilmiş metni döndür, açıklama yazma.\n\nMETİN:\n" + text
      }
    ], { temperature: 0.3 });
    return raw.replace(/^"|"$/g, "").trim();
  }

  async function translate(text, targetLang) {
    const raw = await chat([
      { role: "system", content: systemPrompt() },
      {
        role: "user",
        content:
          `Aşağıdaki metni ${targetLang} diline çevir. Tonu ve samimiyeti koru. ` +
          "Sadece çeviriyi döndür, açıklama yazma.\n\nMETİN:\n" + text
      }
    ], { temperature: 0.3 });
    return raw.replace(/^"|"$/g, "").trim();
  }

  async function cleanupSpeech(text) {
    const raw = await chat([
      { role: "system", content: systemPrompt() },
      {
        role: "user",
        content:
          "Bu bir sesli konuşma dökümüdür: noktalama yok, dolgu kelimeler var, dağınık olabilir. " +
          "Bunu anlamlı, akıcı, düzgün noktalanmış tek bir mesaj haline getir. Anlamı koru, yeni bilgi EKLEME. " +
          "Sadece düzenlenmiş metni döndür.\n\nDÖKÜM:\n" + text
      }
    ], { temperature: 0.4 });
    return raw.replace(/^"|"$/g, "").trim();
  }

  async function restyle(text, toneDesc) {
    const raw = await chat([
      { role: "system", content: systemPrompt() },
      {
        role: "user",
        content:
          `Aşağıdaki metni şu üslupta yeniden yaz: ${toneDesc}. ` +
          "Anlam korunacak ama üslup tamamen değişecek. Sadece metni döndür.\n\nMETİN:\n" + text
      }
    ], { temperature: 0.9 });
    return raw.replace(/^"|"$/g, "").trim();
  }

  async function testConnection() {
    const res = await fetch(baseUrl() + "/models", {
      headers: { "Authorization": "Bearer " + getSettings().apiKey }
    });
    if (!res.ok) throw new Error(`Bağlantı başarısız (${res.status})`);
    return true;
  }

  return { suggestReplies, fixText, translate, cleanupSpeech, restyle, testConnection, getSettings };
})();
