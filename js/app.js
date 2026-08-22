"use strict";

(() => {
  const $ = s => document.querySelector(s);
  const $$ = s => document.querySelectorAll(s);

  const DEFAULT_PERSONS = [
    { id: "p_sevgili", name: "Sevgili", note: "romantik, tatlı, özlem dolu yazışır" },
    { id: "p_arkadas", name: "Yakın Arkadaş", note: "esprili, rahat, laf sokmalı" },
    { id: "p_aile", name: "Aile", note: "saygılı ama doğal" },
    { id: "p_is", name: "İş / Okul", note: "resmi, net, kısa" }
  ];

  const state = {
    persons: [],
    activePersonId: null,
    deferredInstall: null,
    lastOutput: "",
    recognizing: false
  };

  function load(key, fallback) {
    try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; }
  }
  function save(key, val) { localStorage.setItem(key, JSON.stringify(val)); }

  function loadState() {
    const custom = load("ak_persons", []);
    state.persons = [...DEFAULT_PERSONS, ...custom];
    state.activePersonId = load("ak_activePerson", DEFAULT_PERSONS[0].id);
  }

  function toast(msg) {
    const t = $("#toast");
    t.textContent = msg;
    t.classList.remove("hidden");
    clearTimeout(toast._timer);
    toast._timer = setTimeout(() => t.classList.add("hidden"), 2200);
  }

  async function copyText(text) {
    try {
      await navigator.clipboard.writeText(text);
      toast("✓ Kopyalandı");
      return true;
    } catch {
      const ta = document.createElement("textarea");
      ta.value = text;
      document.body.appendChild(ta);
      ta.select();
      const ok = document.execCommand("copy");
      ta.remove();
      if (ok) toast("✓ Kopyalandı"); else toast("Kopyalanamadı, elle seçin");
      return ok;
    }
  }

  function setStatus(el, msg, type = "") {
    el.textContent = msg;
    el.classList.toggle("hidden", !msg);
    el.classList.toggle("error", type === "error");
    el.classList.toggle("ok", type === "ok");
  }

  async function runTask(btn, taskEl, fn) {
    btn.disabled = true;
    const old = btn.textContent;
    btn.textContent = "⏳ ...";
    setStatus(taskEl, "Düşünüyor...");
    try {
      const result = await fn();
      setStatus(taskEl, "");
      return result;
    } catch (e) {
      setStatus(taskEl, e.message, "error");
      return null;
    } finally {
      btn.disabled = false;
      btn.textContent = old;
    }
  }

  // ---------- Sekmeler ----------
  $$(".tab-btn").forEach(b => b.addEventListener("click", () => {
    $$(".tab-btn").forEach(x => x.classList.toggle("active", x === b));
    $$(".tab-page").forEach(p => p.classList.toggle("active", p.dataset.tab === b.dataset.target));
    window.scrollTo({ top: 0 });
  }));

  // ---------- Kişiler ----------
  function renderPersons() {
    const row = $("#personRow");
    row.innerHTML = "";
    state.persons.forEach(p => {
      const chip = document.createElement("button");
      chip.className = "person-chip" + (p.id === state.activePersonId ? " active" : "");
      chip.innerHTML = `<span>${escapeHtml(p.name)}</span>`;
      if (isCustom(p.id)) {
        const x = document.createElement("span");
        x.className = "x";
        x.textContent = "✕";
        x.addEventListener("click", ev => {
          ev.stopPropagation();
          removeCustomPerson(p.id);
        });
        chip.appendChild(x);
      }
      chip.addEventListener("click", () => {
        state.activePersonId = p.id;
        save("ak_activePerson", p.id);
        renderPersons();
      });
      row.appendChild(chip);
    });
    const add = document.createElement("button");
    add.className = "person-chip";
    add.textContent = "＋ Kişi";
    add.addEventListener("click", addCustomPerson);
    row.appendChild(add);
  }

  function isCustom(id) { return !DEFAULT_PERSONS.some(p => p.id === id); }

  function addCustomPerson() {
    const name = prompt("Kişinin adı:");
    if (!name || !name.trim()) return;
    const note = prompt("Nasıl yazışıyorsunuz? (örn: esprili konuşuruz, ona aşıkım)") || "";
    const person = { id: "p_" + Date.now(), name: name.trim(), note: note.trim() };
    state.persons.push(person);
    save("ak_persons", state.persons.filter(p => isCustom(p.id)));
    state.activePersonId = person.id;
    save("ak_activePerson", person.id);
    updatePersonCount();
    renderPersons();
  }

  function removeCustomPerson(id) {
    state.persons = state.persons.filter(p => p.id !== id);
    save("ak_persons", state.persons.filter(p => isCustom(p.id)));
    if (state.activePersonId === id) {
      state.activePersonId = DEFAULT_PERSONS[0].id;
      save("ak_activePerson", state.activePersonId);
    }
    updatePersonCount();
    renderPersons();
  }

  function updatePersonCount() {
    $("#personCount").textContent = String(state.persons.length);
  }

  function activePerson() {
    return state.persons.find(p => p.id === state.activePersonId) || DEFAULT_PERSONS[0];
  }

  // ---------- Cevap üretme ----------
  $("#genBtn").addEventListener("click", async () => {
    const history = $("#historyInput").value.trim();
    if (!history) { toast("Önce sohbeti yapıştır"); return; }
    const person = activePerson();
    const res = await runTask($("#genBtn"), $("#genStatus"), () =>
      AI.suggestReplies({
        personLabel: person.name,
        relationNote: [person.note, AI.getSettings().personNoteExtra].filter(Boolean).join("; "),
        history
      })
    );
    if (res) renderSuggestions(res.analiz, res.oneriler);
  });

  $("#whatNextBtn").addEventListener("click", async () => {
    let history = $("#historyInput").value.trim();
    if (!history) {
      $("#historyInput").value = "(henüz mesaj yok)";
      history = "(henüz mesaj yok - karşı taraftan ilk mesaj gelmek üzere)";
    }
    const person = activePerson();
    const res = await runTask($("#whatNextBtn"), $("#genStatus"), () =>
      AI.suggestReplies({
        personLabel: person.name,
        relationNote: person.note,
        history
      })
    );
    if (res) renderSuggestions(res.analiz, res.oneriler);
  });

  function renderSuggestions(analiz, list) {
    const box = $("#analysisCard");
    if (analiz) {
      box.classList.remove("hidden");
      $("#analysisText").textContent = analiz;
    } else {
      box.classList.add("hidden");
    }
    const wrap = $("#suggestions");
    wrap.innerHTML = "";
    list.forEach((text, i) => {
      const card = document.createElement("div");
      card.className = "suggestion" + (i === 0 ? " best" : "");
      card.innerHTML = (i === 0 ? '<span class="badge">EN UYGUN</span>' : "") +
        `<p>${escapeHtml(text)}</p>
         <div class="meta"><span>📋 Dokun → kopyala</span><span>💬 WhatsApp</span></div>`;
      card.querySelector(".meta span:last-child").addEventListener("click", ev => {
        ev.stopPropagation();
        openWhatsApp(text);
      });
      card.addEventListener("click", () => copyText(text));
      wrap.appendChild(card);
    });
  }

  function openWhatsApp(text) {
    window.open("https://wa.me/?text=" + encodeURIComponent(text), "_blank");
  }

  // ---------- Yaz sekmesi ----------
  const composeInput = $("#composeInput");

  function showComposeOutput(text) {
    state.lastOutput = text;
    $("#outputCard").classList.remove("hidden");
    $("#composeOutput").textContent = text;
  }

  $("#fixBtn").addEventListener("click", async () => {
    const t = composeInput.value.trim();
    if (!t) { toast("Metin gir"); return; }
    const fixed = await runTask($("#fixBtn"), $("#composeStatus"), () => AI.fixText(t));
    if (fixed) showComposeOutput(fixed);
  });

  $("#translateBtn").addEventListener("click", async () => {
    const t = composeInput.value.trim();
    if (!t) { toast("Metin gir"); return; }
    const lang = $("#langSelect").value;
    const out = await runTask($("#translateBtn"), $("#composeStatus"), () => AI.translate(t, lang));
    if (out) showComposeOutput(out);
  });

  $$("#toneChips .chip").forEach(chip => chip.addEventListener("click", async () => {
    const t = composeInput.value.trim();
    if (!t) { toast("Metin gir"); return; }
    $$("#toneChips .chip").forEach(c => c.classList.toggle("active", c === chip));
    const out = await runTask(chip, $("#composeStatus"), () => AI.restyle(t, chip.dataset.tone));
    if (out) showComposeOutput(out);
  }));

  $("#copyOutputBtn").addEventListener("click", () => copyText(state.lastOutput));
  $("#editInComposeBtn").addEventListener("click", () => {
    composeInput.value = state.lastOutput;
    $("#outputCard").classList.add("hidden");
    toast("Düzenlemek için taşındı");
  });
  $("#sendWhatsBtn").addEventListener("click", () => state.lastOutput && openWhatsApp(state.lastOutput));

  // ---------- Sesli sekme ----------
  let recognition = null;
  let finalTranscript = "";

  function initRecognition() {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) return null;
    const rec = new SR();
    rec.continuous = true;
    rec.interimResults = true;
    rec.lang = "tr-TR";
    rec.onresult = ev => {
      let interim = "";
      for (let i = ev.resultIndex; i < ev.results.length; i++) {
        const chunk = ev.results[i][0].transcript;
        if (ev.results[i].isFinal) finalTranscript += chunk + " ";
        else interim += chunk;
      }
      $("#voiceHint").textContent = (finalTranscript + interim).slice(-120) || "Dinliyorum...";
    };
    rec.onend = onVoiceEnd;
    rec.onerror = ev => {
      setStatus($("#voiceStatus"), ev.error === "not-allowed"
        ? "Mikrofon izni gerekli." : "Ses tanıma hatası: " + ev.error, "error");
    };
    return rec;
  }

  $("#micBtn").addEventListener("click", () => {
    if (state.recognizing) {
      recognition && recognition.stop();
      return;
    }
    recognition = initRecognition();
    if (!recognition) {
      setStatus($("#voiceStatus"),
        "Bu tarayıcı ses tanımayı desteklemiyor. iPhone'da klavyedeki 🎙️ dikte düğmesini kullanıp 'Düzenle' ile metni buraya yapıştırabilirsin.", "error");
      return;
    }
    finalTranscript = "";
    state.recognizing = true;
    $("#micBtn").classList.add("recording");
    $("#voiceHint").textContent = "Dinliyorum...";
    setStatus($("#voiceStatus"), "Konuş, bitirince tekrar dokun.");
    try { recognition.start(); } catch {}
  });

  async function onVoiceEnd() {
    state.recognizing = false;
    $("#micBtn").classList.remove("recording");
    const raw = finalTranscript.trim() || $("#rawText").textContent.trim();
    if (!raw) { setStatus($("#voiceStatus"), "Ses algılanmadı.", "error"); return; }

    $("#rawCard").classList.remove("hidden");
    $("#rawText").textContent = raw;

    setStatus($("#voiceStatus"), "AI mesajı düzenliyor...");
    try {
      const clean = await AI.cleanupSpeech(raw);
      $("#cleanCard").classList.remove("hidden");
      $("#cleanText").textContent = clean;
      setStatus($("#voiceStatus"), "✓ Hazır. Dokunarak kopyala.");
      $("#voiceStatus").classList.add("ok");
      copyText(clean);
    } catch (e) {
      setStatus($("#voiceStatus"), e.message, "error");
    }
  }

  $("#copyCleanBtn").addEventListener("click", () => copyText($("#cleanText").textContent));

  // ---------- Ayarlar ----------
  const PRESETS = {
    openai:     { base: "https://api.openai.com/v1",          model: "gpt-4o-mini" },
    groq:       { base: "https://api.groq.com/openai/v1",     model: "llama-3.3-70b-versatile" },
    openrouter: { base: "https://openrouter.ai/api/v1",       model: "google/gemini-2.0-flash-001" },
    custom:     { base: "",                                    model: "" }
  };

  function fillSettingsForm() {
    const s = AI.getSettings();
    $("#apiBaseInput").value = s.apiBase || PRESETS.openai.base;
    $("#apiKeyInput").value = s.apiKey || "";
    $("#modelInput").value = s.model || PRESETS.openai.model;
    $("#userNameInput").value = s.userName || "";
    $("#personaInput").value = s.userPersona || "";
    $("#presetSelect").value = guessPreset(s.apiBase);
  }

  function guessPreset(base) {
    for (const [k, v] of Object.entries(PRESETS)) {
      if (v.base && base && base.replace(/\/+$/, "") === v.base) return k;
    }
    return base ? "custom" : "openai";
  }

  $("#presetSelect").addEventListener("change", e => {
    const p = PRESETS[e.target.value];
    if (p && p.base) {
      $("#apiBaseInput").value = p.base;
      $("#modelInput").value = p.model;
    }
  });

  $("#saveSettingsBtn").addEventListener("click", () => {
    save("ak_settings", {
      apiBase: $("#apiBaseInput").value.trim(),
      apiKey: $("#apiKeyInput").value.trim(),
      model: $("#modelInput").value.trim(),
      userName: $("#userNameInput").value.trim(),
      userPersona: $("#personaInput").value.trim()
    });
    toast("💾 Ayarlar kaydedildi");
    fillSettingsForm();
  });

  $("#testBtn").addEventListener("click", async () => {
    $("#saveSettingsBtn").click();
    const st = $("#testStatus");
    setStatus(st, "Bağlanıyor...");
    try {
      await AI.testConnection();
      setStatus(st, "✓ Bağlantı başarılı!", "ok");
    } catch (e) {
      setStatus(st, e.message, "error");
    }
  });

  $("#clearDataBtn").addEventListener("click", () => {
    if (!confirm("Tüm ayarlar ve kişiler silinsin mi?")) return;
    ["ak_settings", "ak_persons", "ak_activePerson"].forEach(k => localStorage.removeItem(k));
    location.reload();
  });

  // ---------- Sonuç ekranı (otomasyon için) ----------
  function showResultOverlay(tag, before, after) {
    $("#resultTag").textContent = tag;
    const b = $("#resultBefore");
    if (before) { b.classList.remove("hidden"); b.textContent = before; } else b.classList.add("hidden");
    $("#resultAfter").textContent = after;
    $("#resultOverlay").classList.remove("hidden");
    copyText(after);
  }
  $("#resultCopyBtn").addEventListener("click", () => copyText($("#resultAfter").textContent));
  $("#resultCloseBtn").addEventListener("click", () => $("#resultOverlay").classList.add("hidden"));

  async function handleUrlParams() {
    const q = new URLSearchParams(location.search);
    const mode = q.get("mode");
    const text = q.get("text");
    if (!mode || !text) return;
    history.replaceState(null, "", location.pathname);
    const lang = q.get("lang") || "İngilizce";
    const tags = { fix: "🛠 DÜZELTİLDİ", translate: `🌐 ${lang.toUpperCase()}`, voice: "🎙️ DÜZENLENDİ" };
    try {
      let out;
      if (mode === "translate") out = await AI.translate(text, lang);
      else if (mode === "fix") out = await AI.fixText(text);
      else out = await AI.cleanupSpeech(text);
      showResultOverlay(tags[mode] || "SONUÇ", text, out);
    } catch (e) {
      showResultOverlay("HATA", text, e.message);
    }
  }

  // ---------- PWA ----------
  window.addEventListener("beforeinstallprompt", e => {
    e.preventDefault();
    state.deferredInstall = e;
    $("#installBtn").classList.remove("hidden");
  });
  $("#installBtn").addEventListener("click", async () => {
    if (state.deferredInstall) {
      state.deferredInstall.prompt();
      state.deferredInstall = null;
      $("#installBtn").classList.add("hidden");
    }
  });
  if ("serviceWorker" in navigator && location.protocol.startsWith("http")) {
    navigator.serviceWorker.register("sw.js").catch(() => {});
  }

  function escapeHtml(s) {
    return s.replace(/[&<>"']/g, c => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[c]);
  }

  // ---------- Başlat ----------
  loadState();
  renderPersons();
  updatePersonCount();
  fillSettingsForm();

  if (!AI.getSettings().apiKey) {
    setStatus($("#genStatus"),
      "Başlamak için ⚙️ Ayarlar sekmesinden API anahtarını gir. (Groq ücretsizdir)", "error");
  }

  handleUrlParams();
})();
