package org.ztbridge.android;
import org.json.JSONObject;
final class BridgeConfig {
    final String networkId, remoteIp;
    final int localPort, remotePort, connectionLimit;
    BridgeConfig(String networkId, String remoteIp, String localPort, String remotePort) {
        this(networkId, remoteIp, localPort, remotePort, "256");
    }
    BridgeConfig(String networkId, String remoteIp, String localPort, String remotePort, String connectionLimit) {
        this.networkId = networkId.trim().toLowerCase(java.util.Locale.ROOT);
        this.remoteIp = remoteIp.trim();
        if (!this.networkId.matches("[0-9a-f]{16}") || this.networkId.equals("0000000000000000"))
            throw new IllegalArgumentException("Network ID должен содержать 16 шестнадцатеричных символов");
        String[] parts = this.remoteIp.split("\\.", -1);
        if (parts.length != 4) throw new IllegalArgumentException("Укажите IPv4-адрес VPS в сети ZeroTier");
        for (String part : parts) {
            if (!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part) > 255)
                throw new IllegalArgumentException("Неверный IPv4-адрес VPS");
        }
        this.localPort = port(localPort, "Локальный порт");
        this.remotePort = port(remotePort, "Порт VPS");
        try {
            if (!connectionLimit.trim().matches("[0-9]{1,3}")) throw new NumberFormatException();
            this.connectionLimit = Integer.parseInt(connectionLimit.trim());
            if (this.connectionLimit < 16 || this.connectionLimit > 512) throw new NumberFormatException();
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Лимит соединений: число от 16 до 512"); }
    }
    private static int port(String value, String label) {
        try {
            if (!value.trim().matches("[0-9]{1,5}")) throw new NumberFormatException();
            int port = Integer.parseInt(value.trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException e) { throw new IllegalArgumentException(label + ": число от 1 до 65535"); }
    }
    private BridgeConfig() {
        networkId = ""; remoteIp = "";
        localPort = 15000; remotePort = 443; connectionLimit = 256;
    }
    static BridgeConfig defaults() { return new BridgeConfig(); }
    static BridgeConfig parse(String json) {
        try {
            JSONObject o = new JSONObject(json);
            return new BridgeConfig(o.getString("networkId"), o.getString("remoteIp"), o.get("localPort").toString(), o.get("remotePort").toString(),
                o.has("connectionLimit") ? o.get("connectionLimit").toString() : "256");
        } catch (org.json.JSONException e) { throw new IllegalArgumentException("Не удалось прочитать настройки", e); }
    }
    JSONObject json() {
        JSONObject o = new JSONObject();
        try { o.put("networkId", networkId).put("remoteIp", remoteIp).put("localPort", localPort).put("remotePort", remotePort).put("connectionLimit", connectionLimit); }
        catch (org.json.JSONException e) { throw new IllegalStateException(e); }
        return o;
    }
    long network() { return Long.parseUnsignedLong(networkId, 16); }
    String local() { return "127.0.0.1:" + localPort; }
    String remote() { return remoteIp + ":" + remotePort; }
}
