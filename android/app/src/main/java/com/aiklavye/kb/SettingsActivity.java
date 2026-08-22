package com.aiklavye.kb;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private static final String[][] PRESETS = {
            {"OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"},
            {"Groq (ücretsiz)", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"},
            {"OpenRouter", "https://openrouter.ai/api/v1", "google/gemini-2.0-flash-001"},
            {"Ollama (yerel)", "http://10.0.2.2:11434", "qwen2.5"}
    };

    private Spinner spProvider;
    private EditText etBaseUrl, etApiKey, etModel, etUserName, etPersona;
    private TextView tvStatus;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = new Prefs(this);

        spProvider = findViewById(R.id.spProvider);
        etBaseUrl = findViewById(R.id.etBaseUrl);
        etApiKey = findViewById(R.id.etApiKey);
        etModel = findViewById(R.id.etModel);
        etUserName = findViewById(R.id.etUserName);
        etPersona = findViewById(R.id.etPersona);
        tvStatus = findViewById(R.id.tvStatus);

        String[] names = new String[PRESETS.length];
        for (int i = 0; i < PRESETS.length; i++) names[i] = PRESETS[i][0];
        spProvider.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, names));
        spProvider.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                etBaseUrl.setText(PRESETS[pos][1]);
                etModel.setText(PRESETS[pos][2]);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> p) {
            }
        });

        fillForm();

        findViewById(R.id.btnSave).setOnClickListener(v -> {
            prefs.baseUrl(etBaseUrl.getText().toString());
            prefs.apiKey(etApiKey.getText().toString());
            prefs.model(etModel.getText().toString());
            prefs.userName(etUserName.getText().toString());
            prefs.persona(etPersona.getText().toString());
            toast("✓ Ayarlar kaydedildi");
            fillForm();
        });

        findViewById(R.id.btnTest).setOnClickListener(v -> test());

        findViewById(R.id.btnEnable).setOnClickListener(v ->
                startActivity(new Intent("android.settings.INPUT_METHOD_SETTINGS")));

        findViewById(R.id.btnMicPerm).setOnClickListener(v -> requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO}, 1));
    }

    private void fillForm() {
        String base = prefs.baseUrl();
        int presetIdx = 0;
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i][1].equals(base)) presetIdx = i;
        }
        spProvider.setSelection(presetIdx);
        etBaseUrl.setText(base);
        etApiKey.setText(prefs.apiKey());
        etModel.setText(prefs.model());
        etUserName.setText(prefs.userName());
        etPersona.setText(prefs.persona());
    }

    private void test() {
        prefs.baseUrl(etBaseUrl.getText().toString());
        prefs.apiKey(etApiKey.getText().toString());
        prefs.model(etModel.getText().toString());
        tvStatus.setText("Bağlanıyor...");
        AiClient.ask(this, "merhaba",
                new AiClient.Cb() {
                    @Override
                    public void ok(String result) {
                        tvStatus.setTextColor(0xFF2DD4A7);
                        tvStatus.setText("✓ Bağlantı başarılı!");
                    }

                    @Override
                    public void err(String message) {
                        tvStatus.setTextColor(0xFFFF5C7A);
                        tvStatus.setText("⚠ " + message);
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == 1) {
            boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            tvStatus.setTextColor(ok ? 0xFF2DD4A7 : 0xFFFF5C7A);
            tvStatus.setText(ok ? "✓ Mikrofon izni verildi" : "⚠ İzin reddedildi");
        }
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}
