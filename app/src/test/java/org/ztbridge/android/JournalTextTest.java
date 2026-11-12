package org.ztbridge.android;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class JournalTextTest {
    @Test public void filtersKeepErrorsVisibleWithoutTcpNoise() {
        assertTrue(JournalText.visible("error", 0)); assertTrue(JournalText.visible("warn", 0));
        assertFalse(JournalText.visible("debug", 0)); assertTrue(JournalText.visible("debug", 2));
        assertFalse(JournalText.visible("info", 1)); assertTrue(JournalText.visible("error", 1));
    }
    @Test public void fullExportContainsAllLevelsAndMultilineErrors() throws Exception {
        JSONObject state = new JSONObject().put("nodeId", "1234567890").put("events", new JSONArray()
            .put(new JSONObject().put("time", 1000).put("level", "debug").put("message", "TCP connected"))
            .put(new JSONObject().put("time", 2000).put("level", "error").put("message", "Read failed\nDetails")));
        String out = JournalText.export(state);
        assertTrue(out.contains("1234567890")); assertTrue(out.contains("[debug] TCP connected"));
        assertTrue(out.contains("[error] Read failed\nDetails"));
    }
}
