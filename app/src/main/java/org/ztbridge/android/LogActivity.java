package org.ztbridge.android;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** The journal has one scroll owner. Reading old entries pauses live replacement. */
@SuppressLint({"SetTextI18n", "ClickableViewAccessibility"})
public class LogActivity extends Activity {
    private static final int BG = 0xff0b1018, CARD = 0xff141d29, TEXT = 0xffeef4fc, MUTED = 0xff98aac0, ACCENT = 0xff75e4cb;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);
    private ScrollView scroll;
    private TextView body, count;
    private Switch follow;
    private Button newer;
    private int filter, restoreY;
    private String displayedKey = "";
    private boolean first = true;
    private final Runnable poll = new Runnable() {
        public void run() { refresh(false); handler.postDelayed(this, 1000); }
    };
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v;
    }
    private GradientDrawable shape(int color) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(14)); return d;
    }
    private Button button(String value) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(13); b.setTextColor(TEXT);
        b.setBackground(shape(CARD)); b.setPadding(dp(10), dp(8), dp(10), dp(8)); b.setMinHeight(dp(44)); b.setMinimumHeight(dp(44)); return b;
    }
    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); return r; }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        boolean landscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if (saved != null) { filter = saved.getInt("filter"); restoreY = saved.getInt("scroll"); }
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(dp(16) + bars.left, dp(12) + bars.top, dp(16) + bars.right, dp(12) + bars.bottom);
            } else view.setPadding(dp(16), dp(12) + insets.getSystemWindowInsetTop(), dp(16), dp(12) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header = row(); Button back = button("←"); back.setContentDescription("Назад");
        back.setOnClickListener(v -> finish()); header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("Журнал", 24, TEXT); title.setTypeface(null, Typeface.BOLD); title.setPadding(dp(14), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1)); count = text("0 / 200", 12, MUTED); header.addView(count); root.addView(header);
        TextView hint = text("Прокрутка останавливает слежение. Записи не будут прыгать во время чтения.", 12, MUTED); hint.setPadding(0, dp(12), 0, dp(8));
        if (!landscape) root.addView(hint);
        Spinner filters = new Spinner(this);
        filters.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{"Основные события", "Только ошибки", "Все, включая TCP"}));
        filters.setSelection(filter);
        if (landscape) header.addView(filters, new LinearLayout.LayoutParams(dp(240), dp(48)));
        else root.addView(filters, new LinearLayout.LayoutParams(-1, dp(48)));
        filters.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                if (filter != position) { filter = position; displayedKey = ""; refresh(true); }
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        scroll = new ScrollView(this); scroll.setBackground(shape(CARD)); scroll.setFillViewport(true);
        body = text("Здесь появятся события подключения", 13, TEXT); body.setTypeface(Typeface.MONOSPACE);
        body.setLineSpacing(dp(4), 1); body.setTextIsSelectable(true); body.setPadding(dp(14), dp(14), dp(14), dp(14));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams viewport = new LinearLayout.LayoutParams(-1, 0, 1); viewport.topMargin = dp(8); viewport.bottomMargin = dp(8); root.addView(scroll, viewport);
        follow = new Switch(this); follow.setText("Следить за новыми записями"); follow.setTextColor(MUTED); follow.setTextSize(12);
        follow.setChecked(saved == null || saved.getBoolean("follow")); root.addView(follow);
        scroll.setOnTouchListener((v, e) -> {
            // Observe only: returning false keeps ScrollView's native gestures and accessibility handling.
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN || e.getActionMasked() == MotionEvent.ACTION_MOVE) follow.setChecked(false);
            return false;
        });
        follow.setOnCheckedChangeListener((v, checked) -> { if (checked) refresh(true); });
        newer = button("Новые записи ↓"); newer.setTextColor(ACCENT); newer.setVisibility(View.GONE);
        newer.setOnClickListener(v -> { follow.setChecked(true); refresh(true); }); root.addView(newer, new LinearLayout.LayoutParams(-1, dp(44)));
        LinearLayout actions = row(); Button copy = button("Копировать всё"), share = button("Поделиться"), clear = button("Очистить");
        for (Button b : new Button[]{copy, share, clear}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(dp(2), dp(8), dp(2), 0); actions.addView(b, p);
        }
        root.addView(actions);
        copy.setOnClickListener(v -> {
            ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Журнал ZT Bridge", JournalText.export(BridgeState.read(this))));
            if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, "Журнал скопирован", Toast.LENGTH_SHORT).show();
        });
        share.setOnClickListener(v -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, JournalText.export(BridgeState.read(this))), "Журнал подключения")));
        clear.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Очистить журнал?").setMessage("Подключение продолжит работать.")
            .setNegativeButton("Отмена", null).setPositiveButton("Очистить", (dialog, which) -> clear()).show());
        setContentView(root); refresh(true);
    }
    private void refresh(boolean explicit) {
        if (body == null) return;
        JSONObject state = BridgeState.read(this);
        String key = state.optLong("revision") + ":" + filter;
        if (!first && key.equals(displayedKey)) {
            if (explicit && follow.isChecked()) scroll.post(() -> scroll.scrollTo(0, Math.max(0, body.getHeight() - scroll.getHeight())));
            return;
        }
        if (!first && !explicit && (!follow.isChecked() || body.hasSelection())) { newer.setVisibility(View.VISIBLE); return; }
        int oldY = scroll.getScrollY(); boolean initial = first; first = false; displayedKey = key;
        SpannableStringBuilder lines = new SpannableStringBuilder(); JSONArray events = state.optJSONArray("events"); int shown = 0;
        if (events != null) for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i); if (e == null || !JournalText.visible(e.optString("level"), filter)) continue;
            String level = e.optString("level"); int start = lines.length();
            String name = level.equals("error") ? "ОШИБКА" : level.equals("warn") ? "ВНИМАНИЕ" : level.equals("debug") ? "TCP" : "СОБЫТИЕ";
            lines.append(clock.format(new Date(e.optLong("time")))).append(" · ").append(name).append('\n');
            lines.setSpan(new ForegroundColorSpan(level.equals("error") ? 0xffff949c : level.equals("warn") ? 0xfff6ce86 : ACCENT), start, lines.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            lines.append(e.optString("message")).append("\n\n"); shown++;
        }
        count.setText(shown + " / 200"); body.setText(shown == 0 ? filter == 1 ? "Ошибок нет" : "Здесь появятся события подключения" : lines);
        newer.setVisibility(View.GONE);
        scroll.post(() -> { if (follow.isChecked()) scroll.scrollTo(0, Math.max(0, body.getHeight() - scroll.getHeight())); else scroll.scrollTo(0, initial ? restoreY : oldY); });
    }
    private void clear() {
        JSONObject state = BridgeState.read(this);
        if (BridgeState.active(this, state)) startService(new Intent(this, BridgeService.class).setAction(BridgeService.CLEAR_LOG));
        else try { state.put("events", new JSONArray()).put("revision", state.optLong("revision") + 1); BridgeState.write(this, state); }
        catch (Exception e) { Toast.makeText(this, "Не удалось очистить журнал", Toast.LENGTH_SHORT).show(); }
        displayedKey = ""; refresh(true); handler.postDelayed(() -> refresh(true), 300);
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putInt("filter", filter); out.putBoolean("follow", follow.isChecked()); out.putInt("scroll", scroll.getScrollY());
    }
    @Override protected void onResume() { super.onResume(); handler.post(poll); }
    @Override protected void onPause() { handler.removeCallbacks(poll); super.onPause(); }
}
