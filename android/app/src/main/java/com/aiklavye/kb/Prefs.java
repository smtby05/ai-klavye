package com.aiklavye.kb;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static final String NAME = "ak_prefs";

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public String baseUrl() { return get("baseUrl", "https://api.openai.com/v1"); }
    public void baseUrl(String v) { set("baseUrl", v); }

    public String apiKey() { return get("apiKey", ""); }
    public void apiKey(String v) { set("apiKey", v); }

    public String model() { return get("model", "gpt-4o-mini"); }
    public void model(String v) { set("model", v); }

    public String userName() { return get("userName", ""); }
    public void userName(String v) { set("userName", v); }

    public String persona() {
        return get("persona", "Doğal, samimi ve kısa mesajlar yazarım.");
    }
    public void persona(String v) { set("persona", v); }

    private String get(String k, String def) {
        return sp.getString(k, def);
    }

    private void set(String k, String v) {
        sp.edit().putString(k, v == null ? "" : v.trim()).apply();
    }
}
