package org.ztbridge.android;
import android.content.Context;
import android.os.SystemClock;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
final class BridgeState {
    static boolean active(Context context, JSONObject state) {
        try {
            android.app.ActivityManager manager = context.getSystemService(android.app.ActivityManager.class);
            for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(20))
                if (service.pid > 0 && service.service.getClassName().equals(BridgeService.class.getName())) return true;
            return false;
        } catch (Exception ignored) { return active(state); }
    }
    static JSONObject read(Context context) {
        try { return new JSONObject(new String(Files.readAllBytes(new File(context.getFilesDir(), "bridge-state.json").toPath()), StandardCharsets.UTF_8)); }
        catch (Exception e) { return new JSONObject(); }
    }
    static void write(Context context, JSONObject state) throws IOException {
        File temp = new File(context.getFilesDir(), "bridge-state-" + android.os.Process.myPid() + ".tmp");
        Files.write(temp.toPath(), state.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(temp.toPath(), new File(context.getFilesDir(), "bridge-state.json").toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    static boolean active(JSONObject state) {
        long age = SystemClock.elapsedRealtime() - state.optLong("heartbeat", -10000);
        return state.optBoolean("active") && age >= 0 && age < 6000;
    }
}
