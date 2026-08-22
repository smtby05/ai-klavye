package com.aiklavye.kb;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class AiClient {

    public interface Cb {
        void ok(String result);

        void err(String message);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static String systemPrompt(Context c) {
        Prefs p = new Prefs(c);
        String name = p.userName().isEmpty() ? "Kullanıcı" : p.userName();
        return "Sen " + name + " adlı kullanıcının kişisel klavye asistanısın.\n"
                + "Kullanıcı profili: " + p.persona() + "\n"
                + "Görevin: kullanıcının adına, karşı taraf ile ilişkisine uygun gerçekçi mesajlar üretmek.\n"
                + "KURALLAR:\n"
                + "- Gerçek bir insan gibi yaz; robotik veya klişe kalıplar kullanma.\n"
                + "- Karşı tarafın üslubunu ve sohbetin tonunu birebir takip et.\n"
                + "- Emoji kullanımını karşı tarafın tarzına göre ayarla; o kullanmıyorsa sen de kullanma.\n"
                + "- Mesajlar kısa olsun, günlük mesajlaşma gibi görünsün.\n"
                + "- Asla 'Yapay zeka olarak' gibi ifadeler kullanma.";
    }

    public static void chat(final Context c, final JSONArray messages, final double temp, final Cb cb) {
        final Prefs p = new Prefs(c);
        if (p.apiKey().isEmpty()) {
            cb.err("Önce AI Klavye uygulamasını açıp API anahtarı gir.");
            return;
        }
        new Thread(() -> {
            try {
                String base = p.baseUrl().replaceAll("/+$", "");
                if (!base.matches(".*\\/v\\d+$")) base += "/v1";
                HttpURLConnection con = (HttpURLConnection) new URL(base + "/chat/completions").openConnection();
                con.setRequestMethod("POST");
                con.setConnectTimeout(30000);
                con.setReadTimeout(60000);
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/json");
                con.setRequestProperty("Authorization", "Bearer " + p.apiKey());

                JSONObject body = new JSONObject();
                body.put("model", p.model());
                body.put("temperature", temp);
                body.put("messages", messages);

                try (OutputStream os = con.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }

                int code = con.getResponseCode();
                InputStream is = code >= 400 ? con.getErrorStream() : con.getInputStream();
                String resp = read(is);
                if (code >= 400) throw new Exception("API hatası " + code + ": " + shorten(resp));
                JSONObject j = new JSONObject(resp);
                String content = j.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content");
                final String out = content.trim();
                MAIN.post(() -> cb.ok(out));
            } catch (final Exception e) {
                String m = e.getMessage() == null ? "Bilinmeyen hata" : e.getMessage();
                if (e instanceof java.net.SocketTimeoutException) m = "Zaman aşımı - tekrar dene.";
                if (e instanceof java.io.IOException && m.contains("Unable to resolve"))
                    m = "İnternet bağlantısı yok.";
                final String msg = m;
                MAIN.post(() -> cb.err(msg));
            }
        }).start();
    }

    public static void suggestReplies(Context c, String personLabel, String relationNote,
                                      String history, Cb cb) {
        try {
            JSONArray msgs = new JSONArray();
            msgs.put(new JSONObject()
                    .put("role", "system").put("content", systemPrompt(c)));
            StringBuilder u = new StringBuilder();
            u.append("Karşı taraf: ").append(personLabel);
            if (relationNote != null && !relationNote.isEmpty())
                u.append(" (").append(relationNote).append(")");
            u.append("\n\nSon konuşma:\n")
             .append(history == null || history.trim().isEmpty()
                     ? "(boş - ilk mesaj yazılacak)" : history)
             .append("\n\nGörev:\n")
             .append("1. Sohbeti ve ilişkiyi analiz et.\n")
             .append("2. Kullanıcı adına 3 alternatif mesaj üret. EN UYGUN olan İLK sırada olsun.\n")
             .append("3. Yanıtlar karşı tarafın üslubuna uysun.\n\n")
             .append("SADECE şu JSON formatında yanıtla: ")
             .append("{\"analiz\":\"tek cümlelik ton analizi\",\"oneriler\":[\"mesaj1\",\"mesaj2\",\"mesaj3\"]}");
            msgs.put(new JSONObject().put("role", "user").put("content", u.toString()));
            chat(c, msgs, 0.9, new Cb() {
                @Override
                public void ok(String result) {
                    cb.ok(result);
                }

                @Override
                public void err(String message) {
                    cb.err(message);
                }
            });
        } catch (Exception e) {
            cb.err(e.getMessage());
        }
    }

    public static void fixText(Context c, String text, Cb cb) {
        simple(c, "Aşağıdaki metni düzelt: yazım hatalarını gider, anlamı KORU, bir şey ekleme veya çıkarma. Sadece düzeltilmiş metni döndür.\n\nMETİN:\n" + text, 0.3, strip(cb));
    }

    public static void translate(Context c, String text, String lang, Cb cb) {
        simple(c, "Metni " + lang + " diline çevir. Tonu ve samimiyeti koru. Sadece çeviriyi döndür.\n\nMETİN:\n" + text, 0.3, strip(cb));
    }

    public static void cleanupSpeech(Context c, String text, Cb cb) {
        simple(c, "Bu sesli konuşma dökümüdür: noktalama yok, dağınık olabilir. Anlamlı, akıcı, düzgün noktalanmış tek mesaj haline getir. Anlamı koru, yeni bilgi EKLEME. Sadece düzenlenmiş metni döndür.\n\nDÖKÜM:\n" + text, 0.4, strip(cb));
    }

    public static void ask(Context c, String question, Cb cb) {
        simple(c, question, 0.5, strip(cb));
    }

    private static Cb strip(Cb cb) {
        return new Cb() {
            @Override
            public void ok(String result) {
                cb.ok(result.replaceAll("^\"|\"$", ""));
            }

            @Override
            public void err(String message) {
                cb.err(message);
            }
        };
    }

    private static void simple(Context c, String userMsg, double temp, Cb cb) {
        try {
            JSONArray msgs = new JSONArray();
            msgs.put(new JSONObject().put("role", "system").put("content", systemPrompt(c)));
            msgs.put(new JSONObject().put("role", "user").put("content", userMsg));
            chat(c, msgs, temp, cb);
        } catch (Exception e) {
            cb.err(e.getMessage());
        }
    }

    public static String[] parseSuggestions(String raw) {
        try {
            String t = raw.replace("```json", "").replace("```", "");
            int s = t.indexOf('{');
            int e = t.lastIndexOf('}');
            if (s != -1 && e > s) {
                JSONObject j = new JSONObject(t.substring(s, e + 1));
                JSONArray arr = j.optJSONArray("oneriler");
                if (arr != null) {
                    String[] out = new String[arr.length()];
                    for (int i = 0; i < arr.length(); i++) out[i] = arr.getString(i);
                    return out;
                }
            }
        } catch (Exception ignored) {
        }
        return new String[]{raw};
    }

    public static String parseAnalysis(String raw) {
        try {
            String t = raw.replace("```json", "").replace("```", "");
            int s = t.indexOf('{');
            int e = t.lastIndexOf('}');
            if (s != -1 && e > s) {
                JSONObject j = new JSONObject(t.substring(s, e + 1));
                return j.optString("analiz", "");
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String read(InputStream is) throws Exception {
        Scanner sc = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
        return sc.hasNext() ? sc.next() : "";
    }

    private static String shorten(String s) {
        return s == null ? "" : (s.length() > 200 ? s.substring(0, 200) : s);
    }
}
