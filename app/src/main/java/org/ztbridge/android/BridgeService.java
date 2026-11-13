package org.ztbridge.android;

import android.app.*;
import android.content.Intent;
import android.os.*;
import com.zerotier.sockets.ZeroTierNode;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static com.zerotier.sockets.ZeroTierNative.*;

public class BridgeService extends Service {
    static final String CLEAR_LOG = "clear_log";
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
    private final Set<Link> links = ConcurrentHashMap.newKeySet();
    private Semaphore slots;
    private long lastLimitWarning;
    private final AtomicLong uploaded = new AtomicLong(), downloaded = new AtomicLong();
    private final AtomicLong lastReceived = new AtomicLong(-1);
    private volatile boolean nodeOnline, transportReady;
    private volatile String networkFailure = "";
    private final ArrayDeque<JSONObject> events = new ArrayDeque<>();
    private volatile boolean running, listenerReady;
    private volatile ServerSocket listener;
    private ZeroTierNode node;
    private BridgeConfig config = BridgeConfig.defaults();
    private String nodeId = "", ztIp = "", phase = "starting", detail = "Запуск ZeroTier…";
    private boolean failed;
    private long revision, started;
    private String notificationText = "";
    private String routeStatus = "";
    private String profileName = "Основной";
    private final Handler main = new Handler(Looper.getMainLooper());

