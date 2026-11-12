package org.ztbridge.android;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

@SuppressLint("SetTextI18n")
public class MainActivity extends Activity {
    private static final int BG = 0xff0b1018, CARD = 0xff141d29, BORDER = 0xff263448;
    private static final int TEXT = 0xffeef4fc, MUTED = 0xff98aac0, ACCENT = 0xff75e4cb, ERROR = 0xffff949c;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView badge, headline, detail, nodeValue, ipValue, route, upload, download, count, formHint;
    private Button connect, save, nodeCopy, tabSettings, tabLogs;
    private EditText network, remoteIp, remotePort, localPort, connectionLimit;
    private LinearLayout settingsPanel, logsPanel;
    private JSONObject snapshot = new JSONObject();
    private boolean active;
    private long startingUntil;
    private int filter;
    private final Runnable refresh = new Runnable() {
        public void run() { renderState(); handler.postDelayed(this, 1000); }
    };

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }
    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v;
    }
    private LinearLayout row() {
        LinearLayout v = new LinearLayout(this); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        v.setLineSpacing(dp(3), 1); return v;
    }
    private void space(LinearLayout layout, int size) { View gap = new View(this); layout.addView(gap, new LinearLayout.LayoutParams(1, dp(size))); }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout box = column(); box.setPadding(dp(20), dp(20), dp(20), dp(20)); box.setBackground(shape(CARD, 24, BORDER));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(16); parent.addView(box, p); return box;
    }
    private Button button(String label, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setTextSize(14); b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(primary ? BG : TEXT); b.setBackground(shape(primary ? ACCENT : 0xff202d3f, 14, 0));
        b.setMinHeight(dp(46)); b.setMinimumHeight(dp(46)); b.setPadding(dp(12), dp(8), dp(12), dp(8));
        b.setStateListAnimator(null); return b;
    }
    private void equal(LinearLayout parent, View child) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(dp(3), 0, dp(3), 0); parent.addView(child, p);
    }
    private EditText field(LinearLayout parent, String label, String value, int type, int id) {
        parent.addView(text(label, 12, MUTED, true)); space(parent, 7);
        EditText v = new EditText(this); v.setId(id); v.setSingleLine(true); v.setText(value); v.setTextSize(16); v.setTextColor(TEXT);
        v.setInputType(type); v.setSelectAllOnFocus(true); v.setPadding(dp(14), dp(12), dp(14), dp(12));
        v.setBackground(shape(0xff0e1621, 12, BORDER));
        v.setMinHeight(dp(50)); parent.addView(v, new LinearLayout.LayoutParams(-1, -2)); space(parent, 17); return v;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout outer = column(); outer.setBackgroundColor(BG);
        outer.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        ScrollView page = new ScrollView(this); page.setFillViewport(true); page.setClipToPadding(false);
        LinearLayout content = column(); content.setPadding(dp(20), dp(24), dp(20), dp(24));
        int available = getResources().getDisplayMetrics().widthPixels;
        android.widget.FrameLayout.LayoutParams contentSize = new android.widget.FrameLayout.LayoutParams(Math.min(available, dp(600)), -2, Gravity.CENTER_HORIZONTAL);
        page.addView(content, contentSize); outer.addView(page, new LinearLayout.LayoutParams(-1, -1)); setContentView(outer);

        LinearLayout header = row();
        ImageView icon = new ImageView(this); icon.setImageResource(R.drawable.ic_bridge); icon.setColorFilter(ACCENT);
        icon.setBackground(shape(0xff193830, 16, 0)); icon.setPadding(dp(12), dp(12), dp(12), dp(12));
        header.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout brand = column(); brand.setPadding(dp(14), 0, 0, 0);
        brand.addView(text("ZT Bridge", 27, TEXT, true)); brand.addView(text("Твоя сеть. Локальное подключение.", 12, MUTED, false));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1)); content.addView(header); space(content, 24);

        LinearLayout status = card(content);
        badge = text("●  ОТКЛЮЧЕНО", 11, MUTED, true); status.addView(badge); space(status, 12);
        headline = text("Готов к подключению", 24, TEXT, true); status.addView(headline); space(status, 7);
        detail = text("Запустите мост, затем включите профиль v2rayNG.", 14, MUTED, false); status.addView(detail); space(status, 20);
        LinearLayout identity = row(); LinearLayout values = column();
        values.addView(text("NODE ID", 10, MUTED, true)); nodeValue = text("Появится после запуска", 16, TEXT, true); nodeValue.setTextIsSelectable(true); values.addView(nodeValue);
        identity.addView(values, new LinearLayout.LayoutParams(0, -2, 1));
        nodeCopy = button("Копировать", false); nodeCopy.setTextSize(12); nodeCopy.setOnClickListener(v -> copy("Node ID", snapshot.optString("nodeId"))); identity.addView(nodeCopy);
        status.addView(identity); space(status, 12);
        ipValue = text("ZeroTier IP  —", 12, MUTED, false); status.addView(ipValue); space(status, 18);
        View divider = new View(this); divider.setBackgroundColor(BORDER); status.addView(divider, new LinearLayout.LayoutParams(-1, dp(1))); space(status, 16);
        LinearLayout stats = row();
        upload = stat(stats, "ОТПРАВЛЕНО"); download = stat(stats, "ПОЛУЧЕНО"); count = stat(stats, "СОЕДИНЕНИЙ"); status.addView(stats); space(status, 20);
        connect = button("Подключить", true); status.addView(connect, new LinearLayout.LayoutParams(-1, dp(52))); connect.setOnClickListener(v -> toggleConnection());

        LinearLayout tabs = row(); tabs.setBackground(shape(CARD, 16, 0)); tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabSettings = button("Подключение", false); tabLogs = button("Журнал", false); equal(tabs, tabSettings); equal(tabs, tabLogs);
        content.addView(tabs); space(content, 16);
        settingsPanel = column(); logsPanel = column(); content.addView(settingsPanel); content.addView(logsPanel);
        tabSettings.setOnClickListener(v -> selectTab(false)); tabLogs.setOnClickListener(v -> startActivity(new Intent(this, LogActivity.class)));
        if (state != null) filter = state.getInt("filter");
        buildSettings(); selectTab(false);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        renderState();
    }
    private TextView stat(LinearLayout parent, String label) {
        LinearLayout box = column(); TextView value = text("0", 18, TEXT, true); box.addView(value); box.addView(text(label, 9, MUTED, true));
        equal(parent, box); return value;
    }
    private void buildSettings() {
        BridgeConfig config;
        try { config = BridgeConfig.parse(getPreferences(0).getString("config", "")); }
        catch (Exception e) { config = BridgeConfig.defaults(); }
        LinearLayout box = card(settingsPanel);
        box.addView(text("Настройки моста", 19, TEXT, true)); space(box, 5);
        formHint = text("Сохраняются на этом устройстве", 12, MUTED, false); box.addView(formHint); space(box, 22);
        network = field(box, "СЕТЬ · NETWORK ID", config.networkId, android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, 101);
        network.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16)});
        remoteIp = field(box, "IP VPS В СЕТИ ZEROTIER", config.remoteIp, android.text.InputType.TYPE_CLASS_PHONE, 102);
        LinearLayout ports = row(); LinearLayout left = column(), right = column();
        equal(ports, left); equal(ports, right);
        localPort = field(left, "ЛОКАЛЬНЫЙ ПОРТ", "" + config.localPort, android.text.InputType.TYPE_CLASS_NUMBER, 103);
        remotePort = field(right, "ПОРТ VPS", "" + config.remotePort, android.text.InputType.TYPE_CLASS_NUMBER, 104); box.addView(ports);
        connectionLimit = field(box, "ЛИМИТ TCP-СОЕДИНЕНИЙ · 16–512", "" + config.connectionLimit, android.text.InputType.TYPE_CLASS_NUMBER, 105);
        box.addView(text("По умолчанию 256. Больший лимит увеличивает расход памяти. Зависшие соединения очищаются автоматически.", 12, MUTED, false)); space(box, 18);
        route = text("", 13, ACCENT, true); box.addView(route); space(box, 18);
        save = button("Сохранить настройки", false); box.addView(save, new LinearLayout.LayoutParams(-1, dp(46))); save.setOnClickListener(v -> saveConfig());
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { updateRoute(); }
            public void afterTextChanged(Editable s) {}
        };
        for (EditText field : new EditText[]{network, remoteIp, remotePort, localPort}) field.addTextChangedListener(watcher);
        updateRoute();
        LinearLayout help = card(settingsPanel); help.addView(text("Как подключить v2rayNG", 16, TEXT, true)); space(help, 10);
        help.addView(text("1. Отключите обычный ZeroTier.\n2. Исключите ZT Local Bridge из VPN.\n3. В профиле укажите 127.0.0.1 и локальный порт.\n4. Сохраните параметры TLS / REALITY.", 13, MUTED, false));
        space(help, 10); help.addView(text("При смене сети разрешите этот Node ID в её панели управления.", 12, MUTED, false));
    }
    private void selectTab(boolean logs) {
        settingsPanel.setVisibility(logs ? View.GONE : View.VISIBLE); logsPanel.setVisibility(logs ? View.VISIBLE : View.GONE);
        tabSettings.setBackground(shape(logs ? CARD : 0xff293a50, 12, 0)); tabLogs.setBackground(shape(logs ? 0xff293a50 : CARD, 12, 0));
        tabSettings.setTextColor(logs ? MUTED : TEXT); tabLogs.setTextColor(logs ? TEXT : MUTED);
    }
    private void updateRoute() { route.setText(remoteIp.getText().toString().trim().isEmpty() ? "Укажите адрес VPS для проброса" : "127.0.0.1:" + localPort.getText() + "  →  " + remoteIp.getText() + ":" + remotePort.getText()); }
    private BridgeConfig readConfig() { return new BridgeConfig(network.getText().toString(), remoteIp.getText().toString(), localPort.getText().toString(), remotePort.getText().toString(), connectionLimit.getText().toString()); }
    private BridgeConfig saveConfig() {
        try {
            BridgeConfig config = readConfig();
            getPreferences(0).edit().putString("config", config.json().toString()).apply();
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(network.getWindowToken(), 0);
            Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show(); return config;
        } catch (IllegalArgumentException e) { new AlertDialog.Builder(this).setTitle("Проверьте настройки").setMessage(e.getMessage()).setPositiveButton("Понятно", null).show(); return null; }
    }
    private void toggleConnection() {
        if (active) { stopService(new Intent(this, BridgeService.class)); connect.setEnabled(false); connect.setText("Останавливаем…"); }
        else {
            BridgeConfig config = saveConfig(); if (config == null) return;
            startingUntil = SystemClock.elapsedRealtime() + 5000;
            try { startForegroundService(new Intent(this, BridgeService.class).putExtra("config", config.json().toString())); }
            catch (Exception e) { startingUntil = 0; Toast.makeText(this, "Не удалось запустить: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
            renderState();
        }
    }
    private void renderState() {
        snapshot = BridgeState.read(this); active = BridgeState.active(this, snapshot);
        if (active) startingUntil = 0;
        boolean pending = startingUntil > SystemClock.elapsedRealtime();
        boolean busy = active || pending;
        String phase = snapshot.optString("phase", "stopped");
        boolean stale = snapshot.optBoolean("active") && (!active || !BridgeState.active(snapshot)) && !pending;
        String title, label; int tint = MUTED;
        if (pending) { title = "Запускаем мост…"; label = "●  ПОДКЛЮЧЕНИЕ"; tint = ACCENT; }
        else if (stale) { title = "Статус задерживается"; label = active ? "●  СЛУЖБА РАБОТАЕТ" : "●  ОБНОВЛЕНИЕ НЕДОСТУПНО"; tint = 0xfff6ce86; }
        else switch (phase) {
            case "traffic": title = "Передача данных"; label = "●  ДАННЫЕ ОТ VPS"; tint = ACCENT; break;
            case "ready": title = "Мост готов"; label = "●  СЕТЬ ГОТОВА"; tint = ACCENT; break;
            case "degraded": title = "Мост запущен"; label = "●  СВЯЗЬ НЕ ПОДТВЕРЖДЕНА"; tint = 0xfff6ce86; break;
            case "starting": case "joining": title = "Подключаемся…"; label = "●  ПОДКЛЮЧЕНИЕ"; tint = ACCENT; break;
            case "authorization": title = "Нужен доступ к сети"; label = "●  АВТОРИЗАЦИЯ"; tint = 0xfff6ce86; break;
            case "offline": title = "Ожидаем интернет"; label = "●  НЕТ СВЯЗИ"; tint = 0xfff6ce86; break;
            case "error": title = "Не удалось подключиться"; label = "●  ОШИБКА"; tint = ERROR; break;
            default: title = "Готов к подключению"; label = "●  ОТКЛЮЧЕНО";
        }
        headline.setText(title); badge.setText(label); badge.setTextColor(tint);
        detail.setText(pending ? "Подготавливаем ZeroTier и локальный порт…" : stale ? "Данные интерфейса давно не обновлялись. Это само по себе не подтверждает обрыв передачи." : snapshot.optString("detail", "Запустите мост, затем включите профиль v2rayNG."));
        String id = snapshot.optString("nodeId"); nodeValue.setText(id.isEmpty() ? "Появится после запуска" : id); nodeCopy.setEnabled(!id.isEmpty()); nodeCopy.setAlpha(id.isEmpty() ? .4f : 1f);
        String ip = snapshot.optString("ztIp"); ipValue.setText("ZeroTier IP  " + (ip.isEmpty() || !active ? "—" : ip));
        upload.setText(bytes(snapshot.optLong("uploaded"))); download.setText(bytes(snapshot.optLong("downloaded")));
        JSONObject currentConfig = snapshot.optJSONObject("config");
        count.setTextSize(16); count.setText(active ? snapshot.optInt("connections") + " / " + (currentConfig == null ? 256 : currentConfig.optInt("connectionLimit", 256)) : "0");
        connect.setEnabled(!pending); connect.setText(pending ? "Запускаем…" : active ? "Остановить" : "Подключить");
        connect.setBackground(shape(active ? 0xff293a50 : ACCENT, 14, 0)); connect.setTextColor(active ? TEXT : BG);
        for (EditText field : new EditText[]{network, remoteIp, remotePort, localPort, connectionLimit}) { field.setEnabled(!busy); field.setAlpha(busy ? .55f : 1f); }
        save.setEnabled(!busy); save.setAlpha(busy ? .5f : 1f); formHint.setText(busy ? "Для изменения настроек остановите подключение" : "Сохраняются на этом устройстве");
    }
    private String bytes(long n) {
        if (n < 1024) return n + " Б";
        if (n < 1048576) return String.format(Locale.ROOT, "%.1f КБ", n / 1024.0);
        if (n < 1073741824) return String.format(Locale.ROOT, "%.1f МБ", n / 1048576.0);
        return String.format(Locale.ROOT, "%.2f ГБ", n / 1073741824.0);
    }
    private void copy(String label, String value) {
        ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(label, value));
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putInt("filter", filter);
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
