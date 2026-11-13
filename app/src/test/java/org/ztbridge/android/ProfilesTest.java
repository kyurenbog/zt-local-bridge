package org.ztbridge.android;
import org.junit.Test;
import static org.junit.Assert.*;
public class ProfilesTest {
    @Test public void migratesExistingConfigWithoutChangingIdentityOrEndpoints() {
        String config = new BridgeConfig("0123456789abcdef", "10.2.3.4", "15000", "443", "128").json().toString();
        Profiles migrated = Profiles.load("", config);
        assertEquals("Основной", migrated.current().name);
        assertEquals("10.2.3.4", migrated.current().config.optString("remoteIp"));
        assertEquals(128, migrated.current().config.optInt("connectionLimit"));
    }
    @Test public void switchingProfilesPreservesDraftsAndSelectionAcrossRestart() throws Exception {
        Profiles profiles = Profiles.load("", "");
        String first = profiles.selected;
        profiles.current().config.put("remoteIp", "10.2.3.4");
        profiles.add("Дом"); profiles.current().config.put("networkId", "fedcba9876543210");
        Profiles restored = Profiles.load(profiles.json(), "");
        assertEquals("Дом", restored.current().name);
        assertEquals("fedcba9876543210", restored.current().config.optString("networkId"));
        restored.selected = first;
        assertEquals("10.2.3.4", restored.current().config.optString("remoteIp"));
    }
    @Test public void renameAndDeleteAreSafe() {
        Profiles profiles = Profiles.load("", ""); profiles.add("Работа"); profiles.rename("Офис");
        assertEquals("Офис", profiles.current().name);
        try { profiles.rename("Основной"); fail(); } catch (IllegalArgumentException expected) {}
        profiles.removeCurrent(); assertEquals("Основной", profiles.current().name);
        try { profiles.removeCurrent(); fail(); } catch (IllegalArgumentException expected) {}
        try { profiles.add("  "); fail(); } catch (IllegalArgumentException expected) {}
    }
}