    private synchronized void log(String level, String message) {
        try { events.add(new JSONObject().put("time", System.currentTimeMillis()).put("level", level).put("message", message)); }
        catch (JSONException ignored) {}
        while (events.size() > 200) events.removeFirst();
        revision++;
        // The heartbeat batches live records; connection bursts must not write a full journal per socket.
        if (!running) publish();
    }
    private synchronized void state(String next, String description) {
        phase = next; detail = description; publish();
    }
    private synchronized void publish() {
        try {
            JSONObject snapshot = new JSONObject().put("active", running).put("heartbeat", SystemClock.elapsedRealtime())
                .put("phase", phase).put("detail", detail).put("nodeId", nodeId).put("ztIp", ztIp)
                .put("config", config.json()).put("connections", links.size()).put("uploaded", uploaded.get())
                .put("downloaded", downloaded.get()).put("started", started).put("revision", revision)
                .put("nodeOnline", nodeOnline).put("networkReady", transportReady)
                .put("lastReceived", lastReceived.get())
                .put("route", routeStatus).put("profileName", profileName)
                .put("events", new JSONArray(events));
            BridgeState.write(this, snapshot);
        } catch (Exception ignored) {}
        if (running && !detail.equals(notificationText)) {
            notificationText = detail;
            getSystemService(NotificationManager.class).notify(1, notification(detail));
        }
    }
    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, BridgeService.class).setAction("stop"), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "bridge").setContentTitle("ZT Local Bridge").setContentText(text)
            .setSmallIcon(R.drawable.ic_bridge).setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Остановить", stop).build()).build();
    }
    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel("bridge", "Подключение ZeroTier", NotificationManager.IMPORTANCE_LOW));
        startForeground(1, notification("Подготовка подключения…"));
        JSONObject previous = BridgeState.read(this);
        nodeId = previous.optString("nodeId");
        JSONArray old = previous.optJSONArray("events");
        if (old != null) for (int i = Math.max(0, old.length() - 200); i < old.length(); i++) {
            JSONObject entry = old.optJSONObject(i); if (entry != null) events.add(entry);
        }
        revision = previous.optLong("revision") + 1;
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && "stop".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (intent != null && CLEAR_LOG.equals(intent.getAction())) {
            synchronized (this) { events.clear(); revision++; publish(); }
            if (!running) stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            try {
                config = BridgeConfig.parse(intent == null ? "" : intent.getStringExtra("config"));
                profileName = intent.getStringExtra("profileName");
                if (profileName == null) profileName = "Основной";
                slots = new Semaphore(config.connectionLimit);
                running = true; started = System.currentTimeMillis();
                state("starting", "Подключаемся к ZeroTier…");
                heartbeat.scheduleWithFixedDelay(this::maintainConnections, 1, 1, TimeUnit.SECONDS);
                workers.execute(this::runBridge);
            } catch (Exception e) { failed = true; state("error", e.getMessage()); log("error", e.toString()); stopSelf(); }
        }
        return START_NOT_STICKY;
    }
    private void check(int result, String operation) throws IOException {
        if (result != 0) throw new IOException(operation + " (код " + result + ")");
    }
    private void maintainConnections() {
        int reaped = 0;
        for (Link link : links) if (link.lifetime.reap(SystemClock.elapsedRealtime())) reaped++;
        if (reaped > 0) log("debug", "Закрываем неактивные TCP-соединения: " + reaped);
        if (listenerReady) refreshRoutes();
        refreshHealth();
        publish();
    }
    private void refreshRoutes() {
        String current = NativeIO.syncRoutes(config.network(), config.remoteIp);
        if (current != null && !current.equals(routeStatus)) {
            routeStatus = current;
            log(current.startsWith("Нет ") ? "warn" : "info", current);
        }
    }
    private void refreshHealth() {
        if (!running || !listenerReady || failed) return;
        try {
            nodeOnline = node.isOnline();
            transportReady = node.isNetworkTransportReady(config.network());
            if (!networkFailure.isEmpty()) return;
            BridgeHealth health = BridgeHealth.evaluate(nodeOnline, transportReady, lastReceived.get(), SystemClock.elapsedRealtime());
            synchronized (this) { phase = health.phase; detail = health.detail; }
        } catch (Exception e) { log("debug", "Не удалось обновить состояние ZeroTier: " + e.getMessage()); }
    }
    private void event(long id, int event) {
        if (!running) return;
        if (event == ZTS_EVENT_NODE_ONLINE) {
            nodeOnline = true;
            log("debug", "ZeroTier сообщает online; состояние VPS определяется отдельно по трафику.");
        } else if (event == ZTS_EVENT_NODE_OFFLINE) {
            nodeOnline = false;
            log("debug", "ZeroTier сообщает offline. Это событие само по себе не подтверждает обрыв связи с VPS.");
        } else if (event == ZTS_EVENT_NETWORK_ACCESS_DENIED) {
            networkFailure = "authorization";
            state("authorization", "Разрешите этот Node ID в панели сети ZeroTier.");
            log("warn", "Сеть отклонила доступ: требуется авторизация устройства.");
        } else if (event == ZTS_EVENT_NETWORK_NOT_FOUND) {
            networkFailure = "not_found";
            state("error", "Сеть не найдена. Проверьте Network ID."); log("error", "Сеть ZeroTier не найдена.");
        } else if (event == ZTS_EVENT_NETWORK_CLIENT_TOO_OLD) {
            networkFailure = "too_old";
            state("error", "Сеть требует более новую версию ZeroTier."); log("error", "Версия ядра ZeroTier устарела для этой сети.");
        } else if (event == ZTS_EVENT_NETWORK_DOWN) {
            transportReady = false;
            log("debug", "ZeroTier обновляет состояние сети. Проверяем готовность и поступление данных отдельно.");
        } else if (event == ZTS_EVENT_NETWORK_READY_IP4 || event == ZTS_EVENT_NETWORK_READY_IP4_IP6 || event == ZTS_EVENT_NETWORK_OK) {
            networkFailure = "";
        } else if (event == ZTS_EVENT_NODE_FATAL_ERROR) {
            failed = true; state("error", "Внутренняя ошибка ядра ZeroTier. Перезапустите подключение.");
            log("error", "Критическая ошибка ZeroTier."); main.post(this::stopSelf);
        }
    }
    private void runBridge() {
        try {
            log("info", "Новое подключение · сеть " + config.networkId);
            System.loadLibrary("zt");
            node = new ZeroTierNode();
            File identity = new File(getFilesDir(), "identity");
            if (!identity.isDirectory() && !identity.mkdirs()) throw new IOException("Не удалось открыть хранилище устройства");
            check(node.initFromStorage(identity.getAbsolutePath()), "Инициализация ZeroTier");
            check(node.initSetEventHandler(this::event), "Обработчик событий");
            check(node.start(), "Запуск ZeroTier");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
            while (running && !node.isOnline()) {
                if (System.nanoTime() > deadline) throw new IOException("ZeroTier не вышел онлайн за 90 секунд. Проверьте интернет и исключение из VPN.");
                Thread.sleep(250);
            }
            if (!running) return;
            synchronized (this) { nodeId = String.format(Locale.ROOT, "%010x", node.getId()); }
            state("joining", "Подключаемся к сети. При первом запуске разрешите Node ID в панели.");
            log("info", "Устройство " + nodeId + " подключается к сети");
            check(node.join(config.network()), "Подключение к сети");
            while (running && !node.isNetworkTransportReady(config.network())) Thread.sleep(300);
            if (!running) return;
            synchronized (this) { ztIp = node.getIPv4Address(config.network()).getHostAddress(); }
            refreshRoutes();
            ServerSocket server = new ServerSocket(); listener = server;
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), config.localPort));
            if (!running) { server.close(); return; }
            listenerReady = true;
            state("ready", "Локальный порт открыт. Можно включать v2rayNG.");
            log("info", "Проброс готов: " + config.local() + " → " + config.remote());
            while (running) {
                Socket socket = server.accept();
                if (!slots.tryAcquire()) {
                    socket.close();
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastLimitWarning >= 5000) {
                        lastLimitWarning = now;
                        log("warn", "Достигнут лимит " + config.connectionLimit + " TCP-соединений. Лимит можно изменить в настройках.");
                    }
                    continue;
                }
                Link link = new Link(socket); links.add(link); workers.execute(link::connect);
            }
        } catch (Throwable e) {
            if (running) {
                failed = true;
                String message = e instanceof BindException ? "Локальный порт занят. Выберите другой порт." : e.getMessage();
                state("error", message == null ? "Ошибка запуска" : message);
                log("error", "Не удалось запустить проброс: " + e); main.post(this::stopSelf);
            }
        }
    }
    private final class Link {
        final Socket local;
        final ConnectionLifetime lifetime;
        int fd = -1;
        volatile boolean closed;
        boolean released;
        Link(Socket socket) {
            local = socket;
            lifetime = new ConnectionLifetime(SystemClock.elapsedRealtime(), () -> {
                try { workers.execute(this::abortIo); }
                catch (RejectedExecutionException e) { abortIo(); }
            }, this::dispose);
        }
        void finishWithoutPumps() {
            lifetime.finish(0, SystemClock.elapsedRealtime());
            lifetime.finish(1, SystemClock.elapsedRealtime());
        }
        void connect() {
            try {
                local.setTcpNoDelay(true);
                local.setSoTimeout(1000);
                synchronized (this) {
                    if (closed || lifetime.cancelled() || !running) { finishWithoutPumps(); return; }
                    fd = zts_bsd_socket(ZTS_AF_INET, ZTS_SOCK_STREAM, 0);
                    if (fd < 0) throw new IOException("Не удалось создать сокет: " + fd);
                }
                int result = zts_connect(fd, config.remoteIp, config.remotePort, 15000);
                if (result < 0) throw new IOException("Не удалось подключиться к " + config.remote() + " (код " + result + ")");
                if (!running || closed || lifetime.cancelled()) { finishWithoutPumps(); return; }
                if (NativeIO.configure(fd) != 0) throw new IOException("Не удалось настроить тайм-ауты TCP");
                lifetime.connected(SystemClock.elapsedRealtime());
                log("debug", "TCP-соединение с " + config.remote() + " установлено");
                workers.execute(() -> pump(true)); pump(false);
            } catch (Exception e) {
                if (running && !closed && !lifetime.cancelled()) log("error", e.getMessage());
                lifetime.cancel(); finishWithoutPumps();
            }
        }
        void pump(boolean upload) {
            try {
                byte[] buffer = new byte[32768];
                if (upload) {
                    int n; InputStream in = local.getInputStream();
                    while (running && !lifetime.cancelled()) {
                        try { n = in.read(buffer); } catch (SocketTimeoutException e) { continue; }
                        if (n == -1) break;
                        int offset = 0;
                        while (offset < n && !lifetime.cancelled()) {
                            int sent = NativeIO.write(fd, buffer, offset, n - offset);
                            if (sent == -2) continue;
                            if (sent <= 0) throw new IOException("Запись в ZeroTier: " + sent);
                            offset += sent; uploaded.addAndGet(sent);
                            lifetime.activity(SystemClock.elapsedRealtime());
                        }
                    }
                    if (!lifetime.cancelled()) zts_bsd_shutdown(fd, ZTS_SHUT_WR);
                } else {
                    OutputStream out = local.getOutputStream(); int n;
                    while (running && !lifetime.cancelled()) {
                        n = NativeIO.read(fd, buffer);
                        if (n == -2) continue;
                        if (n == 0) break;
                        if (n < 0) throw new IOException("Чтение из ZeroTier: " + n);
                        lastReceived.set(SystemClock.elapsedRealtime());
                        out.write(buffer, 0, n); downloaded.addAndGet(n);
                        lifetime.activity(SystemClock.elapsedRealtime());
                    }
                    if (!lifetime.cancelled()) local.shutdownOutput();
                }
            } catch (Exception e) {
                if (running && !closed && !lifetime.cancelled()) log("debug", "TCP-поток закрыт: " + e.getMessage());
                lifetime.cancel();
            }
            finally { lifetime.finish(upload ? 0 : 1, SystemClock.elapsedRealtime()); }
        }
        synchronized void abortIo() {
            if (!closed) {
                closed = true;
                try { local.close(); } catch (IOException ignored) {}
                if (fd >= 0) zts_bsd_shutdown(fd, ZTS_SHUT_RDWR);
            }
        }
        synchronized void dispose() {
            if (!released) {
                released = true;
                abortIo();
                if (fd >= 0) { zts_bsd_close(fd); fd = -1; }
                links.remove(this); slots.release();
            }
        }
    }
    @Override public void onDestroy() {
        running = false; heartbeat.shutdownNow();
        try { if (listener != null) listener.close(); } catch (IOException ignored) {}
        for (Link link : links) link.lifetime.cancel();
        workers.shutdownNow();
        if (!failed) { state("stopped", "Подключение остановлено"); log("info", "Подключение остановлено"); }
        else publish();
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
