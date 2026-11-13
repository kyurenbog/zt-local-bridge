package org.ztbridge.android;

import org.json.*;
import java.util.*;

final class Profiles {
    static final class Profile {
        final String id;
        String name;
        JSONObject config;
        Profile(String id, String name, JSONObject config) { this.id = id; this.name = name; this.config = config; }
        @Override public String toString() { return name; }
    }
    final List<Profile> items = new ArrayList<>();
    String selected;
    private static String name(String value) {
        value = value.trim();
        if (value.isEmpty() || value.length() > 60) throw new IllegalArgumentException("Имя профиля: от 1 до 60 символов");
        return value;
    }
    private void unique(String value, String except) {
        for (Profile p : items) if (!p.id.equals(except) && p.name.equalsIgnoreCase(value))
            throw new IllegalArgumentException("Профиль с таким именем уже есть");
    }
    Profile current() {
        for (Profile p : items) if (p.id.equals(selected)) return p;
        throw new IllegalStateException("Профиль не найден");
    }
    Profile add(String value) {
        if (items.size() >= 64) throw new IllegalArgumentException("Можно сохранить до 64 профилей");
        value = name(value); unique(value, "");
        Profile p = new Profile(UUID.randomUUID().toString(), value, BridgeConfig.defaults().json());
        items.add(p); selected = p.id; return p;
    }
    void rename(String value) { value = name(value); unique(value, selected); current().name = value; }
    void removeCurrent() {
        if (items.size() == 1) throw new IllegalArgumentException("Последний профиль удалить нельзя");
        items.remove(current()); selected = items.get(0).id;
    }
    static Profiles load(String raw, String legacy) {
        Profiles profiles = new Profiles();
        try {
            if (raw != null && !raw.isEmpty()) {
                JSONObject root = new JSONObject(raw); JSONArray list = root.getJSONArray("profiles");
                HashSet<String> ids = new HashSet<>();
                for (int i = 0; i < list.length(); i++) {
                    JSONObject p = list.getJSONObject(i); String id = p.getString("id"); String label = name(p.getString("name"));
                    if (!ids.add(id) || list.length() > 64) throw new JSONException("Invalid profile list");
                    profiles.items.add(new Profile(id, label, p.getJSONObject("config")));
                }
                if (profiles.items.isEmpty()) throw new JSONException("Empty profile list");
                profiles.selected = root.optString("selected");
                if (!ids.contains(profiles.selected)) profiles.selected = profiles.items.get(0).id;
                return profiles;
            }
            Profile first = profiles.add("Основной");
            if (legacy != null && !legacy.isEmpty()) first.config = new JSONObject(legacy);
            return profiles;
        } catch (JSONException e) { throw new IllegalArgumentException("Не удалось прочитать профили", e); }
    }
    String json() {
        try {
            JSONArray list = new JSONArray();
            for (Profile p : items) list.put(new JSONObject().put("id", p.id).put("name", p.name).put("config", p.config));
            return new JSONObject().put("selected", selected).put("profiles", list).toString();
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }
}
