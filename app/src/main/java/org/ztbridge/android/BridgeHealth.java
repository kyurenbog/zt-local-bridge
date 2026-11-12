package org.ztbridge.android;

/** A root-node status event is not an end-to-end test of the VPS connection. */
final class BridgeHealth {
    final String phase, detail;
    private BridgeHealth(String phase, String detail) { this.phase = phase; this.detail = detail; }
    static BridgeHealth evaluate(boolean online, boolean networkReady, long lastReceived, long now) {
        boolean receiving = lastReceived >= 0 && now >= lastReceived && now - lastReceived < 10_000;
        if (receiving) return new BridgeHealth("traffic", online
            ? "Получаем данные от VPS через ZeroTier."
            : "Данные от VPS поступают. ZeroTier сообщает offline; это не остановило текущую передачу.");
        if (!online) return new BridgeHealth("degraded", "Мост запущен. ZeroTier сообщает offline; доступность VPS сейчас не подтверждена.");
        if (!networkReady) return new BridgeHealth("degraded", "Мост запущен, но ZeroTier пока не подтверждает готовность сети. Ожидаем обновления.");
        return new BridgeHealth("ready", "Локальный порт открыт, сеть ZeroTier готова. Отсутствие трафика само по себе не означает обрыв.");
    }
}
