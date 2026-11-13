package org.ztbridge.android;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

final class JournalText {
    static boolean visible(String level, int filter) {
        return filter == 1 ? level.equals("error") : filter == 2 || !level.equals("debug");
    }
    static String export(JSONObject state) {
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        StringBuilder out = new StringBuilder("ZT Local Bridge 0.5\nNode ID: ").append(state.optString("nodeId"))
            .append("\nСостояние: ").append(state.optString("detail")).append('\n');
        JSONObject config = state.optJSONObject("config"); if (config != null) out.append("Настройки: ").append(config).append('\n');
        JSONArray entries = state.optJSONArray("events");
        if (entries != null) for (int i = 0; i < entries.length(); i++) {
            JSONObject e = entries.optJSONObject(i); if (e == null) continue;
            out.append(date.format(new Date(e.optLong("time")))).append(" [").append(e.optString("level")).append("] ")
                .append(e.optString("message")).append('\n');
        }
        return out.toString();
    }
}
