package com.aiklavye.kb;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;

public class ImeService extends InputMethodService {

    private static final int BG = 0xFF0E1220;
    private static final int KEY_BG = 0xFF232941;
    private static final int KEY_TEXT = 0xFFEEF0F6;
    private static final int ACCENT = 0xFF7C5CFF;
    private static final int GOOD = 0xFF2DD4A7;
    private static final int PANEL_BG = 0xFF161B2C;

    private static final String[] PERSONS = {"Sevgili", "Yakın Arkadaş", "Aile", "İş/Okul"};
    private static final String[] PERSON_NOTES = {
            "romantik, tatlı, özlem dolu",
            "esprili, rahat, laf sokmalı",
            "saygılı ama doğal",
            "resmi, net, kısa"
    };
    private static final String[] LANGS = {
            "İngilizce", "Almanca", "Türkçe", "Fransızca",
            "İspanyolca", "Arapça", "Rusça", "İtalyanca"
    };

    private LinearLayout root;
    private FrameLayout keyArea;
    private LinearLayout aiPanel;
    private EditText convoInput;
    private LinearLayout resultsBox;
    private LinearLayout suggestionStrip;
    private LinearLayout personRow;
    private TextView statusView;

    private boolean shiftOn = true;
    private boolean symbolMode = false;
    private int selectedPerson = 0;
    private Button shiftBtn;
    private SpeechRecognizer recognizer;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float dp = getResources().getDisplayMetrics().density;

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(4), dp(2), dp(4), dp(4));

        root.addView(buildToolbar());
        root.addView(buildStrip());
        root.addView(buildAiPanel());
        root.addView(buildKeyArea());
        return root;
    }

    // ---------- UI bölümleri ----------

    private View buildToolbar() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        row.addView(toolBtn("🎙️ Sesli", v -> startVoice()));
        row.addView(toolBtn("✨ Öner", v -> {
            aiPanel.setVisibility(aiPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        }));
        row.addView(toolBtn("🛠 Düzelt", v -> fixBeforeCursor()));
        row.addView(toolBtn("🌐 Çevir", v -> pickLanguageAndTranslate()));
        row.addView(toolBtn("❓ Sor", v -> askDialog()));
        row.addView(toolBtn("⚙️", v -> openSettings()));
        hs.addView(row);
        return hs;
    }

    private View buildStrip() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        suggestionStrip = new LinearLayout(this);
        suggestionStrip.setOrientation(LinearLayout.HORIZONTAL);
        suggestionStrip.setPadding(0, 0, 0, dp(4));
        hs.addView(suggestionStrip);
        showTip();
        return hs;
    }

    private void showTip() {
        suggestionStrip.removeAllViews();
        suggestionStrip.addView(chip("💡 Sohbeti yapıştır → ✨ Öner", null, 0xFF262B3D));
    }

    private View buildAiPanel() {
        aiPanel = new LinearLayout(this);
        aiPanel.setOrientation(LinearLayout.VERTICAL);
        aiPanel.setBackgroundColor(PANEL_BG);
        aiPanel.setPadding(dp(10), dp(8), dp(10), dp(10));
        aiPanel.setVisibility(View.GONE);

        personRow = new LinearLayout(this);
        personRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < PERSONS.length; i++) {
            final int idx = i;
            personRow.addView(personChip(i));
        }
        aiPanel.addView(personRow);

        convoInput = new EditText(this);
        convoInput.setHint("Son mesajları yapıştır...\nElif: bugün ne yapıyorsun\nBen: evdeyim");
        convoInput.setTextSize(13);
        convoInput.setTextColor(KEY_TEXT);
        convoInput.setHintTextColor(0xFF6B7290);
        convoInput.setBackground(roundRect(0xFF1F2437, 10));
        convoInput.setPadding(dp(8), dp(6), dp(8), dp(6));
        convoInput.setMinLines(2);
        convoInput.setMaxLines(5);
        convoInput.setVerticalScrollBarEnabled(true);
        aiPanel.addView(convoInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout genRow = new LinearLayout(this);
        genRow.setOrientation(LinearLayout.HORIZONTAL);
        genRow.setPadding(0, dp(6), 0, 0);
        Button gen = pillButton("✨ Cevap Üret", ACCENT, Color.WHITE);
        gen.setOnClickListener(v -> generateSuggestions());
        genRow.addView(gen, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button close = pillButton("✕", 0xFF262B3D, KEY_TEXT);
        close.setOnClickListener(v -> aiPanel.setVisibility(View.GONE));
        genRow.addView(close, new LinearLayout.LayoutParams(dp(44),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        aiPanel.addView(genRow);

        resultsBox = new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        aiPanel.addView(resultsBox);
        return aiPanel;
    }

    private View personChip(int idx) {
        TextView t = new TextView(this);
        t.setText(PERSONS[idx]);
        t.setTextSize(12);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        t.setBackground(roundRect(idx == selectedPerson ? ACCENT : 0xFF262B3D, 20));
        t.setTextColor(idx == selectedPerson ? Color.WHITE : 0xFFB9BED1);
        t.setOnClickListener(v -> {
            selectedPerson = idx;
            personRow.removeAllViews();
            for (int i = 0; i < PERSONS.length; i++) personRow.addView(personChip(i));
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        lp.bottomMargin = dp(4);
        t.setLayoutParams(lp);
        return t;
    }

    private View buildKeyArea() {
        keyArea = new FrameLayout(this);
        keyArea.addView(buildLetters(), matchParent());
        keyArea.addView(buildSymbols(), matchParent());
        applyMode();
        return keyArea;
    }

    private LinearLayout buildLetters() {
        LinearLayout page = pageContainer("letters");
        String[][] rows = {
                {"q", "w", "e", "r", "t", "y", "u", "ı", "o", "p", "ğ", "ü"},
                {"a", "s", "d", "f", "g", "h", "j", "k", "l", "ş", "i"},
                {"SHIFT", "z", "x", "c", "v", "b", "n", "m", "ö", "ç", "BS"},
                {"123", ",", "SPACE", ".", "ENTER"}
        };
        fillRows(page, rows);
        return page;
    }

    private LinearLayout buildSymbols() {
        LinearLayout page = pageContainer("symbols");
        String[][] rows = {
                {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
                {"@", "#", "₺", "%", "&", "*", "-", "+", "(", ")"},
                {"ABC", "!", "?", ":", ";", "'", "\"", "_", "/", "BS"},
                {"ABC", ",", "SPACE", ".", "ENTER"}
        };
        fillRows(page, rows);
        return page;
    }

    private LinearLayout pageContainer(String tag) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setTag(tag);
        return page;
    }

    private void fillRows(LinearLayout page, String[][] rows) {
        for (String[] r : rows) {
            LinearLayout rowL = new LinearLayout(this);
            rowL.setOrientation(LinearLayout.HORIZONTAL);
            for (String k : r) rowL.addView(makeKey(k, rowL));
            page.addView(rowL, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        }
    }

    private View makeKey(String k, LinearLayout parent) {
        switch (k) {
            case "SHIFT": {
                shiftBtn = specialKey("⇧");
                shiftBtn.setOnClickListener(v -> {
                    shiftOn = !shiftOn;
                    applyShiftVisual();
                });
                parent.addView(shiftBtn, weight(1.4f));
                return shiftBtn;
            }
            case "BS": {
                Button bs = specialKey("⌫");
                bs.setOnClickListener(v -> del(1));
                startRepeatOnLongPress(bs);
                parent.addView(bs, weight(1.4f));
                return bs;
            }
            case "SPACE": {
                Button sp = specialKey("boşluk");
                sp.setOnClickListener(v -> commit(" "));
                parent.addView(sp, weight(4f));
                return sp;
            }
            case "ENTER": {
                Button en = specialKey("↵");
                en.setOnClickListener(v -> pressEnter());
                parent.addView(en, weight(1.6f));
                return en;
            }
            case "123":
            case "ABC": {
                Button m = specialKey(k.equals("123") ? "?123" : "ABC");
                m.setOnClickListener(v -> {
                    symbolMode = !symbolMode;
                    applyMode();
                });
                parent.addView(m, weight(1.4f));
                return m;
            }
            default: {
                Button b = new Button(this);
                b.setText(k);
                b.setTextSize(16);
                b.setTypeface(null, Typeface.BOLD);
                b.setTextColor(KEY_TEXT);
                b.setBackground(roundRect(KEY_BG, 10));
                b.setAllCaps(false);
                b.setPadding(0, 0, 0, 0);
                b.setOnClickListener(v -> commit(displayChar(k)));
                parent.addView(b, weight(1f));
                return b;
            }
        }
    }

    private boolean capsLocked() {
        return false;
    }

    private void applyShiftVisual() {
        if (shiftBtn != null) {
            shiftBtn.setBackground(roundRect(shiftOn ? ACCENT : KEY_BG, 10));
        }
        refreshCaseLabels();
    }

    private void refreshCaseLabels() {
        // harf sayfalarındaki tuş etiketlerini shift durumuna göre güncelle
        LinearLayout letters = (LinearLayout) keyArea.findViewWithTag("letters");
        updateRowCase(letters);
    }

    private void updateRowCase(LinearLayout page) {
        if (page == null) return;
        for (int i = 0; i < page.getChildCount(); i++) {
            View v = page.getChildAt(i);
            if (!(v instanceof LinearLayout)) continue;
            LinearLayout rowL = (LinearLayout) v;
            for (int j = 0; j < rowL.getChildCount(); j++) {
                View kv = rowL.getChildAt(j);
                if (kv instanceof Button) {
                    Button b = (Button) kv;
                    CharSequence txt = b.getText();
                    if (txt.length() == 1 && Character.isLetter(txt.charAt(0))) {
                        b.setText(shiftOn ? txt.toString().toUpperCase() : txt.toString().toLowerCase());
                    }
                }
            }
        }
    }

    private String displayChar(String base) {
        String ch = shiftOn ? base.toUpperCase() : base.toLowerCase();
        if (shiftOn && !symbolMode) {
            handler.postDelayed(() -> {
                shiftOn = false;
                applyShiftVisual();
            }, 60);
        }
        return ch;
    }

    private void applyMode() {
        for (int i = 0; i < keyArea.getChildCount(); i++) {
            View p = keyArea.getChildAt(i);
            String tag = (String) p.getTag();
            boolean show = symbolMode ? "symbols".equals(tag) : "letters".equals(tag);
            p.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    // ---------- Girdi işlemleri ----------

    private void commit(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.commitText(s, 1);
    }

    private void del(int n) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) ic.commitText("", 1);
        else ic.deleteSurroundingText(n, 0);
    }

    private void pressEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei != null && (ei.inputType & EditorInfo.TYPE_CLASS_NUMBER) != 0) {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            return;
        }
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
    }

    private void insertResult(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) ic.commitText("", 1);
        ic.commitText(text, 1);
        Toast.makeText(this, "✓ Eklendi", Toast.LENGTH_SHORT).show();
    }

    // ---------- AI eylemleri ----------

    private void setStatus(String msg) {
        suggestionStrip.removeAllViews();
        suggestionStrip.addView(chip(msg, null, 0xFF262B3D));
    }

    private String beforeCursor(int n) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";
        CharSequence cs = ic.getTextBeforeCursor(n, 0);
        return cs == null ? "" : cs.toString();
    }

    private void replaceBeforeCursor(int len, String newText) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) {
            ic.commitText(newText, 1);
            return;
        }
        ic.deleteSurroundingText(len, 0);
        ic.commitText(newText, 1);
    }

    private void fixBeforeCursor() {
        CharSequence selRaw = getCurrentInputConnection() == null
                ? null : getCurrentInputConnection().getSelectedText(0);
        boolean hasSel = selRaw != null && selRaw.length() > 0;
        String text = hasSel ? selRaw.toString() : lastSentence(beforeCursor(400));
        if (text.trim().isEmpty()) {
            toast("Düzeltilecek metin yok");
            return;
        }
        setStatus("🛠 AI düzeltiyor...");
        AiClient.fixText(this, text, new AiClient.Cb() {
            @Override
            public void ok(String result) {
                if (hasSel) getCurrentInputConnection().commitText(result, 1);
                else replaceBeforeCursor(text.length(), result);
                setStatus("✓ Düzeltildi");
                handler.postDelayed(ImeService.this::showTip, 2500);
            }

            @Override
            public void err(String m) {
                setStatus("⚠ " + m);
            }
        });
    }

    private String lastSentence(String text) {
        String t = text;
        for (int i = t.length() - 1; i >= 0; i--) {
            char c = t.charAt(i);
            if ((c == '.' || c == '!' || c == '?' || c == '\n') && i < t.length() - 1) {
                return t.substring(i + 1);
            }
        }
        return t;
    }

    private void pickLanguageAndTranslate() {
        try {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Hangi dile çevrilsin?")
                    .setItems(LANGS, (d, which) -> translateBeforeCursor(LANGS[which]))
                    .setNegativeButton("İptal", null)
                    .show();
        } catch (Exception e) {
            translateBeforeCursor("İngilizce");
        }
    }

    private void translateBeforeCursor(String lang) {
        CharSequence selRaw = getCurrentInputConnection() == null
                ? null : getCurrentInputConnection().getSelectedText(0);
        boolean hasSel = selRaw != null && selRaw.length() > 0;
        String text = hasSel ? selRaw.toString() : beforeCursor(800);
        if (text.trim().isEmpty()) {
            toast("Çevrilecek metin yok");
            return;
        }
        setStatus("🌐 Çeviriliyor (" + lang + ")...");
        AiClient.translate(this, text, lang, new AiClient.Cb() {
            @Override
            public void ok(String result) {
                if (hasSel) getCurrentInputConnection().commitText(result, 1);
                else replaceBeforeCursor(text.length(), result);
                setStatus("✓ Çevrildi");
                handler.postDelayed(ImeService.this::showTip, 2500);
            }

            @Override
            public void err(String m) {
                setStatus("⚠ " + m);
            }
        });
    }

    private void generateSuggestions() {
        String history = convoInput.getText().toString();
        resultsBox.removeAllViews();
        setStatus("✨ AI cevap üretiyor...");
        AiClient.suggestReplies(this, PERSONS[selectedPerson], PERSON_NOTES[selectedPerson],
                history, new AiClient.Cb() {
                    @Override
                    public void ok(String raw) {
                        String analysis = AiClient.parseAnalysis(raw);
                        String[] list = AiClient.parseSuggestions(raw);
                        renderSuggestions(list, analysis);
                    }

                    @Override
                    public void err(String m) {
                        setStatus("⚠ " + m);
                    }
                });
    }

    private void renderSuggestions(String[] list, String analysis) {
        resultsBox.removeAllViews();
        suggestionStrip.removeAllViews();

        if (analysis != null && !analysis.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("🔍 " + analysis);
            tv.setTextSize(11);
            tv.setTextColor(0xFF9AA0B8);
            tv.setPadding(dp(4), dp(2), dp(4), dp(4));
            resultsBox.addView(tv);
        }

        for (int i = 0; i < list.length; i++) {
            final String text = list[i];
            String label = (i == 0 ? "⭐ EN UYGUN: " : "") + truncate(text, 42);
            Button b = pillButton(label, i == 0 ? GOOD : 0xFF262B3D, i == 0 ? 0xFF06281E : KEY_TEXT);
            b.setOnClickListener(v -> insertResult(text));
            resultsBox.addView(b, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView chipV = chip(truncate(text, 22), v -> insertResult(text),
                    i == 0 ? GOOD : 0xFF262B3D);
            suggestionStrip.addView(chipV);
        }
    }

    private void askDialog() {
        try {
            final EditText q = new EditText(this);
            q.setHint("Bir şey sor... (kelime anlamı, nasıl yazılır)");
            q.setTextColor(KEY_TEXT);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(16), dp(8), dp(16), 0);
            box.addView(q);

            new android.app.AlertDialog.Builder(this)
                    .setTitle("❓ Yapay Zekaya Sor")
                    .setView(box)
                    .setPositiveButton("Sor", (d, w) -> askAi(q))
                    .setNegativeButton("İptal", null)
                    .show();
        } catch (Exception e) {
            toast("Diyalog açılamadı");
        }
    }

    private void askAi(EditText q) {
        String question = q.getText().toString().trim();
        if (question.isEmpty()) return;
        setStatus("❓ Soruluyor...");
        AiClient.ask(this, question, new AiClient.Cb() {
            @Override
            public void ok(String result) {
                showAnswerDialog(question, result);
            }

            @Override
            public void err(String m) {
                setStatus("⚠ " + m);
            }
        });
    }

    private void showAnswerDialog(String question, String answer) {
        suggestionStrip.removeAllViews();
        suggestionStrip.addView(chip("💬 " + truncate(answer, 30), v -> insertResult(answer), GOOD));
        try {
            TextView tv = new TextView(this);
            tv.setText(answer);
            tv.setTextSize(15);
            tv.setTextColor(KEY_TEXT);
            tv.setPadding(dp(16), dp(8), dp(16), 0);
            new android.app.AlertDialog.Builder(this)
                    .setTitle(question)
                    .setView(tv)
                    .setPositiveButton("Mesaja Ekle", (d, w) -> insertResult(answer))
                    .setNegativeButton("Kapat", null)
                    .show();
        } catch (Exception ignored) {
        }
    }

    private void openSettings() {
        Intent i = new Intent(this, SettingsActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    // ---------- Sesli giriş ----------

    private void startVoice() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            toast("Mikrofon izni yok - AI Klavye uygulamasını açıp izin ver");
            openSettings();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Bu cihazda ses tanıma yok - klavyedeki 🎙️ dikteyi kullan");
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onResults(android.os.Bundle results) {
                    ArrayList<String> list =
                            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (list != null && !list.isEmpty()) {
                        setStatus("🎙 AI düzenliyor...");
                        AiClient.cleanupSpeech(ImeService.this, list.get(0), new AiClient.Cb() {
                            @Override
                            public void ok(String result) {
                                insertResult(result);
                                setStatus("✓ Hazır");
                                handler.postDelayed(ImeService.this::showTip, 2000);
                            }

                            @Override
                            public void err(String m) {
                                insertResult(list.get(0));
                                setStatus("⚠ " + m + " (ham metin eklendi)");
                            }
                        });
                    } else {
                        showTip();
                    }
                }

                @Override
                public void onError(int error) {
                    setStatus(error == SpeechRecognizer.ERROR_NO_MATCH
                            ? "🎙 Ses algılanmadı" : "🎙 Hata kodu: " + error);
                    handler.postDelayed(ImeService.this::showTip, 2000);
                }

                @Override
                public void onReadyForSpeech(android.os.Bundle params) {
                    setStatus("🎙 Dinliyorum...");
                }

                @Override
                public void onBeginningOfSpeech() {
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                    setStatus("🎙 İşleniyor...");
                }

                @Override
                public void onPartialResults(android.os.Bundle partialResults) {
                }

                @Override
                public void onEvent(int eventType, android.os.Bundle params) {
                }
            });
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
        recognizer.startListening(i);
    }

    // ---------- Yardımcılar ----------

    private void startRepeatOnLongPress(final Button btn) {
        final Runnable[] rep = new Runnable[1];
        btn.setOnLongClickListener(v -> {
            rep[0] = new Runnable() {
                @Override
                public void run() {
                    del(1);
                    handler.postDelayed(this, 55);
                }
            };
            handler.postDelayed(rep[0], 320);
            return true;
        });
        btn.setOnTouchListener((v, e) -> {
            if ((e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL)
                    && rep[0] != null) {
                handler.removeCallbacks(rep[0]);
            }
            return false;
        });
    }

    private Button toolBtn(String label, View.OnClickListener l) {
        Button b = pillButton(label, 0xFF1A2036, KEY_TEXT);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private Button pillButton(String label, int bgColor, int textColor) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(textColor);
        b.setBackground(roundRect(bgColor, 20));
        b.setAllCaps(false);
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        return b;
    }

    private TextView chip(String label, View.OnClickListener l, int bgColor) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(13);
        t.setTextColor(bgColor == GOOD ? 0xFF06281E : KEY_TEXT);
        t.setBackground(roundRect(bgColor, 18));
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        t.setMaxWidth(dp(220));
        if (l != null) t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    private Button specialKey(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(label.length() > 2 ? 12 : 17);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(KEY_TEXT);
        b.setBackground(roundRect(KEY_BG, 10));
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private GradientDrawable roundRect(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusDp * dp);
        return g;
    }

    private LinearLayout.LayoutParams weight(float w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, w);
        lp.setMargins(dp(2), 0, dp(2), dp(3));
        return lp;
    }

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private int dp(float v) {
        return (int) (v * dp);
    }

    private String truncate(String s, int n) {
        s = s.replace("\n", " ");
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFinishInput() {
        shiftOn = true;
        symbolMode = false;
        super.onFinishInput();
    }

    @Override
    public void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        super.onDestroy();
    }
}
